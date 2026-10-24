package com.lopleec.slopeweather.ui

import com.lopleec.slopeweather.data.WeatherKind
import kotlin.math.sin

/** A card's current viewport geometry. Reused while scrolling; the rim is sampled only on resize. */
internal class RainSurface(var profile: FloatArray) {
    var left = 0f
    var top = 0f
    var width = 0f
    var height = 0f
    var visible = false
    var contactVisible = true

    fun rimY(x: Float): Float {
        if (width <= 0f || x < left || x > left + width) return Float.NaN
        val sample = ((x - left) / width * profile.lastIndex).coerceIn(0f, profile.lastIndex.toFloat())
        val a = sample.toInt()
        val b = (a + 1).coerceAtMost(profile.lastIndex)
        return top + profile[a] + (profile[b] - profile[a]) * (sample - a)
    }

    fun covers(x: Float, y: Float): Boolean = y >= top && y < top + height && y >= rimY(x)
}

internal class RainDrop {
    var x = 0f
    var y = 0f
    var speed = 0f
    var drift = 0f
    var radius = 0f
    var phase = 0f
    var ice = false
}

internal class SurfaceImpact {
    var active = false
    var born = 0f
    var fraction = 0f
    var ice = false
}

internal class RainChannel {
    var surface: RainSurface? = null
    var lastContact = Float.NEGATIVE_INFINITY
    var present = false
    val impacts = Array(3) { SurfaceImpact() }
}

/** Fixed pools: every rain ray stops at its first card. Sheltered cards never emit water. */
internal class RainField(private val kind: WeatherKind, private val strength: Float) {
    val drops = Array(MAX_DROPS) { RainDrop() }
    val channels = Array(MAX_SURFACES) { RainChannel() }
    var dropCount = 0
        private set
    private var randomState = 4727
    private var width = 0f
    private var height = 0f
    private var density = 1f
    private var lastTime = Float.NaN
    private var geometryVersion = Long.MIN_VALUE
    private var surfaces: List<RainSurface> = emptyList()
    private val snow = kind == WeatherKind.SNOW || kind == WeatherKind.SLEET ||
        kind == WeatherKind.THUNDER_SNOW || kind == WeatherKind.THUNDER_SLEET
    private val baseDropCount = when (kind) {
        WeatherKind.DRIZZLE -> (8+strength*12).toInt()
        WeatherKind.SNOW, WeatherKind.THUNDER_SNOW -> 20
        else -> (12 + strength * 30).toInt()
    }

    // No Random, collections, sequences or particle objects are created in the frame loop.
    private fun random(): Float {
        var x = randomState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        randomState = x
        return (x ushr 8) / 16777216f
    }

    private fun resetDrop(drop: RainDrop, initial: Boolean) {
        drop.x = random() * width
        drop.y = if (initial) random() * height else -random() * height * .15f
        drop.ice = kind.frozen && (!kind.wet || random() < .5f)
        val flake = drop.ice && snow
        drop.speed = (if (flake) 22 + random() * 28 else 190 + strength * 300 + random() * 140) * density
        drop.drift = (if (flake) 8f else -19f) * density
        drop.radius = (if (flake) 1.2f + random() * 1.1f else .7f + random() * .6f) * density
        drop.phase = random() * 6.283185f
    }

    fun setViewport(width: Float, height: Float, density: Float) {
        if (this.width == width && this.height == height && this.density == density) return
        this.width = width
        this.height = height
        this.density = density
        for (drop in drops) resetDrop(drop, true)
        lastTime = Float.NaN
        geometryVersion = Long.MIN_VALUE
    }

    fun setSurfaces(surfaces: List<RainSurface>, version: Long, time: Float) {
        if (geometryVersion == version) return
        geometryVersion = version
        this.surfaces = surfaces
        for (channel in channels) channel.present = false
        // Retain channels and their local contact positions when a card moves or changes order.
        for (i in surfaces.indices) {
            val surface = surfaces[i]
            for (channel in channels) if (surface.contactVisible && channel.surface === surface) { channel.present = true; break }
        }
        for (channel in channels) if (!channel.present) {
            channel.surface = null
            for (impact in channel.impacts) impact.active = false
        }
        for (i in surfaces.indices) {
            val surface = surfaces[i]
            if (!surface.visible || !surface.contactVisible) continue
            var existing = false
            for (channel in channels) if (channel.surface === surface) { existing = true; break }
            if (existing) continue
            var free: RainChannel? = null
            for (channel in channels) if (channel.surface == null) { free = channel; break }
            if (free == null) break
            free.surface = surface
            free.present = true
            free.lastContact = Float.NEGATIVE_INFINITY
        }
        // A newly exposed/covering card changes the rain shadow immediately, not one frame later.
        for (channel in channels) {
            val surface = channel.surface ?: continue
            for (impact in channel.impacts) if (impact.active &&
                firstSurfaceAt(surface.left + surface.width * impact.fraction) !== surface) impact.active = false
        }
    }

