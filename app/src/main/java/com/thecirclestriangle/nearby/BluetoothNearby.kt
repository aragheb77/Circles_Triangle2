package com.thecirclestriangle.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.IOException
import java.util.UUID

/** Identifies this game's Bluetooth service (both phones use the same id). */
val BLUETOOTH_SERVICE_UUID: UUID = UUID.fromString("7b3c9a4e-5d1f-4c6a-9e2b-3f8a1d6c0b57")

fun bluetoothAdapter(context: Context): BluetoothAdapter? =
    (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

private fun wrap(socket: BluetoothSocket) = Connection(
    socket.inputStream, socket.outputStream,
    { try { socket.close() } catch (e: IOException) { } }, "Bluetooth",
)

/**
 * Hosts a game over Bluetooth: listens on an RFCOMM service and accepts one player. The *insecure* RFCOMM variant
 * is used so two phones that have never been paired can still connect without a pairing prompt.
 * (The screen separately asks the system to make the phone discoverable, so the other phone can find it.)
 */
@SuppressLint("MissingPermission")
class BluetoothHost(
    context: Context,
    private val onClient: (Connection) -> Unit,
    private val onFail: (String) -> Unit,
) {
    private val adapter = bluetoothAdapter(context)
    private val main = Handler(Looper.getMainLooper())
    private var server: BluetoothServerSocket? = null
    @Volatile private var stopped = false

    fun start(): Boolean {
        val a = adapter ?: run { onFail("This device has no Bluetooth."); return false }
        val srv = try {
            a.listenUsingInsecureRfcommWithServiceRecord("CirclesTriangle", BLUETOOTH_SERVICE_UUID)
        } catch (e: IOException) {
            onFail("Could not start Bluetooth. Is Bluetooth turned on?"); return false
        } catch (e: SecurityException) {
            onFail("Bluetooth permission is needed."); return false
        }
        server = srv
        Thread({
            try {
                val sock = srv.accept()
                if (stopped) { sock.close(); return@Thread }
                stop()
                val conn = wrap(sock)
                main.post { onClient(conn) }
            } catch (e: IOException) {
                if (!stopped) main.post { onFail("The Bluetooth connection was interrupted. Please try again.") }
            }
        }, "bt-accept").apply { isDaemon = true }.start()
        return true
    }

    fun stop() {
        stopped = true
        try { server?.close() } catch (e: IOException) { }
        server = null
    }
}

/** Lists nearby (and already paired) Bluetooth devices so the player can pick the friend who is hosting. */
@SuppressLint("MissingPermission")
class BluetoothScanner(
    private val context: Context,
    private val onPeers: (List<Peer>) -> Unit,
    private val onFinished: () -> Unit,
    private val onFail: (String) -> Unit,
) {
    private val adapter = bluetoothAdapter(context)
    private val devices = LinkedHashMap<String, BluetoothDevice>()
    private val peers = LinkedHashMap<String, Peer>()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    add(d, paired = false)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onFinished()
            }
        }
    }

    fun deviceFor(id: String): BluetoothDevice? = devices[id]

    fun start() {
        val a = adapter ?: run { onFail("This device has no Bluetooth."); return }
        try {
            if (!registered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                ContextCompat.registerReceiver(context.applicationContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                registered = true
            }
            a.bondedDevices?.forEach { add(it, paired = true) }
            if (a.isDiscovering) a.cancelDiscovery()
            if (!a.startDiscovery()) onFail("Could not start searching for Bluetooth devices.")
        } catch (e: SecurityException) {
            onFail("Bluetooth permission is needed.")
        }
    }

    private fun add(d: BluetoothDevice, paired: Boolean) {
        val name = try { d.name } catch (e: SecurityException) { null }
        if (name.isNullOrBlank()) return
        val major = d.bluetoothClass?.majorDeviceClass
        if (major in IRRELEVANT_CLASSES) return
        devices[d.address] = d
        peers[d.address] = Peer(d.address, name, if (paired) "Paired device • Bluetooth" else "Bluetooth", Transport.BLUETOOTH)
        onPeers(peers.values.toList())
    }

    fun stop() {
        try { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() } catch (e: SecurityException) { }
        if (registered) {
            try { context.applicationContext.unregisterReceiver(receiver) } catch (e: IllegalArgumentException) { }
            registered = false
        }
    }

    private companion object {
        val IRRELEVANT_CLASSES = setOf(
            BluetoothClass.Device.Major.AUDIO_VIDEO, BluetoothClass.Device.Major.PERIPHERAL,
            BluetoothClass.Device.Major.IMAGING, BluetoothClass.Device.Major.WEARABLE,
            BluetoothClass.Device.Major.TOY, BluetoothClass.Device.Major.HEALTH,
        )
    }
}

/** Connects to the chosen device. Retries once, because the first RFCOMM attempt often fails right after discovery. */
@SuppressLint("MissingPermission")
class BluetoothConnector(
    private val adapter: BluetoothAdapter?,
    private val device: BluetoothDevice,
    private val onConnected: (Connection) -> Unit,
    private val onFail: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var cancelled = false
    @Volatile private var connected = false

    fun start() {
        try { adapter?.cancelDiscovery() } catch (e: SecurityException) { }
        Thread({
            var last: Exception? = null
            for (attempt in 1..2) {
                if (cancelled) return@Thread
                try {
                    val s = device.createInsecureRfcommSocketToServiceRecord(BLUETOOTH_SERVICE_UUID)
                    socket = s
                    s.connect()
                    if (cancelled) { s.close(); return@Thread }
                    connected = true
                    val conn = wrap(s)
                    main.post { if (!cancelled) onConnected(conn) else conn.close() }
                    return@Thread
                } catch (e: IOException) {
                    last = e
                    try { socket?.close() } catch (e: IOException) { }
                    try { Thread.sleep(500) } catch (e: InterruptedException) { return@Thread }
                } catch (e: SecurityException) {
                    last = e; break
                }
            }
            if (!cancelled) main.post {
                onFail(
                    if (last is SecurityException) "Bluetooth permission is needed."
                    else "Could not connect. Make sure your friend tapped “Host a game” first.",
                )
            }
        }, "bt-connect").apply { isDaemon = true }.start()
    }

    fun cancel() {
        if (connected) return
        cancelled = true
        try { socket?.close() } catch (e: IOException) { }
    }
}