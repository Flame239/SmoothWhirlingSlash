package org.poe2.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object ApplicationIcon {
    private val original: BufferedImage by lazy {
        ImageIO.read(requireNotNull(javaClass.getResource("/icons/whirling-slash.png")))
    }

    fun image(size: Int): BufferedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { image ->
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(original, 0, 0, size, size, null)
        } finally { graphics.dispose() }
    }

    val windowImages by lazy { listOf(16, 32, 48, 64, 256).map(::image) }
}
