package org.poe2.ui

import java.awt.*
import java.awt.event.KeyEvent
import javax.swing.*

/** Paints the switch and its label on one standard button, keeping all input on one target. */
class SettingSwitch(title: String, description: String, selected: Boolean = false, large: Boolean = false) :
    JToggleButton(title, selected) {

    private val style = if (large) prominentStyle else compactStyle

    init {
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isRolloverEnabled = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = description
        getAccessibleContext().accessibleName = title
        getAccessibleContext().accessibleDescription = description
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed")
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released")
    }

    override fun getPreferredSize() = Dimension(style.preferredWidth, style.height)
    override fun getMinimumSize() = Dimension(style.minimumWidth, style.height)

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = when {
                !isEnabled -> Color(239, 242, 244)
                model.isPressed && model.isArmed -> if (isSelected) Color(204, 229, 221) else Color(227, 233, 236)
                isSelected -> if (model.isRollover) Color(220, 239, 232) else Color(231, 245, 239)
                model.isRollover -> Color(240, 244, 246)
                else -> Color.WHITE
            }
            g.fillRoundRect(0, 0, width - 1, height - 1, 12, 12)
            g.color = when {
                hasFocus() -> UiTheme.accent
                isEnabled && isSelected -> UiTheme.accent
                else -> UiTheme.border
            }
            g.stroke = BasicStroke(if (hasFocus()) 2f else 1f)
            g.drawRoundRect(2, 2, width - 5, height - 5, 10, 10)

            val padding = style.padding
            g.font = style.titleFont
            g.color = if (isEnabled) UiTheme.text else UiTheme.muted
            g.drawString(text, padding, style.titleBaseline)
            g.font = style.statusFont
            g.color = if (isEnabled && isSelected) UiTheme.accent else UiTheme.muted
            g.drawString(if (isSelected) "ON" else "OFF", padding, style.statusBaseline)

            val trackWidth = style.trackWidth
            val trackHeight = style.trackHeight
            val trackX = width - padding - trackWidth
            val trackY = (height - trackHeight) / 2
            g.color = if (isEnabled && isSelected) UiTheme.accent else Color(150, 162, 170)
            g.fillRoundRect(trackX, trackY, trackWidth, trackHeight, trackHeight, trackHeight)
            val thumbSize = trackHeight - 6
            val thumbX = trackX + if (isSelected) trackWidth - thumbSize - 3 else 3
            g.color = if (isEnabled) Color.WHITE else Color(224, 229, 233)
            g.fillOval(thumbX, trackY + 3, thumbSize, thumbSize)
        } finally { g.dispose() }
    }

    private data class SwitchStyle(
        val preferredWidth: Int,
        val minimumWidth: Int,
        val height: Int,
        val padding: Int,
        val titleFont: Font,
        val statusFont: Font,
        val titleBaseline: Int,
        val statusBaseline: Int,
        val trackWidth: Int,
        val trackHeight: Int,
    )

    private companion object {
        val compactStyle = SwitchStyle(
            preferredWidth = 184, minimumWidth = 176, height = 56, padding = 12,
            titleFont = UiTheme.boldFont(12), statusFont = UiTheme.boldFont(11),
            titleBaseline = 22, statusBaseline = 43, trackWidth = 44, trackHeight = 24,
        )
        val prominentStyle = SwitchStyle(
            preferredWidth = 400, minimumWidth = 300, height = 76, padding = 16,
            titleFont = UiTheme.boldFont(18), statusFont = UiTheme.boldFont(12),
            titleBaseline = 34, statusBaseline = 59, trackWidth = 72, trackHeight = 36,
        )
    }
}
