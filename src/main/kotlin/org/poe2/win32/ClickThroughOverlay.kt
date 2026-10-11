package org.poe2.win32

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import org.poe2.screen.ScreenBounds
import java.awt.Window

/** Applies native click-through and non-activating styles to an already packed Swing window. */
class ClickThroughOverlay(window: Window, private val bounds: ScreenBounds) {
    private val user = User32.INSTANCE
    private val handle = HWND(Native.getComponentPointer(window))

    init {
        val currentStyle = user.GetWindowLong(handle, WinUser.GWL_EXSTYLE)
        val overlayStyle = WinUser.WS_EX_LAYERED or WinUser.WS_EX_TRANSPARENT or WS_EX_NOACTIVATE or WS_EX_TOOLWINDOW
        Native.setLastError(0)
        val previousStyle = user.SetWindowLong(handle, WinUser.GWL_EXSTYLE, currentStyle or overlayStyle)
        check(previousStyle != 0 || Native.getLastError() == 0) { "Cannot configure overlay styles (${Native.getLastError()})." }
        alignWithGameClient()
    }

    fun alignWithGameClient() {
        check(user.SetWindowPos(handle, HWND_TOPMOST, bounds.x, bounds.y, bounds.width, bounds.height,
            WinUser.SWP_NOACTIVATE or WinUser.SWP_NOOWNERZORDER)) { "SetWindowPos failed (${Native.getLastError()})." }
    }
}
