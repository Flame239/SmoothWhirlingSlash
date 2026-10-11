package org.poe2.win32

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.POINT
import org.poe2.cursor.KeyboardAndCursor
import org.poe2.screen.ScreenPoint

class NativeKeyboardAndCursor : KeyboardAndCursor {
    override fun isKeyDown(key: Int) = ExtendedUser32.INSTANCE.GetAsyncKeyState(key).toInt() and ASYNC_KEY_DOWN_MASK != 0

    override fun readCursorPosition(): ScreenPoint {
        val point = POINT()
        check(User32.INSTANCE.GetCursorPos(point)) { "GetCursorPos failed (${Native.getLastError()})." }
        return ScreenPoint(point.x, point.y)
    }

    override fun moveCursorTo(point: ScreenPoint) {
        check(ExtendedUser32.INSTANCE.SetCursorPos(point.x, point.y)) { "SetCursorPos failed (${Native.getLastError()})." }
    }
}
