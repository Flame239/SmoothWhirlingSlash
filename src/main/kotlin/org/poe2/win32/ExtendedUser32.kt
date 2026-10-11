package org.poe2.win32

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** User32 functions absent from JNA's standard interface. */
interface ExtendedUser32 : StdCallLibrary {
    fun SetProcessDpiAwarenessContext(context: Pointer): Boolean
    fun GetThreadDpiAwarenessContext(): Pointer?
    fun GetAwarenessFromDpiAwarenessContext(context: Pointer?): Int
    fun GetAsyncKeyState(key: Int): Short
    fun IsIconic(window: HWND): Boolean
    fun ClientToScreen(window: HWND, point: POINT): Boolean
    fun SetCursorPos(x: Int, y: Int): Boolean

    companion object {
        val INSTANCE: ExtendedUser32 = Native.load("user32", ExtendedUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
