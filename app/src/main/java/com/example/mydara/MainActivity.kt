package com.example.mydara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.mydara.ui.GameConfig
import com.example.mydara.ui.GameScreen
import com.example.mydara.ui.MenuScreen
import com.example.mydara.ui.SettingsScreen
import com.example.mydara.ui.SetupConfig
import com.example.mydara.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MydaraTheme {
                MydaraApp()
            }
        }
    }
}

private sealed interface Screen {
    data object Menu : Screen
    data object Setup : Screen
    data object Settings : Screen
    data class Game(val config: GameConfig, val round: Int) : Screen
}

/** Menu -> Setup -> Game. System back goes one step up (in a game it opens the pause menu); no quit button. */
@Composable
private fun MydaraApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    var setup by remember { mutableStateOf(SetupConfig()) }
    var rounds by remember { mutableIntStateOf(0) }

    when (val s = screen) {
        Screen.Menu -> MenuScreen(
            onPlay = { screen = Screen.Setup },
            onSettings = { screen = Screen.Settings }
        )
        Screen.Setup -> {
            BackHandler { screen = Screen.Menu }
            SetupScreen(
                setup = setup,
                onChange = { setup = it },
                onStart = {
                    rounds++
                    screen = Screen.Game(setup.toGameConfig(), rounds)
                },
                onBack = { screen = Screen.Menu }
            )
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.Menu }
            SettingsScreen(onBack = { screen = Screen.Menu })
        }
        is Screen.Game -> {
            // The in-game menu handles back itself (it opens / closes the pause menu).
            key(s.round) {
                GameScreen(
                    config = s.config,
                    onBackToSetup = { screen = Screen.Setup },
                    onExitToMenu = { screen = Screen.Menu },
                    // Same settings, same map (same seed)
                    onRestart = { seed ->
                        rounds++
                        screen = Screen.Game(s.config.copy(seed = seed), rounds)
                    },
                    // Same settings, new random map
                    onRegenerate = {
                        rounds++
                        screen = Screen.Game(s.config.copy(seed = null), rounds)
                    }
                )
            }
        }
    }
}
