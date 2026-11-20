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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecirclestriangle.Confirm
import com.thecirclestriangle.GameMode
import com.thecirclestriangle.GameViewModel
import com.thecirclestriangle.Screen
import com.thecirclestriangle.engine.Board
import com.thecirclestriangle.engine.Difficulty
import com.thecirclestriangle.engine.MatchResult

@Composable
fun Root(vm: GameViewModel) {
    BackHandler(enabled = vm.screen == Screen.GAME || vm.screen == Screen.VICTORY) {
        if (vm.screen == Screen.GAME) { if (vm.showPause) vm.resume() else vm.pause() } else vm.toSetup()
    }
    when (vm.screen) {
        Screen.SPLASH -> SplashScreen(onDone = vm::finishSplash)
        Screen.SETUP -> SetupScreen(vm)
        Screen.GAME -> GameScreen(vm)
        Screen.VICTORY -> VictoryScreen(vm)
    }
}

// ---------------------------------------------------------------- 01 Splash
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) { progress.snapTo(1f) } else { progress.animateTo(1f, tween(1800, easing = LinearEasing)) }
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(0f to Color(0xFF6E7FE8), 0.42f to Color(0xFF343B83), 1f to Color(0xFF171D48), center = Offset(500f, 700f), radius = 1800f)
        ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("A STRATEGY GAME", color = Color(0xFFD8E1FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
            Spacer(Modifier.height(8.dp))
            Text("CIRCLES", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp)
            Text("TRIANGLE", color = Color(0xFFFFCF68), fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp)
            Spacer(Modifier.height(24.dp))
            TriangleLogo()
            Spacer(Modifier.height(24.dp))
            Text("THINK • CONNECT • WIN", color = Color(0xFFDCE4FF), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(28.dp))
            Box(Modifier.width(190.dp).height(5.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF))) {
                Box(
                    Modifier.fillMaxWidth(progress.value).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFCF68), Color(0xFFFF7E8E))))
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("LOADING YOUR NEXT CHALLENGE", color = Color(0xFFCBD4FF), fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

/** Triangle path and gradient from Kite (244x250 viewBox). Circle positions are APPROXIMATED - replace with the Kite SVG. */
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
        val pts = listOf(122f to 18f, 18f to 218f, 226f to 218f, 70f to 118f, 174f to 118f, 122f to 218f, 122f to 151f)
        for ((x, y) in pts) {
            drawCircle(Color(0xFFF7F8FF), 13f * k, Offset(x * k, y * k))
            drawCircle(Color(0xFFFFBD64), 13f * k - 2f * k, Offset(x * k, y * k), style = Stroke(4f * k))
        }
    }
}

// ---------------------------------------------------------------- 02 Setup
@Composable
fun SetupScreen(vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().background(Tk.CanvasBrush).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text("CIRCLES TRIANGLE", color = Tk.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text("New Game", color = Tk.Ink, fontSize = 29.sp, fontWeight = FontWeight.Black)
        }

        val modeShape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().clip(modeShape).background(Tk.ModeBrush).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SectionLabel("GAME MODE", Color(0xFFDCE4FF))
            ModeOption(Icons.Filled.Memory, "Computer", vm.mode == GameMode.COMPUTER, true) { vm.mode = GameMode.COMPUTER }
            ModeOption(Icons.Filled.People, "Two Players / Same Device", vm.mode == GameMode.TWO_PLAYERS, true) { vm.mode = GameMode.TWO_PLAYERS }
            ModeOption(Icons.Filled.Language, "Online Multiplayer", false, false) {}
            ModeOption(Icons.Filled.Wifi, "Wi-Fi / LAN Multiplayer", false, false) {}
        }

        WhiteCard {
            SectionLabel("BOARD SIZE (ROWS)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in Board.SUPPORTED_ROWS) Chip(n.toString(), vm.rows == n, { vm.rows = n }, Modifier.weight(1f))
            }
            Text("${Board.of(vm.rows).cellCount} circles", color = Tk.MutedText, fontSize = 12.sp)
        }

        if (vm.mode == GameMode.COMPUTER) {
            WhiteCard {
                SectionLabel("DIFFICULTY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in Difficulty.values())
                        Chip(d.name.lowercase().replaceFirstChar { it.uppercase() }, vm.difficulty == d, { vm.difficulty = d }, Modifier.weight(1f))
                }
            }
        }
        CtaButton("START GAME  →", onClick = vm::startGame)
    }
}

@Composable
private fun ModeOption(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f).clip(shape)
            .background(if (selected) Color(0x22FFFFFF) else Color(0x12FFFFFF))
            .then(if (selected) Modifier.border(1.5.dp, Tk.Gold, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFFFDA8A), modifier = Modifier.size(21.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, modifier = Modifier.weight(1f))
        if (selected) Text("SELECTED", color = Tk.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        if (!enabled) Text("NOT YET AVAILABLE", color = Color(0xFFDCE4FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- 05 Victory
@Composable
fun VictoryScreen(vm: GameViewModel) {
    val s = vm.state ?: return
    val computer = vm.mode == GameMode.COMPUTER
    val (title, subtitle) = when (s.result) {
        MatchResult.PLAYER_ONE_WINS -> (if (computer) "VICTORY!" else "PLAYER 1 WINS!") to (if (computer) "Brilliant strategy. Well played." else "Congratulations!")
        MatchResult.PLAYER_TWO_WINS -> (if (computer) "GAME OVER" else "PLAYER 2 WINS!") to (if (computer) "The computer took this one." else "Congratulations!")
        else -> "DRAW" to "Evenly matched."
    }
    val l1 = if (computer) "YOUR SCORE" else "PLAYER 1"
    val l2 = if (computer) "OPPONENT" else "PLAYER 2"
    Column(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(0f to Color(0xFF7987E8), 0.48f to Color(0xFF4653A5), 1f to Color(0xFF252D68), center = Offset(500f, 500f), radius = 1900f))
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("CIRCLES TRIANGLE", color = Color(0xFFDCE4FF), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
        Text(title, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, textAlign = TextAlign.Center)
        Text(subtitle, color = Color(0xFFDCE4FF), fontSize = 15.sp)
        Box(
            Modifier.size(154.dp).clip(CircleShape)
                .background(Brush.radialGradient(0f to Color(0xFFFFE8A1), 0.65f to Color(0xFFFFBB58), 1f to Color(0xFFE98D52))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.EmojiEvents, contentDescription = "Trophy", tint = Color.White, modifier = Modifier.size(76.dp)) }

        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(Color(0x18FFFFFF)).border(1.dp, Color(0x35FFFFFF), shape).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScoreCol(l1, s.scoreOne, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(50.dp).background(Color(0x44FFFFFF)))
                ScoreCol(l2, s.scoreTwo, Modifier.weight(1f))
            }
            Text("Board size: ${s.board.rows} rows", color = Color(0xFFDCE4FF), fontSize = 12.sp)
        }
        CtaButton("PLAY AGAIN", onClick = vm::startGame)
        SecondaryButton("New Setup", vm::toSetup, dark = true)
        SecondaryButton("Main Menu", vm::toMainMenu, dark = true)
    }
}

@Composable
private fun ScoreCol(label: String, score: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFFDCE4FF), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(score.toString(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
    }
}
