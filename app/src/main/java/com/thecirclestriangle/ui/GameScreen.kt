package com.thecirclestriangle.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.thecirclestriangle.audio.Sound
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecirclestriangle.Confirm
import com.thecirclestriangle.GameMode
import com.thecirclestriangle.GameViewModel
import com.thecirclestriangle.MoveFeedback
import com.thecirclestriangle.Screen
import com.thecirclestriangle.Stage
import com.thecirclestriangle.engine.GameState
import com.thecirclestriangle.engine.Player

private fun playerColor(p: Player) = if (p == Player.ONE) Tk.Player1 else Tk.Player2

@Composable
fun GameScreen(vm: GameViewModel) {
    val s = vm.state ?: return
    val computer = vm.mode == GameMode.COMPUTER
    val nearby = vm.mode == GameMode.NEARBY
    val opponent = vm.localPlayer?.opponent ?: Player.TWO
    val dur = if (vm.reducedMotion) 0 else 200
    val fb = vm.feedback

    // While the "who starts?" draw spins, the spotlight (not the real first player) drives tint and highlights,
    // so nothing gives the answer away early.
    val drawing = vm.starterActive && !vm.starterRevealed
    val turn = if (vm.starterActive) (vm.starterHighlight ?: Player.ONE) else vm.shownTurn

    // Stage 4: cross-fade of the active-player tint (180-220 ms)
    val tint by animateColorAsState(playerColor(turn), tween(dur), label = "tint")

    // Stage 2: line draw (~300 ms) and node pulse share one progress value
    val progress = remember { Animatable(1f) }
    LaunchedEffect(fb?.id) {
        if (fb != null) {
            if (vm.reducedMotion) progress.snapTo(1f) else { progress.snapTo(0f); progress.animateTo(1f, tween(300, easing = LinearEasing)) }
        }
    }
    // Board entrance: the lattice fades in and circles pop in row by row at the start of every game.
    val entrance = remember(vm.gameId) { Animatable(if (vm.reducedMotion) 1f else 0f) }
    LaunchedEffect(vm.gameId) {
        if (!vm.reducedMotion) { entrance.snapTo(0f); delay(200); entrance.animateTo(1f, tween(850, easing = LinearEasing)) }
    }
    // Slow breathing used by the last-move ring.
    val pulse = if (vm.reducedMotion) null else rememberInfiniteTransition(label = "pulse")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "ring")

    val title = when {
        drawing -> "Who goes first?"
        vm.isYou(turn) -> "Your Turn"
        else -> "${vm.nameOf(turn)}'s Turn"
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(Tk.CanvasBrush).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("CIRCLES TRIANGLE", color = Color(0xFF7580B2), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    // the turn title slides up and cross-fades whenever it changes
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            if (vm.reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            else (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260, 60))) togetherWith (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(120)))
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        label = "turnTitle",
                    ) { t ->
                        Text(
                            t, color = Tk.Ink, fontSize = if (t.length > 14) 20.sp else 25.sp, fontWeight = FontWeight.Black,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // music on / off, always one tap away
                    IconTile(if (Sound.musicOn) Lucide.Music else Lucide.MusicOff, if (Sound.musicOn) "Turn music off" else "Turn music on") { Sound.setMusic(!Sound.musicOn) }
                    IconTile(Lucide.Pause, "Pause", vm::pause)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreCard(vm.nameOf(Player.ONE), Player.ONE, vm.shownScoreOne, turn == Player.ONE, fb, vm.stage, dur, Modifier.weight(1f))
                ScoreCard(vm.nameOf(Player.TWO), Player.TWO, vm.shownScoreTwo, turn == Player.TWO, fb, vm.stage, dur, Modifier.weight(1f))
            }
            // Board panel: 15% opacity tint of the active player's colour (85% transparency)
            val panel = RoundedCornerShape(24.dp)
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(panel)
                    .background(tint.copy(alpha = 0.15f))
                    .border(1.5.dp, tint.copy(alpha = 0.533f), panel)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BoardView(s, fb, { progress.value }, { entrance.value }, { pulse?.value ?: 0f }, vm::onCellTap, Modifier.fillMaxSize())
                if ((vm.stage == Stage.THINKING && computer) || vm.waitingForRemote) ThinkingPill(vm.nameOf(opponent), vm.reducedMotion, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                if (vm.stage == Stage.SCORE && fb != null && fb.points > 0) ScoreToast(fb, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
                AnimatedVisibility(vm.starterActive, enter = fadeIn(tween(250)), exit = fadeOut(tween(400))) {
                    StarterDraw(vm, Modifier.fillMaxSize())
                }
            }
            val tb = RoundedCornerShape(18.dp)
            Row(
                Modifier.fillMaxWidth().shadow(6.dp, tb, ambientColor = Color(0x226876B3), spotColor = Color(0x226876B3)).clip(tb).background(Color.White).padding(15.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                if (!nearby) ToolButton(Lucide.Undo2, "Undo", vm.canUndo && vm.stage == Stage.IDLE && !vm.starterActive, vm::undo)
                ToolButton(Lucide.CirclePause, "Pause", true, vm::pause)
            }
        }

        AnimatedVisibility(vm.showPause, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) { PauseOverlay(vm) }
        // Play Nearby: the other phone left, or the connection dropped
        if (screenIsGame(vm)) vm.nearbyEnded?.let { msg ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Game ended") },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = vm::toSetup) { Text("OK") } },
            )
        }
        vm.confirm?.let { c ->
            AlertDialog(
                onDismissRequest = { vm.confirm = null },
                title = { Text("Are you sure?") },
                text = { Text("The current game will be lost.") },
                confirmButton = { TextButton(onClick = vm::applyConfirm) { Text(when (c) { Confirm.RESTART -> "Restart"; Confirm.NEW_GAME -> "New game"; Confirm.EXIT -> "Exit" }) } },
                dismissButton = { TextButton(onClick = { vm.confirm = null }) { Text("Cancel") } },
            )
        }
    }
}

