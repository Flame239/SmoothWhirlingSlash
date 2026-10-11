package org.poe2.cursor

import org.poe2.screen.ScreenPoint

/** Reads physical keyboard/cursor state and moves the cursor in screen pixels. */
interface KeyboardAndCursor {
    fun isKeyDown(key: Int): Boolean
    fun readCursorPosition(): ScreenPoint
    fun moveCursorTo(point: ScreenPoint)
}
