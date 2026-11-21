package com.thecirclestriangle.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecirclestriangle.audio.Sfx
import com.thecirclestriangle.audio.Sound

@Composable
fun CirclesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Tk.Indigo, onPrimary = Color.White, background = Tk.Canvas, surface = Color.White, onSurface = Tk.Ink),
        content = content,
    )
}

/** Springs the element down to 94 % while [source] is pressed. Put it FIRST in the chain so background and shadow scale too. */
fun Modifier.pressScale(source: MutableInteractionSource, enabled: Boolean = true): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Clickable that plays the UI click sound. Keep it where `.clickable` used to be (after clip / background). */
fun Modifier.tapClickable(
    source: MutableInteractionSource, enabled: Boolean = true, role: Role = Role.Button, sound: Boolean = true, onClick: () -> Unit,
): Modifier = composed {
    clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = role) {
        if (sound) Sound.play(Sfx.CLICK)
        onClick()
    }
}

/** Both in one, for plain elements with no shadow / clip in front of the click (e.g. icon + label buttons). */
fun Modifier.bounceClick(enabled: Boolean = true, role: Role = Role.Button, sound: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    pressScale(source, enabled).tapClickable(source, enabled, role, sound, onClick)
}

@Composable
fun CtaButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    textSize: Int = 17, vPad: Int = 17, radius: Int = 16, brush: Brush = Tk.CtaBrush, letterSpacing: Float = 0.5f,
) {
    val shape = RoundedCornerShape(radius.dp)
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src)
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color(0x44ED9D66), spotColor = Color(0x44ED9D66))
            .clip(shape)
            .background(brush)
            .tapClickable(src, onClick = onClick)
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
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(src).fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Tk.Border, shape)
            .tapClickable(src, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
}

/** Translucent button used on result screens (New Setup / Main Menu). */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Color = Color.Transparent) {
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(src).clip(shape).background(fill).border(1.dp, Color(0x44FFFFFF), shape)
            .tapClickable(src, onClick = onClick).padding(vertical = 15.dp),
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
    val bg by animateColorAsState(if (selected) Tk.Indigo else Color(0xFFEDF0FF), tween(180), label = "chip")
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src)
            .then(if (selected) Modifier.shadow(5.dp, shape, ambientColor = Color(0x554654A5), spotColor = Color(0x554654A5)) else Modifier)
            .clip(shape)
            .background(bg)
            .tapClickable(src, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = vPad.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) Color.White else Tk.Ink, fontSize = fontSize.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
        )
    }
}

/** Single-line text field in the setup-card style (light fill, indigo border while focused). */
@Composable
fun NameField(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String, dot: Color,
    last: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier, maxLength: Int = 12,
) {
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(13.dp)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) Tk.Indigo else Tk.Border, tween(150), label = "nameBorder")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.padding(top = 1.dp).size(13.dp).clip(CircleShape).background(dot))
            Text(label, color = Tk.Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it != '\n' }.take(maxLength)) },
            singleLine = true,
            textStyle = TextStyle(color = Tk.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Tk.Indigo),
            interactionSource = source,
            keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) },
                onDone = { focus.clearFocus(); onDone() },
            ),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(shape).background(Color(0xFFF4F6FF)).border(BorderStroke(1.5.dp, border), shape)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    if (value.isEmpty()) Text(placeholder, color = Tk.MutedText.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    inner()
                }
            },
        )
    }
}

/** Switch row used in the Settings dialog and the pause menu. */
@Composable
fun SwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Tk.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(hint, color = Tk.MutedText, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Tk.Indigo, checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFDCE1FA), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color(0xFFDCE1FA),
            ),
        )
    }
}
