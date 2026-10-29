package com.example.mydara.game

/** Owner of a planet / unit batch. Colors are ARGB longs so this package stays free of Android/Compose. */
enum class Fraction(val argb: Long, val label: String) {
    NEUTRAL(0xFFAAAAAA, "Neutral"),
    GREEN(0xFF2ECC40, "Green"),
    RED(0xFFFF4136, "Red"),
    YELLOW(0xFFFFDC00, "Yellow"),
    CYAN(0xFF39CCCC, "Cyan");

    val isNeutral: Boolean get() = this == NEUTRAL

    companion object {
        val PLAYER: Fraction = GREEN

        /** AI colors in assignment order: first opponent red, second yellow, third cyan. */
        val AI_ORDER: List<Fraction> = listOf(RED, YELLOW, CYAN)
    }
}
