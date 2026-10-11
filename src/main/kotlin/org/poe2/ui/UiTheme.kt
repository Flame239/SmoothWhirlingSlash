package org.poe2.ui

import com.formdev.flatlaf.FlatLightLaf
import java.awt.Color
import java.awt.Font
import javax.swing.*

object UiTheme {
    val bodyFont = Font("Segoe UI", Font.PLAIN, 13)
    fun boldFont(size: Int) = bodyFont.deriveFont(Font.BOLD, size.toFloat())

    val page = Color(246, 248, 250)
    val text = Color(31, 41, 46)
    val muted = Color(89, 103, 111)
    val accent = Color(39, 111, 102)
    val error = Color(167, 48, 55)
    val border = Color(218, 225, 229)
    const val OUTER = 16
    const val GAP = 12

    fun install() {
        UIManager.setLookAndFeel(FlatLightLaf())
        UIManager.put("defaultFont", bodyFont)
        UIManager.put("Component.focusColor", accent)
        UIManager.put("Component.accentColor", accent)
        UIManager.put("Component.arc", 10)
        UIManager.put("Button.arc", 10)
        UIManager.put("TextComponent.arc", 8)
        UIManager.put("Slider.thumbColor", accent)
        UIManager.put("Slider.trackValueColor", accent)
    }

    fun heading(label: String, size: Int = 16) = JLabel(label).apply {
        font = boldFont(size); foreground = UiTheme.text
    }

    fun note(text: String) = WrappedText(text).apply { foreground = muted }

    fun outline(component: JComponent, invalid: Boolean) {
        component.putClientProperty("JComponent.outline", if (invalid) "error" else null)
    }
}
