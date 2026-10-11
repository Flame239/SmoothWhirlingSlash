package org.poe2.geometry

/** Ellipse radii and displacement from the game client's center, in physical pixels. */
data class RangeCalibration(
    val innerX: Double = 170.0,
    val innerY: Double = 115.0,
    val outerX: Double = 240.0,
    val outerY: Double = 180.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = -35.0,
)
