package com.example.mydara.game

/**
 * @param maxDefense number of defense points (= shield arc segments) a planet of this type spawns with.
 * @param growthMultiplier multiplier on garrison growth for owned planets.
 * @param defenseRegenMultiplier multiplier on defense regeneration (0 = never regenerates).
 */
enum class PlanetType(
    val maxDefense: Int,
    val growthMultiplier: Double,
    val defenseRegenMultiplier: Double
) {
    NORMAL(7, 1.0, 1.0),
    ECONOMIC(0, 2.0, 0.0),
    DEFENSIVE(14, 1.0, 3.0)
}