    internal fun firstSurfaceAt(x: Float): RainSurface? {
        var closest: RainSurface? = null
        var firstY = Float.POSITIVE_INFINITY
        for (i in surfaces.indices) {
            val surface = surfaces[i]
            if (!surface.visible) continue
            val y = surface.rimY(x)
            if (y < firstY) { firstY = y; closest = surface }
        }
        return closest
    }

    private fun contact(surface: RainSurface, x: Float, time: Float, ice: Boolean) {
        if (!surface.contactVisible) return
        for (channel in channels) if (channel.surface === surface) {
            val interval = if (kind == WeatherKind.DRIZZLE) .3f else .18f
            if (time - channel.lastContact < interval) return
            channel.lastContact = time
            var slot = channel.impacts[0]
            for (candidate in channel.impacts) {
                if (!candidate.active) { slot = candidate; break }
                if (candidate.born < slot.born) slot = candidate
            }
            slot.active = true
            slot.born = time
            slot.fraction = ((x - surface.left) / surface.width).coerceIn(0f, 1f)
            slot.ice = ice
            return
        }
    }

    fun tick(time: Float, reduced: Boolean = false) {
        val elapsed = if (lastTime.isNaN()) 0f else (time - lastTime).coerceIn(0f, .08f)
        lastTime = time
        dropCount = if (reduced) (baseDropCount * .6f).toInt() else baseDropCount
        for (channel in channels) for (impact in channel.impacts) {
            if (impact.active && time - impact.born > IMPACT_LIFETIME) impact.active = false
        }
        for (i in 0 until dropCount) {
            val drop = drops[i]
            val before = drop.y
            drop.y += drop.speed * elapsed
            drop.x += (drop.drift + if (drop.ice) sin(time * 1.4f + drop.phase) * 9 * density else 0f) * elapsed
            val roof = firstSurfaceAt(drop.x)
            val roofY = if (roof == null) Float.POSITIVE_INFINITY else roof.rimY(drop.x)
            if (drop.y >= roofY) {
                // Drops already below a roof (initialization/scrolling) are discarded without a splash.
                if (before < roofY && roof != null) contact(roof, drop.x, time, drop.ice)
                resetDrop(drop, false)
            } else if (drop.y > height + 20 * density || drop.x < -20 * density || drop.x > width + 20 * density) {
                resetDrop(drop, false)
            }
        }
    }

    fun backgroundVisible(x: Float, y: Float): Boolean {
        val roof = firstSurfaceAt(x) ?: return true
        return y < roof.rimY(x)
    }

    companion object {
        const val MAX_DROPS = 48
        const val MAX_SURFACES = 12
        const val IMPACT_LIFETIME = .55f
    }
}

/** Animation quality follows interaction and slow frames; it never changes layout or input handling. */
internal class WeatherFramePacer {
    private var previousVsync = 0L
    private var previousRain = 0L
    private var previousCloud = 0L
    private var slowFrames = 0
    private var reducedUntil = 0L
    var reduced = false
        private set

    fun onVsync(now: Long, scrolling: Boolean, powerSave: Boolean): Int {
        if (previousVsync != 0L) {
            if (now - previousVsync > 50_000_000L) slowFrames++ else slowFrames = (slowFrames - 1).coerceAtLeast(0)
            if (slowFrames >= 3) { reducedUntil = now + 3_000_000_000L; slowFrames = 0 }
        }
        previousVsync = now
        reduced = scrolling || powerSave || now < reducedUntil
        // Reduce particle work under load instead of dropping movement to a visible
        // 15 fps staircase. Normal and gesture animation track a 60 Hz cadence.
        val rainInterval = if (powerSave) 33_333_333L else 16_666_667L
        var flags = 0
        if (now - previousRain + 1_000_000L >= rainInterval) { previousRain = now; flags = flags or RAIN }
        // Cached cloud layers keep drifting during gestures; translation needs no texture redraw.
        val cloudInterval=if(reduced) 66_666_667L else 33_333_333L
        if (now - previousCloud + 1_000_000L >= cloudInterval) {
            previousCloud = now
            flags = flags or CLOUD
        }
        return flags
    }

    companion object { const val RAIN = 1; const val CLOUD = 2 }
}
