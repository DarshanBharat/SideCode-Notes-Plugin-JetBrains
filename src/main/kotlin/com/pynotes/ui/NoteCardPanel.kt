package com.pynotes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.Messages
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.pynotes.model.Note
import com.pynotes.model.NoteColor
import com.pynotes.model.NoteType
import com.pynotes.storage.NotesStorageService
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import kotlin.math.max

/**
 * Modern, Apple Notes / Google Keep card preview component.
 * Implements [HeightForWidth] for clean masonry or compact list layouts inside [StackPanel].
 */
class NoteCardPanel(
    private val project: Project,
    private val note: Note,
    private val onOpenNote: (Note) -> Unit,
    var viewMode: NotesViewMode = NotesViewMode.GRID
) : JPanel(), HeightForWidth {

    private var isHovered = false
    private val previewFont = Theme.font(12f)
    private val previewLines: List<String>
    private var lastMeasuredWidth = -1
    private var lastMeasuredHeight = 0

    // Action buttons
    private val pinBtn = IconButton(
        if (note.isPinned) NotesIcons.PINNED else NotesIcons.PIN,
        if (note.isPinned) "Unpin Note" else "Pin to Top",
        box = 24
    ) {
        note.isPinned = !note.isPinned
        NotesStorageService.getInstance(project).saveNote(note)
    }.apply { revealed = note.isPinned }

    private val moreBtn = IconButton(
        AllIcons.Actions.More,
        "More Options",
        box = 24
    ) {
        showContextMenu(this, 0, height)
    }.apply { revealed = false }

    private val navigation = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            if (!isHovered) {
                isHovered = true
                updateButtonsVisibility()
                repaint()
            }
        }
        override fun mouseExited(e: MouseEvent) {
            if (isHovered && !isPointerInside(this@NoteCardPanel)) {
                isHovered = false
                updateButtonsVisibility()
                repaint()
            }
        }
        override fun mouseClicked(e: MouseEvent) {
            if (SwingUtilities.isRightMouseButton(e)) {
                showContextMenu(e.component, e.x, e.y)
            } else if (e.clickCount == 2) {
                NotesStorageService.getInstance(project).openNoteInMainEditor(note)
            } else if (SwingUtilities.isLeftMouseButton(e)) {
                onOpenNote(note)
            }
        }
    }

    init {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        val snippet = if (note.type == NoteType.CHECKLIST) {
            note.checklistItems.take(4).joinToString("\n") { (if (it.isDone) "● " else "○ ") + it.text }
        } else {
            note.previewText()
        }
        previewLines = if (snippet.isEmpty()) emptyList() else snippet.lines()

        buildLayout(snippet)

        addMouseListener(navigation)
        isFocusable = true
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "open")
        actionMap.put("open", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) { onOpenNote(note) }
        })
    }

    private fun updateButtonsVisibility() {
        pinBtn.revealed = isHovered || note.isPinned
        moreBtn.revealed = isHovered
    }

    private fun buildLayout(snippet: String) {
        removeAll()
        layout = BorderLayout()

        if (viewMode == NotesViewMode.LIST) {
            buildListLayout(snippet)
        } else {
            buildGridLayout(snippet)
        }
    }

    private fun buildGridLayout(snippet: String) {
        border = JBUI.Borders.empty(10, 12, 10, 12)

        // Top Header Row
        val header = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            addMouseListener(navigation)
        }

        val titleLabel = JLabel(note.displayTitle).apply {
            font = Theme.font(13.5f, bold = true)
            foreground = Theme.textPrimary
            addMouseListener(navigation)
        }
        header.add(titleLabel, BorderLayout.CENTER)

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(1), 0)).apply {
            isOpaque = false
            add(pinBtn)
            add(moreBtn)
        }
        header.add(actions, BorderLayout.EAST)
        add(header, BorderLayout.NORTH)

        // Body Preview
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(6, 0, 4, 0)
            addMouseListener(navigation)
        }

        if (note.type == NoteType.CHECKLIST) {
            val summary = note.getChecklistSummary()
            if (summary.isNotEmpty()) {
                val checkProgress = JLabel("●  $summary").apply {
                    font = Theme.font(11f, bold = true)
                    foreground = note.color.accent
                    border = JBUI.Borders.emptyBottom(4)
                    addMouseListener(navigation)
                }
                body.add(checkProgress)
            }
        }

        if (snippet.isNotEmpty()) {
            val previewLabel = JTextArea(snippet).apply {
                isEditable = false
                isFocusable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                border = null
                font = previewFont
                foreground = Theme.textSecondary
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(navigation)
            }
            body.add(previewLabel)
        }
        add(body, BorderLayout.CENTER)

        // Footer Row
        val footer = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(6)
            addMouseListener(navigation)
        }

        val dateLabel = JLabel(note.getFormattedDate()).apply {
            font = Theme.font(10.5f)
            foreground = Theme.textMuted
            addMouseListener(navigation)
        }
        footer.add(dateLabel, BorderLayout.WEST)

        if (note.tags.isNotEmpty()) {
            val tagChips = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(3), 0)).apply {
                isOpaque = false
                addMouseListener(navigation)
            }
            note.tags.take(3).forEach { tag ->
                val pill = JLabel("#$tag").apply {
                    font = Theme.font(10f, bold = true)
                    foreground = Theme.textSecondary
                    border = JBUI.Borders.empty(2, 6)
                    isOpaque = true
                    background = note.color.pillBg
                    addMouseListener(navigation)
                }
                tagChips.add(pill)
            }
            footer.add(tagChips, BorderLayout.EAST)
        }
        add(footer, BorderLayout.SOUTH)
    }

    private fun buildListLayout(snippet: String) {
        border = JBUI.Borders.empty(8, 12, 8, 10)

        val mainContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            addMouseListener(navigation)
        }

        val titleLabel = JLabel(note.displayTitle).apply {
            font = Theme.font(13f, bold = true)
            foreground = Theme.textPrimary
            alignmentX = Component.LEFT_ALIGNMENT
            addMouseListener(navigation)
        }
        mainContent.add(titleLabel)

        val subText = when {
            note.type == NoteType.CHECKLIST -> {
                val sum = note.getChecklistSummary()
                if (sum.isNotEmpty()) "● $sum" else snippet.replace('\n', ' ')
            }
            snippet.isNotEmpty() -> snippet.lines().firstOrNull { it.isNotBlank() } ?: ""
            else -> ""
        }

        if (subText.isNotEmpty()) {
            val subLabel = JLabel(subText).apply {
                font = Theme.font(11.5f)
                foreground = Theme.textSecondary
                alignmentX = Component.LEFT_ALIGNMENT
                border = JBUI.Borders.emptyTop(2)
                addMouseListener(navigation)
            }
            mainContent.add(subLabel)
        }

        add(mainContent, BorderLayout.CENTER)

        val rightPanel = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            addMouseListener(navigation)
        }

        val dateLabel = JLabel(note.getFormattedDate()).apply {
            font = Theme.font(10f)
            foreground = Theme.textMuted
            addMouseListener(navigation)
        }
        rightPanel.add(dateLabel, BorderLayout.WEST)

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(1), 0)).apply {
            isOpaque = false
            add(pinBtn)
            add(moreBtn)
        }
        rightPanel.add(actions, BorderLayout.EAST)
        add(rightPanel, BorderLayout.EAST)
    }

    override fun heightForWidth(width: Int): Int {
        if (viewMode == NotesViewMode.LIST) {
            val h = JBUI.scale(56)
            lastMeasuredWidth = width
            lastMeasuredHeight = h
            return h
        }
        if (width == lastMeasuredWidth && lastMeasuredHeight > 0) {
            return lastMeasuredHeight
        }
        val available = max(JBUI.scale(60), width - JBUI.scale(28))
        val metrics = getFontMetrics(previewFont)
        val lines = previewLines.sumOf { max(1, (metrics.stringWidth(it) + available - 1) / available) }.coerceAtMost(10)
        val computed = JBUI.scale(96) + lines * metrics.height + if (note.type == NoteType.CHECKLIST) JBUI.scale(20) else 0
        lastMeasuredWidth = width
        lastMeasuredHeight = computed
        return computed
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val arc = JBUI.scale(if (viewMode == NotesViewMode.LIST) 8 else 12).toFloat()
            val shape = RoundRectangle2D.Float(1f, 1f, width - 2f, height - 2f, arc, arc)

            // Fill background with note color pastel
            g2.color = note.color.background
            g2.fill(shape)

            if (isHovered) {
                g2.color = Theme.hover
                g2.fill(shape)
            }

            // Stroke outline: modern card border, accent when hovered or focused
            val borderStroke = if (isHovered || hasFocus()) Theme.accent else Theme.cardBorder
            g2.color = borderStroke
            g2.stroke = if (isHovered || hasFocus()) Theme.STROKE_1_5 else Theme.STROKE_1
            g2.draw(shape)

            // Left accent line if non-default color
            if (!note.color.isDefault) {
                g2.color = note.color.accent
                val barW = JBUIScale.scale(3.5f)
                val padY = if (viewMode == NotesViewMode.LIST) 6f else 10f
                val barShape = RoundRectangle2D.Float(
                    JBUIScale.scale(3.5f),
                    JBUIScale.scale(padY),
                    barW,
                    height - JBUIScale.scale(padY * 2),
                    barW,
                    barW
                )
                g2.fill(barShape)
            }
        }
        super.paintComponent(g)
    }

    private fun showContextMenu(invoker: Component, x: Int, y: Int) {
        val menu = JBPopupMenu()

        menu.add(JMenuItem("Open in Main Coding Area", NotesIcons.OPEN_IN_EDITOR).apply {
            addActionListener { NotesStorageService.getInstance(project).openNoteInMainEditor(note) }
        })

        menu.add(JMenuItem("Edit Note", AllIcons.Actions.Edit).apply {
            addActionListener { onOpenNote(note) }
        })

        menu.add(JMenuItem("Copy Content", AllIcons.Actions.Copy).apply {
            addActionListener {
                val str = if (note.type == NoteType.CHECKLIST) {
                    note.checklistItems.joinToString("\n") { (if (it.isDone) "[x] " else "[ ] ") + it.text }
                } else {
                    note.plainContent()
                }
                val sel = java.awt.datatransfer.StringSelection(str)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            }
        })

        menu.add(JMenuItem(if (note.isPinned) "Unpin Note" else "Pin to Top",
            if (note.isPinned) NotesIcons.PIN else NotesIcons.PINNED).apply {
            addActionListener {
                note.isPinned = !note.isPinned
                NotesStorageService.getInstance(project).saveNote(note)
            }
        })

        val colorMenu = JMenu("Color").apply { icon = NotesIcons.PALETTE }
        NoteColor.entries.forEach { c ->
            colorMenu.add(JMenuItem(c.displayName).apply {
                icon = ColorSwatchIcon(JBUI.scale(12), c.accent)
                addActionListener {
                    NotesStorageService.getInstance(project).setNoteColor(note, c)
                }
            })
        }
        menu.add(colorMenu)

        menu.add(JMenuItem("Duplicate Note", AllIcons.Actions.Copy).apply {
            addActionListener {
                val copy = Note("${note.displayTitle} (Copy)", note.type).apply {
                    isGlobal = note.isGlobal
                    content = note.content
                    color = note.color
                    tags = ArrayList(note.tags)
                    checklistItems = note.checklistItems.map { it.copyItem() }.toMutableList()
                }
                NotesStorageService.getInstance(project).saveNote(copy)
            }
        })

        menu.addSeparator()

        menu.add(JMenuItem("Delete Note", AllIcons.Actions.GC).apply {
            addActionListener {
                val res = Messages.showYesNoDialog(
                    this@NoteCardPanel,
                    "Delete note \"${note.displayTitle}\"?",
                    "Delete Note",
                    Messages.getQuestionIcon()
                )
                if (res == Messages.YES) {
                    NotesStorageService.getInstance(project).deleteNote(note)
                }
            }
        })

        menu.show(invoker, x, y)
    }

    private class ColorSwatchIcon(private val size: Int, private val color: Color) : Icon {
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fillOval(x, y, size, size)
            g2.dispose()
        }
        override fun getIconWidth() = size
        override fun getIconHeight() = size
    }
}
