package org.poe2.overlay

import org.poe2.config.AppSettings
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Ellipse2D

object RangeOverlayPainter {
    private val RANGE_FILL_COLOR = Color(90, 200, 120, 51)
    private val RANGE_OUTLINE_COLOR = Color(90, 200, 120, 100)
    private val TARGET_COLOR = Color(170, 210, 180, 70)
    private val RANGE_OUTLINE_STROKE = BasicStroke(1f)
    private val TARGET_STROKE = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
        10f, floatArrayOf(8f, 6f), 0f)

    fun paint(graphics: Graphics2D, settings: AppSettings, centerX: Double, centerY: Double) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            fun ellipse(rx: Double, ry: Double) = Ellipse2D.Double(centerX - rx, centerY - ry, rx * 2, ry * 2)
            val inner = ellipse(settings.innerX, settings.innerY)
            val outer = ellipse(settings.outerX, settings.outerY)
            g.color = RANGE_FILL_COLOR
            g.fill(Area(outer).apply { subtract(Area(inner)) })
            g.stroke = RANGE_OUTLINE_STROKE
            g.color = RANGE_OUTLINE_COLOR
            g.draw(inner)
            g.draw(outer)
            if (settings.dashedTarget) {
                g.stroke = TARGET_STROKE
                g.color = TARGET_COLOR
                g.draw(ellipse((settings.innerX + settings.outerX) / 2, (settings.innerY + settings.outerY) / 2))
            }
        } finally {
            g.dispose()
        }
    }
}
