package com.thecirclestriangle.nearby

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thecirclestriangle.engine.NetProtocol

/**
 * Lobby logic for "Play Nearby": hosting, searching and connecting over Wi-Fi or Bluetooth.
 * Everything the lobby screen shows is observable state here. When a link is established [onLinked] is called
 * (on the main thread) with the open [Connection] and whether this phone is the host; discovery is then stopped.
 *
 * Runtime permissions and the system dialogs (turn Bluetooth on, make discoverable) are handled by the screen,
 * because only an Activity can show them; this class assumes they have been granted.
 */
class NearbyController(private val app: Application) {
    var transport by mutableStateOf(Transport.WIFI)
    var phase by mutableStateOf(NearbyPhase.IDLE)
        private set
    val peers = mutableStateListOf<Peer>()
    var error by mutableStateOf<String?>(null)
    /** Wi-Fi host: this phone's address on the network (fallback when automatic discovery is blocked). */
    var localAddress by mutableStateOf<String?>(null)
        private set
    var localPort by mutableStateOf(WIFI_PORT)
        private set
    /** Bluetooth search is running (it stops by itself after ~12 s; the lobby offers "Search again"). */
    var searching by mutableStateOf(false)
        private set
    var connectingTo by mutableStateOf<String?>(null)
        private set

    var onLinked: ((Connection, Boolean) -> Unit)? = null

    private var wifiHost: WifiHost? = null
    private var wifiScanner: WifiScanner? = null
    private var wifiConnector: WifiConnector? = null
    private var btHost: BluetoothHost? = null
    private var btScanner: BluetoothScanner? = null
    private var btConnector: BluetoothConnector? = null

    fun bluetoothStatus(): BluetoothStatus {
        val a = bluetoothAdapter(app) ?: return BluetoothStatus.UNSUPPORTED
        return if (a.isEnabled) BluetoothStatus.ON else BluetoothStatus.OFF
    }

    /** Becomes the host: waits for one player to join. [name] is what the other phone shows in its list. */
    fun startHosting(name: String, rows: Int) {
        stop(); error = null
        val shown = NetProtocol.cleanName(name).ifEmpty { "Player" }
        phase = NearbyPhase.HOSTING
        when (transport) {
            Transport.WIFI -> {
                localAddress = localIpv4()
                val h = WifiHost(app, shown, rows, { linked(it, true) }, ::failed)
                wifiHost = h
                val port = h.start()
                if (port != null) localPort = port
            }
            Transport.BLUETOOTH -> {
                val h = BluetoothHost(app, { linked(it, true) }, ::failed)
                btHost = h
                h.start()
            }
        }
    }

    /** Starts searching for hosts. */
    fun startSearching() {
        stop(); error = null
        phase = NearbyPhase.SCANNING
        when (transport) {
            Transport.WIFI -> {
                val s = WifiScanner(app, { list -> setPeers(list) }, ::failed)
                wifiScanner = s
                searching = true
                s.start()
            }
            Transport.BLUETOOTH -> {
                val s = BluetoothScanner(app, { list -> setPeers(list) }, { searching = false }, ::failed)
                btScanner = s
                searching = true
                s.start()
            }
        }
    }

    /** Joins a host from the list. */
    fun join(peer: Peer) {
        when (peer.transport) {
            Transport.WIFI -> {
                val host = peer.host ?: return
                beginConnecting(peer.name)
                wifiConnector = WifiConnector(host, peer.port, { linked(it, false) }, ::failed).also { it.start() }
            }
            Transport.BLUETOOTH -> {
                val device = btScanner?.deviceFor(peer.id) ?: return
                beginConnecting(peer.name)
                btConnector = BluetoothConnector(bluetoothAdapter(app), device, { linked(it, false) }, ::failed).also { it.start() }
            }
        }
    }

    /** Wi-Fi fallback: join by typing the host's address, "192.168.1.23" or "192.168.1.23:48615". */
    fun joinAddress(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val host = t.substringBefore(':').trim()
        val port = t.substringAfter(':', "").trim().toIntOrNull() ?: WIFI_PORT
        if (host.isEmpty() || port !in 1..65535) { error = "That address does not look right."; return }
        beginConnecting(host)
        wifiConnector = WifiConnector(host, port, { linked(it, false) }, ::failed).also { it.start() }
    }

    private fun beginConnecting(label: String) {
        error = null
        wifiScanner?.stop(); wifiScanner = null
        btScanner?.stop(); btScanner = null
        searching = false
        connectingTo = label
        phase = NearbyPhase.CONNECTING
    }

    private fun setPeers(list: List<Peer>) {
        peers.clear(); peers.addAll(list)
    }

    private fun linked(c: Connection, host: Boolean) {
        // The connectors own the socket that just became [c]; forget them BEFORE stop(), otherwise stop() would
        // cancel them and close the freshly opened connection.
        wifiConnector = null
        btConnector = null
        stop()
        onLinked?.invoke(c, host) ?: c.close()
    }

    private fun failed(msg: String) {
        stop()
        error = msg
    }

    /** Stops hosting / searching / connecting. An already established [Connection] is NOT closed. */
    fun stop() {
        wifiHost?.stop(); wifiHost = null
        wifiScanner?.stop(); wifiScanner = null
        wifiConnector?.cancel(); wifiConnector = null
        btHost?.stop(); btHost = null
        btScanner?.stop(); btScanner = null
        btConnector?.cancel(); btConnector = null
        peers.clear()
        searching = false; connectingTo = null
        phase = NearbyPhase.IDLE
    }

    /** True if Bluetooth permission/hardware makes the Bluetooth tab usable at all. */
    fun bluetoothAdapterOrNull(): BluetoothAdapter? = bluetoothAdapter(app)
}