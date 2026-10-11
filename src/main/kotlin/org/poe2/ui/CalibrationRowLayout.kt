package org.poe2.ui

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/** Keeps numeric controls usable while stacking the label in narrower windows. */
internal class CalibrationRowLayout(
    private val label: Component,
    private val slider: Component,
    private val controls: Component,
    private val error: Component,
    private val wideLayout: () -> Boolean,
) : LayoutManager {
    override fun addLayoutComponent(name: String?, component: Component?) = Unit
    override fun removeLayoutComponent(component: Component?) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension {
        val insets = parent.insets
        val rowHeight = maxOf(slider.preferredSize.height, controls.preferredSize.height)
        val labelHeight = if (wideLayout()) 0 else label.preferredSize.height + 3
        val errorHeight = if (error.isVisible) error.preferredSize.height + 3 else 0
        val labelWidth = if (wideLayout()) 130 + 8 else 0
        return Dimension(insets.left + insets.right + labelWidth + slider.preferredSize.width + 8 + controls.preferredSize.width,
            insets.top + insets.bottom + labelHeight + rowHeight + errorHeight)
    }

    override fun minimumLayoutSize(parent: Container) = preferredLayoutSize(parent).apply { width = 300 }

    override fun layoutContainer(parent: Container) {
        val insets = parent.insets
        val width = (parent.width - insets.left - insets.right).coerceAtLeast(0)
        var y = insets.top
        val rowHeight = maxOf(slider.preferredSize.height, controls.preferredSize.height)
        val wide = wideLayout()
        val labelWidth = if (wide) 130 else 0
        if (wide) label.setBounds(insets.left, y, labelWidth, rowHeight)
        else {
            label.setBounds(insets.left, y, width, label.preferredSize.height)
            y += label.preferredSize.height + 3
        }
        val controlWidth = controls.preferredSize.width.coerceAtMost(width)
        val sliderX = insets.left + if (wide) labelWidth + 8 else 0
        val controlX = insets.left + width - controlWidth
        slider.setBounds(sliderX, y, (controlX - sliderX - 8).coerceAtLeast(0), rowHeight)
        controls.setBounds(controlX, y, controlWidth, rowHeight)
        if (error.isVisible) error.setBounds(insets.left, y + rowHeight + 3, width, error.preferredSize.height)
    }
}
