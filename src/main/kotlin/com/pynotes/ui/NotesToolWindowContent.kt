package com.pynotes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.pynotes.model.Note
import com.pynotes.model.NoteType
import com.pynotes.storage.NotesStorageService
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

/**
 * Modern Apple Notes & Google Keep master tool window content.
 */
class NotesToolWindowContent(
    private val project: Project
) : JPanel(BorderLayout()), NotesStorageService.NotesChangeListener, Disposable {

    companion object {
        private const val CARD_LIST = "LIST"
        private const val CARD_DETAIL = "DETAIL"
        private val WHITESPACE_REGEX = Regex("\\s+")
    }

    private val cardLayout = CardLayout()
    private val cardsPanel = JPanel(cardLayout).apply {
        isOpaque = true
        background = JBColor.PanelBackground
    }

    private val searchField: SearchTextField
    private val tagsFilterPanel = JPanel(WrapLayout(FlowLayout.LEFT, 0, JBUI.scale(4))).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(4, 0, 4, 0)
    }

    private var viewMode: NotesViewMode = NotesViewMode.GRID

    private val notesGrid = StackPanel(maxColumns = 2, minColumnWidth = 260, gap = 8).apply {
        border = JBUI.Borders.empty(4, 0, 12, 0)
    }

    private val pinFilterBtn: IconButton
    private val viewModeBtn: IconButton
    private val detailHolder = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = JBColor.PanelBackground
    }

    private var currentSearchText = ""
    private var selectedTag = "ALL"
    private var displayedTagsSnapshot: List<String>? = null
    private var displayedSelectedTag: String? = null
    private var showOnlyPinned = false
    private var editingNote: Note? = null
    private var detailView: NoteDetailView? = null
    private val searchDelay = Debouncer(140) { refreshNotesList() }
    private var scope = "All"
    private var sort = "Recently edited"
    private val scrollPane: JBScrollPane
    private val cardCache = mutableMapOf<String, Pair<String, NoteCardPanel>>()

    private fun getOrCreateCard(note: Note): NoteCardPanel {
        val key = "${note.id}:${note.updatedAt}:${note.isPinned}:${note.color.name}:${note.title}:${note.content.hashCode()}:${note.tags.joinToString(",")}:${viewMode.name}"
        val cached = cardCache[note.id]
        if (cached != null && cached.first == key) {
            return cached.second
        }
        val card = NoteCardPanel(project, note, ::openNoteDetail, viewMode)
        cardCache[note.id] = key to card
        return card
    }

    init {
        isOpaque = true
        background = JBColor.PanelBackground

        // Master List View
        val listViewPanel = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            isOpaque = true
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(8, 8, 4, 8)
        }

        val topHeader = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty()
        }

        // Action Row (+ Note, + Checklist, View mode, Pin filter, Refresh, Restore)
        val actionRow = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyBottom(6)
        }

        val buttonsLeft = JPanel(WrapLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }

        val newNoteBtn = PillButton("Note", PillButton.Kind.PRIMARY, AllIcons.General.Add) {
            createNewNote(NoteType.TEXT)
        }
        val newChecklistBtn = PillButton("Checklist", PillButton.Kind.SECONDARY, NotesIcons.CHECKLIST) {
            createNewNote(NoteType.CHECKLIST)
        }
        buttonsLeft.add(newNoteBtn)
        buttonsLeft.add(Box.createHorizontalStrut(JBUI.scale(6)))
        buttonsLeft.add(newChecklistBtn)
        actionRow.add(buttonsLeft, BorderLayout.CENTER)

        val buttonsRight = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(1), 0)).apply {
            isOpaque = false
        }

        viewModeBtn = IconButton(
            if (viewMode == NotesViewMode.GRID) AllIcons.Actions.GroupBy else AllIcons.Actions.ListFiles,
            if (viewMode == NotesViewMode.GRID) "Switch to compact list view" else "Switch to grid card view",
            box = 26
        ) {
            viewMode = if (viewMode == NotesViewMode.GRID) NotesViewMode.LIST else NotesViewMode.GRID
            viewModeBtn.icon = if (viewMode == NotesViewMode.GRID) AllIcons.Actions.GroupBy else AllIcons.Actions.ListFiles
            viewModeBtn.tooltip = if (viewMode == NotesViewMode.GRID) "Switch to compact list view" else "Switch to grid card view"
            notesGrid.maxColumns = if (viewMode == NotesViewMode.LIST) 1 else 2
            notesGrid.gap = if (viewMode == NotesViewMode.LIST) 4 else 8
            cardCache.clear()
            refreshNotesList()
        }
        buttonsRight.add(viewModeBtn)

        pinFilterBtn = IconButton(
            NotesIcons.PIN,
            "Filter: Pinned Only",
            box = 26
        ) {
            showOnlyPinned = !showOnlyPinned
            pinFilterBtn.icon = if (showOnlyPinned) NotesIcons.PINNED else NotesIcons.PIN
            pinFilterBtn.tooltip = if (showOnlyPinned) "Show All Notes" else "Filter: Pinned Only"
            refreshNotesList()
        }
        buttonsRight.add(pinFilterBtn)

        val refreshBtn = IconButton(AllIcons.Actions.Refresh, "Reload notes from disk", box = 26) {
            NotesStorageService.getInstance(project).reloadAllNotes()
        }
        buttonsRight.add(refreshBtn)

        val undoBtn = IconButton(AllIcons.Actions.Undo, "Restore last deleted note", box = 26) {
            val restored = NotesStorageService.getInstance(project).restoreLastDeleted()
            if (!restored) JOptionPane.showMessageDialog(this, "No deleted notes to restore.", "Restore note", JOptionPane.INFORMATION_MESSAGE)
        }
        buttonsRight.add(undoBtn)

        actionRow.add(buttonsRight, BorderLayout.EAST)

        // Search Bar Row
        val searchRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.emptyBottom(4)
        }

        searchField = SearchTextField().apply {
            textEditor.emptyText.text = "Search notes, tags, checklists... (Ctrl+F)"
            textEditor.font = Theme.font(12f)
            addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    currentSearchText = text.trim().lowercase()
                    searchDelay.poke()
                }
            })
        }
        searchRow.add(searchField, BorderLayout.CENTER)

        // Scope and Sort Row
        val scopeSortRow = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(2, 0, 4, 0)
        }

        val scopeControl = SegmentedControl(listOf("All", "Project", "Global"), 0) { _, name ->
            scope = name
            refreshNotesList()
        }
        scopeSortRow.add(scopeControl, BorderLayout.CENTER)

        val sortMenuBtn = IconButton(SortIcon(), "Sort: $sort", box = 26) {
            val menu = JPopupMenu()
            listOf("Recently edited", "Newest first", "Title A-Z").forEach { s ->
                val item = JCheckBoxMenuItem(s, s == sort).apply {
                    addActionListener {
                        sort = s
                        refreshNotesList()
                    }
                }
                menu.add(item)
            }
            menu.show(this, 0, height)
        }
        scopeSortRow.add(sortMenuBtn, BorderLayout.EAST)

        topHeader.add(actionRow)
        topHeader.add(searchRow)
        topHeader.add(scopeSortRow)
        topHeader.add(tagsFilterPanel)

        // Shortcut: Ctrl+F to find notes
        val shortcutMask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask), "findNotes")
        actionMap.put("findNotes", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (editingNote == null) { searchField.textEditor.requestFocusInWindow(); searchField.textEditor.selectAll() }
            }
        })

        // Shortcut: Ctrl+N to create a new note
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask), "newNote")
        actionMap.put("newNote", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (editingNote == null) { createNewNote(NoteType.TEXT) }
            }
        })

        listViewPanel.add(topHeader, BorderLayout.NORTH)

        scrollPane = JBScrollPane(notesGrid).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 16
            viewport.isOpaque = true
            viewport.background = JBColor.PanelBackground
            isOpaque = true
            background = JBColor.PanelBackground
        }
        listViewPanel.add(scrollPane, BorderLayout.CENTER)

        cardsPanel.add(listViewPanel, CARD_LIST)
        cardsPanel.add(detailHolder, CARD_DETAIL)
        add(cardsPanel, BorderLayout.CENTER)

        NotesStorageService.getInstance(project).addChangeListener(this)

        refreshTags()
        refreshNotesList()
    }

    private fun createNewNote(type: NoteType) {
        val note = Note(
            title = "",
            type = type
        )
        NotesStorageService.getInstance(project).saveNote(note)
        openNoteDetail(note)
    }

    fun openNoteDetail(note: Note) {
        detailView?.dispose()
        editingNote = note
        detailHolder.removeAll()
        val view = NoteDetailView(project, note, { backToList() })
        detailView = view
        detailHolder.add(view, BorderLayout.CENTER)
        detailHolder.revalidate()
        detailHolder.repaint()
        cardLayout.show(cardsPanel, CARD_DETAIL)
        view.focusEditor()
    }

    private fun backToList() {
        detailView?.dispose()
        detailView = null
        detailHolder.removeAll()
        editingNote = null
        cardLayout.show(cardsPanel, CARD_LIST)
        refreshTags()
        refreshNotesList()
    }

    private fun refreshTags() {
        val allTags = NotesStorageService.getInstance(project).getAllTags().toList()
        if (displayedTagsSnapshot == allTags && displayedSelectedTag == selectedTag) {
            return
        }
        displayedTagsSnapshot = allTags
        displayedSelectedTag = selectedTag

        tagsFilterPanel.removeAll()

        val allChip = Chip("All", selected = selectedTag == "ALL", marginRight = 6).apply {
            onAction = {
                selectedTag = "ALL"
                refreshTags()
                refreshNotesList()
            }
        }
        tagsFilterPanel.add(allChip)

        val allNotes = NotesStorageService.getInstance(project).getAllNotes()
        allTags.forEach { tag ->
            val count = allNotes.count { note -> note.tags.any { it.equals(tag, ignoreCase = true) } }
            val label = if (count > 0) "#$tag ($count)" else "#$tag"
            val isSelected = tag.equals(selectedTag, ignoreCase = true)
            val chip = Chip(label, selected = isSelected, marginRight = 6).apply {
                onAction = {
                    selectedTag = if (isSelected) "ALL" else tag.lowercase()
                    refreshTags()
                    refreshNotesList()
                }
            }
            tagsFilterPanel.add(chip)
        }

        tagsFilterPanel.revalidate()
        tagsFilterPanel.repaint()
    }

    private fun refreshNotesList() {
        notesGrid.removeAll()

        val allNotes = NotesStorageService.getInstance(project).getAllNotes()
        val activeIds = allNotes.mapTo(HashSet()) { it.id }
        cardCache.keys.retainAll(activeIds)
        val queryTokens = if (currentSearchText.isEmpty()) emptyList() else currentSearchText.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }

        val filtered = allNotes.filter { note ->
            if (scope == "Project" && note.isGlobal || scope == "Global" && !note.isGlobal) return@filter false
            if (showOnlyPinned && !note.isPinned) return@filter false

            if (selectedTag != "ALL") {
                if (!note.tags.any { it.equals(selectedTag, ignoreCase = true) }) {
                    return@filter false
                }
            }

            if (queryTokens.isNotEmpty()) {
                val matchString = note.searchableText()
                if (!queryTokens.all { matchString.contains(it) }) {
                    return@filter false
                }
            }
            true
        }.let { result ->
            when (sort) {
                "Title A-Z" -> result.sortedBy { it.displayTitle.lowercase() }
                "Newest first" -> result.sortedByDescending { it.createdAt }
                else -> result
            }
        }

        if (filtered.isEmpty()) {
            val emptyPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.empty(40, 16)
                putClientProperty(StackPanel.FULL_WIDTH, true)
            }

            val iconLabel = JLabel(if (currentSearchText.isEmpty()) NotesIcons.NOTES else AllIcons.Actions.Search).apply {
                alignmentX = Component.CENTER_ALIGNMENT
            }
            emptyPanel.add(iconLabel)
            emptyPanel.add(Box.createVerticalStrut(JBUI.scale(10)))

            val titleText = if (currentSearchText.isEmpty()) "No notes yet" else "No matching notes found"
            val titleLabel = JLabel(titleText).apply {
                alignmentX = Component.CENTER_ALIGNMENT
                foreground = Theme.textPrimary
                font = Theme.font(14f, bold = true)
            }
            emptyPanel.add(titleLabel)

            val subText = if (currentSearchText.isEmpty()) {
                "Create quick notes and checklists, or right-click code to save snippets."
            } else {
                "No notes matched \"$currentSearchText\". Try another keyword or clear search."
            }
            val subLabel = JLabel(subText).apply {
                alignmentX = Component.CENTER_ALIGNMENT
                foreground = Theme.textMuted
                font = Theme.font(11.5f)
                border = JBUI.Borders.emptyTop(4)
            }
            emptyPanel.add(subLabel)
            emptyPanel.add(Box.createVerticalStrut(JBUI.scale(14)))

            if (currentSearchText.isNotEmpty()) {
                val clearBtn = PillButton("Clear search", PillButton.Kind.SECONDARY) {
                    searchField.text = ""
                }.apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                }
                emptyPanel.add(clearBtn)
            } else {
                val actionBtns = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(6), 0)).apply {
                    isOpaque = false
                    alignmentX = Component.CENTER_ALIGNMENT
                    add(PillButton("+ New Note", PillButton.Kind.PRIMARY) { createNewNote(NoteType.TEXT) })
                    add(PillButton("+ New Checklist", PillButton.Kind.SECONDARY) { createNewNote(NoteType.CHECKLIST) })
                }
                emptyPanel.add(actionBtns)
            }

            notesGrid.add(emptyPanel)
        } else {
            val (pinned, others) = filtered.partition { it.isPinned }

            if (pinned.isNotEmpty()) {
                notesGrid.add(createSectionHeader("PINNED (${pinned.size})"))
                pinned.forEach { note ->
                    notesGrid.add(getOrCreateCard(note))
                }
            }

            if (others.isNotEmpty()) {
                if (pinned.isNotEmpty()) {
                    notesGrid.add(createSectionHeader("NOTES (${others.size})"))
                }
                others.forEach { note ->
                    notesGrid.add(getOrCreateCard(note))
                }
            }
        }

        notesGrid.revalidate()
        notesGrid.repaint()
    }

    private fun createSectionHeader(title: String): JComponent {
        val header = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(10, 2, 4, 2)
            putClientProperty(StackPanel.FULL_WIDTH, true)
        }
        val label = JLabel(title).apply {
            font = Theme.font(10f, bold = true)
            foreground = Theme.textMuted
        }
        val divider = JSeparator(SwingConstants.HORIZONTAL).apply {
            foreground = Theme.cardBorder
        }
        header.add(label, BorderLayout.WEST)
        header.add(divider, BorderLayout.CENTER)
        return header
    }

    override fun dispose() {
        detailView?.dispose()
        searchDelay.cancel()
        NotesStorageService.getInstance(project).removeChangeListener(this)
    }

    override fun onNotesChanged() {
        if (editingNote == null) {
            refreshTags()
            refreshNotesList()
        }
    }

    private class SortIcon : Icon {
        override fun getIconWidth() = JBUI.scale(15)
        override fun getIconHeight() = JBUI.scale(15)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 15.0, iconHeight / 15.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawLine(2, 4, 13, 4)
                g.drawLine(2, 8, 9, 8)
                g.drawLine(2, 12, 6, 12)
            } finally { g.dispose() }
        }
    }
}