/**
 * "Who goes first?" draw (Computer mode). A spotlight jumps between the two players, slows down and
 * lands on the randomly chosen one, who then pops forward with a pulsing ring and a short announcement.
 */
@Composable
private fun StarterDraw(vm: GameViewModel, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val first = vm.starterHighlight
    Box(modifier.clip(shape).background(Color(0xEBF3F5FF)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text(
                if (vm.starterRevealed) "FIRST MOVE" else "DRAWING THE FIRST MOVE…",
                color = Tk.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
            )
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (p in listOf(Player.ONE, Player.TWO)) {
                    StarterCard(vm.nameOf(p), playerColor(p), lit = first == p, won = vm.starterRevealed && first == p, Modifier.weight(1f))
                }
            }
            // the announcement fades in once the spotlight has settled
            val shown by animateFloatAsState(if (vm.starterRevealed) 1f else 0f, tween(if (vm.reducedMotion) 0 else 300), label = "announce")
            val who = first?.let { if (vm.isYou(it)) "You go first!" else "${vm.nameOf(it)} goes first!" } ?: ""
            Text(
                who, color = Tk.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.graphicsLayer { alpha = shown; translationY = (1f - shown) * 14f },
            )
        }
    }
}

@Composable
private fun StarterCard(name: String, color: Color, lit: Boolean, won: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val scale by animateFloatAsState(
        if (won) 1.12f else if (lit) 1.06f else 0.94f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "cardScale",
    )
    val fade by animateFloatAsState(if (lit) 1f else 0.5f, tween(90), label = "cardFade")
    val ring = rememberInfiniteTransition(label = "winRing")
    val ringT by ring.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ringT")
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade }
            .drawBehind {
                if (won) drawCircle(color.copy(alpha = 0.45f * (1f - ringT)), radius = size.maxDimension * (0.45f + 0.35f * ringT), style = Stroke(5.dp.toPx()))
            }
            .shadow(if (lit) 12.dp else 2.dp, shape, ambientColor = color.copy(alpha = 0.5f), spotColor = color.copy(alpha = 0.5f))
            .clip(shape).background(Color.White)
            .border(if (lit) 3.dp else 1.5.dp, if (lit) color else Tk.Border, shape)
            .padding(vertical = 20.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(color))
        Text(name.uppercase(), color = Tk.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun IconTile(icon: List<String>, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.pressScale(src).size(42.dp).shadow(5.dp, shape, ambientColor = Color(0x448B98CE), spotColor = Color(0x448B98CE)).clip(shape).background(Color.White)
            .semantics { contentDescription = label }.tapClickable(src, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LIcon(icon, 20.dp, Tk.IconStroke) }
}

@Composable
private fun ScoreCard(label: String, player: Player, score: Int, active: Boolean, fb: MoveFeedback?, stage: Stage, dur: Int, modifier: Modifier) {
    val base = playerColor(player)
    val one = player == Player.ONE
    val bgA by animateFloatAsState(if (active) 0.149f else if (one) 0.07f else 0.12f, tween(dur), label = "bg")
    val bdA by animateFloatAsState(if (active) 0.8f else if (one) 0.333f else 0.5f, tween(dur), label = "bd")
    val shown by animateIntAsState(score, tween(if (dur == 0) 0 else 450), label = "count") // Stage 3: count-up
    val scoring = fb != null && fb.player == player && fb.points > 0 && stage == Stage.SCORE
    // the card bumps when its player scores; the active card's border breathes gently
    val bump by animateFloatAsState(if (scoring) 1.07f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "bump")
    val breathe by if (dur == 0 || !active) remember { mutableStateOf(0f) } else
        rememberInfiniteTransition(label = "breathe").animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "b")
    val shape = RoundedCornerShape(17.dp)
    val name = label.uppercase()
    Box(
        modifier.graphicsLayer { scaleX = bump; scaleY = bump }
            .shadow(6.dp, shape, ambientColor = Color(0x226876B3), spotColor = Color(0x226876B3)).clip(shape)
            .background(base.copy(alpha = bgA)).border(1.5.dp, base.copy(alpha = (bdA * (0.82f + 0.18f * breathe)).coerceAtMost(1f)), shape)
            .semantics(mergeDescendants = true) { contentDescription = "$label score $score" },
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(name, color = Color(0xFF7781B1), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(13.dp).clip(CircleShape).background(base))
                Text(shown.toString().padStart(2, '0'), color = Tk.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
        }
        if (scoring && fb != null) FloatingPoints(fb, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 14.dp))
    }
}

