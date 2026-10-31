package com.example.mydara.game

/**
 * Owner of a planet / unit batch. Colors are ARGB longs so this package stays free of Android/Compose.
 * [argb] is the pastel planet fill; [particleArgb] is the more saturated splash color.
 */
enum class Fraction(val argb: Long, val particleArgb: Long, val label: String) {
    NEUTRAL(0xFF7F9BA8, 0xFFA9BCC7, "Neutral"),
    GREEN(0xFF7EE8A2, 0xFF2EE67A, "Green"),
    RED(0xFFFF8A7A, 0xFFFF5A4A, "Red"),
    YELLOW(0xFFF6D55C, 0xFFFFC933, "Yellow"),
    CYAN(0xFF5FD3F3, 0xFF25C8F5, "Cyan");

    val isNeutral: Boolean get() = this == NEUTRAL

    companion object {
        val PLAYER: Fraction = GREEN

        /** AI colors in assignment order: first opponent red, second yellow, third cyan. */
        val AI_ORDER: List<Fraction> = listOf(RED, YELLOW, CYAN)
    }
}
