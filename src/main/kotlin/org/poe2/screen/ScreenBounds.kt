package org.poe2.screen

/** A rectangle in physical screen coordinates; right and bottom edges are exclusive. */
data class ScreenBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(point: ScreenPoint) = point.x >= x && point.y >= y &&
        point.x.toLong() < x.toLong() + width && point.y.toLong() < y.toLong() + height
}
