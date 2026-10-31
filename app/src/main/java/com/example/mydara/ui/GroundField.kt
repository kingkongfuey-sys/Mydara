package com.example.mydara.ui

import kotlin.random.Random

/**
 * Animated menu background: large translucent rounded squares that drift slowly upward and respawn below
 * the bottom edge. Normalized coordinates: x in screen widths, y in screen heights, [GroundCell.size] in
 * screen widths. Pure logic; the Compose side only draws it.
 */
class GroundCell(
    var x: Double,
    var y: Double,
    var size: Double,
    var alpha: Float,
    var speed: Double,
    var shade: Int
)

class GroundField(seed: Long = 1L, count: Int = 14, private val shades: Int = 3) {
    private val rng = Random(seed)
    val cells: List<GroundCell> = List(count) { newCell(initial = true) }

    private fun newCell(initial: Boolean): GroundCell = GroundCell(
        x = -0.1 + rng.nextDouble() * 1.0,
        y = if (initial) -0.1 + rng.nextDouble() * 1.2 else RESPAWN_MIN_Y + rng.nextDouble() * 0.15,
        size = MIN_SIZE + rng.nextDouble() * (MAX_SIZE - MIN_SIZE),
        alpha = (0.08 + rng.nextDouble() * 0.18).toFloat(),
        speed = 0.012 + rng.nextDouble() * 0.02, // screen heights per second
        shade = rng.nextInt(shades)
    )

    fun step(dtSeconds: Double) {
        for (c in cells) {
            c.y -= c.speed * dtSeconds
            if (c.y < DESPAWN_Y) {
                val n = newCell(initial = false)
                c.x = n.x; c.y = n.y; c.size = n.size; c.alpha = n.alpha; c.speed = n.speed; c.shade = n.shade
            }
        }
    }

    companion object {
        const val MIN_SIZE = 0.18
        const val MAX_SIZE = 0.45
        const val DESPAWN_Y = -0.35
        const val RESPAWN_MIN_Y = 1.05
    }
}
