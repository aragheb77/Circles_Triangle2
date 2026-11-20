package com.thecirclestriangle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.thecirclestriangle.engine.GameState
import com.thecirclestriangle.engine.Player

@Composable
fun GameScreen(vm: GameViewModel) {
    val s = vm.state ?: return
    val computer = vm.mode == GameMode.COMPUTER
    val name1 = if (computer) "You" else "Player 1"
    val name2 = if (computer) "Computer" else "Player 2"
    val title = when {
        computer && s.current == Player.TWO -> "AI's Turn"
        computer -> "Your Turn"
        else -> "${if (s.current == Player.ONE) name1 else name2}'s Turn"
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(Tk.CanvasBrush).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CIRCLES TRIANGLE", color = Color(0xFF7580B2), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text(title, color = Tk.Ink, fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                IconTile(Icons.Filled.Pause, "Pause", vm::pause)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreCard(name1.uppercase(), Tk.Player1, s.scoreOne, if (computer) "YOU" else "P1", Modifier.weight(1f))
                ScoreCard(name2.uppercase(), Tk.Player2, s.scoreTwo, if (computer) "AI" else "P2", Modifier.weight(1f))
            }
            val panel = RoundedCornerShape(24.dp)
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(panel)
                    .background(Brush.linearGradient(listOf(Color.White, Color(0xFFE9EDFF))))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BoardView(s, vm.hintCell, vm.pendingCell, vm::onCellTap, Modifier.fillMaxSize())
            }
            if (vm.pendingCell != null) {
                Text("Tap the highlighted circle again to confirm", color = Tk.MutedText, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            val tb = RoundedCornerShape(18.dp)
            Row(
                Modifier.fillMaxWidth().shadow(6.dp, tb, ambientColor = Color(0x226876B3), spotColor = Color(0x226876B3)).clip(tb).background(Color.White).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", vm.canUndo && !vm.aiThinking, Tk.Indigo, vm::undo)
                ToolButton(Icons.Filled.Lightbulb, "Hint", vm.isHumanTurn(), Color(0xFFE5A43E), vm::hint)
                ToolButton(Icons.Filled.PauseCircle, "Pause", true, Tk.Indigo, vm::pause)
            }
        }

        if (vm.showPause) PauseOverlay(vm)
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

@Composable
private fun IconTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(48.dp).shadow(5.dp, shape, ambientColor = Color(0x448B98CE), spotColor = Color(0x448B98CE)).clip(shape).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Color(0xFF5966A4), modifier = Modifier.size(22.dp)) }
}

@Composable
private fun ScoreCard(label: String, dot: Color, score: Int, tag: String, modifier: Modifier) {
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier.shadow(6.dp, shape, ambientColor = Color(0x226876B3), spotColor = Color(0x226876B3)).clip(shape).background(Color.White).padding(15.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label score $score" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(dot))
            Text(label, color = Color(0xFF7781B1), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(score.toString().padStart(2, '0'), color = Tk.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(tag, color = Color(0xFF6472B1), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = Tk.Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------- 04 Pause
@Composable
private fun PauseOverlay(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(Tk.Scrim).clickable(onClick = vm::resume), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier.padding(24.dp).fillMaxWidth().shadow(24.dp, shape).clip(shape)
                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFEDF0FF))))
                .clickable(enabled = false) {}
                .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Box(
                Modifier.size(70.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF6572D3), Color(0xFF9AA5FF)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PauseCircle, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
            Text("Game Paused", color = Tk.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Take a breath. Your game is saved.", color = Tk.MutedText, fontSize = 14.sp, textAlign = TextAlign.Center)
            CtaButton("RESUME", vm::resume, textSize = 15)
            SecondaryButton("Restart", { vm.requestConfirm(Confirm.RESTART) })
            SecondaryButton("Game Setup / New Game", { vm.requestConfirm(Confirm.NEW_GAME) })
            Text(
                "Exit to Main Menu", color = Color(0xFF7C83A9), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(role = Role.Button) { vm.requestConfirm(Confirm.EXIT) }.padding(8.dp),
            )
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
fun BoardView(state: GameState, hint: Int?, pending: Int?, onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val board = state.board
    val desc = buildString {
        append("Game board, ${board.rows} rows, ${board.cellCount} circles.")
        state.lastMove?.let { append(" Last move: row ${board.rowOf[it] + 1}, circle ${board.colOf[it] + 1}.") }
        hint?.let { append(" Suggested move: row ${board.rowOf[it] + 1}, circle ${board.colOf[it] + 1}.") }
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
        fun pos(cell: Int) = Offset(g.x(board.rowOf[cell], board.colOf[cell]), g.y(board.rowOf[cell]))
        for (l in board.lines) {
            drawLine(Tk.Lattice, pos(l.cells.first()), pos(l.cells.last()), strokeWidth = 3.5f * g.k, cap = StrokeCap.Round)
        }
        for (lid in state.completedLineIds) {
            val l = board.lines[lid]
            drawLine(Tk.Gold, pos(l.cells.first()), pos(l.cells.last()), strokeWidth = 5f * g.k, cap = StrokeCap.Round)
        }
        val sw = 3f * g.k
        for (cell in 0 until board.cellCount) {
            val c = pos(cell)
            val owner = state.ownerOf(cell)
            val fill = when (owner) { Player.ONE -> Tk.Player1; Player.TWO -> Tk.Player2; null -> Color.White }
            drawCircle(fill, g.d / 2f, c)
            drawCircle(if (owner == null) Tk.MarkerOutline else Color.White, g.d / 2f - sw / 2f, c, style = Stroke(sw))
            if (owner == Player.TWO) drawCircle(Color.White, g.d * 0.14f, c) // shape cue: not colour alone
        }
        state.lastMove?.let { drawCircle(Tk.Gold, g.d / 2f + 4f * g.k, pos(it), style = Stroke(2.5f * g.k)) }
        hint?.let { drawCircle(Tk.GoldLight, g.d / 2f + 6f * g.k, pos(it), style = Stroke(3.5f * g.k)) }
        pending?.let { drawCircle(Tk.Indigo, g.d / 2f + 6f * g.k, pos(it), style = Stroke(3.5f * g.k)) }
    }
}
