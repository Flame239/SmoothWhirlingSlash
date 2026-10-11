package org.poe2.overlay

import org.poe2.config.AppSettings
import org.poe2.win32.GameWindow
import org.poe2.win32.ClickThroughOverlay
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JWindow

private val TRANSPARENT_BACKGROUND_COLOR = Color(0, 0, 0, 0)

class OverlayWindow(private val gameWindow: GameWindow, initial: AppSettings) : AutoCloseable {
    private var display = OverlayState(initial)
    private val canvas = object : JComponent() {
        override fun paintComponent(g: Graphics) {
            OverlayPainter.paint(g as Graphics2D, display, width, height)
        }
    }.apply { isOpaque = false }

    private val window = JWindow().apply {
        name = "Whirling Slash range"
        focusableWindowState = false
        isAutoRequestFocus = false
        background = TRANSPARENT_BACKGROUND_COLOR
        isAlwaysOnTop = true
        contentPane.add(canvas)
        // Create the native peer while hidden so click-through styles precede the first display.
        pack()
        val bounds = gameWindow.bounds
        setBounds(bounds.x, bounds.y, bounds.width, bounds.height)
    }
    private val clickThroughOverlay = try {
        ClickThroughOverlay(window, gameWindow.bounds)
    } catch (e: Exception) {
        window.dispose()
        throw e
    }

    fun updateDisplay(next: OverlayState) {
        if (display != next) {
            display = next
            canvas.repaint()
        }
        val visible = next.shouldShow(gameWindow.isFocused())
        if (window.isVisible != visible) {
            if (visible) clickThroughOverlay.alignWithGameClient()
            window.isVisible = visible
        }
    }

    override fun close() {
        window.dispose()
    }
}
