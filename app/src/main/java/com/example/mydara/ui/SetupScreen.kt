package com.example.mydara.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mydara.game.Difficulty
import com.example.mydara.game.Fraction
import com.example.mydara.game.MapSize

private fun pretty(e: Enum<*>): String = e.name.lowercase().replaceFirstChar { it.uppercase() }

private fun blurb(d: Difficulty): String = when (d) {
    Difficulty.EASY -> "Attacks weak neighbors. Never upgrades planets."
    Difficulty.NORMAL -> "Also reinforces its front line and builds economic planets."
    Difficulty.HARD -> "Also fortifies threatened planets and coordinates attacks."
}

private fun difficultyTag(d: Difficulty): String = when (d) {
    Difficulty.EASY -> "attacks"
    Difficulty.NORMAL -> "+ economy"
    Difficulty.HARD -> "+ tactics"
}

/** Skirmish setup: map size, opponents, difficulty, fog of war, seed. No map preview. */
@Composable
fun SetupScreen(
    setup: SetupConfig,
    onChange: (SetupConfig) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedGround()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ScreenHeader("Skirmish", onBack)

            Section("Map size") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (size in MapSize.values()) {
                        val sel = setup.size == size
                        OptionChip(sel, Modifier.weight(1f), { onChange(setup.copy(size = size)) }) {
                            ChipTitle(pretty(size), sel)
                            ChipSub("${size.planetCount} planets", sel)
                        }
                    }
                }
            }

            Section("Opponents") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 1..3) {
                        val sel = setup.opponents == n
                        OptionChip(sel, Modifier.weight(1f), { onChange(setup.copy(opponents = n)) }) {
                            ChipTitle(n.toString(), sel)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (f in Fraction.AI_ORDER.take(n)) {
                                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(f.argb)))
                                }
                            }
                        }
                    }
                }
            }

            Section("Difficulty") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (d in Difficulty.values()) {
                            val sel = setup.difficulty == d
                            OptionChip(sel, Modifier.weight(1f), { onChange(setup.copy(difficulty = d)) }) {
                                ChipTitle(pretty(d), sel)
                                ChipSub(difficultyTag(d), sel)
                            }
                        }
                    }
                    Text(blurb(setup.difficulty), color = IconFill.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            SwitchRow(
                title = "Fog of war",
                sub = "Planets more than one link from yours are dimmed and hidden",
                checked = setup.fogOfWar,
                onChecked = { onChange(setup.copy(fogOfWar = it)) }
            )

            Section("Seed") {
                OutlinedTextField(
                    value = setup.seedText,
                    onValueChange = { onChange(setup.copy(seedText = SetupConfig.sanitizeSeed(it))) },
                    label = { Text("Empty = random") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Start", fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
