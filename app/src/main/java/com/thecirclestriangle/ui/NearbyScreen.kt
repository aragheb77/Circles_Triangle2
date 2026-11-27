package com.thecirclestriangle.ui

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecirclestriangle.GameViewModel
import com.thecirclestriangle.nearby.BluetoothStatus
import com.thecirclestriangle.nearby.NearbyPermissions
import com.thecirclestriangle.nearby.NearbyPhase
import com.thecirclestriangle.nearby.Peer
import com.thecirclestriangle.nearby.Transport
import com.thecirclestriangle.nearby.WIFI_PORT

/**
 * "Play Nearby" lobby: one phone hosts, the other joins, over the same Wi-Fi network or over Bluetooth.
 * When the other phone connects the match starts automatically (the ViewModel switches to the game screen).
 */
@Composable
fun NearbyScreen(vm: GameViewModel) {
    val ctx = LocalContext.current
    val n = vm.nearby
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var address by remember { mutableStateOf("") }

    // --- system dialogs: runtime permissions, "turn Bluetooth on", "make this phone discoverable"
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val next = pending; pending = null
        if (result.values.all { it }) next?.invoke()
        else n.error = "Bluetooth needs the “Nearby devices” permission. You can allow it in the phone’s Settings, under Apps."
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val next = pending; pending = null
        if (n.bluetoothStatus() == BluetoothStatus.ON) next?.invoke() else n.error = "Bluetooth is still turned off."
    }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* hosting already listens */ }

    /** Makes sure permissions are granted and Bluetooth is on, then runs [then]. Re-enters itself after each dialog. */
    fun ensureReady(then: () -> Unit) {
        val missing = NearbyPermissions.missing(ctx, n.transport)
        if (missing.isNotEmpty()) {
            pending = { ensureReady(then) }
            permissions.launch(missing)
            return
        }
        if (n.transport == Transport.BLUETOOTH) {
            when (n.bluetoothStatus()) {
                BluetoothStatus.UNSUPPORTED -> { n.error = "This device has no Bluetooth."; return }
                BluetoothStatus.OFF -> {
                    pending = { ensureReady(then) }
                    try { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    catch (e: ActivityNotFoundException) { pending = null; n.error = "Please turn Bluetooth on in the phone’s settings." }
                    return
                }
                BluetoothStatus.ON -> {}
            }
        }
        then()
    }

    fun host() = ensureReady {
        n.startHosting(vm.myName, vm.rows)
        if (n.transport == Transport.BLUETOOTH) {
            try {
                discoverable.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                )
            } catch (e: ActivityNotFoundException) { /* paired phones can still connect */ }
        }
    }

    fun search() = ensureReady { n.startSearching() }

    fun pick(t: Transport) {
        if (t == n.transport) return
        n.stop(); n.error = null
        n.transport = t
    }

    Column(
        Modifier.fillMaxSize().background(Tk.CanvasBrush).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Lucide.ArrowLeft, "Back", vm::leaveNearby)
            Column {
                Text("CIRCLES TRIANGLE", color = Tk.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Spacer4()
                Text("Play Nearby", color = Tk.Ink, fontSize = 29.sp, fontWeight = FontWeight.Black)
            }
        }

        n.error?.let { msg ->
            val shape = RoundedCornerShape(14.dp)
            Text(
                msg, color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clip(shape).background(Color(0xFFFDECEA)).padding(14.dp),
            )
        }

        if (vm.nearbyConnected) {
            WhiteCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Radar(if (n.transport == Transport.WIFI) Lucide.Wifi else Lucide.Bluetooth, animate = !vm.reducedMotion)
                    Text("Connected!", color = Tk.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Starting the game…", color = Tk.MutedText, fontSize = 14.sp)
                }
            }
        } else {
            if (n.phase == NearbyPhase.IDLE) {
                WhiteCard {
                    SectionLabel("YOUR NAME")
                    NameField(vm.myName, { vm.myName = it }, "SHOWN TO YOUR FRIEND", "Player", Tk.Indigo, last = true, onDone = {})
                }
            }

            WhiteCard {
                SectionLabel("CONNECT USING")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Wi-Fi", n.transport == Transport.WIFI, { pick(Transport.WIFI) }, Modifier.weight(1f), radius = 11, vPad = 12, fontSize = 14)
                    Chip("Bluetooth", n.transport == Transport.BLUETOOTH, { pick(Transport.BLUETOOTH) }, Modifier.weight(1f), radius = 11, vPad = 12, fontSize = 14)
                }
                Text(
                    if (n.transport == Transport.WIFI) "Both phones need to be on the same Wi-Fi network. No internet is needed."
                    else "Phones within a few metres of each other. No Wi-Fi or internet is needed.",
                    color = Tk.MutedText, fontSize = 13.sp,
                )
            }

            AnimatedContent(
                targetState = n.phase,
                transitionSpec = { fadeIn(tween(220, 80)) togetherWith fadeOut(tween(100)) },
                label = "nearbyPhase",
            ) { phase ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (phase) {
                        NearbyPhase.IDLE -> {
                            CtaButton("HOST A GAME", onClick = ::host)
                            OutlineButton("Join a game", ::search)
                            Text(
                                "One phone hosts and the other joins. If you host, the board has ${vm.rows} rows (change it on the previous screen).",
                                color = Tk.MutedText, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        NearbyPhase.HOSTING -> {
                            WhiteCard {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Radar(if (n.transport == Transport.WIFI) Lucide.Wifi else Lucide.Bluetooth, animate = !vm.reducedMotion)
                                    Text("Waiting for a player…", color = Tk.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    if (n.transport == Transport.WIFI) {
                                        Text("On the other phone, tap “Join a game”.", color = Tk.MutedText, fontSize = 14.sp, textAlign = TextAlign.Center)
                                        val ip = n.localAddress
                                        if (ip != null) {
                                            val shown = if (n.localPort == WIFI_PORT) ip else "$ip:${n.localPort}"
                                            Text("If it does not show up, type this address there:", color = Tk.MutedText, fontSize = 12.sp, textAlign = TextAlign.Center)
                                            Text(shown, color = Tk.Indigo, fontSize = 22.sp, fontWeight = FontWeight.Black)
                                        } else {
                                            Text("This phone is not connected to a Wi-Fi network yet.", color = Color(0xFFB3261E), fontSize = 13.sp, textAlign = TextAlign.Center)
                                        }
                                    } else {
                                        Text(
                                            "Your phone is visible to nearby devices for 5 minutes. On the other phone tap “Join a game” and choose this phone.",
                                            color = Tk.MutedText, fontSize = 14.sp, textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            OutlineButton("Cancel", { n.stop() })
                        }
                        NearbyPhase.SCANNING -> {
                            WhiteCard {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Radar(if (n.transport == Transport.WIFI) Lucide.Wifi else Lucide.Bluetooth, size = 54.dp, animate = n.searching && !vm.reducedMotion)
                                    Column(Modifier.weight(1f)) {
                                        Text(if (n.searching) "Searching for games…" else "Search finished", color = Tk.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                        Text(
                                            if (n.transport == Transport.WIFI) "Ask your friend to tap “Host a game”." else "Your friend should tap “Host a game” first.",
                                            color = Tk.MutedText, fontSize = 13.sp,
                                        )
                                    }
                                }
                                if (n.peers.isEmpty()) {
                                    Text(
                                        if (n.searching) "Nobody found yet." else "No games found. Check that your friend is hosting, then search again.",
                                        color = Tk.MutedText, fontSize = 14.sp,
                                    )
                                }
                                for (p in n.peers) PeerRow(p) { n.join(p) }
                                if (n.transport == Transport.BLUETOOTH && NearbyPermissions.bluetoothScanNeedsLocationSwitch) {
                                    Text("On this Android version, Location must be switched on to find devices over Bluetooth.", color = Tk.MutedText, fontSize = 12.sp)
                                }
                            }
                            if (n.transport == Transport.WIFI) {
                                WhiteCard {
                                    SectionLabel("OR TYPE THE HOST’S ADDRESS")
                                    NameField(address, { address = it }, "ADDRESS", "192.168.1.23", Tk.Indigo, last = true, onDone = { n.joinAddress(address) }, maxLength = 21, keyboardType = KeyboardType.Uri)
                                    OutlineButton("Connect", { n.joinAddress(address) })
                                }
                            } else if (!n.searching) {
                                OutlineButton("Search again", ::search)
                            }
                            OutlineButton("Stop searching", { n.stop() })
                        }
                        NearbyPhase.CONNECTING -> {
                            WhiteCard {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Radar(if (n.transport == Transport.WIFI) Lucide.Wifi else Lucide.Bluetooth, animate = !vm.reducedMotion)
                                    Text("Connecting to ${n.connectingTo ?: "your friend"}…", color = Tk.Ink, fontSize = 19.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                    Text("The game starts as soon as you are connected.", color = Tk.MutedText, fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                            }
                            OutlineButton("Cancel", { n.stop() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer4() = Box(Modifier.size(width = 1.dp, height = 6.dp))

/** Expanding rings around a radio icon: "something is happening". Static when [animate] is false. */
@Composable
private fun Radar(icon: List<String>, size: Dp = 120.dp, animate: Boolean = true) {
    val t by rememberInfiniteTransition(label = "radar").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    val phase = if (animate) t else 0.4f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0..2) {
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    Tk.Indigo.copy(alpha = 0.38f * (1f - p)),
                    radius = this.size.minDimension / 2f * (0.35f + 0.65f * p),
                    style = Stroke(2.5.dp.toPx()),
                )
            }
        }
        Box(
            Modifier.size(size * 0.42f).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF6572D3), Color(0xFF9AA5FF)))),
            contentAlignment = Alignment.Center,
        ) { LIcon(icon, size * 0.22f, Color.White) }
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier.pressScale(src).fillMaxWidth().shadow(4.dp, shape, ambientColor = Color(0x226876B3), spotColor = Color(0x226876B3))
            .clip(shape).background(Color(0xFFF4F6FF)).tapClickable(src, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE2E6FF)), contentAlignment = Alignment.Center) {
            LIcon(if (peer.transport == Transport.WIFI) Lucide.Wifi else Lucide.Bluetooth, 19.dp, Tk.Indigo)
        }
        Column(Modifier.weight(1f)) {
            Text(peer.name, color = Tk.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(peer.detail, color = Tk.MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("JOIN", color = Tk.Indigo, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}
