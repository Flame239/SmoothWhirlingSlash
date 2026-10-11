package org.poe2.ui

import org.poe2.shortcuts.ShortcutAction

data class SettingsFeedback(
    val saveFeedback: String = "Saved automatically",
    val shortcutErrors: Map<ShortcutAction, String> = emptyMap(),
    val snappingGuidance: String = "",
    val trayAvailable: Boolean = true,
)
