package com.thecirclestriangle.nearby

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.thecirclestriangle.engine.NetMsg
import com.thecirclestriangle.engine.NetProtocol
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One live link to the other phone, over a Wi-Fi socket or a Bluetooth socket (both are plain byte streams).
 * Messages are single text lines (see [NetProtocol]). Everything is delivered to [listener] on the main thread.
 *
 * - Reading happens on its own thread; writing on a single-thread executor so the UI never blocks.
 * - A keep-alive Ping is sent every [PING_MS]; if nothing at all arrives for [TIMEOUT_MS] the link is declared lost.
 * - Incoming lines are capped in size, so a misbehaving peer cannot exhaust memory.
 */
class Connection(
    private val input: InputStream,
    private val output: OutputStream,
    private val closeUnderlying: () -> Unit,
    val description: String,
) {
    enum class CloseReason { PEER_LEFT, LOST }

    interface Listener {
        fun onMessage(msg: NetMsg)
        fun onClosed(reason: CloseReason)
    }

    @Volatile var listener: Listener? = null

    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    @Volatile private var detached = false        // we closed it ourselves: report nothing more
    @Volatile private var peerSaidBye = false
    @Volatile private var lastRx = SystemClock.elapsedRealtime()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "nearby-write").apply { isDaemon = true } }
    private val writeLock = Any()

    /** Call once, after [listener] has been set. */
    fun start() {
        Thread(::readLoop, "nearby-read").apply { isDaemon = true }.start()
        Thread(::keepAliveLoop, "nearby-ping").apply { isDaemon = true }.start()
    }

    fun send(msg: NetMsg) {
        if (closed.get()) return
        try {
            writer.execute {
                try { writeLine(msg) } catch (e: IOException) { finish(CloseReason.LOST) }
            }
        } catch (e: RejectedExecutionException) { /* already closing */ }
    }

    /** Leaves on purpose: tells the other side, then closes. No further callbacks are made. */
    fun close() {
        detached = true
        if (!closed.compareAndSet(false, true)) return
        try {
            writer.execute {
                try { writeLine(NetMsg.Bye) } catch (e: IOException) { }
                closeUnderlying()
            }
        } catch (e: RejectedExecutionException) { closeUnderlying() }
        writer.shutdown()
    }

    private fun writeLine(msg: NetMsg) {
        val bytes = (NetProtocol.encode(msg) + "\n").toByteArray(Charsets.UTF_8)
        synchronized(writeLock) { output.write(bytes); output.flush() }
    }

    private fun readLoop() {
        val buf = ByteArrayOutputStream()
        val inp = BufferedInputStream(input, 512)
        try {
            while (true) {
                val b = inp.read()
                if (b < 0) break
                if (b == '\n'.code) {
                    handleLine(buf.toString(Charsets.UTF_8.name()))
                    buf.reset()
                } else {
                    if (buf.size() >= MAX_LINE_BYTES) break      // not our protocol: drop the link
                    buf.write(b)
                }
            }
        } catch (e: IOException) { /* closed or broken */ }
        finish(if (peerSaidBye) CloseReason.PEER_LEFT else CloseReason.LOST)
    }

    private fun handleLine(line: String) {
        lastRx = SystemClock.elapsedRealtime()
        val msg = NetProtocol.decode(line) ?: return
        when (msg) {
            NetMsg.Ping -> Unit
            NetMsg.Bye -> peerSaidBye = true
            else -> main.post { if (!detached) listener?.onMessage(msg) }
        }
    }

    private fun keepAliveLoop() {
        while (!closed.get()) {
            try { Thread.sleep(PING_MS) } catch (e: InterruptedException) { return }
            if (closed.get()) return
            if (SystemClock.elapsedRealtime() - lastRx > TIMEOUT_MS) { finish(CloseReason.LOST); return }
            send(NetMsg.Ping)
        }
    }

    /** The link ended without us asking (peer left, cable/radio dropped, timeout). */
    private fun finish(reason: CloseReason) {
        if (!closed.compareAndSet(false, true)) return
        writer.shutdownNow()
        try { closeUnderlying() } catch (e: Exception) { }
        main.post { if (!detached) listener?.onClosed(reason) }
    }

    private companion object {
        const val MAX_LINE_BYTES = 512
        const val PING_MS = 3_000L
        const val TIMEOUT_MS = 12_000L
    }
}
