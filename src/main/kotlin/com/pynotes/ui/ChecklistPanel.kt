package com.pynotes.ui

import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import com.pynotes.model.ChecklistItem
import com.pynotes.model.Note
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Authentic Apple Notes & Google Keep Checklist experience.
 *
 * Design features:
 * - Direct top-aligned layout (never vertically centered).
 * - Circular Apple Notes checkboxes with smooth check paths.
 * - Direct inline "+ First task" or "+ List item" row right beneath tasks.
 * - Type directly onto the placeholder without deleting anything.
 * - Enter automatically creates a new item below.
 * - Backspace on an empty item removes it and focuses the previous item.
 * - Responsive: fills available panel width dynamically.
 */
class ChecklistPanel(
    private val note: Note,
    private val onSave: () -> Unit
) : JPanel() {

    private val activeItemsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val completedItemsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private var showCompleted = true
    private val completedHeaderBtn = PillButton("Completed (0)", PillButton.Kind.GHOST, AllIcons.General.ArrowDown) {
        showCompleted = !showCompleted
        completedItemsPanel.isVisible = showCompleted
        updateCompletedHeader()
    }

    private val completedHeaderRow = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(12, 0, 4, 0)
        alignmentX = Component.LEFT_ALIGNMENT
        add(completedHeaderBtn, BorderLayout.WEST)
    }

    private val itemTextFields = mutableListOf<JTextField>()

    private val addItemRow = createAddItemRow()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentY = Component.TOP_ALIGNMENT

        add(activeItemsPanel)
        add(addItemRow)
        add(completedHeaderRow)
        add(completedItemsPanel)

        rebuildItems()
    }

    private fun createAddItemRow(): JPanel {
        val row = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 2)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val addIcon = RoundCheck(checked = false, box = 18) {}
        row.add(addIcon, BorderLayout.WEST)

        val inputField = borderlessField(placeholder = "List item", font = Theme.font(14f)).apply {
            foreground = Theme.textPrimary
            addActionListener {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    note.checklistItems.add(ChecklistItem(text = t, isDone = false))
                    text = ""
                    onSave()
                    rebuildItems()
                    requestFocusInWindow()
                }
            }
        }
        row.add(inputField, BorderLayout.CENTER)
        return row
    }

    fun rebuildItems() {
        activeItemsPanel.removeAll()
        completedItemsPanel.removeAll()
        itemTextFields.clear()

        var completedCount = 0

        note.checklistItems.forEachIndexed { index, item ->
            val row = createItemRow(item, index)
            if (item.isDone) {
                completedItemsPanel.add(row)
                completedCount++
            } else {
                activeItemsPanel.add(row)
            }
        }

        // When checklist is completely empty, the add row acts as "First task"
        // Otherwise it acts as the trailing "+ List item"
        val placeholder = if (note.checklistItems.isEmpty()) "First task" else "List item"
        (addItemRow.getComponent(1) as? JTextField)?.let {
            if (it is com.intellij.ui.components.JBTextField) {
                it.emptyText.text = placeholder
            }
        }

        completedHeaderRow.isVisible = completedCount > 0
        completedItemsPanel.isVisible = showCompleted && completedCount > 0
        updateCompletedHeader()

        revalidate()
        repaint()
    }

    private fun updateCompletedHeader() {
        val count = note.checklistItems.count { it.isDone }
        val arrow = if (showCompleted) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        completedHeaderBtn.text = "Completed ($count)"
        completedHeaderBtn.icon = arrow
    }

    private fun createItemRow(item: ChecklistItem, index: Int): JPanel {
        val row = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 2)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // Circular Apple Notes checkbox
        val check = RoundCheck(item.isDone, box = 18) { done ->
            item.isDone = done
            onSave()
            rebuildItems()
        }
        row.add(check, BorderLayout.WEST)

        // Text field with strikethrough if done
        val regularFont = Theme.font(14f)
        val struckFont = regularFont.struck()

        val textField = borderlessField(item.text, font = if (item.isDone) struckFont else regularFont).apply {
            foreground = if (item.isDone) Theme.textMuted else Theme.textPrimary

            onTextChange { item.text = text; onSave() }
            addKeyListener(object : KeyAdapter() {

                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        e.consume()
                        val newIndex = note.checklistItems.indexOf(item) + 1
                        note.checklistItems.add(newIndex, ChecklistItem(text = "", isDone = false))
                        onSave()
                        rebuildItems()
                        SwingUtilities.invokeLater {
                            if (newIndex < itemTextFields.size) {
                                itemTextFields[newIndex].requestFocusInWindow()
                            }
                        }
                    } else if (e.keyCode == KeyEvent.VK_BACK_SPACE && text.isEmpty()) {
                        e.consume()
                        val currIndex = note.checklistItems.indexOf(item)
                        note.checklistItems.remove(item)
                        onSave()
                        rebuildItems()
                        SwingUtilities.invokeLater {
                            val prevIndex = (currIndex - 1).coerceAtLeast(0)
                            if (prevIndex < itemTextFields.size) {
                                val prevField = itemTextFields[prevIndex]
                                prevField.requestFocusInWindow()
                                prevField.caretPosition = prevField.text.length
                            }
                        }
                    }
                }
            })
        }
        itemTextFields.add(textField)
        row.add(textField, BorderLayout.CENTER)

        // Hover-revealed delete button
        val delBtn = IconButton(AllIcons.Actions.Close, "Remove item", box = 22) {
            note.checklistItems.remove(item)
            onSave()
            rebuildItems()
        }.apply {
            revealed = false
        }
        row.add(delBtn, BorderLayout.EAST)

        installHover(row) { hovered ->
            delBtn.revealed = hovered
        }

        return row
    }
}
