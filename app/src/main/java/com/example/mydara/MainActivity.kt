package com.example.mydara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.mydara.ui.GameConfig
import com.example.mydara.ui.GameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MydaraTheme {
                // Temporary until the menu screen (Stage 4): "Return to menu" starts a fresh random match.
                var round by remember { mutableIntStateOf(0) }
                key(round) {
                    GameScreen(config = GameConfig(), onExit = { round++ })
                }
            }
        }
    }
}
