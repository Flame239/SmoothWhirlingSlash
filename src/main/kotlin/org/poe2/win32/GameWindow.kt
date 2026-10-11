package org.poe2.win32

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import org.poe2.screen.ScreenBounds

/** The selected native game window and the client bounds captured at startup. */
class GameWindow(private val handle: HWND, val bounds: ScreenBounds) {
    fun isFocused(): Boolean {
        val user = User32.INSTANCE
        return user.IsWindow(handle) && user.IsWindowVisible(handle) &&
            !ExtendedUser32.INSTANCE.IsIconic(handle) && sameWindow(user.GetForegroundWindow(), handle)
    }

    companion object {
        private const val WINDOW_TITLE_BUFFER_CHARS = 1024

        fun findByTitle(titleMatch: String): GameWindow? {
            val user = User32.INSTANCE
            val candidates = mutableListOf<HWND>()
            check(user.EnumWindows({ handle, _ ->
                if (user.IsWindowVisible(handle) && !ExtendedUser32.INSTANCE.IsIconic(handle)) {
                    val buffer = CharArray(WINDOW_TITLE_BUFFER_CHARS)
                    user.GetWindowText(handle, buffer, buffer.size)
                    if (Native.toString(buffer).contains(titleMatch, ignoreCase = true)) candidates += handle
                }
                true
            }, null)) { "EnumWindows failed (${Native.getLastError()})." }
            val foreground = user.GetForegroundWindow()
            val handle = candidates.firstOrNull { sameWindow(it, foreground) } ?: candidates.firstOrNull() ?: return null
            return GameWindow(handle, readClientBounds(handle))
        }

        private fun readClientBounds(handle: HWND): ScreenBounds {
            val client = RECT()
            val origin = POINT(0, 0)
            check(User32.INSTANCE.GetClientRect(handle, client) && ExtendedUser32.INSTANCE.ClientToScreen(handle, origin)) {
                "Could not read game client bounds (${Native.getLastError()})."
            }
            val width = client.right - client.left
            val height = client.bottom - client.top
            check(width > 0 && height > 0) { "Game client area is empty." }
            return ScreenBounds(origin.x, origin.y, width, height)
        }

        private fun sameWindow(first: HWND?, second: HWND?) = first != null && second != null &&
            Pointer.nativeValue(first.pointer) == Pointer.nativeValue(second.pointer)
    }
}
