package org.poe2

import org.poe2.config.AppSettings
import org.poe2.overlay.RangeOverlayPainter
import java.awt.image.BufferedImage
import kotlin.test.*

class RangeOverlayPainterTest {
    @Test fun `overlay fills only the range at twenty percent opacity`() {
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            RangeOverlayPainter.paint(g, AppSettings(innerX = 50.0, innerY = 50.0,
                outerX = 100.0, outerY = 100.0, dashedTarget = false), 200.0, 200.0)
        } finally { g.dispose() }
        assertEquals(0, image.getRGB(200, 200) ushr 24)
        assertEquals(0, image.getRGB(350, 200) ushr 24)
        assertEquals(51, image.getRGB(275, 200) ushr 24)
        val fill = java.awt.Color(image.getRGB(275, 200), true)
        // ARGB images round colors through premultiplication during painting.
        assertTrue(fill.red in 88..92); assertTrue(fill.green in 198..202); assertTrue(fill.blue in 118..122)
        assertTrue(image.getRGB(250, 200) ushr 24 in 52..131)
        assertTrue(image.getRGB(300, 200) ushr 24 in 52..131)
        assertTrue((0 until image.width).all { x -> (0 until image.height).all { y -> image.getRGB(x, y) ushr 24 <= 131 } })
    }

    @Test fun `optional guide draws over the fill without changing transparency outside the range`() {
        fun render(dashed: Boolean): BufferedImage {
            val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                RangeOverlayPainter.paint(g, AppSettings(innerX = 50.0, innerY = 50.0,
                    outerX = 100.0, outerY = 100.0, dashedTarget = dashed), 200.0, 200.0)
            } finally { g.dispose() }
            return image
        }
        val plain = render(false)
        val guided = render(true)
        assertTrue((120..280).any { x -> (120..280).any { y ->
            plain.getRGB(x, y) ushr 24 == 51 && guided.getRGB(x, y) ushr 24 > 51
        } })
        assertEquals(0, guided.getRGB(200, 200) ushr 24)
        assertEquals(0, guided.getRGB(350, 200) ushr 24)
    }
}
