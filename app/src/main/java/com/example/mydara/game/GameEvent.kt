package com.example.mydara.game

/** One-shot notifications for the UI layer (particles, sounds later). Drain with GameModel.drainEvents(). */
sealed class GameEvent {
    /** A batch died on a link. */
    data class UnitDied(val x: Double, val y: Double, val fraction: Fraction) : GameEvent()

    /** A hostile batch hit a planet. */
    data class PlanetHit(val planet: Planet, val attacker: Fraction) : GameEvent()

    data class PlanetCaptured(val planet: Planet, val newOwner: Fraction) : GameEvent()

    /** A link changed between dotted/solid or changed solid color. */
    data class LinkRecolored(val link: Link) : GameEvent()
}
