package com.thecirclestriangle.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import com.thecirclestriangle.audio.Sound
import com.thecirclestriangle.engine.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecirclestriangle.GameMode
import com.thecirclestriangle.GameViewModel
import com.thecirclestriangle.Screen
import com.thecirclestriangle.engine.Board
import com.thecirclestriangle.engine.Difficulty
import com.thecirclestriangle.engine.MatchResult
import kotlin.math.hypot

/** CSS-style "radial-gradient(ellipse at cx cy, ...)" (farthest-corner) with an optional vertical overlay. */
fun Modifier.radialBg(cx: Float, cy: Float, vararg stops: Pair<Float, Color>, overlay: Boolean = false) = drawBehind {
    val c = Offset(size.width * cx, size.height * cy)
    val r = hypot(maxOf(c.x, size.width - c.x), maxOf(c.y, size.height - c.y))
    drawRect(Brush.radialGradient(*stops, center = c, radius = r))
    if (overlay) {
        drawRect(Brush.verticalGradient(0f to Color(0x1AFFFFFF), 0.42f to Color.Transparent, 1f to Color(0x6B080D28)))
    }
}

@Composable
fun Root(vm: GameViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        vm.reducedMotion = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    BackHandler(enabled = vm.screen == Screen.GAME || vm.screen == Screen.VICTORY) {
        if (vm.screen == Screen.GAME) { if (vm.showPause) vm.resume() else vm.pause() } else vm.toSetup()
    }
    // Screen-to-screen motion: the new screen fades and settles in (scale 96 -> 100 %), the old one fades out.
    AnimatedContent(
        targetState = vm.screen,
        transitionSpec = {
            if (vm.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(340, delayMillis = 70)) + scaleIn(tween(340, delayMillis = 70), initialScale = 0.96f)) togetherWith fadeOut(tween(160))
        },
        label = "screens",
    ) { screen ->
        when (screen) {
            Screen.SPLASH -> SplashScreen(vm.reducedMotion, onDone = vm::finishSplash)
            Screen.SETUP -> SetupScreen(vm)
            Screen.GAME -> GameScreen(vm)
            Screen.VICTORY -> ResultScreen(vm)
        }
    }
}

