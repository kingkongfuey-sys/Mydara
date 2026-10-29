package com.example.mydara.game

/** A planet. Coordinates are in map units where map width = 1.0. */
class Planet(
    val id: Int,
    val x: Double,
    val y: Double,
    var fraction: Fraction = Fraction.NEUTRAL,
    var type: PlanetType = PlanetType.NORMAL,
    val radius: Double = RADIUS
) {
    /** Garrison. Fractional part accumulates growth; whole part is what can be sent. */
    var units: Double = START_UNITS.toDouble()

    /** Current defense (float; visible shield segments = ceil). */
    var defense: Double = type.maxDefense.toDouble()

    val links: MutableList<Link> = ArrayList()

    /** Linked planet this planet re-sends its full garrison to, or null. */
    var autoTarget: Planet? = null
    var autoAccumulator: Double = 0.0

    /** 1.0 right after a send/friendly arrival/capture, decays to 0 over 0.5s of game time. */
    var pop: Double = 0.0

    val wholeUnits: Int get() = units.toInt()
    val wholeDefense: Int get() = defense.toInt()

    /** Garrison + defense (whole parts), used for attack decisions. */
    val fullHp: Int get() = wholeUnits + wholeDefense

    val popScale: Double get() = 1.0 + 0.2 * pop

    fun linkTo(other: Planet): Link? = links.firstOrNull { it.other(this) === other }

    fun isLinkedTo(other: Planet): Boolean = linkTo(other) != null

    companion object {
        const val RADIUS = 0.05
        const val START_UNITS = 3
        const val MAX_UNITS = 99_999.0
        const val UPGRADE_COST = 10
    }
}
