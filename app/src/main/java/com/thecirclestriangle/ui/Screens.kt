package com.thecirclestriangle.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
    when (vm.screen) {
        Screen.SPLASH -> SplashScreen(vm.reducedMotion, onDone = vm::finishSplash)
        Screen.SETUP -> SetupScreen(vm)
        Screen.GAME -> GameScreen(vm)
        Screen.VICTORY -> ResultScreen(vm)
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
    Column(
        Modifier.fillMaxSize()
            .radialBg(0.5f, 0.35f, 0f to Color(0xFF6E7FE8), 0.42f to Color(0xFF343B83), 1f to Color(0xFF171D48), overlay = true)
            .statusBarsPadding().navigationBarsPadding().padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("A STRATEGY GAME", color = Color(0xFFD8E1FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(Modifier.height(14.dp))
            Text("CIRCLES", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, lineHeight = 40.sp)
            Text("TRIANGLE", color = Color(0xFFFFCF68), fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, lineHeight = 40.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
            TriangleLogo()
            Text("THINK • CONNECT • WIN", color = Color(0xFFDCE4FF), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
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

/** Logo exactly as in Kite (244x250 viewBox): gradient triangle outline and seven circles. */
@Composable
fun TriangleLogo() {
    Canvas(Modifier.size(244.dp, 250.dp)) {
        val k = size.width / 244f
        val path = Path().apply {
            moveTo(122f * k, 18f * k); lineTo(18f * k, 218f * k); lineTo(226f * k, 218f * k); close()
        }
        drawPath(
            path,
            Brush.linearGradient(listOf(Color(0xFFFFF2A6), Color(0xFFFFBA55), Color(0xFFFF6D8C)), Offset(0f, 0f), Offset(size.width, size.height)),
            style = Stroke(8f * k, join = StrokeJoin.Round),
        )
        val pts = listOf(122f to 18f, 70f to 118f, 174f to 118f, 18f to 218f, 122f to 218f, 226f to 218f, 122f to 118f)
        for ((x, y) in pts) {
            drawCircle(Color(0xFFF7F8FF), 13f * k, Offset(x * k, y * k))
            drawCircle(Color(0xFFFFBD64), 13f * k - 2f * k, Offset(x * k, y * k), style = Stroke(4f * k))
        }
    }
}

// ---------------------------------------------------------------- 02 Setup
@Composable
fun SetupScreen(vm: GameViewModel) {
    var info by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(Tk.CanvasBrush).statusBarsPadding().navigationBarsPadding()
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
            Box(
                Modifier.size(42.dp).shadow(5.dp, shape, ambientColor = Color(0x448B98CE), spotColor = Color(0x448B98CE)).clip(shape).background(Color.White)
                    .clickable(role = Role.Button) { info = "Settings are not available in this version yet." },
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

        if (vm.mode == GameMode.COMPUTER) {
            WhiteCard {
                SectionLabel("DIFFICULTY")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in Difficulty.values())
                        Chip(d.name.lowercase().replaceFirstChar { it.uppercase() }, vm.difficulty == d, { vm.difficulty = d }, Modifier.weight(1f), radius = 11, vPad = 11, fontSize = 13)
                }
            }
        }
        CtaButton("START GAME →", onClick = {
            if (vm.canStart) vm.startGame() else info = "Online multiplayer is not available in this version yet. Choose Play Against Computer or Two Players."
        })
    }
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
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Color(0x22FFFFFF) else Color(0x12FFFFFF))
            .then(if (selected) Modifier.border(1.5.dp, Tk.Gold, shape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LIcon(icon, 21.dp, Color(0xFFFFDA8A))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selected) Text("SELECTED", color = Tk.GoldLight, fontSize = 11.sp)
    }
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

@Composable
fun ResultScreen(vm: GameViewModel) {
    val s = vm.state ?: return
    val computer = vm.mode == GameMode.COMPUTER
    // Outcome comes from the real match result; "Defeat" = the human (Player 1) lost to the computer.
    val outcome = when (s.result) {
        MatchResult.PLAYER_ONE_WINS -> Outcome.P1_VICTORY
        MatchResult.PLAYER_TWO_WINS -> if (computer) Outcome.DEFEAT else Outcome.P2_VICTORY
        else -> Outcome.DRAW
    }
    val st = styleFor(outcome)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minH = maxHeight
        Column(
            Modifier.fillMaxSize().radialBg(st.cx, st.cy, *st.stops)
                .verticalScroll(rememberScrollState()).heightIn(min = minH)
                .statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CIRCLES TRIANGLE", color = st.headColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Spacer(Modifier.height(14.dp))
                Text(st.title, color = Color.White, fontSize = st.titleSize.sp, fontWeight = FontWeight.Black, letterSpacing = st.titleSpacing.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(st.subtitle, color = st.headColor, fontSize = st.subSize.sp, textAlign = TextAlign.Center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.padding(12.dp).size(st.emblemOuter.dp)
                        .drawBehind { drawCircle(st.ringColor, radius = size.minDimension / 2f + 12.dp.toPx()) }
                        .shadow(18.dp, CircleShape, ambientColor = st.shadowColor, spotColor = st.shadowColor)
                        .clip(CircleShape).background(st.emblemBrush),
                    contentAlignment = Alignment.Center,
                ) { Emblem(outcome, st.emblemInner.dp) }
                Text(st.label, color = st.labelColor, fontSize = st.labelSize.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
            val panel = RoundedCornerShape(22.dp)
            Column(
                Modifier.fillMaxWidth().clip(panel).background(Color(0x18FFFFFF)).border(1.dp, Color(0x35FFFFFF), panel).padding(20.dp),
            ) {
                val winnerTwo = s.scoreTwo > s.scoreOne
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("PLAYER 1", color = st.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(s.scoreOne.toString().padStart(2, '0'), color = Color.White, fontSize = st.scoreSize.sp, fontWeight = FontWeight.Black)
                    }
                    if (st.centerLabel) Text("FINAL SCORE", color = st.muted, fontSize = 12.sp)
                    else Box(Modifier.height(50.dp).width(1.dp).background(Color(0x44FFFFFF)))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PLAYER 2" + (if (winnerTwo) st.p2Tag else ""), color = st.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(s.scoreTwo.toString().padStart(2, '0'), color = st.p2Color, fontSize = st.scoreSize.sp, fontWeight = FontWeight.Black)
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
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtaButton("PLAY AGAIN", onClick = vm::startGame, textSize = 16, vPad = 17, radius = 15, brush = Tk.ResultCtaBrush, letterSpacing = 0f)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("New Setup", vm::toSetup, Modifier.weight(1f), st.glassFill)
                    GlassButton("Main Menu", vm::toMainMenu, Modifier.weight(1f), st.glassFill)
                }
            }
        }
    }
}
