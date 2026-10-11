package org.poe2.shortcuts

import com.sun.jna.platform.win32.Win32VK
import com.sun.jna.platform.win32.WinUser

object ShortcutLabels {
    fun keyName(code: Int): String = when (code) {
        in LETTER_KEYS, in DIGIT_KEYS -> code.toChar().toString()
        in FUNCTION_KEYS -> "F${code - Win32VK.VK_F1.code + 1}"
        Win32VK.VK_BACK.code -> "Backspace"
        Win32VK.VK_TAB.code -> "Tab"
        Win32VK.VK_RETURN.code -> "Enter"
        Win32VK.VK_ESCAPE.code -> "Escape"
        Win32VK.VK_SPACE.code -> "Space"
        Win32VK.VK_LEFT.code -> "Left"
        Win32VK.VK_UP.code -> "Up"
        Win32VK.VK_RIGHT.code -> "Right"
        Win32VK.VK_DOWN.code -> "Down"
        else -> "VK 0x${code.toString(16).uppercase()}"
    }

    fun bindingText(binding: ShortcutBinding): String = buildList {
        if (binding.modifiers and WinUser.MOD_CONTROL != 0) add("Ctrl")
        if (binding.modifiers and WinUser.MOD_ALT != 0) add("Alt")
        if (binding.modifiers and WinUser.MOD_SHIFT != 0) add("Shift")
        add(keyName(binding.key))
    }.joinToString(" + ")
}

