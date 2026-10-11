package org.poe2

import org.poe2.geometry.RangeCalibration
import org.poe2.screen.ScreenBounds
import org.poe2.geometry.EllipticalRange
import org.poe2.screen.ScreenPoint
import kotlin.math.*
import kotlin.test.*

class EllipticalRangeTest {
    private val range = EllipticalRange(RangeCalibration(innerX = 100.0, innerY = 50.0,
        outerX = 250.0, outerY = 125.0, offsetY = 0.0), ScreenBounds(-1920, 0, 1920, 1080))

    @Test fun `positions already in the range are untouched`() {
        assertNull(range.findSnapPoint(ScreenPoint(-810, 540)))
        assertNull(range.findSnapPoint(ScreenPoint(-860, 540))) // Inner boundary.
        assertNull(range.findSnapPoint(ScreenPoint(-710, 540))) // Outer boundary.
    }

    @Test fun `out of range positions snap radially into an asymmetric range`() {
        for (point in listOf(ScreenPoint(-940, 550), ScreenPoint(-400, 820))) {
            val snapped = assertNotNull(range.findSnapPoint(point))
            assertTrue(range.contains(snapped))
            val originalX = point.x - range.centerX
            val originalY = point.y - range.centerY
            val snappedX = snapped.x - range.centerX
            val snappedY = snapped.y - range.centerY
            assertTrue(originalX * snappedX + originalY * snappedY > 0)
            assertTrue(abs(originalX * snappedY - originalY * snappedX) <= abs(originalX) + abs(originalY))
        }
    }

    @Test fun `snapping uses the midpoint along horizontal vertical and diagonal rays`() {
        val settings = RangeCalibration(innerX = 170.0, innerY = 115.0, outerX = 240.0, outerY = 180.0,
            offsetX = 12.0, offsetY = -35.0)
        val geometry = EllipticalRange(settings, ScreenBounds(0, 0, 1920, 1080))
        val cx = geometry.centerX
        val cy = geometry.centerY
        for ((dx, dy) in listOf(1 to 0, 0 to 1, 3 to 4, -3 to -4)) {
            val length = hypot(dx.toDouble(), dy.toDouble())
            val ux = dx / length
            val uy = dy / length
            fun radius(rx: Double, ry: Double) = 1 / sqrt((ux / rx).pow(2) + (uy / ry).pow(2))
            val midpoint = (radius(settings.innerX, settings.innerY) + radius(settings.outerX, settings.outerY)) / 2
            val expected = ScreenPoint((cx + ux * midpoint).roundToInt(), (cy + uy * midpoint).roundToInt())
            for (distance in listOf(10.0, 500.0)) {
                val cursor = ScreenPoint((cx + ux * distance).roundToInt(), (cy + uy * distance).roundToInt())
                assertEquals(expected, geometry.findSnapPoint(cursor))
                assertTrue(geometry.contains(expected))
            }
        }
    }

    @Test fun `center and destinations outside client bounds have no snap`() {
        val geometry = EllipticalRange(RangeCalibration(), ScreenBounds(0, 0, 1920, 1080))
        assertNull(geometry.findSnapPoint(ScreenPoint(geometry.centerX.roundToInt(), geometry.centerY.roundToInt())))
        val outside = EllipticalRange(RangeCalibration(offsetX = 2000.0), ScreenBounds(0, 0, 1920, 1080))
        assertNull(outside.findSnapPoint(ScreenPoint(1919, 540)))
        val unrepresentable = EllipticalRange(RangeCalibration(offsetX = 1e100), ScreenBounds(0, 0, 1920, 1080))
        assertNull(unrepresentable.findSnapPoint(ScreenPoint(100, 100)))
    }

    @Test fun `rounding that leaves a thin range is rejected`() {
        val geometry = EllipticalRange(RangeCalibration(innerX = 10.4, innerY = 10.4, outerX = 10.6, outerY = 10.6, offsetY = 0.0),
            ScreenBounds(0, 0, 100, 100))
        assertNull(geometry.findSnapPoint(ScreenPoint(99, 50)))
    }
}
