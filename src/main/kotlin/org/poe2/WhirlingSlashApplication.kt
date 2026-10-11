package org.poe2

import org.poe2.config.AppSettings
import org.poe2.config.SettingsStore
import org.poe2.cursor.CursorSnapper
import org.poe2.cursor.KeyboardAndCursor
import org.poe2.shortcuts.ShortcutAction
import org.poe2.shortcuts.ShortcutRegistrationStatus
import org.poe2.geometry.EllipticalRange
import org.poe2.overlay.OverlayState
import org.poe2.overlay.OverlayWindow
import org.poe2.ui.SettingsWindow
import org.poe2.ui.SettingsFeedback
import org.poe2.ui.SystemTrayController
import org.poe2.win32.GameWindow
import org.poe2.win32.GlobalShortcuts
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private const val WORKER_INTERVAL_MS = 10L
private const val SAVE_DEBOUNCE_NANOS = 300_000_000L
private const val FOCUS_CHECK_INTERVAL_NANOS = 50_000_000L
private const val WORKER_SHUTDOWN_TIMEOUT_SECONDS = 2L

/** Mutable controls and cursor movement share a lock so disablement cancels subsequent moves. */
class WhirlingSlashApplication(
    private val gameWindow: GameWindow,
    keyboardAndCursor: KeyboardAndCursor,
    private val store: SettingsStore,
    initial: AppSettings,
) : AutoCloseable {
    private val stateLock = Any()
    @Volatile private var settings = initial
    @Volatile private var snappingEnabled = false
    @Volatile private var closed = false
    private var saveFeedback = "Saved automatically"
    private var snappingGuidance = ""
    private var trayAvailable = true
    @Volatile private var shortcutRecording = false
    private var shortcutStatus = ShortcutRegistrationStatus(false, emptyMap())
    private val emergencyAvailable get() = shortcutStatus.emergencyAvailable
    private var lastFocusCheckAt = Long.MIN_VALUE
    private var gameFocused = false
    private var pendingSave: AppSettings? = null
    private var saveDueAt = 0L
    private val uiUpdateQueued = AtomicBoolean(false)
    private var range = EllipticalRange(initial.rangeCalibration, gameWindow.bounds)
    private val snapper = CursorSnapper(keyboardAndCursor)
    private val overlay = OverlayWindow(gameWindow, settings)
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "overlay-worker").apply { isDaemon = true }
    }
    private val settingsWindow = SettingsWindow(
        initial = settings,
        gameBounds = gameWindow.bounds,
        onSettings = ::updateSettings,
        onSnapping = ::setSnapping,
        onExit = ::close,
        onRecording = ::setShortcutRecording,
        isKeyDown = keyboardAndCursor::isKeyDown,
    )
    private val tray = SystemTrayController(settingsWindow::show, ::close)
    private val globalShortcuts = GlobalShortcuts(settings.hotkeys, ::handleShortcut, ::handleShortcutRegistration)

    private fun handleShortcut(action: ShortcutAction) {
        if (closed || shortcutRecording) return
        // Emergency disablement must not wait behind work on the Swing event queue.
        if (action == ShortcutAction.DISABLE_SNAPPING) {
            setSnapping(false)
            return
        }
        SwingUtilities.invokeLater {
            if (closed || shortcutRecording) return@invokeLater
            when (action) {
                ShortcutAction.TOGGLE_OVERLAY -> updateSettings(settings.copy(overlayEnabled = !settings.overlayEnabled))
                ShortcutAction.TOGGLE_SNAPPING -> setSnapping(!snappingEnabled)
                ShortcutAction.SHOW_SETTINGS -> settingsWindow.toggleVisibility()
                ShortcutAction.DISABLE_SNAPPING -> Unit
            }
        }
    }

    private fun handleShortcutRegistration(status: ShortcutRegistrationStatus) {
        synchronized(stateLock) {
            shortcutStatus = status
            if (!emergencyAvailable) { snappingEnabled = false; snapper.requireTriggerRelease() }
        }
        updateUi()
    }

    fun start() {
        trayAvailable = tray.register()
        settingsWindow.setMinimizeToTray(trayAvailable)
        settingsWindow.show()
        updateUi()
        worker.scheduleWithFixedDelay({ ApplicationErrors.guard(::processGameInteraction) }, 0, WORKER_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun setShortcutRecording(active: Boolean): CompletableFuture<Unit> {
        synchronized(stateLock) {
            // Keep snapping paused until native shortcut registration has finished.
            shortcutRecording = true
            snappingEnabled = false
            snapper.requireTriggerRelease()
        }
        updateUi()
        return ApplicationErrors.observe(globalShortcuts.setSuspended(active).thenApply {
            synchronized(stateLock) {
                if (!active) shortcutRecording = false
            }
            updateUi()
        })
    }

    private fun updateSettings(next: AppSettings) {
        val shortcutsChanged = synchronized(stateLock) {
            if (closed) return
            val changed = next.hotkeys != settings.hotkeys
            settings = next
            range = EllipticalRange(next.rangeCalibration, gameWindow.bounds)
            if (changed) {
                shortcutStatus = shortcutStatus.copy(emergencyAvailable = false)
                snappingEnabled = false
            }
            snapper.requireTriggerRelease()
            pendingSave = next
            saveFeedback = "Saving…"
            saveDueAt = System.nanoTime() + SAVE_DEBOUNCE_NANOS
            changed
        }
        if (shortcutsChanged) ApplicationErrors.observe(globalShortcuts.updateBindings(next.hotkeys))
        updateUi()
    }

    private fun setSnapping(enabled: Boolean) {
        synchronized(stateLock) {
            if (closed) return
            snappingEnabled = enabled && emergencyAvailable && !shortcutRecording
            snapper.requireTriggerRelease()
            snappingGuidance = when {
                enabled && shortcutRecording -> "Finish shortcut recording before enabling snapping."
                enabled && !emergencyAvailable -> "Register an emergency shortcut before enabling snapping."
                else -> ""
            }
        }
        updateUi()
    }

    private fun processGameInteraction() {
        if (closed) return
        val now = System.nanoTime()
        synchronized(stateLock) {
            if (closed) return
            checkGameFocus(now)
            moveCursorIfEnabled(now)
        }
        savePendingSettings(now)
    }

    // Worker steps run under stateLock: disablement cannot race a cursor move.
    private fun checkGameFocus(now: Long) {
        if (lastFocusCheckAt != Long.MIN_VALUE && now - lastFocusCheckAt < FOCUS_CHECK_INTERVAL_NANOS) return
        val focused = gameWindow.isFocused()
        if (focused != gameFocused) {
            gameFocused = focused
            updateUi()
        }
        lastFocusCheckAt = now
        if (!gameFocused) snapper.requireTriggerRelease()
    }

    private fun snappingAllowed() = snappingEnabled && emergencyAvailable && !shortcutRecording && !closed

    private fun moveCursorIfEnabled(now: Long) {
        if (!snappingAllowed() || !gameFocused) {
            snapper.requireTriggerRelease()
            return
        }
        snapper.snapWhileTriggerHeld(now, settings.snapKey, range) {
            snappingAllowed() && gameWindow.isFocused()
        }
    }

    private fun savePendingSettings(now: Long, flush: Boolean = false) {
        val toSave = synchronized(stateLock) {
            if (!flush && now < saveDueAt) return
            pendingSave.also { pendingSave = null }
        } ?: return
        store.save(toSave)
        synchronized(stateLock) {
            saveFeedback = if (pendingSave == null) "Saved automatically" else "Saving…"
        }
        updateUi()
    }

    /** State changes share one queued Swing update, which reads the latest values. */
    private fun updateUi() {
        if (closed || !uiUpdateQueued.compareAndSet(false, true)) return
        SwingUtilities.invokeLater {
            uiUpdateQueued.set(false)
            val currentSettings: AppSettings
            val snapEnabled: Boolean
            val emergencyReady: Boolean
            val feedback: SettingsFeedback
            synchronized(stateLock) {
                if (closed) return@invokeLater
                currentSettings = settings
                snapEnabled = snappingAllowed()
                emergencyReady = emergencyAvailable
                feedback = SettingsFeedback(saveFeedback, shortcutStatus.errors, snappingGuidance, trayAvailable)
            }
            overlay.updateDisplay(OverlayState(currentSettings, snapEnabled))
            settingsWindow.updateControls(currentSettings, snapEnabled, emergencyReady, feedback)
        }
    }

    override fun close() {
        synchronized(stateLock) {
            if (closed) return
            closed = true
            snappingEnabled = false
            snapper.requireTriggerRelease()
        }
        try {
            stopWorker()
            try {
                savePendingSettings(System.nanoTime(), flush = true)
            } finally {
                globalShortcuts.close()
            }
        } finally {
            if (SwingUtilities.isEventDispatchThread()) disposeWindows()
            else SwingUtilities.invokeLater(::disposeWindows)
        }
    }

    private fun stopWorker() {
        worker.shutdown()
        try {
            worker.awaitTermination(WORKER_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun disposeWindows() {
        try {
            tray.close()
        } finally {
            try {
                overlay.close()
            } finally {
                settingsWindow.close()
            }
        }
    }
}
