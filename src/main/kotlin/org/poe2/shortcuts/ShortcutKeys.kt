package org.poe2.shortcuts

import com.sun.jna.platform.win32.Win32VK
import com.sun.jna.platform.win32.WinUser

internal const val SUPPORTED_HOTKEY_MODIFIERS = WinUser.MOD_ALT or WinUser.MOD_CONTROL or WinUser.MOD_SHIFT
internal val VALID_VIRTUAL_KEYS = Win32VK.VK_BACK.code..Win32VK.VK_OEM_CLEAR.code
internal val LETTER_KEYS = Win32VK.VK_A.code..Win32VK.VK_Z.code
internal val DIGIT_KEYS = Win32VK.VK_0.code..Win32VK.VK_9.code
internal val FUNCTION_KEYS = Win32VK.VK_F1.code..Win32VK.VK_F24.code
private val MODIFIER_KEYS = setOf(Win32VK.VK_SHIFT.code, Win32VK.VK_CONTROL.code, Win32VK.VK_MENU.code)

internal fun isNonModifierKey(key: Int) = key in VALID_VIRTUAL_KEYS && key !in MODIFIER_KEYS
