package org.poe2.shortcuts

data class ShortcutRegistrationStatus(val emergencyAvailable: Boolean, val errors: Map<ShortcutAction, String>)
