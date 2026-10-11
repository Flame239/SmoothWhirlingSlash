package org.poe2.geometry

import org.poe2.screen.ScreenBounds
import org.poe2.screen.ScreenPoint
import kotlin.math.*

class EllipticalRange(val calibration: RangeCalibration, val bounds: ScreenBounds) {
    val centerX = bounds.x + bounds.width / 2.0 + calibration.offsetX
    val centerY = bounds.y + bounds.height / 2.0 + calibration.offsetY

    fun contains(point: ScreenPoint): Boolean {
        val dx = point.x - centerX
        val dy = point.y - centerY
        return normalizedDistanceSquared(dx, dy, calibration.innerX, calibration.innerY) >= 1.0 &&
            normalizedDistanceSquared(dx, dy, calibration.outerX, calibration.outerY) <= 1.0
    }

    /** Returns null for a valid cursor or when no safe, representable destination exists. */
    fun findSnapPoint(point: ScreenPoint): ScreenPoint? {
        if (contains(point)) return null
        val dx = point.x - centerX
        val dy = point.y - centerY
        val distance = hypot(dx, dy)
        if (distance == 0.0 || !distance.isFinite()) return null
        val ux = dx / distance
        val uy = dy / distance
        val inner = rayRadius(ux, uy, calibration.innerX, calibration.innerY)
        val outer = rayRadius(ux, uy, calibration.outerX, calibration.outerY)
        val radius = (inner + outer) / 2.0
        val x = centerX + ux * radius
        val y = centerY + uy * radius
        if (!x.isFinite() || !y.isFinite() || x < Int.MIN_VALUE || x > Int.MAX_VALUE ||
            y < Int.MIN_VALUE || y > Int.MAX_VALUE) return null
        val candidate = ScreenPoint(x.roundToInt(), y.roundToInt())
        return candidate.takeIf { contains(it) && bounds.contains(it) }
    }

    private fun rayRadius(ux: Double, uy: Double, rx: Double, ry: Double) =
        1.0 / sqrt((ux / rx).pow(2) + (uy / ry).pow(2))

    private fun normalizedDistanceSquared(dx: Double, dy: Double, rx: Double, ry: Double) =
        (dx / rx).pow(2) + (dy / ry).pow(2)
}
