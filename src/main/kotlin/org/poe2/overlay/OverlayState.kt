package org.poe2.overlay

import org.poe2.config.AppSettings

data class OverlayState(val settings: AppSettings, val snappingEnabled: Boolean = false) {
    fun shouldShow(gameForeground: Boolean) = gameForeground && (settings.overlayEnabled || snappingEnabled)
}
