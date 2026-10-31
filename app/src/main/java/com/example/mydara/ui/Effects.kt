package com.example.mydara.ui

import com.example.mydara.game.Link
import com.example.mydara.game.MapSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle {
    var x = 0.0
    var y = 0.0
    var vx = 0.0
    var vy = 0.0
    var radius = 0.0
    var startRadius = 1.0
    var decay = 0.0
    var friction = 0.0
    var color = 0L

    /** Short light dash (link recolor) instead of a round dot. */
    var spark = false
    var angle = 0.0

    val alpha: Double get() = (radius / startRadius).coerceIn(0.0, 1.0)
}

/** Fraction-colored dot bursts (max 1000). Purely visual; uses its own RNG. */
class ParticleSystem(seed: Long, private val max: Int = MAX_PARTICLES) {
    private val rng = Random(seed)
    private val live = ArrayList<Particle>()
    val particles: List<Particle> get() = live

    /** [scale] multiplies speed and size (planet hits/captures use 2). */
    fun burst(x: Double, y: Double, color: Long, count: Int = 80, scale: Double = 1.0) {
        repeat(count) {
            if (live.size >= max) return
            val ang = rng.nextDouble() * 2 * PI
            val speed = SPEED * (0.4 + rng.nextDouble() * 1.2) * scale
            spawn(x, y, cos(ang) * speed, sin(ang) * speed, color, scale, false)
        }
    }

    /** Dashes scattered along a whole link line (link color changed). */
    fun alongLink(link: Link, color: Long, count: Int = 25) {
        repeat(count) {
            if (live.size >= max) return
            val t = rng.nextDouble()
            val ang = rng.nextDouble() * 2 * PI
            val speed = SPEED * 0.3 * rng.nextDouble()
            spawn(
                link.a.x + (link.b.x - link.a.x) * t,
                link.a.y + (link.b.y - link.a.y) * t,
                cos(ang) * speed, sin(ang) * speed, color, 1.0, true
            )
        }
    }

    private fun spawn(x: Double, y: Double, vx: Double, vy: Double, color: Long, scale: Double, spark: Boolean) {
        val p = Particle()
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.startRadius = (0.05 + 0.10 * rng.nextDouble()) * 0.045 * scale
        p.radius = p.startRadius
        p.decay = p.startRadius / (30 + rng.nextInt(21)) // 30..50 frames of life
        p.friction = 0.26 + (rng.nextDouble() - 0.5) * 0.2
        p.color = color
        p.spark = spark
        p.angle = rng.nextDouble() * PI
        live += p
    }

    /** One 60 fps frame. */
    fun step() {
        var i = 0
        while (i < live.size) {
            val p = live[i]
            p.x += p.vx
            p.y += p.vy
            val keep = 1.0 - p.friction * FRICTION_SCALE
            p.vx *= keep
            p.vy *= keep
            p.radius -= p.decay
            if (p.radius <= 0.0) {
                live[i] = live[live.size - 1]
                live.removeAt(live.size - 1)
            } else {
                i++
            }
        }
    }

    companion object {
        const val MAX_PARTICLES = 1000
        const val SPEED = 0.25 * 0.035
        private const val FRICTION_SCALE = 0.2
    }
}

class Star(val x: Double, val y: Double, val life: Int) {
    var age = 0

    private val t: Double get() = age.toDouble() / life

    /** Scales in over the first 20% of life. */
    val scale: Double get() = if (t < 0.2) t / 0.2 else 1.0

    /** Brief twinkle in the middle, fade-out over the last 30%. */
    val alpha: Double
        get() {
            val twinkle = if (t in 0.45..0.55) 0.55 + 0.45 * sin((t - 0.45) * 10 * PI) else 1.0
            val fade = if (t > 0.7) (1.0 - t) / 0.3 else 1.0
            return (twinkle * fade).coerceIn(0.0, 1.0)
        }
}

/** Decorative stars at random map positions (StarsManager). Spawn rate per size, checked every 6 frames. */
class StarField(seed: Long, private val width: Double, private val height: Double, size: MapSize) {
    private val rng = Random(seed)
    private val live = ArrayList<Star>()
    private val rate = when (size) {
        MapSize.SMALL -> 4
        MapSize.MEDIUM -> 16
        MapSize.LARGE -> 36
        MapSize.HUGE -> 100
    }
    val stars: List<Star> get() = live

    fun step(frame: Long) {
        var i = 0
        while (i < live.size) {
            val s = live[i]
            s.age++
            if (s.age >= s.life) {
                live[i] = live[live.size - 1]
                live.removeAt(live.size - 1)
            } else {
                i++
            }
        }
        if (frame % 6L == 0L) {
            // Interpretation of "N stars per check": N candidates, each spawning with 10% chance.
            repeat(rate) {
                if (live.size < MAX_STARS && rng.nextDouble() < SPAWN_CHANCE) {
                    live += Star(rng.nextDouble() * width, rng.nextDouble() * height, 120 + rng.nextInt(120))
                }
            }
        }
    }

    companion object {
        const val MAX_STARS = 600
        const val SPAWN_CHANCE = 0.1
    }
}
