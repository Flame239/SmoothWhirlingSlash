package org.poe2.win32

import org.poe2.shortcuts.ShortcutBindings
import org.poe2.shortcuts.ShortcutBinding
import org.poe2.shortcuts.ShortcutAction
import org.poe2.shortcuts.ShortcutRegistrationStatus

internal const val FIRST_HOTKEY_ID = 1

/** Used only by the thread owning the Win32 registrations. */
internal class ShortcutRegistration(
    initial: ShortcutBindings,
    private val register: (Int, ShortcutBinding) -> String?,
    private val unregister: (Int) -> Unit,
) {
    private var bindings = initial
    private var suspended = false
    private val registered = mutableSetOf<Int>()

    fun applyBindings(next: ShortcutBindings? = null, suspend: Boolean? = null): ShortcutRegistrationStatus {
        unregisterAll()
        if (next != null) bindings = next
        if (suspend != null) suspended = suspend
        val errors = mutableMapOf<ShortcutAction, String>()
        if (!suspended) ShortcutAction.entries.forEach { action ->
            val id = action.ordinal + FIRST_HOTKEY_ID
            val error = register(id, bindings.forAction(action))
            if (error == null) registered += id else errors[action] = "Shortcut unavailable ($error). Choose another combination."
        }
        return ShortcutRegistrationStatus(ShortcutAction.DISABLE_SNAPPING.ordinal + FIRST_HOTKEY_ID in registered, errors)
    }

    fun registeredAction(id: Int): ShortcutAction? = if (!suspended && id in registered)
        ShortcutAction.entries.getOrNull(id - FIRST_HOTKEY_ID) else null

    fun unregisterAll() {
        registered.forEach(unregister)
        registered.clear()
    }
}
