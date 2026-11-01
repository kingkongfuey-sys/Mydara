package com.example.mydara.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-page, fully opaque in-game menu. The game is paused while it is open (the caller does the pausing).
 * Restart keeps the same map and settings; New map keeps the settings but generates a fresh map.
 */
@Composable
fun PauseMenu(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onRegenerate: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            // nothing may reach the game underneath (without consuming: that would cancel the buttons' taps)
            .blockTouchesBehind()
    ) {
        AnimatedGround()
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Paused", color = IconFill, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(10.dp))
            PauseButton("Resume", null, primary = true, onClick = onResume)
            PauseButton("Restart", "Same map, same settings", primary = false, onClick = onRestart)
            PauseButton("New map", "Same settings, new layout", primary = false, onClick = onRegenerate)
            PauseButton("Exit to menu", null, primary = false, onClick = onExit)
        }
    }
}

@Composable
private fun PauseButton(title: String, sub: String?, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (primary) AccentGreen else PanelColor)
            .border(1.5.dp, if (primary) AccentGreen else IconFill.copy(alpha = 0.3f), shape)
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = if (primary) InkColor else IconFill,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        if (sub != null) {
            Text(sub, color = IconFill.copy(alpha = 0.65f), fontSize = 12.sp)
        }
    }
}
