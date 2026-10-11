package org.poe2.config

import com.sun.jna.platform.win32.Win32VK
import kotlinx.serialization.Serializable
import org.poe2.geometry.RangeCalibration
import org.poe2.shortcuts.ShortcutBindings
import org.poe2.shortcuts.isNonModifierKey

@Serializable
data class AppSettings(
    val innerX: Double = 170.0,
    val innerY: Double = 115.0,
    val outerX: Double = 240.0,
    val outerY: Double = 180.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = -35.0,
    val dashedTarget: Boolean = false,
    val overlayEnabled: Boolean = false,
    val snapKey: Int = Win32VK.VK_E.code,
    val hotkeys: ShortcutBindings = ShortcutBindings(),
) {
    val rangeCalibration: RangeCalibration
        get() = RangeCalibration(innerX, innerY, outerX, outerY, offsetX, offsetY)

    fun validationError(): String? {
        val bindings = hotkeys.asList()
        return when {
            listOf(innerX, innerY, outerX, outerY, offsetX, offsetY).any { !it.isFinite() } -> "Geometry must use finite numbers."
            innerX <= 0 || innerY <= 0 -> "Inner radii must be positive."
            outerX <= innerX || outerY <= innerY -> "Each outer radius must exceed its inner radius."
            !isNonModifierKey(snapKey) -> "Choose a non-modifier snap key."
            bindings.any { !it.isValid() } -> "Control shortcuts require Alt, Ctrl, or Shift and a valid key."
            bindings.distinct().size != bindings.size -> "Control shortcuts must be distinct."
            bindings.any { it.key == snapKey } -> "Control shortcuts cannot use the snap trigger key."
            else -> null
        }
    }
}
