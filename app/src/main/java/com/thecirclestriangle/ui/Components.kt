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
fun CtaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, textSize: Int = 17) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x44ED9D66), spotColor = Color(0x44ED9D66))
            .clip(RoundedCornerShape(16.dp))
            .background(Tk.CtaBrush)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Tk.ButtonText, fontSize = textSize.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (dark) Color(0x20FFFFFF) else Color.White
    val line = if (dark) Color(0x44FFFFFF) else Tk.Border
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (dark) Color.White else Tk.Indigo, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun SectionLabel(text: String, color: Color = Tk.Muted) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Tk.Indigo else Color(0xFFEDF0FF))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else Tk.Indigo, fontSize = 14.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
    }
}
