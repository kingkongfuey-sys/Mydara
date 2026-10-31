package com.example.mydara.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GroundShades = listOf(Color(0xFF14506A), Color(0xFF0F5C73), Color(0xFF1A4A78))
internal val PanelColor = Color(0xFF123A4E)
internal val AccentGreen = Color(0xFF7EE8A2)

/** Calm drifting translucent cells behind every menu screen. */
@Composable
internal fun AnimatedGround(modifier: Modifier = Modifier) {
    val field = remember { GroundField(seed = 7L) }
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(field) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) field.step(((now - last) / 1_000_000_000.0).coerceAtMost(0.1))
                last = now
                tick++
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        tick // read so the canvas redraws every frame
        drawRect(BackgroundColor)
        for (c in field.cells) {
            val s = (c.size * size.width).toFloat()
            drawRoundRect(
                color = GroundShades[c.shade].copy(alpha = c.alpha),
                topLeft = Offset((c.x * size.width).toFloat(), (c.y * size.height).toFloat()),
                size = Size(s, s),
                cornerRadius = CornerRadius(s * 0.22f)
            )
        }
    }
}

@Composable
internal fun BackButton(onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val chevron = Path().apply {
                moveTo(size.width * 0.7f, size.height * 0.1f)
                lineTo(size.width * 0.25f, size.height * 0.5f)
                lineTo(size.width * 0.7f, size.height * 0.9f)
            }
            drawPath(
                chevron, IconFill,
                style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack)
        Spacer(Modifier.width(6.dp))
        Text(title, color = IconFill, fontSize = 26.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = IconFill.copy(alpha = 0.7f), fontSize = 12.sp, letterSpacing = 1.5.sp)
        content()
    }
}

/** Selectable rounded option (map size, opponents, difficulty). */
@Composable
internal fun OptionChip(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val edge = if (selected) AccentGreen else IconFill.copy(alpha = 0.25f)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) AccentGreen else PanelColor.copy(alpha = 0.85f))
            .border(1.5.dp, edge, shape)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
internal fun ChipTitle(text: String, selected: Boolean) {
    Text(text, color = if (selected) InkColor else IconFill, fontSize = 16.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun ChipSub(text: String, selected: Boolean) {
    Text(
        text,
        color = if (selected) InkColor.copy(alpha = 0.75f) else IconFill.copy(alpha = 0.65f),
        fontSize = 11.sp
    )
}

/** Title + description with a switch on the right. */
@Composable
internal fun SwitchRow(title: String, sub: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PanelColor.copy(alpha = 0.85f))
            .clickable { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = IconFill, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = IconFill.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