// ---------------------------------------------------------------- 01 Splash
@Composable
fun SplashScreen(reduceMotion: Boolean, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) progress.snapTo(1f) else progress.animateTo(1f, tween(1800, easing = LinearEasing))
        onDone()
    }
    val p = progress.value
    fun win(a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxSize()
            .radialBg(0.5f, 0.35f, 0f to Color(0xFF6E7FE8), 0.42f to Color(0xFF343B83), 1f to Color(0xFF171D48), overlay = true)
            .statusBarsPadding().navigationBarsPadding().padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // title drops in
        Column(
            Modifier.graphicsLayer { alpha = win(0f, 0.25f); translationY = (1f - win(0f, 0.25f)) * -28f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("A STRATEGY GAME", color = Color(0xFFD8E1FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(Modifier.height(14.dp))
            Text("CIRCLES", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, lineHeight = 40.sp)
            Text("TRIANGLE", color = Color(0xFFFFCF68), fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, lineHeight = 40.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
            TriangleLogo(p)   // triangle is drawn edge by edge, then the seven circles pop in
            Text(
                "THINK • CONNECT • WIN", color = Color(0xFFDCE4FF), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer { alpha = win(0.55f, 0.8f); translationY = (1f - win(0.55f, 0.8f)) * 16f },
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.width(190.dp).height(5.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF))) {
                Box(
                    Modifier.fillMaxWidth(progress.value).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFCF68), Color(0xFFFF7E8E))))
                )
            }
            Text("LOADING YOUR NEXT CHALLENGE", color = Color(0xFFCBD4FF), fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

internal fun easeOutBack(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val x = t - 1f; return 1f + c3 * x * x * x + c1 * x * x }

/** Logo exactly as in Kite (244x250 viewBox). [progress] 0..1: the outline draws itself, then the circles pop in one by one. */
@Composable
fun TriangleLogo(progress: Float = 1f) {
    Canvas(Modifier.size(244.dp, 250.dp)) {
        val k = size.width / 244f
        val path = Path().apply {
            moveTo(122f * k, 18f * k); lineTo(18f * k, 218f * k); lineTo(226f * k, 218f * k); close()
        }
        val edge = (progress / 0.6f).coerceIn(0f, 1f)
        val shown = if (edge >= 1f) path else Path().also {
            val m = PathMeasure(); m.setPath(path, false); m.getSegment(0f, m.length * edge, it, true)
        }
        drawPath(
            shown,
            Brush.linearGradient(listOf(Color(0xFFFFF2A6), Color(0xFFFFBA55), Color(0xFFFF6D8C)), Offset(0f, 0f), Offset(size.width, size.height)),
            style = Stroke(8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val pts = listOf(122f to 18f, 70f to 118f, 174f to 118f, 18f to 218f, 122f to 218f, 226f to 218f, 122f to 118f)
        pts.forEachIndexed { i, (x, y) ->
            val t = ((progress - (0.2f + i * 0.085f)) / 0.22f).coerceIn(0f, 1f)
            if (t > 0f) {
                val r = 13f * k * easeOutBack(t)
                drawCircle(Color(0xFFF7F8FF), r, Offset(x * k, y * k))
                drawCircle(Color(0xFFFFBD64), (r - 2f * k).coerceAtLeast(0f), Offset(x * k, y * k), style = Stroke(4f * k))
            }
        }
    }
}

// ---------------------------------------------------------------- 02 Setup
@Composable
fun SetupScreen(vm: GameViewModel) {
    var info by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Tk.CanvasBrush).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("CIRCLES TRIANGLE", color = Tk.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text("New Game", color = Tk.Ink, fontSize = 29.sp, fontWeight = FontWeight.Black)
            }
            val shape = RoundedCornerShape(14.dp)
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier.pressScale(src).size(42.dp).shadow(5.dp, shape, ambientColor = Color(0x448B98CE), spotColor = Color(0x448B98CE)).clip(shape).background(Color.White)
                    .semantics { contentDescription = "Settings" }
                    .tapClickable(src) { showSettings = true },
                contentAlignment = Alignment.Center,
            ) { LIcon(Lucide.Settings, 20.dp, Tk.IconStroke) }
        }

        val modeShape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().shadow(12.dp, modeShape, ambientColor = Color(0x444654A5), spotColor = Color(0x444654A5)).clip(modeShape).background(Tk.ModeBrush).padding(18.dp),
        ) {
            Text("CHOOSE YOUR PLAY MODE", color = Color(0xFFDCE2FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ModeOption(Lucide.Cpu, "Play Against Computer", vm.mode == GameMode.COMPUTER) { vm.mode = GameMode.COMPUTER }
                ModeOption(Lucide.Users, "Two Players", vm.mode == GameMode.TWO_PLAYERS) { vm.mode = GameMode.TWO_PLAYERS }
                ModeOption(Lucide.Globe, "Online Multiplayer", vm.mode == GameMode.ONLINE) { vm.mode = GameMode.ONLINE }
            }
        }

        WhiteCard {
            SectionLabel("BOARD SIZE")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (n in 5..9) Chip(n.toString(), vm.rows == n, { vm.rows = n }, Modifier.size(48.dp, 44.dp), vPad = 0)
            }
        }

        // Difficulty (vs computer) or player names (two players): the card swaps with a soft cross-fade.
        if (vm.mode != GameMode.ONLINE) {
            AnimatedContent(
                targetState = vm.mode,
                transitionSpec = { (fadeIn(tween(240, 90)) + scaleIn(tween(240, 90), initialScale = 0.97f)) togetherWith fadeOut(tween(90)) using SizeTransform(clip = false) },
                label = "modeCard",
            ) { m ->
                if (m == GameMode.COMPUTER) {
                    WhiteCard {
                        SectionLabel("DIFFICULTY")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (d in Difficulty.values())
                                Chip(d.name.lowercase().replaceFirstChar { it.uppercase() }, vm.difficulty == d, { vm.difficulty = d }, Modifier.weight(1f), radius = 11, vPad = 11, fontSize = 13)
                        }
                    }
                } else {
                    val focus = LocalFocusManager.current
                    WhiteCard {
                        SectionLabel("PLAYER NAMES")
                        NameField(vm.nameOne, { vm.nameOne = it }, "PLAYER 1", "Player 1", Tk.Player1, last = false, onDone = {})
                        NameField(vm.nameTwo, { vm.nameTwo = it }, "PLAYER 2", "Player 2", Tk.Player2, last = true, onDone = { focus.clearFocus() })
                        Text("Optional. Leave a name empty to use the default.", color = Tk.MutedText, fontSize = 12.sp)
                    }
                }
            }
        }
        CtaButton("START GAME →", onClick = {
            if (vm.canStart) vm.startGame() else info = "Online multiplayer is not available in this version yet. Choose Play Against Computer or Two Players."
        })
    }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
    info?.let { msg ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text("Not available yet") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun ModeOption(icon: List<String>, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier.pressScale(src).fillMaxWidth().clip(shape)
            .background(if (selected) Color(0x22FFFFFF) else Color(0x12FFFFFF))
            .then(if (selected) Modifier.border(1.5.dp, Tk.Gold, shape) else Modifier)
            .tapClickable(src, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LIcon(icon, 21.dp, Color(0xFFFFDA8A))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selected) Text("SELECTED", color = Tk.GoldLight, fontSize = 11.sp)
    }
}

