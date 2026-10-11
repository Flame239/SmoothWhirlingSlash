package org.poe2

import org.poe2.ui.SystemTrayController
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionEvent
import javax.swing.SwingUtilities
import kotlin.test.*

class SystemTrayControllerTest {
    private fun edt(block: () -> Unit) {
        var error: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (e: Throwable) { error = e } }
        error?.let { throw it }
    }

    @Test fun `tray registration is idempotent actions run on EDT and exit removes the icon`() {
        lateinit var controller: SystemTrayController
        var icon: TrayIcon? = null
        val actions = mutableListOf<String>()
        edt {
            controller = SystemTrayController({
                assertTrue(SwingUtilities.isEventDispatchThread()); actions += "open"
            }, {
                assertTrue(SwingUtilities.isEventDispatchThread()); actions += "exit"; controller.close()
            })
        }
        try {
            edt {
                val before = if (SystemTray.isSupported()) SystemTray.getSystemTray().trayIcons.toSet() else emptySet()
                val registered = controller.register()
                if (!registered) {
                    assertFalse(SystemTray.isSupported())
                    if (SystemTray.isSupported()) assertEquals(before, SystemTray.getSystemTray().trayIcons.toSet())
                } else {
                    val tray = SystemTray.getSystemTray()
                    val registeredIcon = tray.trayIcons.single { it !in before }
                    icon = registeredIcon
                    assertTrue(controller.register())
                    assertEquals(before.size + 1, tray.trayIcons.size)
                    val event = ActionEvent(registeredIcon, ActionEvent.ACTION_PERFORMED, "open")
                    registeredIcon.actionListeners.forEach { it.actionPerformed(event) }
                    registeredIcon.popupMenu.getItem(0).actionListeners.forEach { it.actionPerformed(event) }
                }
            }
            edt {
                icon?.let { registeredIcon ->
                    assertEquals(listOf("open", "open"), actions)
                    val event = ActionEvent(registeredIcon, ActionEvent.ACTION_PERFORMED, "exit")
                    registeredIcon.popupMenu.getItem(2).actionListeners.forEach { it.actionPerformed(event) }
                    // A queued action must not reopen a disposed configuration after Exit.
                    registeredIcon.actionListeners.forEach { it.actionPerformed(event) }
                }
            }
            edt {
                if (icon != null) {
                    assertEquals(listOf("open", "open", "exit"), actions)
                    assertFalse(SystemTray.getSystemTray().trayIcons.contains(icon))
                }
            }
        } finally { edt { controller.close(); controller.close() } }
    }
}
