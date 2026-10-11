package org.poe2.ui

import java.awt.Dimension
import javax.swing.JTextArea

/** Calculates its wrapped height during layout instead of painting text into a fixed-height row. */
open class WrappedText(text: String = "") : JTextArea(text) {
    init {
        isEditable = false; isFocusable = false; lineWrap = true; wrapStyleWord = true
        isOpaque = false; border = null
        font = UiTheme.bodyFont
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        val widthChanged = width != this.width
        super.setBounds(x, y, width, height)
        if (widthChanged) revalidate()
    }

    override fun getPreferredSize(): Dimension {
        val available = if (width > 0) width else 280
        return Dimension(available, preferredHeight(available))
    }

    fun preferredHeight(available: Int): Int {
        val view = getUI().getRootView(this)
        view.setSize((available - insets.left - insets.right).coerceAtLeast(1).toFloat(), Float.MAX_VALUE)
        return kotlin.math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS).toDouble()).toInt() + insets.top + insets.bottom
    }
}
