package org.poe2.ui

import com.sun.jna.platform.win32.Win32VK

import org.poe2.ApplicationErrors
import org.poe2.shortcuts.ShortcutBinding
import org.poe2.config.AppSettings
import org.poe2.screen.ScreenBounds
import org.poe2.shortcuts.ShortcutLabels
import org.poe2.shortcuts.ShortcutAction
import java.awt.*
import java.awt.event.*
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class SettingsWindow(
    initial: AppSettings,
    gameBounds: ScreenBounds,
    private val onSettings: (AppSettings) -> Unit,
    private val onSnapping: (Boolean) -> Unit,
    private val onExit: () -> Unit,
    private val onRecording: (Boolean) -> CompletableFuture<Unit>,
    private val isKeyDown: (Int) -> Boolean,
) : AutoCloseable {
    private var current = initial
    private var updating = false
    private var disposed = false
    private val frame = JFrame("Whirling Slash — Settings")
    private var minimizeToTray = false
    private var feedback = SettingsFeedback()
    private val status = JLabel("Saved automatically").apply { foreground = UiTheme.muted }
    private val trayNotice = UiTheme.note("").apply { isVisible = false }
    private val snappingNotice = UiTheme.note("").apply { isVisible = false }
    private val rangeSummary = UiTheme.note("")
    private val visibility = SettingSwitch("Range overlay", "Show the inner and outer range boundaries.", initial.overlayEnabled)
    private val target = SettingSwitch("Midpoint guide", "Show a dashed line halfway between the boundaries.", initial.dashedTarget)
    private val snapping = SettingSwitch("Cursor snapping", "Snap while holding the trigger key. Pauses when the game is unfocused.", large = true)
        .apply { isEnabled = false }
    private val calibrationError = errorLabel()
    private val innerHorizontal = CalibrationEditor("Inner horizontal", AppSettings::innerX, 1, gameBounds.width)
    private val innerVertical = CalibrationEditor("Inner vertical", AppSettings::innerY, 1, gameBounds.height)
    private val outerHorizontal = CalibrationEditor("Outer horizontal", AppSettings::outerX, 1, gameBounds.width)
    private val outerVertical = CalibrationEditor("Outer vertical", AppSettings::outerY, 1, gameBounds.height)
    private val centerOffsetX = CalibrationEditor("Center offset X", AppSettings::offsetX, -gameBounds.width, gameBounds.width)
    private val centerOffsetY = CalibrationEditor("Center offset Y", AppSettings::offsetY, -gameBounds.height, gameBounds.height)
    private val calibrationEditors = listOf(innerHorizontal, innerVertical, outerHorizontal, outerVertical, centerOffsetX, centerOffsetY)
    private val snapKey = ShortcutControl("Snap trigger", ShortcutBinding(0, initial.snapKey))
    private val bindings = ShortcutAction.entries.map { action ->
        ShortcutControl(shortcutLabel(action), initial.hotkeys.forAction(action), action)
    }
    private val rangeHeader = JToggleButton("Range & position").apply {
        accessibleContext.accessibleName = "Range & position"
        horizontalAlignment = SwingConstants.LEFT
        icon = UIManager.getIcon("Tree.collapsedIcon")
        selectedIcon = UIManager.getIcon("Tree.expandedIcon")
        isContentAreaFilled = false
        border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
        margin = Insets(8, 10, 8, 10)
    }
    private val rangeControls = JPanel(GridBagLayout()).apply { name = "range-controls"; isOpaque = false }
    private var recording: ShortcutControl? = null
    private var pendingBinding: ShortcutBinding? = null
    private var captureMessage = ""
    private enum class CapturePhase { PAUSING_SHORTCUTS, LISTENING, WAITING_FOR_RELEASE, RESTORING_SHORTCUTS }
    private var capturePhase = CapturePhase.PAUSING_SHORTCUTS
    private var releaseKey: Int? = null
    private val keyboard = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val dispatcher = KeyEventDispatcher(::captureKey)
    private val releaseTimer = Timer(30) { finishWhenReleased() }
    private var emergencyAvailable = false

    init {
        frame.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
        frame.iconImages = ApplicationIcon.windowImages
        frame.minimumSize = Dimension(420, 420)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) { hideSettings() }
            override fun windowIconified(e: WindowEvent) {
                if (minimizeToTray) hideSettings()
            }
            override fun windowDeactivated(e: WindowEvent) { cancelRecording() }
        })
        val root = JPanel(BorderLayout()).apply { background = UiTheme.page }
        root.add(createSettingsBody(), BorderLayout.CENTER)
        rangeHeader.addActionListener { setRangeExpanded(rangeHeader.isSelected) }
        setRangeExpanded(false)
        root.add(createFooter(), BorderLayout.SOUTH)
        rangeHeader.font = UiTheme.boldFont(13)
        rangeHeader.foreground = UiTheme.text
        rangeHeader.toolTipText = "Adjust the range radii and center offset in physical pixels."
        frame.contentPane = root; frame.setSize(500, 600); frame.setLocationRelativeTo(null)
        updateRangeSummary(initial)
        visibility.addActionListener { if (!updating) applySettings(current.copy(overlayEnabled = visibility.isSelected)) }
        target.addActionListener { if (!updating) applySettings(current.copy(dashedTarget = target.isSelected)) }
        snapping.addActionListener { if (!updating) onSnapping(snapping.isSelected) }
    }

    private fun createSettingsBody(): JScrollPane {
        val groups = listOf(
            section("Inner boundary", listOf(innerHorizontal.panel, innerVertical.panel)),
            section("Outer boundary", listOf(outerHorizontal.panel, outerVertical.panel)),
            section("Center offset", listOf(centerOffsetX.panel, centerOffsetY.panel)),
            calibrationError)
        groups.forEachIndexed { index, component -> rangeControls.add(component, GridBagConstraints().apply {
            gridx = 0; gridy = index; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 12, 0)
        }) }
        val calibration = JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(rangeHeader, BorderLayout.NORTH)
            add(JPanel(BorderLayout(0, 12)).apply {
                isOpaque = false
                add(rangeSummary, BorderLayout.NORTH)
                add(rangeControls, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
        }
        val switches = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            add(JPanel(BorderLayout(0, 4)).apply {
                isOpaque = false
                add(snapping, BorderLayout.NORTH)
                add(snappingNotice, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(JPanel(GridLayout(1, 2, 8, 0)).apply {
                isOpaque = false; add(visibility); add(target)
            }, BorderLayout.CENTER)
        }
        return page(listOf(
            switches,
            section("Shortcuts", listOf(snapKey.panel) + bindings.map { it.panel }),
            calibration, trayNotice))
    }

    private fun createFooter() = JPanel(BorderLayout(16, 0)).apply {
        background = UiTheme.page
        border = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.border),
            BorderFactory.createEmptyBorder(8, 16, 12, 16))
        add(status, BorderLayout.CENTER)
        add(JButton("Exit application").apply { addActionListener { onExit() } }, BorderLayout.EAST)
    }

    private fun setRangeExpanded(expanded: Boolean) {
        rangeHeader.isSelected = expanded
        rangeHeader.accessibleContext.accessibleDescription = if (expanded) "Expanded" else "Collapsed"
        if (!expanded && SwingUtilities.isDescendingFrom(keyboard.focusOwner ?: frame, rangeControls))
            rangeHeader.requestFocusInWindow()
        rangeControls.isVisible = expanded
        fun enableEditors(component: Component) {
            if (component is JTextField || component is JSlider || component is JButton) {
                component.isEnabled = expanded; component.isFocusable = expanded
            }
            if (component is Container) component.components.forEach(::enableEditors)
        }
        enableEditors(rangeControls)
        rangeControls.parent?.revalidate(); rangeControls.parent?.repaint()
    }

    private fun page(items: List<JComponent>): JScrollPane {
        val body = object : JPanel(GridBagLayout()), Scrollable {
            override fun getPreferredScrollableViewportSize() = preferredSize
            override fun getScrollableTracksViewportWidth() = true
            override fun getScrollableTracksViewportHeight() = false
            override fun getScrollableUnitIncrement(rect: Rectangle, orientation: Int, direction: Int) = 24
            override fun getScrollableBlockIncrement(rect: Rectangle, orientation: Int, direction: Int) = rect.height - 24
        }.apply {
            background = UiTheme.page; border = BorderFactory.createEmptyBorder(UiTheme.OUTER, UiTheme.OUTER, UiTheme.OUTER, UiTheme.OUTER)
            items.forEachIndexed { index, component -> add(component, GridBagConstraints().apply {
                gridx = 0; gridy = index; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST; insets = Insets(0, 0, UiTheme.GAP, 0)
            }) }
            add(Box.createVerticalGlue(), GridBagConstraints().apply {
                gridx = 0; gridy = items.size; weighty = 1.0; weightx = 1.0
            })
        }
        return JScrollPane(body).apply {
            name = "configuration-body"; border = null
            viewport.background = UiTheme.page
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 24
        }
    }

    private fun section(title: String, items: List<JComponent>) = JPanel(GridBagLayout()).apply {
        isOpaque = false
        (listOf(UiTheme.heading(title, 13)) + items).forEachIndexed { index, component ->
            add(component, GridBagConstraints().apply {
                gridx = 0; gridy = index; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                insets = Insets(0, 0, if (index == 0) 8 else if (index == items.size) 0 else 4, 0)
                anchor = GridBagConstraints.WEST
            })
        }
    }

    private fun errorLabel() = object : WrappedText(" ") {
        override fun setText(text: String?) {
            super.setText(text)
            isVisible = !text.isNullOrBlank()
        }
    }.apply { foreground = UiTheme.error }

    private fun updateRangeSummary(settings: AppSettings) {
        rangeSummary.text = "Inner ${format(settings.innerX)} × ${format(settings.innerY)} · " +
            "Outer ${format(settings.outerX)} × ${format(settings.outerY)} px\n" +
            "Center offset ${format(settings.offsetX)}, ${format(settings.offsetY)} px"
    }

    private inner class CalibrationEditor(label: String, val settingsValue: (AppSettings) -> Double, minimum: Int, maximum: Int) {
        val text = JTextField(format(settingsValue(current)), 7)
        private val error = errorLabel()
        var dirty = false
        private var changing = false
        private val slider = JSlider(minimum, maximum.coerceAtLeast(minimum + 1))
        private val labelComponent = JLabel(label).apply { labelFor = this@CalibrationEditor.text }
        private val controls = JPanel(FlowLayout(FlowLayout.RIGHT, 3, 0)).apply {
                isOpaque = false
                add(JButton("−").apply { margin = Insets(3, 7, 3, 7); toolTipText = "Decrease by 1 px"; addActionListener { step(-1) } })
                add(text); add(JLabel("px"))
                add(JButton("+").apply { margin = Insets(3, 7, 3, 7); toolTipText = "Increase by 1 px"; addActionListener { step(1) } })
        }
        val panel = JPanel().apply {
            isOpaque = false
            layout = CalibrationRowLayout(labelComponent, slider, controls, error) { frame.width >= 620 }
            add(labelComponent); add(slider); add(controls); add(error)
        }
        init {
            text.accessibleContext.accessibleName = "$label in pixels"
            slider.accessibleContext.accessibleName = label; slider.isOpaque = false
            displayValue(settingsValue(current))
            text.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = changed()
                override fun removeUpdate(e: DocumentEvent) = changed()
                override fun changedUpdate(e: DocumentEvent) = changed()
                private fun changed() { if (!changing && !updating) dirty = true }
            })
            text.addActionListener { applyCalibration() }
            text.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) { if (dirty && !disposed) applyCalibration() }
            })
            slider.addChangeListener {
                if (!changing && !updating) { text.text = format(slider.value.toDouble()); applyCalibration() }
            }
        }
        fun readValue(): Double? {
            val value = text.text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            showError(if (value == null) "Enter a finite number." else null)
            return value
        }
        fun showError(message: String?) {
            error.text = message ?: " "
            UiTheme.outline(text, message != null)
        }
        private fun step(amount: Int) {
            val value = readValue() ?: return
            text.text = format(value + amount); applyCalibration()
        }
        fun displayValue(value: Double) {
            changing = true
            try {
                text.text = format(value)
                slider.minimum = minOf(slider.minimum, floor(value).coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt())
                slider.maximum = maxOf(slider.maximum, ceil(value).coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt())
                slider.value = value.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).roundToInt()
                dirty = false; showError(null)
            } finally { changing = false }
        }
    }

    private fun shortcutLabel(action: ShortcutAction) = when (action) {
        ShortcutAction.TOGGLE_OVERLAY -> "Overlay visibility"
        ShortcutAction.TOGGLE_SNAPPING -> "Toggle snapping"
        ShortcutAction.SHOW_SETTINGS -> "Open configuration"
        ShortcutAction.DISABLE_SNAPPING -> "Emergency disable"
    }

    private inner class ShortcutControl(label: String, var binding: ShortcutBinding, val action: ShortcutAction? = null) {
        val singleKey get() = action == null
        val button = JButton(ShortcutLabels.bindingText(binding))
        private val error = errorLabel()
        val panel = JPanel(BorderLayout(8, 3)).apply {
            isOpaque = false
            add(JLabel(label).apply { labelFor = button; preferredSize = Dimension(150, preferredSize.height) }, BorderLayout.WEST)
            add(button, BorderLayout.EAST); add(error, BorderLayout.SOUTH)
        }
        init {
            button.minimumSize = Dimension(160, 28)
            button.preferredSize = Dimension(160, 28)
            button.accessibleContext.accessibleName = "$label shortcut"
            button.addActionListener { beginRecording(this) }
            updateButtonLabel()
        }
        fun updateButtonLabel() {
            button.text = ShortcutLabels.bindingText(binding)
            button.toolTipText = "${button.text} · Click to change; Esc cancels." +
                if (singleKey) " The trigger key passes through to the game." else ""
        }
        fun showHint(message: String) {
            error.text = message; error.foreground = UiTheme.muted
            button.putClientProperty("JComponent.outline", "warning")
        }
        fun showError(message: String?) {
            error.text = message ?: " "
            error.foreground = UiTheme.error
            UiTheme.outline(button, message != null)
        }
    }

    private fun applyCalibration() {
        if (updating || disposed || !rangeHeader.isSelected) return
        val innerX = innerHorizontal.readValue()
        val innerY = innerVertical.readValue()
        val outerX = outerHorizontal.readValue()
        val outerY = outerVertical.readValue()
        val offsetX = centerOffsetX.readValue()
        val offsetY = centerOffsetY.readValue()
        if (innerX == null || innerY == null || outerX == null || outerY == null || offsetX == null || offsetY == null) {
            calibrationError.text = "Fix the highlighted values to apply changes."
            return
        }
        val next = current.copy(
            innerX = innerX, innerY = innerY, outerX = outerX, outerY = outerY, offsetX = offsetX, offsetY = offsetY,
        )
        innerHorizontal.showError(if (next.innerX <= 0) "Radius must be positive." else null)
        innerVertical.showError(if (next.innerY <= 0) "Radius must be positive." else null)
        outerHorizontal.showError(if (next.outerX <= next.innerX) "Outer radius must exceed the inner radius." else null)
        outerVertical.showError(if (next.outerY <= next.innerY) "Outer radius must exceed the inner radius." else null)
        val error = next.validationError(); calibrationError.text = error ?: " "
        if (error == null) { calibrationEditors.forEach { it.displayValue(it.settingsValue(next)) }; applySettings(next) }
    }

    private fun validateShortcuts() {
        val errors = ShortcutInput.conflicts(bindings.map { it.binding }, snapKey.binding.key)
        bindings.forEachIndexed { i, editor -> editor.showError(feedback.shortcutErrors[editor.action] ?: errors[i]) }
        snapKey.showError(if (bindings.any { it.binding.key == snapKey.binding.key }) "Also used by a control shortcut." else null)
    }
    private fun settingsWithBinding(editor: ShortcutControl, binding: ShortcutBinding): AppSettings {
        val action = editor.action ?: return current.copy(snapKey = binding.key)
        return current.copy(hotkeys = current.hotkeys.withBinding(action, binding))
    }
    private fun applySettings(next: AppSettings) {
        current = next; onSettings(next)
    }

    private fun beginRecording(editor: ShortcutControl) {
        if (recording != null || disposed) return
        recording = editor; capturePhase = CapturePhase.PAUSING_SHORTCUTS; releaseKey = null
        pendingBinding = null; captureMessage = ""
        editor.showError(null); editor.button.text = "Pausing shortcuts…"
        updateShortcutControls(); keyboard.addKeyEventDispatcher(dispatcher)
        ApplicationErrors.observe(onRecording(true).thenRun { SwingUtilities.invokeLater {
            if (disposed) return@invokeLater
            if (recording === editor && capturePhase == CapturePhase.PAUSING_SHORTCUTS) {
                capturePhase = CapturePhase.LISTENING; editor.button.text = "Press shortcut"
                editor.showHint("Press shortcut · Esc cancels")
                editor.button.requestFocusInWindow()
            }
        } })
    }
    private fun captureKey(event: KeyEvent): Boolean {
        val editor = recording ?: return false
        if (!frame.isActive) { cancelRecording(); return false }
        if (event.id != KeyEvent.KEY_PRESSED || capturePhase != CapturePhase.LISTENING) return true
        if (event.keyCode == KeyEvent.VK_ESCAPE) { releaseKey = Win32VK.VK_ESCAPE.code; cancelRecording(); return true }
        if (event.keyCode in listOf(KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_SHIFT)) return true
        val key = ShortcutInput.toVirtualKey(event.keyCode)
        val error = ShortcutInput.validationError(key, event.modifiersEx, editor.singleKey)
        if (error != null) { editor.showError(error); return true }
        releaseKey = key
        acceptBinding(editor, ShortcutBinding(ShortcutInput.toShortcutModifiers(event.modifiersEx), key!!))
        return true
    }
    private fun acceptBinding(editor: ShortcutControl, candidate: ShortcutBinding) {
        val error = settingsWithBinding(editor, candidate).validationError()
        if (error != null) {
            captureMessage = "$error Previous shortcut kept."
            editor.showError(captureMessage); editor.updateButtonLabel()
        } else {
            pendingBinding = candidate
            editor.button.text = ShortcutLabels.bindingText(candidate)
        }
        waitForKeyRelease(editor)
    }
    private fun cancelRecording() {
        val editor = recording ?: return
        if (capturePhase == CapturePhase.WAITING_FOR_RELEASE || capturePhase == CapturePhase.RESTORING_SHORTCUTS) return
        editor.updateButtonLabel()
        waitForKeyRelease(editor)
    }
    private fun waitForKeyRelease(editor: ShortcutControl) {
        capturePhase = CapturePhase.WAITING_FOR_RELEASE
        if (captureMessage.isEmpty()) editor.showHint("Release keys to ${if (pendingBinding != null) "apply the shortcut" else "restore shortcuts"}.")
        releaseTimer.start()
    }
    private fun finishWhenReleased() {
        val editor = recording ?: return
        if (capturePhase != CapturePhase.WAITING_FOR_RELEASE) return
        val keys = listOfNotNull(releaseKey) + listOf(Win32VK.VK_CONTROL.code, Win32VK.VK_MENU.code,
            Win32VK.VK_SHIFT.code, Win32VK.VK_LWIN.code, Win32VK.VK_RWIN.code)
        if (keys.any(isKeyDown)) return
        releaseTimer.stop(); keyboard.removeKeyEventDispatcher(dispatcher)
        capturePhase = CapturePhase.RESTORING_SHORTCUTS
        pendingBinding?.let { candidate ->
            val next = settingsWithBinding(editor, candidate)
            editor.binding = candidate
            if (next != current) applySettings(next)
        }
        pendingBinding = null; editor.updateButtonLabel()
        ApplicationErrors.observe(onRecording(false).thenRun { SwingUtilities.invokeLater {
            recording = null
            if (disposed) return@invokeLater
            updateShortcutControls()
            validateShortcuts()
            if (captureMessage.isNotEmpty()) editor.showError(captureMessage)
        } })
    }
    private fun updateShortcutControls() {
        (bindings + snapKey).forEach { it.button.isEnabled = recording == null || it === recording }
        updateSnappingControl()
    }

    private fun updateSnappingControl() {
        snapping.isEnabled = emergencyAvailable && recording == null
        val description = when {
            recording != null -> "Finish recording a shortcut to enable snapping."
            !emergencyAvailable -> "Set an available emergency shortcut to enable snapping."
            else -> "Snap into range while holding ${ShortcutLabels.keyName(current.snapKey)}. Pauses when unfocused."
        }
        snapping.toolTipText = description
        snapping.accessibleContext.accessibleDescription = description
        snappingNotice.text = feedback.snappingGuidance.ifBlank {
            when {
                recording != null -> "Finish recording before enabling snapping."
                !emergencyAvailable -> "Emergency shortcut unavailable. Change it below."
                else -> ""
            }
        }
        snappingNotice.isVisible = snappingNotice.text.isNotBlank()
    }

    fun updateControls(settings: AppSettings, snapEnabled: Boolean, emergencyAvailable: Boolean, feedback: SettingsFeedback) {
        if (disposed) return
        updating = true
        try {
            current = settings; this.emergencyAvailable = emergencyAvailable; this.feedback = feedback
            visibility.isSelected = settings.overlayEnabled; target.isSelected = settings.dashedTarget
            snapping.isSelected = snapEnabled
            updateSnappingControl()
            calibrationEditors.forEach { editor ->
                if (!editor.dirty && !editor.text.hasFocus()) editor.displayValue(editor.settingsValue(settings))
            }
            bindings.forEach { editor ->
                if (recording !== editor) {
                    editor.action?.let { editor.binding = settings.hotkeys.forAction(it) }
                    editor.updateButtonLabel()
                }
            }
            if (recording !== snapKey) { snapKey.binding = ShortcutBinding(0, settings.snapKey); snapKey.updateButtonLabel() }
            if (recording == null) validateShortcuts()
            updateRangeSummary(settings)
            status.text = feedback.saveFeedback
            trayNotice.text = "System tray unavailable. Use your configuration shortcut to reopen Settings."
            trayNotice.isVisible = !feedback.trayAvailable
        } finally { updating = false }
    }
    fun setMinimizeToTray(enabled: Boolean) { minimizeToTray = enabled }
    fun show() {
        if (disposed) return
        frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
        frame.isVisible = true; frame.toFront(); frame.requestFocus()
    }
    fun toggleVisibility() {
        if (frame.isVisible && frame.extendedState and Frame.ICONIFIED == 0) {
            hideSettings()
        } else show()
    }
    private fun hideSettings() {
        cancelRecording()
        frame.isVisible = false
    }
    override fun close() {
        disposed = true; releaseTimer.stop(); keyboard.removeKeyEventDispatcher(dispatcher)
        recording = null; frame.dispose()
    }
    private fun format(value: Double): String = if (value % 1.0 == 0.0 && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble())
        value.toLong().toString() else value.toString()
}
