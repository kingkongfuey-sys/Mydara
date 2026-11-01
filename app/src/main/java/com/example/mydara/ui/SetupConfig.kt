package com.example.mydara.ui

import com.example.mydara.game.Difficulty
import com.example.mydara.game.MapSize

/** Skirmish setup screen state (pure; the screen only edits this and calls [toGameConfig]). */
data class SetupConfig(
    val size: MapSize = MapSize.MEDIUM,
    val opponents: Int = 1,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val fogOfWar: Boolean = false
) {
    /** Every match gets a fresh random map (the seed is chosen when the match is created). */
    fun toGameConfig(): GameConfig = GameConfig(
        size = size,
        opponents = opponents.coerceIn(1, 3),
        difficulty = difficulty,
        fogOfWar = fogOfWar
    )
}
