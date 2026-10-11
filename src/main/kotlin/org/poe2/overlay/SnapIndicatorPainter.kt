package org.poe2.overlay

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

/** Standard PC HUD: middle slot of the lower skill row, anchored to the bottom-right. */
object SnapIndicatorPainter {
    private const val REFERENCE_CLIENT_HEIGHT = 1080.0
    private const val SLOT_CENTER_FROM_RIGHT = 362.0
    private const val SLOT_CENTER_FROM_BOTTOM = 40.0
    private const val SLOT_SIZE = 52.0
    private const val CORNER_LENGTH = 6.0
    private val OUTER_GLOW = Color(90, 200, 120, 20)
    private val INNER_GLOW = Color(90, 200, 120, 35)
    private val OUTLINE = Color(110, 210, 140, 175)
    private val CORNER_COLOR = Color(202, 174, 113, 210)

    fun paint(graphics: Graphics2D, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val scale = height / REFERENCE_CLIENT_HEIGHT
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.translate(width - SLOT_CENTER_FROM_RIGHT * scale, height - SLOT_CENTER_FROM_BOTTOM * scale)
            g.scale(scale, scale)
            val half = SLOT_SIZE / 2
            val frame = Rectangle2D.Double(-half, -half, SLOT_SIZE, SLOT_SIZE)
            // Strokes leave the skill artwork, cooldown, and key label unobscured.
            g.color = OUTER_GLOW; g.stroke = BasicStroke(7f); g.draw(frame)
            g.color = INNER_GLOW; g.stroke = BasicStroke(4f); g.draw(frame)
            g.color = OUTLINE; g.stroke = BasicStroke(1.5f); g.draw(frame)
            g.color = CORNER_COLOR
            g.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val corners = Path2D.Double()
            for (xSign in listOf(-1, 1)) for (ySign in listOf(-1, 1)) {
                val x = xSign * half
                val y = ySign * half
                corners.moveTo(x - xSign * CORNER_LENGTH, y)
                corners.lineTo(x, y)
                corners.lineTo(x, y - ySign * CORNER_LENGTH)
            }
            g.draw(corners)
        } finally {
            g.dispose()
        }
    }
}