/** Settings: music and sound-effect switches (remembered between launches). */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Settings", color = Tk.Ink, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SwitchRow("Music", "Background music", Sound.musicOn) { Sound.setMusic(it) }
                SwitchRow("Sound effects", "Taps, scoring and results", Sound.effectsOn) { Sound.setEffects(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Tk.Indigo, fontWeight = FontWeight.ExtraBold) } },
    )
}

// ---------------------------------------------------------------- Results (05 / 06 / 07 / 08)
private class ResultStyle(
    val cx: Float, val cy: Float, val stops: Array<Pair<Float, Color>>,
    val title: String, val titleSize: Int, val titleSpacing: Float, val subtitle: String, val subSize: Int, val headColor: Color,
    val label: String, val labelSize: Int, val labelColor: Color,
    val emblemOuter: Int, val emblemInner: Int, val emblemBrush: Brush, val ringColor: Color, val shadowColor: Color,
    val muted: Color, val p2Tag: String, val centerLabel: Boolean, val glassFill: Color, val showBoardSize: Boolean,
    val scoreSize: Int, val p2Color: Color,
)

private fun styleFor(o: Outcome): ResultStyle = when (o) {
    Outcome.P1_VICTORY -> ResultStyle(
        0.5f, 0.30f, arrayOf(0f to Color(0xFF7987E8), 0.48f to Color(0xFF4653A5), 1f to Color(0xFF252D68)),
        "PLAYER 1 WINS", 36, -1f, "A brilliant game. Player 1 takes the win.", 15, Color(0xFFDCE2FF),
        "WINNER • PLAYER 1", 15, Color(0xFFFFDFA0),
        154, 88, Brush.radialGradient(0f to Color(0xFFFFE8A1), 0.65f to Color(0xFFFFBB58), 1f to Color(0xFFE98D52)), Color(0x18FFFFFF), Color(0x66131B4E),
        Color(0xFFD8E0FF), "", false, Color(0x20FFFFFF), true, 36, Color.White,
    )
    Outcome.P2_VICTORY -> ResultStyle(
        0.5f, 0.30f, arrayOf(0f to Color(0xFF8992F0), 0.48f to Color(0xFF5966BD), 1f to Color(0xFF252D68)),
        "PLAYER 2 WINS!", 35, 0f, "A brilliant game. Player 2 takes the win.", 14, Color(0xFFE2E6FF),
        "WINNER • PLAYER 2", 14, Color(0xFFE2E6FF),
        148, 88, Brush.radialGradient(0f to Color(0xFFE3E7FF), 0.65f to Color(0xFF9CA7F3), 1f to Color(0xFF737EE0)), Color(0x18FFFFFF), Color(0x66131B4E),
        Color(0xFFE2E6FF), " • WINNER", false, Color.Transparent, false, 34, Color.White,
    )
    Outcome.DEFEAT -> ResultStyle(
        0.5f, 0.25f, arrayOf(0f to Color(0xFF5363B2), 0.58f to Color(0xFF303B7B), 1f to Color(0xFF202958)),
        "DEFEAT", 37, 0f, "Player 2 wins this round", 14, Color(0xFFDCE2FF),
        "KEEP PRACTISING", 14, Color(0xFFDCE2FF),
        148, 84, Brush.radialGradient(0f to Color(0xFFC7CDF2), 0.68f to Color(0xFF737EE0), 1f to Color(0xFF4654A5)), Color(0x14FFFFFF), Color(0x66111840),
        Color(0xFFDCE2FF), " • WINNER", true, Color.Transparent, false, 34, Color(0xFFC9D0FF),
    )
    Outcome.DRAW -> ResultStyle(
        0.5f, 0.25f, arrayOf(0f to Color(0xFF7A83BD), 0.58f to Color(0xFF465078), 1f to Color(0xFF292F50)),
        "IT'S A DRAW", 37, 0f, "An evenly matched game. No winner this time.", 14, Color(0xFFE1E5FA),
        "WELL PLAYED • BOTH PLAYERS", 14, Color(0xFFE1E5FA),
        148, 84, Brush.radialGradient(0f to Color.White, 0.68f to Color(0xFFDCE2FF), 1f to Color(0xFFAEB8E8)), Color(0x14FFFFFF), Color(0x66111840),
        Color(0xFFE1E5FA), "", true, Color.Transparent, false, 34, Color.White,
    )
}

