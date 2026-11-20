package com.thecirclestriangle.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Values read live from Kite file 3s6pvavsrg3ieakg, page "Game UI" (see design/design_tokens.json). */
object Tk {
    val Ink = Color(0xFF20264C)
    val Indigo = Color(0xFF4654A5)
    val IndigoMid = Color(0xFF414E9D)
    val IndigoLight = Color(0xFF737EE0)
    val Canvas = Color(0xFFEEF2FF)
    val CanvasMid = Color(0xFFDFE5FF)
    val CanvasEnd = Color(0xFFF8F9FF)
    val Gold = Color(0xFFFFCF70)
    val GoldLight = Color(0xFFFFE5A9)
    val Coral = Color(0xFFFF9B75)
    val Muted = Color(0xFF6974AA)
    val MutedText = Color(0xFF7881AD)
    val Border = Color(0xFFDCE1FA)
    val IconStroke = Color(0xFF5966A4)
    // frame-level values (not in the Kite design system)
    val Player1 = Color(0xFFFFAE55)
    val Player2 = Color(0xFF737EE0)
    val Lattice = Color(0xFFE7EAF7)
    val MarkerOutline = Color(0xFF7785C8)
    val Scrim = Color(0x9E1C2353)
    val ButtonText = Color(0xFF292B52)
    val SecondaryMuted = Color(0xFF7C83A9)

    val CanvasBrush = Brush.linearGradient(listOf(Canvas, CanvasMid, CanvasEnd))
    val CtaBrush = Brush.horizontalGradient(listOf(Color(0xFFFFCA6B), Coral))
    val ResultCtaBrush = Brush.horizontalGradient(listOf(Gold, Coral))
    val ModeBrush = Brush.linearGradient(listOf(IndigoMid, IndigoLight))
}
