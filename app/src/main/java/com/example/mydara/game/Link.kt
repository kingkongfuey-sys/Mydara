package com.example.mydara.game

import kotlin.math.hypot

/** Fixed connection between two planets, shared by all fractions. */
class Link(val id: Int, val a: Planet, val b: Planet) {
    val length: Double = hypot(a.x - b.x, a.y - b.y)

    /** Last solid-color state reported to the renderer/event stream (managed by GameModel). */
    internal var styleFraction: Fraction? = null

    fun other(p: Planet): Planet = if (p === a) b else a

    /** Solid (fraction-colored) when both ends belong to the same non-neutral fraction; otherwise dotted. */
    val isSolid: Boolean get() = a.fraction == b.fraction && !a.fraction.isNeutral

    val solidFraction: Fraction? get() = if (isSolid) a.fraction else null
}