/** Stage 3: floating "+N" that drifts up and fades. */
@Composable
private fun FloatingPoints(fb: MoveFeedback, modifier: Modifier) {
    val t = remember(fb.id) { Animatable(0f) }
    LaunchedEffect(fb.id) { t.animateTo(1f, tween(550, easing = LinearEasing)) }
    Text(
        "+${fb.points}", color = if (fb.player == Player.ONE) Color(0xFFD98B32) else Tk.Indigo, fontSize = 18.sp, fontWeight = FontWeight.Black,
        modifier = modifier.graphicsLayerCompat(t.value),
    )
}

private fun Modifier.graphicsLayerCompat(t: Float): Modifier = this.offset(y = (-14f * t).dp).alpha(1f - 0.8f * t)

@Composable
private fun ToolButton(icon: List<String>, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.alpha(if (enabled) 1f else 0.4f).bounceClick(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LIcon(icon, 22.dp, Tk.IconStroke)
        Text(label, color = Tk.Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------- Stage 1 / Stage 3 overlays
@Composable
private fun ThinkingPill(name: String, reduced: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(999.dp)
    val phase = if (reduced) 0f else {
        val t = rememberInfiniteTransition(label = "dots")
        t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    }
    Row(
        modifier.shadow(4.dp, shape, ambientColor = Color(0x225966A4), spotColor = Color(0x225966A4)).clip(shape)
            .background(Color(0xFFF1F2FC)).border(1.dp, Tk.Player2.copy(alpha = 0.33f), shape)
            .padding(horizontal = 13.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "$name is thinking" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Tk.Player2))
        Text("${name.uppercase()} IS THINKING", color = Tk.Indigo, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0..2) Box(Modifier.size(5.dp).clip(CircleShape).background(Tk.Player2.copy(alpha = if (phase.toInt() == i) 1f else 0.35f)))
        }
    }
}

@Composable
private fun ScoreToast(fb: MoveFeedback, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (fb.player == Player.ONE) Color(0xFFD98B32) else Tk.Player2
    Row(
        modifier.shadow(7.dp, shape, ambientColor = Color(0x334654A5), spotColor = Color(0x334654A5)).clip(shape)
            .background(Color.White).border(1.dp, playerColor(fb.player).copy(alpha = 0.33f), shape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (fb.points == 1) "+1 POINT" else "+${fb.points} POINTS", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        Text(fb.scoreBefore.toString().padStart(2, '0'), color = Tk.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Text("→", color = Color(0xFF9299BB), fontSize = 12.sp)
        Text(fb.scoreAfter.toString().padStart(2, '0'), color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

// ---------------------------------------------------------------- 04 Pause
@Composable
private fun PauseOverlay(vm: GameViewModel) {
    // the card springs in from 90 %
    val pop = remember { Animatable(if (vm.reducedMotion) 1f else 0.9f) }
    LaunchedEffect(Unit) { if (!vm.reducedMotion) pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
    Box(Modifier.fillMaxSize().background(Tk.Scrim).clickable(onClick = vm::resume), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier.padding(24.dp).fillMaxWidth().graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .shadow(24.dp, shape, ambientColor = Color(0x6611183F), spotColor = Color(0x6611183F)).clip(shape)
                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFEDF0FF))))
                .clickable(enabled = false) {}
                .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            val tile = RoundedCornerShape(22.dp)
            Box(
                Modifier.size(70.dp).shadow(9.dp, tile, ambientColor = Color(0x446977D3), spotColor = Color(0x446977D3)).clip(tile)
                    .background(Brush.linearGradient(listOf(Color(0xFF6572D3), Color(0xFF9AA5FF)))),
                contentAlignment = Alignment.Center,
            ) { LIcon(Lucide.Pause, 30.dp, Color.White) }
            Text("Game Paused", color = Tk.Ink, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text(if (vm.mode == GameMode.NEARBY) "Your friend’s game keeps running while you are paused." else "Take a breath. Your game is saved.", color = Tk.MutedText, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.offset(y = (-8).dp))
            SwitchRow("Music", "Background music", Sound.musicOn) { Sound.setMusic(it) }
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtaButton("RESUME GAME", vm::resume, textSize = 16, vPad = 15, radius = 14, letterSpacing = 0f)
                if (vm.mode != GameMode.NEARBY) {
                    OutlineButton("Restart Game", { vm.requestConfirm(Confirm.RESTART) })
                    OutlineButton("Game Setup", { vm.requestConfirm(Confirm.NEW_GAME) })
                }
                OutlineButton("Exit to Main Menu", { vm.requestConfirm(Confirm.EXIT) }, textColor = Tk.SecondaryMuted)
            }
        }
    }
}

// ---------------------------------------------------------------- Board
/** Geometry scaled from the 5-row Kite board (hPitch 76.5, vPitch = 0.866 x hPitch, circle 22 / cap 0.6 x pitch). */
private class Geo(val rows: Int, val w: Float, val h: Float) {
    val k = minOf(w / 330f, h / 300f)
    val hp = 306f * k / (rows - 1)
    val vp = hp * 0.866f
    val d = minOf(22f * k, 0.6f * hp)
    private val oy = (h - ((rows - 1) * vp + d)) / 2f + d / 2f
    fun x(r: Int, c: Int) = w / 2f + (c - r / 2f) * hp
    fun y(r: Int) = oy + r * vp
}

@Composable
fun BoardView(
    state: GameState, feedback: MoveFeedback?, progress: () -> Float, entrance: () -> Float, pulse: () -> Float,
    onTap: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val board = state.board
    val desc = buildString {
        append("Game board, ${board.rows} rows, ${board.cellCount} circles.")
        state.lastMove?.let { append(" Last move: row ${board.rowOf[it] + 1}, circle ${board.colOf[it] + 1}.") }
    }
    Canvas(
        modifier
            .semantics { contentDescription = desc }
            .onSizeChanged { boxSize = it }
            .pointerInput(board.rows) {
                detectTapGestures { p ->
                    if (boxSize.width == 0) return@detectTapGestures
                    val g = Geo(board.rows, boxSize.width.toFloat(), boxSize.height.toFloat())
                    var best = -1
                    var bestDist = Float.MAX_VALUE
                    for (cell in 0 until board.cellCount) {
                        val dx = p.x - g.x(board.rowOf[cell], board.colOf[cell])
                        val dy = p.y - g.y(board.rowOf[cell])
                        val dist = dx * dx + dy * dy
                        if (dist < bestDist) { bestDist = dist; best = cell }
                    }
                    val reach = maxOf(g.hp * 0.55f, g.d)
                    if (best >= 0 && bestDist <= reach * reach) onTap(best)
                }
            },
    ) {
        val g = Geo(board.rows, size.width, size.height)
        val reveal = if (feedback != null) progress() else 1f
        val enter = entrance()
        fun pos(cell: Int) = Offset(g.x(board.rowOf[cell], board.colOf[cell]), g.y(board.rowOf[cell]))

        // light lattice: every neighbouring pair of circles (Kite: #E7EAF7, 1.5 px)
        for (cell in 0 until board.cellCount) {
            val r = board.rowOf[cell]; val c = board.colOf[cell]
            val from = pos(cell)
            if (c + 1 <= r) drawLine(Tk.Lattice.copy(alpha = enter), from, pos(cell + 1), strokeWidth = 1.5f * g.k, cap = StrokeCap.Round)
            if (r + 1 < board.rows) {
                drawLine(Tk.Lattice.copy(alpha = enter), from, pos(com.thecirclestriangle.engine.Board.idOf(r + 1, c)), strokeWidth = 1.5f * g.k, cap = StrokeCap.Round)
                drawLine(Tk.Lattice.copy(alpha = enter), from, pos(com.thecirclestriangle.engine.Board.idOf(r + 1, c + 1)), strokeWidth = 1.5f * g.k, cap = StrokeCap.Round)
            }
        }
        // completed lines (confirmed scoring state only); lines completed by the latest move are drawn progressively (Stage 2)
        val fresh = feedback?.newLines?.toSet() ?: emptySet()
        for (lid in state.completedLineIds) {
            val l = board.lines[lid]
            val a = pos(l.cells.first()); val b = pos(l.cells.last())
            val end = if (lid in fresh) a + (b - a) * reveal else b
            if (lid in fresh) drawLine(Tk.Gold.copy(alpha = 0.38f), a, end, strokeWidth = 12f * g.k, cap = StrokeCap.Round) // glow while it is drawn
            drawLine(Tk.Gold, a, end, strokeWidth = 5f * g.k, cap = StrokeCap.Round)
        }
        val sw = 3f * g.k
        for (cell in 0 until board.cellCount) {
            val c = pos(cell)
            val owner = state.ownerOf(cell)
            val fill = when (owner) { Player.ONE -> Tk.Player1; Player.TWO -> Tk.Player2; null -> Color.White }
            // entrance: rows pop in one after another (top to bottom); the circle just played springs up from small
            val rowT = ((enter * (board.rows + 2) - board.rowOf[cell]) / 2.5f).coerceIn(0f, 1f)
            var scale = if (rowT >= 1f) 1f else easeOutBack(rowT).coerceAtLeast(0f)
            if (feedback != null && feedback.cell == cell) scale *= easeOutBack(reveal).coerceAtLeast(0.2f)
            val rad = g.d / 2f * scale
            if (rad <= 0.5f) continue
            drawCircle(fill, rad, c)
            drawCircle(if (owner == null) Tk.MarkerOutline else Color.White, (rad - sw / 2f).coerceAtLeast(0f), c, style = Stroke(sw))
        }
        // Stage 2: pulse on the node that was just played
        if (feedback != null && reveal < 1f) {
            drawCircle(playerColor(feedback.player).copy(alpha = 1f - reveal), g.d / 2f + (3f + 9f * reveal) * g.k, pos(feedback.cell), style = Stroke(2.5f * g.k))
        }
        // the latest move keeps a softly breathing gold ring
        state.lastMove?.let { drawCircle(Tk.Gold, g.d / 2f + (2.75f + 1.5f * pulse()) * g.k, pos(it), style = Stroke(2.5f * g.k)) }
    }
}

private fun screenIsGame(vm: GameViewModel) = vm.screen == Screen.GAME
