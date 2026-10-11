package org.poe2.shortcuts

import com.sun.jna.platform.win32.Win32VK
import com.sun.jna.platform.win32.WinUser
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

private const val CTRL_ALT_MODIFIERS = WinUser.MOD_CONTROL or WinUser.MOD_ALT

@Serializable
data class ShortcutBindings(
    val visibility: ShortcutBinding = ShortcutBinding(CTRL_ALT_MODIFIERS, Win32VK.VK_F8.code),
    val snapping: ShortcutBinding = ShortcutBinding(CTRL_ALT_MODIFIERS, Win32VK.VK_F9.code),
    @SerialName("calibration") val showSettings: ShortcutBinding = ShortcutBinding(CTRL_ALT_MODIFIERS, Win32VK.VK_F10.code),
    val emergency: ShortcutBinding = ShortcutBinding(CTRL_ALT_MODIFIERS, Win32VK.VK_F12.code),
) {
    fun forAction(action: ShortcutAction): ShortcutBinding = when (action) {
        ShortcutAction.TOGGLE_OVERLAY -> visibility
        ShortcutAction.TOGGLE_SNAPPING -> snapping
        ShortcutAction.SHOW_SETTINGS -> showSettings
        ShortcutAction.DISABLE_SNAPPING -> emergency
    }

    fun withBinding(action: ShortcutAction, binding: ShortcutBinding): ShortcutBindings = when (action) {
        ShortcutAction.TOGGLE_OVERLAY -> copy(visibility = binding)
        ShortcutAction.TOGGLE_SNAPPING -> copy(snapping = binding)
        ShortcutAction.SHOW_SETTINGS -> copy(showSettings = binding)
        ShortcutAction.DISABLE_SNAPPING -> copy(emergency = binding)
    }

    fun asList() = ShortcutAction.entries.map(::forAction)
}
