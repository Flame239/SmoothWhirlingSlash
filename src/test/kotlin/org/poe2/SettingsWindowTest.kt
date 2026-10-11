package org.poe2


import org.poe2.config.AppSettings
import org.poe2.shortcuts.ShortcutBinding
import org.poe2.screen.ScreenBounds
import org.poe2.ui.SettingsFeedback
import org.poe2.ui.UiTheme
import org.poe2.ui.WrappedText
import org.poe2.shortcuts.ShortcutAction
import org.poe2.ui.SettingSwitch
import org.poe2.ui.SettingsWindow

import java.awt.Component
import java.awt.Container
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import javax.swing.*
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import kotlin.test.*

class SettingsWindowTest {
    @BeforeTest fun theme() = edt { UiTheme.install() }
    private fun edt(block: () -> Unit) {
        var error: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (e: Throwable) { error = e } }
        error?.let { throw it }
    }

    private fun components(root: Component): List<Component> = listOf(root) +
        if (root is Container) root.components.flatMap(::components) else emptyList()

    private fun frame(window: SettingsWindow) = SettingsWindow::class.java.getDeclaredField("frame")
        .apply { isAccessible = true }.get(window) as JFrame

    private fun create(saved: MutableList<AppSettings>, keyDown: (Int) -> Boolean = { false },
        recording: (Boolean) -> CompletableFuture<Unit> = { CompletableFuture.completedFuture(Unit) }): SettingsWindow = SettingsWindow(
        AppSettings(), ScreenBounds(0, 0, 1920, 1080),
        { saved += it }, {}, {}, recording, keyDown,
    )

    private fun mouseClick(box: SettingSwitch, from: Point, to: Point = from) {
        fun dispatch(id: Int, point: Point, modifiers: Int = 0, button: Int = MouseEvent.BUTTON1) {
            val recipient = SwingUtilities.getDeepestComponentAt(box, point.x, point.y) ?: box
            val local = SwingUtilities.convertPoint(box, point, recipient)
            recipient.dispatchEvent(MouseEvent(recipient, id, System.currentTimeMillis(), modifiers,
                local.x, local.y, 1, false, button))
        }
        dispatch(MouseEvent.MOUSE_PRESSED, from, MouseEvent.BUTTON1_DOWN_MASK)
        dispatch(MouseEvent.MOUSE_DRAGGED, to, MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON)
        dispatch(MouseEvent.MOUSE_RELEASED, to)
        dispatch(MouseEvent.MOUSE_CLICKED, to)
    }

    @Test fun `switch mouse sequences toggle exactly once across indicator text and empty space`() {
        edt {
            var settings = AppSettings()
            var snapping = false
            var settingChanges = 0
            var snapChanges = 0
            lateinit var window: SettingsWindow
            window = SettingsWindow(settings,
                ScreenBounds(0, 0, 1920, 1080),
                { settings = it; settingChanges++; window.updateControls(settings, snapping, true, SettingsFeedback()) },
                { snapping = it; snapChanges++; window.updateControls(settings, snapping, true, SettingsFeedback()) },
                {}, { CompletableFuture.completedFuture(Unit) }, { false })
            try {
                val frame = frame(window)
                frame.pack(); frame.validate()
                window.updateControls(settings, false, true, SettingsFeedback())
                val boxes = components(frame).filterIsInstance<SettingSwitch>()
                for (box in boxes) {
                    assertEquals(0, box.componentCount, "Switch input must have one target")
                    assertEquals(AccessibleRole.TOGGLE_BUTTON, box.accessibleContext.accessibleRole)
                    val large = box.height == 76
                    val trackWidth = if (large) 72 else 44
                    val trackHeight = if (large) 36 else 24
                    val padding = if (large) 16 else 12
                    val trackX = box.width - padding - trackWidth
                    val label = Point(50, if (large) 28 else 18)
                    val track = Point(trackX + trackWidth / 2, box.height / 2)
                    val offThumb = Point(trackX + trackHeight / 2, box.height / 2)
                    val onThumb = Point(box.width - padding - trackHeight / 2, box.height / 2)
                    val state = Point(20, if (large) 55 else 40)
                    val emptySpace = Point(box.width / 2, box.height - 8)
                    for ((from, to) in listOf(label to label, track to track, offThumb to offThumb,
                        onThumb to onThumb, state to state, emptySpace to emptySpace, label to track, track to state)) {
                        val before = settingChanges + snapChanges
                        val selected = box.isSelected
                        mouseClick(box, from, to)
                        assertEquals(before + 1, settingChanges + snapChanges, "${box.text}: $from -> $to")
                        assertEquals(!selected, box.isSelected)
                    }
                }
                assertEquals(16, settingChanges)
                assertEquals(8, snapChanges)
                assertFalse(settings.overlayEnabled)
                assertFalse(settings.dashedTarget)
                assertFalse(snapping)
                val snapBox = boxes.single { it.text == "Cursor snapping" }
                window.updateControls(settings, false, false, SettingsFeedback())
                mouseClick(snapBox, Point(50, snapBox.height / 2))
                assertEquals(8, snapChanges)
                assertFalse(snapBox.isSelected)
            } finally { window.close() }
        }
    }

    @Test fun `switch keyboard activation preserves accessible state and respects disablement`() {
        edt {
            val saved = mutableListOf<AppSettings>()
            val window = create(saved)
            try {
                val box = components(frame(window)).filterIsInstance<SettingSwitch>().single { it.text == "Range overlay" }
                fun key(code: Int) {
                    for (released in listOf(false, true)) {
                        val action = box.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(code, 0, released))
                        assertNotNull(action)
                        box.actionMap.get(action).actionPerformed(ActionEvent(box, ActionEvent.ACTION_PERFORMED, ""))
                    }
                }
                key(KeyEvent.VK_SPACE)
                assertTrue(box.isSelected)
                assertTrue(box.accessibleContext.accessibleStateSet.contains(AccessibleState.CHECKED))
                key(KeyEvent.VK_ENTER)
                assertFalse(box.isSelected)
                assertEquals(2, saved.size)
                box.isEnabled = false
                key(KeyEvent.VK_SPACE); key(KeyEvent.VK_ENTER)
                assertFalse(box.isSelected)
                assertEquals(2, saved.size)
            } finally { window.close() }
        }
    }

    @Test fun `snapping switch works with hidden overlay and survives visibility refreshes`() {
        edt {
            var settings = AppSettings(overlayEnabled = false)
            var snapEnabled = false
            val requests = mutableListOf<Boolean>()
            lateinit var window: SettingsWindow
            window = SettingsWindow(
                settings, ScreenBounds(0, 0, 1920, 1080),
                { settings = it; window.updateControls(settings, snapEnabled, true, SettingsFeedback()) },
                { requests += it; snapEnabled = it; window.updateControls(settings, snapEnabled, true, SettingsFeedback()) },
                {}, { CompletableFuture.completedFuture(Unit) }, { false },
            )
            try {
                val checkboxes = components(frame(window)).filterIsInstance<SettingSwitch>()
                val snapping = checkboxes.single { it.text == "Cursor snapping" }
                val visibility = checkboxes.single { it.text == "Range overlay" }
                window.updateControls(settings, snapEnabled, true, SettingsFeedback())
                assertFalse(visibility.isSelected)
                assertTrue(snapping.isEnabled)
                snapping.doClick()
                assertEquals(listOf(true), requests)
                assertTrue(snapping.isSelected)
                repeat(2) {
                    visibility.doClick()
                    assertTrue(snapping.isSelected)
                    assertTrue(snapping.isEnabled)
                }
                assertFalse(settings.overlayEnabled)
                snapping.doClick()
                assertEquals(listOf(true, false), requests)
                assertFalse(snapping.isSelected)
                window.updateControls(settings, false, false, SettingsFeedback())
                assertFalse(snapping.isEnabled)
                snapping.doClick()
                assertEquals(listOf(true, false), requests)
            } finally { window.close() }
        }
    }

    @Test fun `geometry edits preserve invalid drafts and apply valid slider and decimal changes`() {
        edt {
            val saved = mutableListOf<AppSettings>()
            val window = create(saved)
            try {
                val parts = components(frame(window))
                parts.filterIsInstance<JToggleButton>().single { it.accessibleContext.accessibleName == "Range & position" }.doClick()
                fun field(name: String) = parts.filterIsInstance<JTextField>()
                    .single { it.accessibleContext.accessibleName == "$name in pixels" }
                val inner = field("Inner horizontal")
                inner.text = "unfinished"
                window.updateControls(AppSettings(), false, true, SettingsFeedback())
                assertEquals("unfinished", inner.text)
                inner.postActionEvent()
                assertTrue(saved.isEmpty())
                inner.text = "300"
                inner.postActionEvent()
                assertTrue(saved.isEmpty()) // Outer radius is still 240.
                inner.text = "170.5"
                inner.postActionEvent()
                assertEquals(170.5, saved.last().innerX)
                val slider = parts.filterIsInstance<JSlider>().single { it.accessibleContext.accessibleName == "Inner horizontal" }
                slider.value = 175
                assertEquals(175.0, saved.last().innerX)
                val offset = field("Center offset X")
                offset.text = "-2500.25"
                offset.postActionEvent()
                assertEquals(-2500.25, saved.last().offsetX)
                val offsetSlider = parts.filterIsInstance<JSlider>().single { it.accessibleContext.accessibleName == "Center offset X" }
                assertTrue(offsetSlider.minimum <= -2501)
                assertEquals("-2500.25", offset.text)
            } finally { window.close() }
        }
    }

    @Test fun `capture waits for unregister acknowledgement and cancellation restores applied shortcuts`() {
        val events = mutableListOf<Boolean>()
        val paused = CompletableFuture<Unit>()
        var held = true
        lateinit var window: SettingsWindow
        lateinit var button: JButton
        lateinit var snapping: SettingSwitch
        edt {
            window = create(mutableListOf(), { held }) { active ->
                events += active
                if (active) paused else CompletableFuture.completedFuture(Unit)
            }
            button = components(frame(window)).filterIsInstance<JButton>()
                .single { it.accessibleContext.accessibleName == "Overlay visibility shortcut" }
            snapping = components(frame(window)).filterIsInstance<SettingSwitch>()
                .single { it.text == "Cursor snapping" }
            window.updateControls(AppSettings(overlayEnabled = false), false, true, SettingsFeedback())
            assertTrue(snapping.isEnabled)
            button.doClick()
            assertFalse(snapping.isEnabled)
            assertEquals(listOf(true), events)
            assertEquals("Pausing shortcuts…", button.text)
        }
        try {
            paused.complete(Unit)
            edt {
                assertEquals("Press shortcut", button.text)
                assertTrue(components(frame(window)).filterIsInstance<WrappedText>()
                    .any { it.isVisible && it.text == "Press shortcut · Esc cancels" })
                frame(window).windowListeners.forEach { it.windowDeactivated(WindowEvent(frame(window), WindowEvent.WINDOW_DEACTIVATED)) }
                SettingsWindow::class.java.getDeclaredMethod("finishWhenReleased").apply { isAccessible = true }.invoke(window)
                assertEquals(listOf(true), events) // Keep registrations suspended until every key is released.
                held = false
                SettingsWindow::class.java.getDeclaredMethod("finishWhenReleased").apply { isAccessible = true }.invoke(window)
                assertEquals(listOf(true, false), events)
            }
            edt {
                assertEquals("Ctrl + Alt + F8", button.text)
                assertNull(button.getClientProperty("JComponent.outline"))
                assertTrue(button.isEnabled)
                assertTrue(snapping.isEnabled)
                assertFalse(snapping.isSelected)
            }
        } finally { edt { window.close() } }
    }

    @Test fun `compact window and long errors fit narrow default and wide sizes with an accessible footer`() {
        lateinit var window: SettingsWindow
        lateinit var frame: JFrame
        edt { window = create(mutableListOf()); frame = frame(window); frame.pack() }
        try {
            for ((width, height) in listOf(420 to 420, 500 to 600, 900 to 900)) {
                edt { frame.setSize(width, height); frame.validate() }
                edt {
                    frame.validate()
                    val parts = components(frame)
                    assertFalse(parts.any { it is JTabbedPane })
                    assertEquals(3, parts.filterIsInstance<SettingSwitch>().size)
                    val switches = parts.filterIsInstance<SettingSwitch>()
                    val snap = switches.single { it.text == "Cursor snapping" }
                    val overlay = switches.single { it.text == "Range overlay" }
                    val midpoint = switches.single { it.text == "Midpoint guide" }
                    assertEquals(76, snap.height)
                    assertEquals(56, overlay.height)
                    assertEquals(56, midpoint.height)
                    assertEquals(overlay.y, midpoint.y)
                    assertEquals(8, midpoint.x - (overlay.x + overlay.width))
                    assertTrue(snap.width > overlay.width * 2)
                    for (switch in listOf(overlay, midpoint)) {
                        val metrics = switch.getFontMetrics(UiTheme.boldFont(12))
                        assertTrue(metrics.stringWidth(switch.text) < switch.width - 80, "${switch.text} must fit at $width px")
                    }
                    val headings = parts.filterIsInstance<JLabel>().map { it.text }
                    assertTrue("Shortcuts" in headings)
                    val scroll = parts.filterIsInstance<JScrollPane>().single { it.name == "configuration-body" }
                    assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scroll.horizontalScrollBarPolicy)
                    assertTrue(scroll.viewport.view.width <= scroll.viewport.width)
                    if (width == 500) assertFalse(scroll.verticalScrollBar.isVisible, "All collapsed settings should fit by default")
                    scroll.verticalScrollBar.value = 0
                    val exit = parts.filterIsInstance<JButton>().single { it.text == "Exit application" }
                    val position = SwingUtilities.convertPoint(exit.parent, exit.location, frame.contentPane)
                    assertTrue(position.y >= 0 && position.y + exit.height <= frame.contentPane.height)
                    assertFalse(parts.filterIsInstance<JButton>().any { it.text in listOf("Apply shortcuts", "Disable snapping") })
                    render(frame, "compact-settings-$width-$height")
                    parts.filterIsInstance<JToggleButton>().single { it.accessibleContext.accessibleName == "Range & position" }.doClick()
                }
                edt { frame.validate() }
                edt {
                    val parts = components(frame)
                    val slider = parts.filterIsInstance<JSlider>().first()
                    assertTrue(slider.width > 50, "Slider needs room at $width px")
                    val scroll = parts.filterIsInstance<JScrollPane>().single { it.name == "configuration-body" }
                    scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum
                    render(frame, "compact-calibration-$width-$height")
                    parts.filterIsInstance<JToggleButton>().single { it.accessibleContext.accessibleName == "Range & position" }.doClick()
                    window.updateControls(AppSettings(), false, false, SettingsFeedback(shortcutErrors = mapOf(
                        ShortcutAction.DISABLE_SNAPPING to "Shortcut unavailable (Win32 1409). Another application uses this combination. Choose another combination to enable cursor snapping.")))
                    frame.validate()
                }
                edt {
                    frame.validate()
                    val parts = components(frame)
                    val error = parts.filterIsInstance<WrappedText>().single { it.text.startsWith("Shortcut unavailable") }
                    assertTrue(error.height >= error.preferredHeight(error.width), "Error text must fit at $width px")
                    val scroll = parts.filterIsInstance<JScrollPane>().single { it.name == "configuration-body" }
                    scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum
                    render(frame, "compact-shortcut-error-$width-$height")
                    window.updateControls(AppSettings(), false, true, SettingsFeedback())
                }
            }
        } finally { edt { window.close() } }
    }

    @Test fun `switch state refresh changes appearance without firing callbacks and focus stays visible`() {
        data class Appearance(val name: String, val settings: AppSettings, val snapping: Boolean, val available: Boolean)
        val saved = mutableListOf<AppSettings>()
        lateinit var window: SettingsWindow
        lateinit var frame: JFrame
        edt { window = create(saved); frame = frame(window); frame.pack() }
        try {
            for ((width, height) in listOf(420 to 420, 500 to 600)) {
                for ((name, settings, snapEnabled, available) in listOf(
                    Appearance("off", AppSettings(), false, true),
                    Appearance("on", AppSettings(overlayEnabled = true, dashedTarget = true), true, true),
                    Appearance("disabled", AppSettings(), false, false))) {
                    edt {
                        frame.setSize(width, height)
                        window.updateControls(settings, snapEnabled, available, SettingsFeedback())
                        frame.validate()
                    }
                    edt {
                        frame.validate()
                        val scroll = components(frame).filterIsInstance<JScrollPane>().single()
                        scroll.verticalScrollBar.value = 0
                        render(frame, "switches-$name-$width-$height")
                    }
                }
                edt {
                    window.updateControls(AppSettings(), false, true, SettingsFeedback())
                    frame.validate()
                    val switch = components(frame).filterIsInstance<SettingSwitch>().single { it.text == "Cursor snapping" }
                    val previous = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    val focus = object : DefaultKeyboardFocusManager() {
                        fun focus(component: Component) { setGlobalFocusOwner(component) }
                    }
                    try {
                        KeyboardFocusManager.setCurrentKeyboardFocusManager(focus)
                        focus.focus(switch)
                        assertTrue(switch.hasFocus())
                        render(frame, "switches-focused-$width-$height")
                    } finally {
                        focus.focus(frame)
                        KeyboardFocusManager.setCurrentKeyboardFocusManager(previous)
                    }
                }
            }
            assertTrue(saved.isEmpty(), "Refreshing switch state must not apply settings again")
        } finally { edt { window.close() } }
    }
    @Test fun `closing and minimizing to tray preserve snapping and restoration clears minimized state`() {
        edt {
            for (snapEnabled in listOf(false, true)) {
                val requests = mutableListOf<Boolean>()
                val settings = AppSettings()
                val window = SettingsWindow(settings,
                    ScreenBounds(0, 0, 1920, 1080),
                    {}, { requests += it }, {}, { CompletableFuture.completedFuture(Unit) }, { false })
                val frame = frame(window)
                try {
                    window.updateControls(settings, snapEnabled, true, SettingsFeedback())
                    window.setMinimizeToTray(true)
                    window.show()
                    frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
                    assertFalse(frame.isVisible)
                    window.show()
                    frame.windowListeners.forEach {
                        it.windowIconified(WindowEvent(frame, WindowEvent.WINDOW_ICONIFIED))
                    }
                    assertFalse(frame.isVisible)
                    frame.extendedState = java.awt.Frame.ICONIFIED
                    window.toggleVisibility()
                    assertTrue(frame.isVisible)
                    assertEquals(0, frame.extendedState and java.awt.Frame.ICONIFIED)
                    assertEquals(snapEnabled, components(frame).filterIsInstance<SettingSwitch>()
                        .single { it.text == "Cursor snapping" }.isSelected)
                    assertTrue(requests.isEmpty(), "Hiding and restoring must not request snapping changes")
                } finally { window.close() }
            }
        }
    }

    @Test fun `without tray registration iconification leaves the window available`() {
        edt {
            val window = create(mutableListOf())
            try {
                window.show()
                val frame = frame(window)
                frame.windowListeners.forEach {
                    it.windowIconified(WindowEvent(frame, WindowEvent.WINDOW_ICONIFIED))
                }
                assertTrue(frame.isVisible)
                frame.extendedState = java.awt.Frame.ICONIFIED
                window.toggleVisibility()
                assertEquals(0, frame.extendedState and java.awt.Frame.ICONIFIED)
                assertTrue(frame.isVisible)
            } finally { window.close() }
        }
    }

    @Test fun `range starts folded prevents edits and retains expansion across hiding`() {
        edt {
            val saved = mutableListOf<AppSettings>()
            val window = create(saved)
            try {
                val frame = frame(window)
                val parts = components(frame)
                val range = parts.filterIsInstance<JPanel>().single { it.name == "range-controls" }
                val header = parts.filterIsInstance<JToggleButton>().single { it.accessibleContext.accessibleName == "Range & position" }
                val field = parts.filterIsInstance<JTextField>().single { it.accessibleContext.accessibleName == "Inner horizontal in pixels" }
                assertFalse(range.isVisible)
                assertFalse(field.isEnabled)
                assertFalse(field.isFocusable)
                field.text = "180"; field.postActionEvent()
                assertTrue(saved.isEmpty())
                header.doClick()
                assertTrue(range.isVisible)
                assertTrue(field.isFocusable)
                field.postActionEvent()
                assertEquals(180.0, saved.single().innerX)
                window.show()
                frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
                window.show()
                assertTrue(range.isVisible)
                assertTrue(header.isSelected)
                header.doClick()
                assertFalse(field.isFocusable)
            } finally { window.close() }
        }
    }

    @Test fun `cancelling during pause restores once and retains registration errors after acknowledgement`() {
        val events = mutableListOf<Boolean>()
        val saved = mutableListOf<AppSettings>()
        val paused = CompletableFuture<Unit>()
        val restored = CompletableFuture<Unit>()
        lateinit var window: SettingsWindow
        lateinit var button: JButton
        lateinit var otherButton: JButton
        lateinit var snapping: SettingSwitch
        edt {
            window = create(saved, { false }) { active ->
                events += active
                if (active) paused else restored
            }
            window.updateControls(AppSettings(), false, true, SettingsFeedback())
            val parts = components(frame(window))
            button = parts.filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Overlay visibility shortcut" }
            otherButton = parts.filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == "Toggle snapping shortcut" }
            snapping = parts.filterIsInstance<SettingSwitch>().single { it.text == "Cursor snapping" }
            button.doClick()
            frame(window).windowListeners.forEach {
                it.windowDeactivated(WindowEvent(frame(window), WindowEvent.WINDOW_DEACTIVATED))
            }
            release(window)
            release(window)
            assertEquals(listOf(true, false), events)
            assertFalse(otherButton.isEnabled)
            assertFalse(snapping.isEnabled)
        }
        try {
            paused.complete(Unit)
            edt {
                assertEquals("Ctrl + Alt + F8", button.text)
                window.updateControls(AppSettings(), false, true, SettingsFeedback(
                    shortcutErrors = mapOf(ShortcutAction.TOGGLE_OVERLAY to "Shortcut unavailable (Win32 1409)."),
                ))
            }
            restored.complete(Unit)
            edt {
                assertTrue(saved.isEmpty())
                assertTrue(otherButton.isEnabled)
                assertTrue(snapping.isEnabled)
                assertFalse(snapping.accessibleContext.accessibleDescription.contains("Finish recording"))
                assertEquals("error", button.getClientProperty("JComponent.outline"))
                assertTrue(components(frame(window)).filterIsInstance<WrappedText>()
                    .any { it.isVisible && it.text.contains("Shortcut unavailable") })
            }
        } finally { edt { window.close() } }
    }

    // Feed the validated capture result without depending on OS window activation in tests.
    private fun accept(window: SettingsWindow, candidate: ShortcutBinding) {
        val editor = SettingsWindow::class.java.getDeclaredField("recording").apply { isAccessible = true }.get(window)
        SettingsWindow::class.java.getDeclaredField("releaseKey").apply { isAccessible = true }.set(window, candidate.key)
        SettingsWindow::class.java.declaredMethods.single { it.name == "acceptBinding" }
            .apply { isAccessible = true }.invoke(window, editor, candidate)
    }

    private fun release(window: SettingsWindow) {
        SettingsWindow::class.java.getDeclaredMethod("finishWhenReleased").apply { isAccessible = true }.invoke(window)
    }

    @Test fun `shortcut and snap trigger changes apply after release before restoring registrations`() {
        for ((name, candidate) in listOf("Overlay visibility shortcut" to ShortcutBinding(3, 118),
            "Snap trigger shortcut" to ShortcutBinding(0, 70))) {
            val events = mutableListOf<String>()
            val saved = mutableListOf<AppSettings>()
            var held = true
            lateinit var window: SettingsWindow
            edt {
                window = SettingsWindow(AppSettings(),
                    ScreenBounds(0, 0, 1920, 1080),
                    { saved += it; events += "save" }, {}, {}, { active ->
                        events += if (active) "pause" else "restore"
                        CompletableFuture.completedFuture(Unit)
                    }, { held })
                components(frame(window)).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == name }.doClick()
            }
            try {
                edt {
                    accept(window, candidate)
                    release(window)
                    assertTrue(saved.isEmpty())
                    assertEquals(listOf("pause"), events)
                    held = false; release(window)
                    assertEquals(listOf("pause", "save", "restore"), events)
                    if (name.startsWith("Snap")) assertEquals(70, saved.single().snapKey)
                    else assertEquals(candidate, saved.single().hotkeys.visibility)
                }
                edt {
                    val button = components(frame(window)).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == name }
                    assertTrue(button.isEnabled)
                    window.updateControls(saved.single(), false, true, SettingsFeedback())
                    assertEquals(org.poe2.shortcuts.ShortcutLabels.bindingText(candidate), button.text)
                }
            } finally { edt { window.close() } }
        }
    }

    @Test fun `duplicate and trigger conflicts retain previous shortcuts and show explanations`() {
        for ((name, candidate, expected) in listOf(
            Triple("Overlay visibility shortcut", ShortcutBinding(3, 120), "Ctrl + Alt + F8"),
            Triple("Overlay visibility shortcut", ShortcutBinding(3, 69), "Ctrl + Alt + F8"),
            Triple("Snap trigger shortcut", ShortcutBinding(0, 119), "E"))) {
            val saved = mutableListOf<AppSettings>()
            var held = true
            lateinit var window: SettingsWindow
            lateinit var button: JButton
            edt {
                window = create(saved, { held })
                button = components(frame(window)).filterIsInstance<JButton>().single { it.accessibleContext.accessibleName == name }
                button.doClick()
            }
            try {
                edt {
                    accept(window, candidate)
                    assertEquals(expected, button.text)
                    held = false; release(window)
                }
                edt {
                    assertTrue(saved.isEmpty())
                    assertTrue(button.isEnabled)
                    assertEquals(expected, button.text)
                    assertTrue(components(frame(window)).filterIsInstance<WrappedText>()
                        .any { it.isVisible && it.text.contains("Previous shortcut kept") })
                }
            } finally { edt { window.close() } }
        }
    }

    @Test fun `registration conflicts remain visible and prevent snapping when emergency is unavailable`() {
        edt {
            val window = create(mutableListOf())
            try {
                window.updateControls(AppSettings(), false, false, SettingsFeedback(shortcutErrors = mapOf(ShortcutAction.DISABLE_SNAPPING to "Shortcut unavailable (Win32 1409).")))
                val parts = components(frame(window))
                assertTrue(parts.filterIsInstance<WrappedText>().any { it.isVisible && it.text.contains("Shortcut unavailable") })
                val snapping = parts.filterIsInstance<SettingSwitch>().single { it.text == "Cursor snapping" }
                assertFalse(snapping.isEnabled)
                assertTrue(snapping.accessibleContext.accessibleDescription.contains("emergency shortcut"))
                assertTrue(parts.filterIsInstance<WrappedText>().any { it.isVisible && it.text == "Emergency shortcut unavailable. Change it below." })
                window.updateControls(AppSettings(), false, true, SettingsFeedback())
                assertTrue(snapping.isEnabled)
                assertFalse(parts.filterIsInstance<WrappedText>().any { it.isVisible && it.text.contains("Shortcut unavailable") })
            } finally { window.close() }
        }
    }

    private fun render(frame: JFrame, name: String) {
        val image = BufferedImage(frame.contentPane.width, frame.contentPane.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try { frame.contentPane.printAll(graphics) } finally { graphics.dispose() }
        val directory = Path.of("build", "ui-preview")
        Files.createDirectories(directory)
        ImageIO.write(image, "png", directory.resolve("$name.png").toFile())
    }
}
