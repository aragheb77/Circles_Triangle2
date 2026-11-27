package com.thecirclestriangle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"
private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r v${h - 2 * r}a$r $r 0 0 1 ${-r} $r h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}Z"

/** Lucide icons exactly as used in the Kite file (24x24 viewBox, 2 px round stroke). */
object Lucide {
    val Settings = listOf(
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0a2.34 2.34 0 0 0 3.319 1.915a2.34 2.34 0 0 1 2.33 4.033a2.34 2.34 0 0 0 0 3.831a2.34 2.34 0 0 1-2.33 4.033a2.34 2.34 0 0 0-3.319 1.915a2.34 2.34 0 0 1-4.659 0a2.34 2.34 0 0 0-3.32-1.915a2.34 2.34 0 0 1-2.33-4.033a2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        circle(12f, 12f, 3f),
    )
    val Cpu = listOf(
        "M12 20v2m0-20v2m5 16v2m0-20v2M2 12h2m-2 5h2M2 7h2m16 5h2m-2 5h2M20 7h2M7 20v2M7 2v2",
        rrect(4f, 4f, 16f, 16f, 2f), rrect(8f, 8f, 8f, 8f, 1f),
    )
    val Users = listOf(
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3.128a4 4 0 0 1 0 7.744M22 21v-2a4 4 0 0 0-3-3.87",
        circle(9f, 7f, 4f),
    )
    val Globe = listOf(circle(12f, 12f, 10f), "M12 2a14.5 14.5 0 0 0 0 20a14.5 14.5 0 0 0 0-20M2 12h20")
    val Pause = listOf(rrect(14f, 3f, 5f, 18f, 1f), rrect(5f, 3f, 5f, 18f, 1f))
    val Undo2 = listOf("M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11")
    val CirclePause = listOf(circle(12f, 12f, 10f), "M10 15V9m4 6V9")
    val Wifi = listOf("M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0")
    val Bluetooth = listOf("M7 7L17 17L12 22V2L17 7L7 17")
    val ArrowLeft = listOf("M19 12H5", "M12 19L5 12L12 5")
    val RefreshCw = listOf(
        "M3 12a9 9 0 0 1 9-9a9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9a9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5",
    )
    val Music = listOf("M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f))
    /** Music with a diagonal slash (music switched off). */
    val MusicOff = listOf("M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f), "M3 3L21 21")
}

@Composable
fun LIcon(paths: List<String>, dp: Dp, tint: Color, modifier: Modifier = Modifier) {
    val parsed = remember(paths) { paths.map { parse(it) } }
    Canvas(modifier.size(dp)) {
        val k = this.size.width / 24f
        scale(k, k, pivot = Offset.Zero) {
            for (p in parsed) drawPath(p, tint, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

enum class Outcome { P1_VICTORY, P2_VICTORY, DEFEAT, DRAW }

private class Shape(
    val path: Path?, val cx: Float = 0f, val cy: Float = 0f, val r: Float = 0f,
    val fill: Color? = null, val stroke: Color? = null, val sw: Float = 0f, val round: Boolean = false,
) {
    fun draw(ds: DrawScope) {
        if (path != null) {
            fill?.let { ds.drawPath(path, it) }
            stroke?.let { ds.drawPath(path, it, style = Stroke(sw, cap = if (round) StrokeCap.Round else StrokeCap.Butt, join = if (round) StrokeJoin.Round else StrokeJoin.Miter)) }
        } else {
            fill?.let { ds.drawCircle(it, r, Offset(cx, cy)) }
            stroke?.let { ds.drawCircle(it, r, Offset(cx, cy), style = Stroke(sw)) }
        }
    }
}

private fun p(d: String, fill: Color? = null, stroke: Color? = null, sw: Float = 0f, round: Boolean = false) =
    Shape(parse(d), fill = fill, stroke = stroke, sw = sw, round = round)

private fun build(o: Outcome): List<Shape> = when (o) {
    // Player 1 victory: trophy, laurel and stars (Kite 05 Victory). Gradient fills in the SVG resolve to their first stop.
    Outcome.P1_VICTORY -> listOf(
        p("M23 75C10 64 9 44 17 30M77 75c13-11 14-31 6-45", stroke = Color(0xFFFFF1C1), sw = 3f, round = true),
        p("M19 61l-8 1 3-8M81 61l8 1-3-8M22 43l-8-3 6-5M78 43l8-3-6-5", stroke = Color(0xFFFFF1C1), sw = 2.5f, round = true),
        p("M34 22h32v18c0 13-7 22-16 22s-16-9-16-22V22Z", fill = Color(0xFFFFF5C7), stroke = Color(0xFFFFF9E6), sw = 2.5f),
        p("M34 29H23v7c0 9 6 15 15 15M66 29h11v7c0 9-6 15-15 15", stroke = Color(0xFFFFE8A6), sw = 5f, round = true),
        p("M50 62v11m-12 7h24M43 73h14", stroke = Color(0xFFFFF4D2), sw = 4f, round = true),
        p("m50 29 3.3 6.7 7.4 1.1-5.35 5.2 1.26 7.35L50 45.9l-6.61 3.45 1.26-7.35-5.35-5.2 7.4-1.1L50 29Z", fill = Color(0xFFFFFDF2)),
        p("m17 15 2 5 5 2-5 2-2 5-2-5-5-2 5-2 2-5Zm66 2 1.5 4 4 1.5-4 1.5-1.5 4-1.5-4-4-1.5 4-1.5 1.5-4Z", fill = Color(0xFFFFF2C4)),
    )
    // Defeat: shield, lightning and stars (Kite 06 Loss)
    Outcome.DEFEAT -> listOf(
        p("M50 9 81 21v25c0 20-13 34-31 45C32 80 19 66 19 46V21L50 9Z", fill = Color(0xFFF3F4FF), stroke = Color.White, sw = 2.5f),
        p("m55 22-19 28h13l-4 25 20-32H52l3-21Z", fill = Color(0xFF5966B5), stroke = Color.White, sw = 2.5f, round = true),
        p("M13 28 7 23m80 5 6-5M13 70l-6 5m80-5 6 5", stroke = Color(0xFFE8EBFF), sw = 3f, round = true),
        p("m18 12 2 5 5 2-5 2-2 5-2-5-5-2 5-2 2-5Zm64 0 2 5 5 2-5 2-2 5-2-5-5-2 5-2 2-5Z", fill = Color.White),
    )
    // Draw: overlapping players and infinity motif (Kite 07 Draw)
    Outcome.DRAW -> listOf(
        Shape(null, 39f, 48f, 27f, fill = Color(0xFFFFFFFF).copy(alpha = 0.92f)),
        Shape(null, 39f, 48f, 27f, stroke = Color.White, sw = 2.5f),
        Shape(null, 61f, 48f, 27f, fill = Color(0xFFDDE3FF).copy(alpha = 0.88f)),
        Shape(null, 61f, 48f, 27f, stroke = Color.White, sw = 2.5f),
        p("M35 49c5-10 11-10 16 0s11 10 16 0", stroke = Color(0xFF5966A4), sw = 4f, round = true),
        p("M50 19v-8m-4 4h8M18 78l-5 5m5 0-5-5m69 0-5 5m5 0-5-5", stroke = Color.White, sw = 2.5f, round = true),
        p("m50 2 2.5 6.5L59 11l-6.5 2.5L50 20l-2.5-6.5L41 11l6.5-2.5L50 2Z", fill = Color.White),
    )
    // Player 2 victory: shield, upward arrow and laurel (Kite 08 Player 2 Victory)
    Outcome.P2_VICTORY -> listOf(
        p("M23 77C10 65 10 43 18 29M77 77c13-12 13-34 5-48", stroke = Color(0xFFE7EAFF), sw = 3f, round = true),
        p("m19 62-8 1 3-8m67 7 8 1-3-8M22 43l-8-3 6-5m58 8 8-3-6-5", stroke = Color(0xFFE7EAFF), sw = 2.5f, round = true),
        p("M50 10 78 21v24c0 19-12 32-28 42C34 77 22 64 22 45V21L50 10Z", fill = Color(0xFFF0F2FF), stroke = Color(0xFFF6F7FF), sw = 2.5f),
        p("M50 25v39m-13-26 13-13 13 13", stroke = Color.White, sw = 4f, round = true),
        p("m50 38 5 9-5 8-5-8 5-9Z", fill = Color(0xFF6878D8), stroke = Color.White, sw = 1.5f),
        p("m50 3 2.2 5.8L58 11l-5.8 2.2L50 19l-2.2-5.8L42 11l5.8-2.2L50 3Z", fill = Color.White),
        Shape(null, 84f, 18f, 3f, fill = Color.White),
        Shape(null, 16f, 20f, 2.5f, fill = Color.White),
    )
}

@Composable
fun Emblem(outcome: Outcome, dp: Dp) {
    val shapes = remember(outcome) { build(outcome) }
    Canvas(Modifier.size(dp)) {
        val k = this.size.width / 100f
        scale(k, k, pivot = Offset.Zero) { for (s in shapes) s.draw(this) }
    }
}
