package com.thecirclestriangle.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class Transport { WIFI, BLUETOOTH }

enum class NearbyPhase { IDLE, HOSTING, SCANNING, CONNECTING }

enum class BluetoothStatus { UNSUPPORTED, OFF, ON }

/**
 * A game found nearby. [host]/[port] are used for Wi-Fi; Bluetooth peers are looked up by [id] (the device address).
 */
data class Peer(
    val id: String,
    val name: String,
    val detail: String,
    val transport: Transport,
    val host: String? = null,
    val port: Int = 0,
)

object NearbyPermissions {
    /**
     * Wi-Fi play needs only install-time permissions (INTERNET, multicast); nothing to ask for.
     * Bluetooth: Android 12+ asks for Nearby-devices permissions; older versions need Location to scan.
     */
    fun required(t: Transport): Array<String> = when (t) {
        Transport.WIFI -> emptyArray()
        Transport.BLUETOOTH ->
            if (Build.VERSION.SDK_INT >= 31)
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun missing(ctx: Context, t: Transport): Array<String> =
        required(t).filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    /** Bluetooth scanning on Android 11 and older only returns results while the Location switch is on. */
    val bluetoothScanNeedsLocationSwitch: Boolean get() = Build.VERSION.SDK_INT <= 30
}

/** Wi-Fi TCP port the host listens on (a random free port is used if it is taken). */
const val WIFI_PORT = 48615
const val WIFI_SERVICE_TYPE = "_ctriangle._tcp."
