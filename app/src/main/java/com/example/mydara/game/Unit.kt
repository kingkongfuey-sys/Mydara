package com.example.mydara.game

/**
 * One batched unit entity travelling along a link, carrying a numeric value.
 * (Named UnitBatch, not Unit, to avoid shadowing kotlin.Unit.)
 */
class UnitBatch(
    val id: Int,
    val fraction: Fraction,
    var value: Int,
    val from: Planet,
    val to: Planet,
    val link: Link
) {
    /** 0 = at [from], 1 = at [to]. */
    var progress: Double = 0.0
    var alive: Boolean = true

    val x: Double get() = from.x + (to.x - from.x) * progress
    val y: Double get() = from.y + (to.y - from.y) * progress
}
