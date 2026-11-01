package com.example.mydara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal settings: sound + haptics and the app version.
 * FUTURE: colorblind palette, lock camera, spectate-after-victory, palette / theme switcher.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var soundOn by remember { mutableStateOf(FeedbackSettings.sound) }
    var hapticsOn by remember { mutableStateOf(FeedbackSettings.haptics) }
    LaunchedEffect(Unit) {
        FeedbackPrefs.load(context)
        soundOn = FeedbackSettings.sound
        hapticsOn = FeedbackSettings.haptics
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedGround()
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader("Settings", onBack)
            SwitchRow("Sound", "Effects for sends, hits, captures and results", soundOn) {
                soundOn = it
                FeedbackSettings.sound = it
                FeedbackPrefs.save(context)
            }
            SwitchRow("Haptics", "Vibration feedback on your actions and attacks", hapticsOn) {
                hapticsOn = it
                FeedbackSettings.haptics = it
                FeedbackPrefs.save(context)
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mydara $version", color = IconFill, fontSize = 16.sp)
            }
        }
    }
}
