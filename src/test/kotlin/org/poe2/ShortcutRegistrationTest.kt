package org.poe2

import org.poe2.shortcuts.ShortcutBindings
import org.poe2.shortcuts.ShortcutBinding
import org.poe2.shortcuts.ShortcutAction
import org.poe2.win32.ShortcutRegistration
import kotlin.test.*

class ShortcutRegistrationTest {
    @Test fun `capture unregisters before acknowledgement and restores the latest applied bindings`() {
        val active = mutableMapOf<Int, ShortcutBinding>()
        val events = mutableListOf<String>()
        val registration = ShortcutRegistration(ShortcutBindings(), { id, binding ->
            assertFalse(id in active)
            active[id] = binding; events += "register $id"; null
        }, { id -> active.remove(id); events += "unregister $id" })
        assertTrue(registration.applyBindings().emergencyAvailable)
        assertEquals(ShortcutAction.DISABLE_SNAPPING, registration.registeredAction(4))
        events.clear()
        assertFalse(registration.applyBindings(suspend = true).emergencyAvailable)
        assertTrue(active.isEmpty())
        assertEquals(listOf("unregister 1", "unregister 2", "unregister 3", "unregister 4"), events)
        assertNull(registration.registeredAction(4))
        val changed = ShortcutBindings().copy(visibility = ShortcutBinding(3, 0x70))
        registration.applyBindings(next = changed)
        assertTrue(active.isEmpty())
        assertTrue(registration.applyBindings(suspend = false).emergencyAvailable)
        assertEquals(changed.asList(), active.values.toList())
        registration.unregisterAll()
        assertTrue(active.isEmpty())
    }

    @Test fun `registration conflicts preserve error reporting and disable emergency availability`() {
        val registration = ShortcutRegistration(ShortcutBindings(), { id, _ -> if (id == 4) "Win32 1409" else null }, {})
        val status = registration.applyBindings()
        assertFalse(status.emergencyAvailable)
        assertTrue(status.errors.getValue(ShortcutAction.DISABLE_SNAPPING).contains("Win32 1409"))
        assertNull(registration.registeredAction(4))
        assertNull(registration.registeredAction(100))
        assertEquals(ShortcutAction.TOGGLE_OVERLAY, registration.registeredAction(1))
    }
}
