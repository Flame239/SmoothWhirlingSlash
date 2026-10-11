package org.poe2.win32

import com.sun.jna.Pointer

import com.sun.jna.platform.win32.WinDef.HWND

// Win32 constants not exposed by JNA's WinUser interface.
internal const val WS_EX_NOACTIVATE = 0x08000000
internal const val WS_EX_TOOLWINDOW = 0x00000080
internal const val WM_APP = 0x8000
internal const val PM_NOREMOVE = 0x0000
internal const val PM_REMOVE = 0x0001
internal const val DPI_AWARENESS_PER_MONITOR_AWARE = 2
// Long literals preserve sign extension; JNA's Int overload clears the upper 32 bits.
internal val DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2: Pointer = Pointer.createConstant(-4L)
internal val HWND_TOPMOST = HWND(Pointer.createConstant(-1L))

internal const val ASYNC_KEY_DOWN_MASK = 0x8000
