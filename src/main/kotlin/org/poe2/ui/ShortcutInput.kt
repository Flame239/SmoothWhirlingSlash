package org.poe2.ui

import com.sun.jna.platform.win32.Win32VK
import com.sun.jna.platform.win32.WinUser
import org.poe2.shortcuts.ShortcutBinding



import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal object ShortcutInput {
    fun toVirtualKey(awtKey: Int): Int? = when (awtKey) {
        in KeyEvent.VK_A..KeyEvent.VK_Z, in KeyEvent.VK_0..KeyEvent.VK_9 -> awtKey
        in KeyEvent.VK_F1..KeyEvent.VK_F12 -> Win32VK.VK_F1.code + awtKey - KeyEvent.VK_F1
        in KeyEvent.VK_F13..KeyEvent.VK_F24 -> Win32VK.VK_F13.code + awtKey - KeyEvent.VK_F13
        KeyEvent.VK_BACK_SPACE -> Win32VK.VK_BACK.code
        KeyEvent.VK_TAB -> Win32VK.VK_TAB.code
        KeyEvent.VK_ENTER -> Win32VK.VK_RETURN.code
        KeyEvent.VK_ESCAPE -> Win32VK.VK_ESCAPE.code
        KeyEvent.VK_SPACE -> Win32VK.VK_SPACE.code
        KeyEvent.VK_LEFT -> Win32VK.VK_LEFT.code
        KeyEvent.VK_UP -> Win32VK.VK_UP.code
        KeyEvent.VK_RIGHT -> Win32VK.VK_RIGHT.code
        KeyEvent.VK_DOWN -> Win32VK.VK_DOWN.code
        else -> null
    }

    fun toShortcutModifiers(extended: Int): Int =
        (if (extended and InputEvent.CTRL_DOWN_MASK != 0) WinUser.MOD_CONTROL else 0) or
        (if (extended and InputEvent.ALT_DOWN_MASK != 0) WinUser.MOD_ALT else 0) or
        (if (extended and InputEvent.SHIFT_DOWN_MASK != 0) WinUser.MOD_SHIFT else 0)

    fun validationError(key: Int?, extended: Int, singleKey: Boolean): String? = when {
        extended and (InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK) != 0 -> "Use Ctrl, Alt, or Shift."
        key == null -> "Choose a letter, number, F1–F24, arrow, or basic editing key."
        singleKey && toShortcutModifiers(extended) != 0 -> "The snap trigger must be a single key."
        !singleKey && toShortcutModifiers(extended) == 0 -> "Include Ctrl, Alt, or Shift."
        else -> null
    }

    fun conflicts(bindings: List<ShortcutBinding>, snapKey: Int): List<String?> = bindings.map { binding ->
        when {
            !binding.isValid() -> "Include a modifier and a valid key."
            binding.key == snapKey -> "This key is also the snap trigger."
            bindings.count { it == binding } > 1 -> "This shortcut is assigned twice."
            else -> null
        }
    }
}
