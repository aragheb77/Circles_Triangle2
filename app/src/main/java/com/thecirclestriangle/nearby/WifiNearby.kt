package com.thecirclestriangle.nearby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.thecirclestriangle.engine.NET_PROTOCOL_VERSION
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/** The phone's own IPv4 address on the local network (shown to the host so a friend can type it if discovery fails). */
fun localIpv4(): String? = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
} catch (e: Exception) { null }

private fun wrap(socket: Socket, label: String) = Connection(
    socket.getInputStream(), socket.getOutputStream(),
    { try { socket.close() } catch (e: IOException) { } }, label,
)

/**
 * Hosts a game on the local network: opens a TCP port, advertises it with mDNS / DNS-SD (NsdManager) so the
 * other phone sees it in its list, and accepts exactly one player. Needs no internet connection.
 */
class WifiHost(
    context: Context,
    private val displayName: String,
    private val rows: Int,
    private val onClient: (Connection) -> Unit,
    private val onFail: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var stopped = false

    /** Starts listening. Returns the local port, or null if it failed (then [onFail] was called). */
    fun start(): Int? {
        val srv = try {
            try { ServerSocket(WIFI_PORT) } catch (e: IOException) { ServerSocket(0) }
        } catch (e: IOException) {
            onFail("Could not open a connection on this network.")
            return null
        }
        server = srv
        advertise(srv.localPort)
        Thread({
            try {
                val sock = srv.accept()
                sock.tcpNoDelay = true
                sock.keepAlive = true
                if (stopped) { sock.close(); return@Thread }
                stop()                                   // one player is enough: stop advertising and listening
                val conn = wrap(sock, "Wi-Fi")
                main.post { onClient(conn) }
            } catch (e: IOException) {
                if (!stopped) main.post { onFail("The connection was interrupted. Please try again.") }
            }
        }, "wifi-accept").apply { isDaemon = true }.start()
        return srv.localPort
    }

    private fun advertise(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "CT-" + (1..4).map { "abcdefghjkmnpqrstuvwxyz23456789"[Random.nextInt(31)] }.joinToString("")
            serviceType = WIFI_SERVICE_TYPE
            setPort(port)
            setAttribute("n", displayName)
            setAttribute("r", rows.toString())
            setAttribute("v", NET_PROTOCOL_VERSION.toString())
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { /* still reachable by IP address */ }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        registration = l
        try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { registration = null }
    }

    fun stop() {
        stopped = true
        registration?.let { try { nsd.unregisterService(it) } catch (e: Exception) { } }
        registration = null
        try { server?.close() } catch (e: IOException) { }
        server = null
    }
}

/** Looks for games advertised on the local network and keeps [onPeers] up to date as they appear / disappear. */
class WifiScanner(
    context: Context,
    private val onPeers: (List<Peer>) -> Unit,
    private val onFail: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val peers = LinkedHashMap<String, Peer>()       // main thread only
    private val queue = ArrayDeque<NsdServiceInfo>()        // main thread only
    private val attempts = HashMap<String, Int>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null
    @Volatile private var stopped = false

    fun start() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { if (!stopped) onFail("Could not search this network.") } }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains("_ctriangle")) return
                main.post { if (!stopped) { queue.addLast(info); pump() } }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { if (peers.remove(info.serviceName) != null && !stopped) onPeers(peers.values.toList()) }
            }
        }
        listener = l
        try { nsd.discoverServices(WIFI_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
        catch (e: Exception) { onFail("Could not search this network.") }
    }

    /** NsdManager only allows one resolve at a time, so found services are resolved one after another. */
    private fun pump() {
        if (resolving || queue.isEmpty() || stopped) return
        resolving = true
        val info = queue.removeFirst()
        val done = { main.post { resolving = false; pump() } }
        @Suppress("DEPRECATION")
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) {
                    val n = (attempts[i.serviceName] ?: 0) + 1
                    attempts[i.serviceName] = n
                    main.post {
                        resolving = false
                        if (n <= 3 && !stopped) main.postDelayed({ queue.addLast(info); pump() }, 400L * n)
                        pump()
                    }
                }
                override fun onServiceResolved(r: NsdServiceInfo) {
                    main.post {
                        resolving = false
                        if (!stopped) toPeer(r)?.let { peers[r.serviceName] = it; onPeers(peers.values.toList()) }
                        pump()
                    }
                }
            })
        } catch (e: Exception) { done() }
    }

    private fun toPeer(r: NsdServiceInfo): Peer? {
        fun attr(k: String) = r.attributes[k]?.toString(Charsets.UTF_8)
        if (attr("v")?.toIntOrNull() != NET_PROTOCOL_VERSION) return null     // other app version: cannot play together
        val address = if (Build.VERSION.SDK_INT >= 34)
            (r.hostAddresses.firstOrNull { it is Inet4Address } ?: r.hostAddresses.firstOrNull())?.hostAddress
        else @Suppress("DEPRECATION") r.host?.hostAddress
        address ?: return null
        val name = com.thecirclestriangle.engine.NetProtocol.cleanName(attr("n") ?: "").ifEmpty { "Player" }
        val rows = attr("r")?.toIntOrNull()
        return Peer(r.serviceName, name, if (rows != null) "Board with $rows rows • Wi-Fi" else "Wi-Fi", Transport.WIFI, address, r.port)
    }

    fun stop() {
        stopped = true
        listener?.let { try { nsd.stopServiceDiscovery(it) } catch (e: Exception) { } }
        listener = null
        queue.clear()
    }
}

/** Opens the TCP connection to a host chosen from the list, or typed in by IP address. Cancel with [cancel]. */
class WifiConnector(
    private val host: String,
    private val port: Int,
    private val onConnected: (Connection) -> Unit,
    private val onFail: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var socket: Socket? = null
    @Volatile private var cancelled = false
    @Volatile private var connected = false

    fun start() {
        Thread({
            val s = Socket()
            socket = s
            try {
                s.connect(InetSocketAddress(host, port), 6_000)
                s.tcpNoDelay = true
                s.keepAlive = true
                if (cancelled) { s.close(); return@Thread }
                connected = true
                val conn = wrap(s, "Wi-Fi")
                main.post { if (!cancelled) onConnected(conn) else conn.close() }
            } catch (e: IOException) {
                try { s.close() } catch (e: IOException) { }
                if (!cancelled) main.post { onFail("Could not reach that game. Are both phones on the same Wi-Fi?") }
            }
        }, "wifi-connect").apply { isDaemon = true }.start()
    }

    fun cancel() {
        if (connected) return              // the socket now belongs to the Connection
        cancelled = true
        try { socket?.close() } catch (e: IOException) { }
    }
}