package org.poe2.ui

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.swing.SwingUtilities

/** Created, registered and closed on the Swing event dispatch thread. */
class SystemTrayController(private val onOpen: () -> Unit, private val onExit: () -> Unit) : AutoCloseable {
    private var icon: TrayIcon? = null
    private var closed = false

    /** Unsupported platforms retain ordinary minimization; registration failures propagate. */
    fun register(): Boolean {
        if (icon != null) return true
        if (!SystemTray.isSupported()) return false
        val tray = SystemTray.getSystemTray()
        fun dispatch(action: () -> Unit) = SwingUtilities.invokeLater { if (!closed) action() }
        val menu = PopupMenu().apply {
            add(MenuItem("Open configuration").apply { addActionListener { dispatch(onOpen) } })
            addSeparator()
            add(MenuItem("Exit").apply { addActionListener { dispatch(onExit) } })
        }
        val candidate = TrayIcon(ApplicationIcon.image(tray.trayIconSize.width), "SmoothWhirlingSlash", menu).apply {
            isImageAutoSize = true
            addActionListener { dispatch(onOpen) }
        }
        tray.add(candidate)
        icon = candidate
        return true
    }

    override fun close() {
        closed = true
        icon?.let { SystemTray.getSystemTray().remove(it) }
        icon = null
    }
}
