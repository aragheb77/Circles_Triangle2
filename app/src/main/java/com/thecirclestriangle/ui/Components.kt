package com.thecirclestriangle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CirclesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Tk.Indigo, onPrimary = Color.White, background = Tk.Canvas, surface = Color.White, onSurface = Tk.Ink),
        content = content,
    )
}

@Composable
fun CtaButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    textSize: Int = 17, vPad: Int = 17, radius: Int = 16, brush: Brush = Tk.CtaBrush, letterSpacing: Float = 0.5f,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color(0x44ED9D66), spotColor = Color(0x44ED9D66))
            .clip(shape)
            .background(brush)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = vPad.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Tk.ButtonText, fontSize = textSize.sp, fontWeight = FontWeight.Black, letterSpacing = letterSpacing.sp)
    }
}

/** Light button (Pause modal): white, 1 dp border, 14 dp padding, radius 14. */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, textColor: Color = Tk.Ink) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Tk.Border, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
}

/** Translucent button used on result screens (New Setup / Main Menu). */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Color = Color.Transparent) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape).background(fill).border(1.dp, Color(0x44FFFFFF), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = Color(0x225E6CA5), spotColor = Color(0x225E6CA5))
            .clip(shape)
            .background(Color.White)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
fun SectionLabel(text: String, color: Color = Tk.Muted) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
}

@Composable
fun Chip(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    radius: Int = 12, vPad: Int = 12, fontSize: Int = 14,
) {
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier
            .then(if (selected) Modifier.shadow(5.dp, shape, ambientColor = Color(0x554654A5), spotColor = Color(0x554654A5)) else Modifier)
            .clip(shape)
            .background(if (selected) Tk.Indigo else Color(0xFFEDF0FF))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = vPad.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) Color.White else Tk.Ink, fontSize = fontSize.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
        )
    }
}
