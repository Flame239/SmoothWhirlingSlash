package org.poe2

import com.sun.jna.Pointer
import org.poe2.win32.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2
import org.poe2.win32.HWND_TOPMOST
import kotlin.test.Test
import kotlin.test.assertEquals

class Win32ConstantsTest {
    @Test fun `negative Windows pseudo handles retain pointer sized sign extension`() {
        assertEquals(-1L, Pointer.nativeValue(HWND_TOPMOST.pointer))
        assertEquals(-4L, Pointer.nativeValue(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2))
    }
}
