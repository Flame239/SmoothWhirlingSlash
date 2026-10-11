package org.poe2.overlay

import java.awt.Graphics2D

object OverlayPainter {
    fun paint(graphics: Graphics2D, display: OverlayState, width: Int, height: Int) {
        val settings = display.settings
        if (settings.overlayEnabled) {
            RangeOverlayPainter.paint(graphics, settings, width / 2.0 + settings.offsetX, height / 2.0 + settings.offsetY)
        }
        if (display.snappingEnabled) SnapIndicatorPainter.paint(graphics, width, height)
    }
}
