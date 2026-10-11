package org.poe2

import com.sun.jna.platform.win32.Win32VK
import com.sun.jna.platform.win32.WinUser
import org.poe2.shortcuts.ShortcutBindings
import org.poe2.shortcuts.ShortcutLabels
import org.poe2.ui.ShortcutInput
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.test.*

class ShortcutInputTest {
    @Test fun `AWT keys map to Windows codes including extended function keys`() {
        assertEquals(Win32VK.VK_RETURN.code, ShortcutInput.toVirtualKey(KeyEvent.VK_ENTER))
        assertEquals(Win32VK.VK_F12.code, ShortcutInput.toVirtualKey(KeyEvent.VK_F12))
        assertEquals(Win32VK.VK_F13.code, ShortcutInput.toVirtualKey(KeyEvent.VK_F13))
        assertEquals(Win32VK.VK_F24.code, ShortcutInput.toVirtualKey(KeyEvent.VK_F24))
        assertEquals(Win32VK.VK_LEFT.code, ShortcutInput.toVirtualKey(KeyEvent.VK_LEFT))
        assertEquals(Win32VK.VK_TAB.code, ShortcutInput.toVirtualKey(KeyEvent.VK_TAB))
        assertNull(ShortcutInput.toVirtualKey(KeyEvent.VK_DELETE))
    }

    @Test fun `control shortcuts require modifiers and snap triggers forbid them`() {
        val ctrlAlt = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK
        assertEquals(WinUser.MOD_CONTROL or WinUser.MOD_ALT, ShortcutInput.toShortcutModifiers(ctrlAlt))
        assertNull(ShortcutInput.validationError(Win32VK.VK_E.code, 0, true))
        assertNotNull(ShortcutInput.validationError(Win32VK.VK_E.code, ctrlAlt, true))
        assertNotNull(ShortcutInput.validationError(Win32VK.VK_E.code, 0, false))
        assertNull(ShortcutInput.validationError(Win32VK.VK_F8.code, ctrlAlt, false))
        assertNotNull(ShortcutInput.validationError(Win32VK.VK_E.code, InputEvent.META_DOWN_MASK, false))
        assertNotNull(ShortcutInput.validationError(null, ctrlAlt, false))
        assertEquals("Ctrl + Alt + F8", ShortcutLabels.bindingText(ShortcutBindings().visibility))
    }

    @Test fun `conflicts identify both duplicates and snap trigger collisions`() {
        val bindings = ShortcutBindings().asList()
        assertTrue(ShortcutInput.conflicts(bindings, Win32VK.VK_E.code).all { it == null })
        val duplicate = bindings.toMutableList().apply { this[1] = this[0] }
        val errors = ShortcutInput.conflicts(duplicate, bindings[3].key)
        assertNotNull(errors[0]); assertNotNull(errors[1]); assertNull(errors[2]); assertNotNull(errors[3])
    }
}
