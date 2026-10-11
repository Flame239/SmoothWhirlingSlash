package org.poe2

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import org.poe2.config.AppSettings
import org.poe2.overlay.OverlayWindow
import org.poe2.screen.ScreenBounds
import org.poe2.win32.GameWindow
import org.poe2.win32.WS_EX_NOACTIVATE
import org.poe2.win32.WS_EX_TOOLWINDOW
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlin.test.*

class OverlayWindowTest {
    @Test fun `overlay creates a hidden native window with click through styles and releases it on close`() {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val bounds = ScreenBounds(30, 40, 640, 480)
                val overlay = OverlayWindow(GameWindow(HWND(), bounds), AppSettings())
                val window = OverlayWindow::class.java.getDeclaredField("window")
                    .apply { isAccessible = true }.get(overlay) as JWindow
                try {
                    assertTrue(window.isDisplayable)
                    assertFalse(window.isVisible)
                    assertFalse(window.focusableWindowState)
                    assertFalse(window.isAutoRequestFocus)
                    assertEquals(java.awt.Rectangle(bounds.x, bounds.y, bounds.width, bounds.height), window.bounds)
                    val handle = HWND(Native.getComponentPointer(window))
                    val styles = User32.INSTANCE.GetWindowLong(handle, WinUser.GWL_EXSTYLE)
                    val expected = WinUser.WS_EX_LAYERED or WinUser.WS_EX_TRANSPARENT or WS_EX_NOACTIVATE or WS_EX_TOOLWINDOW
                    assertEquals(expected, styles and expected)
                    assertFalse(User32.INSTANCE.IsWindowVisible(handle))
                } finally { overlay.close() }
                assertFalse(window.isDisplayable)
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }
}
