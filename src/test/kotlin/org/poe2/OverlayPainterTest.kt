package org.poe2

import org.poe2.config.AppSettings
import org.poe2.overlay.OverlayState
import org.poe2.overlay.OverlayPainter
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class OverlayPainterTest {
    private fun render(display: OverlayState, width: Int = 1920, height: Int = 1080): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try { OverlayPainter.paint(g, display, width, height) } finally { g.dispose() }
        return image
    }

    @Test fun `window visibility requires foreground and either ovals or enabled snapping`() {
        for (ovals in listOf(false, true)) for (snapping in listOf(false, true)) {
            val display = OverlayState(AppSettings(overlayEnabled = ovals), snapping)
            assertFalse(display.shouldShow(false))
            assertEquals(ovals || snapping, display.shouldShow(true))
        }
    }

    @Test fun `hidden ovals still permit the indicator and disabling snapping clears it`() {
        for (ovals in listOf(false, true)) for (snapping in listOf(false, true)) {
            val image = render(OverlayState(AppSettings(innerX = 100.0, innerY = 50.0,
                outerX = 250.0, outerY = 125.0, offsetY = 0.0, overlayEnabled = ovals), snapping))
            assertEquals(ovals, image.getRGB(1135, 540) ushr 24 > 0, "Ring visibility")
            assertEquals(snapping, image.getRGB(1532, 1040) ushr 24 > 0, "Indicator visibility")
            assertEquals(0, image.getRGB(960, 540) ushr 24, "Calibrated center remains clear")
        }
    }

    @Test fun `skill accent leaves artwork and key label transparent with a localized soft glow`() {
        val image = render(OverlayState(AppSettings(overlayEnabled = false), true))
        for (x in 1537..1579) for (y in 1019..1061) {
            assertEquals(0, image.getRGB(x, y) ushr 24, "Skill interior at $x, $y")
        }
        assertTrue(image.getRGB(1532, 1040) ushr 24 > image.getRGB(1529, 1040) ushr 24)
        assertTrue(image.getRGB(1529, 1040) ushr 24 > 0, "Glow outside outline")
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (x !in 1528..1588 || y !in 1010..1070) assertEquals(0, image.getRGB(x, y) ushr 24)
        }
        // Optional local gameplay reference is a build artifact, never a test dependency.
        val reference = Path.of("build", "hud-reference.jpg")
        if (Files.exists(reference)) {
            val preview = ImageIO.read(reference.toFile())
            val g = preview.createGraphics()
            try {
                OverlayPainter.paint(g, OverlayState(AppSettings(overlayEnabled = false), true), preview.width, preview.height)
            } finally { g.dispose() }
            val directory = Path.of("build", "ui-preview")
            Files.createDirectories(directory)
            ImageIO.write(preview, "png", directory.resolve("snapping-hud.png").toFile())
            val scale = preview.height / 1080.0
            ImageIO.write(preview.getSubimage(preview.width - (600 * scale).toInt(),
                preview.height - (180 * scale).toInt(), (600 * scale).toInt(), (180 * scale).toInt()),
                "png", directory.resolve("snapping-hud-detail.png").toFile())
        }
    }

    @Test fun `slot frame scales with height and remains anchored to the bottom right`() {
        for ((width, height, left, top, size) in listOf(
            listOf(1920, 1080, 1532, 1014, 52),
            listOf(3840, 2160, 3064, 2028, 104),
            listOf(2560, 1080, 2172, 1014, 52),
        )) {
            val image = render(OverlayState(AppSettings(overlayEnabled = false), true), width, height)
            assertTrue(image.getRGB(left, top + size / 2) ushr 24 > 0)
            assertTrue(image.getRGB(left + size, top + size / 2) ushr 24 > 0)
            assertTrue(image.getRGB(left + size / 2, top) ushr 24 > 0)
            assertTrue(image.getRGB(left + size / 2, top + size) ushr 24 > 0)
            assertEquals(0, image.getRGB(left + size / 2, top + size / 2) ushr 24)
        }
    }
}
