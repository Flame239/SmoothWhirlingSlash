package org.poe2.shortcuts

import com.sun.jna.platform.win32.WinUser
import kotlinx.serialization.Serializable

@Serializable
data class ShortcutBinding(val modifiers: Int, val key: Int) {
    fun isValid() = modifiers in WinUser.MOD_ALT..SUPPORTED_HOTKEY_MODIFIERS && isNonModifierKey(key)
}

