package org.poe2

import org.poe2.geometry.RangeCalibration
import org.poe2.cursor.CursorSnapper
import org.poe2.screen.ScreenBounds
import org.poe2.geometry.EllipticalRange
import org.poe2.screen.ScreenPoint
import org.poe2.cursor.KeyboardAndCursor
import kotlin.test.*

class CursorSnapperTest {
    private class FakeKeyboardAndCursor : KeyboardAndCursor {
        var down = false
        var position = ScreenPoint(970, 540)
        var foreground = true
        var moves = 0

        override fun isKeyDown(key: Int) = down
        override fun readCursorPosition() = position
        override fun moveCursorTo(point: ScreenPoint) { position = point; moves++ }
    }

    @Test fun `holding repeats at 100 ms without moving a valid cursor`() {
        val input = FakeKeyboardAndCursor()
        val snapper = CursorSnapper(input)
        val geometry = EllipticalRange(RangeCalibration(), ScreenBounds(0, 0, 1920, 1080))
        fun snapAt(ms: Long) = snapper.snapWhileTriggerHeld(ms * 1_000_000, 0x45, geometry) { input.foreground }
        snapAt(0) // Observe release before accepting the first press.
        input.down = true
        assertTrue(snapAt(10))
        assertFalse(snapAt(110)) // Valid cursor: no move.
        input.position = ScreenPoint(1500, 540)
        assertFalse(snapAt(150))
        assertTrue(snapAt(210))
        assertEquals(2, input.moves)
        input.down = false
        assertFalse(snapAt(220))
        input.position = ScreenPoint(970, 540)
        input.down = true
        assertTrue(snapAt(230)) // Fresh press has no repeat delay.
    }

    @Test fun `focus loss cancels snapping until a fresh release and press`() {
        val input = FakeKeyboardAndCursor()
        val snapper = CursorSnapper(input)
        val geometry = EllipticalRange(RangeCalibration(), ScreenBounds(0, 0, 1920, 1080))
        fun snapAt(ms: Long) = snapper.snapWhileTriggerHeld(ms * 1_000_000, 0x45, geometry) { input.foreground }
        snapAt(0)
        input.down = true
        snapAt(10)
        input.foreground = false
        input.position = ScreenPoint(1500, 540)
        assertFalse(snapAt(110))
        input.foreground = true
        assertFalse(snapAt(210))
        input.down = false
        snapAt(220)
        input.down = true
        assertTrue(snapAt(230))
        assertEquals(2, input.moves)
    }
}