/** Falling confetti for the win screens (purely decorative, never intercepts touches). */
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t")
    val palette = remember { listOf(Color(0xFFFFCF68), Color(0xFFFF9B75), Color(0xFFFFFFFF), Color(0xFF9CA7F3), Color(0xFFFFE5A9)) }
    val bits = remember {
        val r = kotlin.random.Random(11)
        List(34) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat(), r.nextFloat() * 360f, 5f + r.nextFloat() * 6f, r.nextInt(palette.size).toFloat()) }
    }
    Canvas(modifier) {
        for (b in bits) {
            val prog = (b[1] + t * b[2]) % 1f                                   // 0 = above the screen, 1 = below it
            val x = (b[0] + 0.035f * sin(((t * 3f * b[2]) + b[1]) * 2f * PI.toFloat())) * size.width
            val y = prog * (size.height + 60f) - 30f
            val w = b[4].dp.toPx()
            rotate(b[3] + t * 720f * b[2], pivot = Offset(x, y)) {
                drawRect(palette[b[5].toInt()].copy(alpha = 0.9f), Offset(x - w / 2f, y - w * 0.3f), androidx.compose.ui.geometry.Size(w, w * 0.6f))
            }
        }
    }
}

@Composable
fun ResultScreen(vm: GameViewModel) {
    val s = vm.state ?: return
    val computer = vm.mode == GameMode.COMPUTER
    // Outcome comes from the real match result; "Defeat" = the human (You) lost to the computer.
    val outcome = when (s.result) {
        MatchResult.PLAYER_ONE_WINS -> Outcome.P1_VICTORY
        MatchResult.PLAYER_TWO_WINS -> if (computer) Outcome.DEFEAT else Outcome.P2_VICTORY
        else -> Outcome.DRAW
    }
    val st = styleFor(outcome)
    val n1 = vm.nameOf(Player.ONE)
    val n2 = vm.nameOf(Player.TWO)
    // Names replace "Player 1" / "Player 2" (and "You" / "Computer" in computer mode).
    val (title, subtitle, label) = when (outcome) {
        Outcome.P1_VICTORY ->
            if (computer) Triple("YOU WIN!", "A brilliant game. You take the win.", "WINNER • YOU")
            else Triple("${n1.uppercase()} WINS", "A brilliant game. $n1 takes the win.", "WINNER • ${n1.uppercase()}")
        Outcome.P2_VICTORY -> Triple("${n2.uppercase()} WINS!", "A brilliant game. $n2 takes the win.", "WINNER • ${n2.uppercase()}")
        Outcome.DEFEAT -> Triple(st.title, "$n2 wins this round", st.label)
        Outcome.DRAW -> Triple(st.title, st.subtitle, st.label)
    }
    val titleSize = if (title.length <= 14) st.titleSize.toFloat() else (st.titleSize * 14f / title.length).coerceAtLeast(22f)

    // ---- motion: staggered entrance, emblem spring, floating emblem, score count-up
    val enter = remember { Animatable(if (vm.reducedMotion) 1f else 0f) }
    val emblemScale = remember { Animatable(if (vm.reducedMotion) 1f else 0.2f) }
    var scoreTarget by remember { mutableStateOf(if (vm.reducedMotion) 1 else 0) }
    LaunchedEffect(Unit) {
        if (!vm.reducedMotion) {
            launch { enter.animateTo(1f, tween(1000, easing = LinearEasing)) }
            launch { delay(180); emblemScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            delay(650); scoreTarget = 1
        }
    }
    fun win(a: Float, b: Float) = ((enter.value - a) / (b - a)).coerceIn(0f, 1f)
    val shownOne by animateIntAsState(s.scoreOne * scoreTarget, tween(if (vm.reducedMotion) 0 else 900), label = "s1")
    val shownTwo by animateIntAsState(s.scoreTwo * scoreTarget, tween(if (vm.reducedMotion) 0 else 900), label = "s2")
    val idle = rememberInfiniteTransition(label = "idle")
    val bob by idle.animateFloat(-5f, 5f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "bob")
    val halo by idle.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "halo")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minH = maxHeight
        Column(
            Modifier.fillMaxSize().radialBg(st.cx, st.cy, *st.stops)
                .verticalScroll(rememberScrollState()).heightIn(min = minH)
                .statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.graphicsLayer { alpha = win(0f, 0.35f); translationY = (1f - win(0f, 0.35f)) * -24f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("CIRCLES TRIANGLE", color = st.headColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Spacer(Modifier.height(14.dp))
                Text(title, color = Color.White, fontSize = titleSize.sp, fontWeight = FontWeight.Black, letterSpacing = st.titleSpacing.sp, textAlign = TextAlign.Center, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = st.headColor, fontSize = st.subSize.sp, textAlign = TextAlign.Center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.padding(12.dp).size(st.emblemOuter.dp)
                        .graphicsLayer {
                            scaleX = emblemScale.value; scaleY = emblemScale.value
                            translationY = if (emblemScale.value >= 0.99f) bob.dp.toPx() else 0f
                        }
                        .drawBehind { drawCircle(st.ringColor, radius = (size.minDimension / 2f + 12.dp.toPx()) * halo) }
                        .shadow(18.dp, CircleShape, ambientColor = st.shadowColor, spotColor = st.shadowColor)
                        .clip(CircleShape).background(st.emblemBrush),
                    contentAlignment = Alignment.Center,
                ) { Emblem(outcome, st.emblemInner.dp) }
                Text(label, color = st.labelColor, fontSize = st.labelSize.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    modifier = Modifier.graphicsLayer { alpha = win(0.35f, 0.65f) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val panel = RoundedCornerShape(22.dp)
            Column(
                Modifier.fillMaxWidth().graphicsLayer { alpha = win(0.4f, 0.75f); translationY = (1f - win(0.4f, 0.75f)) * 40f }
                    .clip(panel).background(Color(0x18FFFFFF)).border(1.dp, Color(0x35FFFFFF), panel).padding(20.dp),
            ) {
                val winnerTwo = s.scoreTwo > s.scoreOne
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(n1.uppercase(), color = st.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(shownOne.toString().padStart(2, '0'), color = Color.White, fontSize = st.scoreSize.sp, fontWeight = FontWeight.Black)
                    }
                    if (st.centerLabel) Text("FINAL SCORE", color = st.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    else Box(Modifier.height(50.dp).width(1.dp).background(Color(0x44FFFFFF)))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(n2.uppercase() + (if (winnerTwo) st.p2Tag else ""), color = st.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(shownTwo.toString().padStart(2, '0'), color = st.p2Color, fontSize = st.scoreSize.sp, fontWeight = FontWeight.Black)
                    }
                }
                if (st.showBoardSize) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
                    Spacer(Modifier.height(13.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Board size", color = Color(0xFFE1E6FF), fontSize = 13.sp)
                        Text("${s.board.rows} rows", color = Color(0xFFE1E6FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().graphicsLayer { alpha = win(0.6f, 1f); translationY = (1f - win(0.6f, 1f)) * 40f },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CtaButton("PLAY AGAIN", onClick = vm::startGame, textSize = 16, vPad = 17, radius = 15, brush = Tk.ResultCtaBrush, letterSpacing = 0f)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("New Setup", vm::toSetup, Modifier.weight(1f), st.glassFill)
                    GlassButton("Main Menu", vm::toMainMenu, Modifier.weight(1f), st.glassFill)
                }
            }
        }
        // a victory is celebrated with confetti; a draw or defeat is not
        val celebrate = outcome == Outcome.P1_VICTORY || outcome == Outcome.P2_VICTORY
        if (celebrate && !vm.reducedMotion) Confetti(Modifier.fillMaxSize().alpha(win(0.2f, 0.5f)))
    }
}
