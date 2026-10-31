package com.example.mydara.ui

import com.example.mydara.game.Difficulty
import com.example.mydara.game.MapSize

/** Skirmish setup screen state (pure; the screen only edits this and calls [toGameConfig]). */
data class SetupConfig(
    val size: MapSize = MapSize.MEDIUM,
    val opponents: Int = 1,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val fogOfWar: Boolean = false,
    val seedText: String = ""
) {
    /** Empty or unparsable seed = random (null). */
    fun seed(): Long? = seedText.toLongOrNull()

    fun toGameConfig(): GameConfig = GameConfig(
        size = size,
        opponents = opponents.coerceIn(1, 3),
        difficulty = difficulty,
        fogOfWar = fogOfWar,
        seed = seed()
    )

    companion object {
        const val MAX_SEED_DIGITS = 9

        /** Keeps digits only and limits the length. */
        fun sanitizeSeed(raw: String): String = raw.filter { it.isDigit() }.take(MAX_SEED_DIGITS)
    }
}
