package com.pynotes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.pynotes.model.Note
import com.pynotes.model.NoteColor
import com.pynotes.model.NoteType
import com.pynotes.storage.NotesStorageService
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.text.StyledEditorKit
import javax.swing.text.html.HTMLEditorKit

/** Shared rich editor. Formatting is serialized as HTML inside the Markdown note body. */
class NoteDetailView(
    private val project: Project,
    note: Note,
    private val onBackToList: () -> Unit,
    private val mainArea: Boolean = false
) : JPanel(BorderLayout()) {
    private val storage = NotesStorageService.getInstance(project)
    private val session = storage.acquireSession(note)
    private val note = session.note

    private val pinBtn: IconButton = IconButton(
        if (this.note.isPinned) NotesIcons.PINNED else NotesIcons.PIN,
        if (this.note.isPinned) "Unpin Note" else "Pin Note",
        box = 26
    )

    private val scopeBtn: IconButton = IconButton(
        if (this.note.isGlobal) AllIcons.General.Web else AllIcons.Nodes.Folder,
        if (this.note.isGlobal) "Global note (click to make Project-only)" else "Project note (click to make Global)",
        box = 26
    )

    private var displayedTags = emptyList<String>()
    private val titleField = borderlessField(note.title, "Untitled note", Theme.font(18f, true))
    private val status = JLabel("All changes saved")
    private val count = JLabel()
    private val body = JPanel(BorderLayout())
    private var editor: JTextPane? = null
    private var disposed = false
    private var displayedColor: NoteColor? = null

    private val sessionListener: () -> Unit = {
        val nextStatus = when {
            session.error != null -> "Could not save - retry by editing"
            session.dirty -> "Saving..."
            else -> "All changes saved"
        }
        if (status.text != nextStatus) status.text = nextStatus
        if (status.toolTipText != session.error) status.toolTipText = session.error
        applyNoteColor()
        pinBtn.icon = if (this.note.isPinned) NotesIcons.PINNED else NotesIcons.PIN
        pinBtn.tooltip = if (this.note.isPinned) "Unpin Note" else "Pin Note"
        scopeBtn.icon = if (this.note.isGlobal) AllIcons.General.Web else AllIcons.Nodes.Folder
        scopeBtn.tooltip = if (this.note.isGlobal) "Global note (click to make Project-only)" else "Project note (click to make Global)"
        if (displayedTags != this.note.tags) renderTags()
        updateCount()
    }

    private val tags = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3)))

    init {
        pinBtn.onAction = {
            this.note.isPinned = !this.note.isPinned
            pinBtn.icon = if (this.note.isPinned) NotesIcons.PINNED else NotesIcons.PIN
            pinBtn.tooltip = if (this.note.isPinned) "Unpin Note" else "Pin Note"
            changed()
        }
        scopeBtn.onAction = {
            flush()
            storage.moveNote(this.note, !this.note.isGlobal)
            scopeBtn.icon = if (this.note.isGlobal) AllIcons.General.Web else AllIcons.Nodes.Folder
            scopeBtn.tooltip = if (this.note.isGlobal) "Global note (click to make Project-only)" else "Project note (click to make Global)"
            status.text = if (this.note.isGlobal) "Moved to Global" else "Moved to Project"
        }

        isOpaque = true
        background = note.color.background
        border = JBUI.Borders.empty(10, 12, 10, 12)
        val header = JPanel(BorderLayout(0, JBUI.scale(8)))
        val navigation = JPanel(BorderLayout())

        if (!mainArea) {
            val backBtn = IconButton(AllIcons.Actions.Back, "Back to notes (Escape)", box = 26) {
                flush()
                onBackToList()
            }
            navigation.add(backBtn, BorderLayout.WEST)
        }

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply {
            isOpaque = false
        }

        // Color palette button right in the top bar
        val paletteBtn = IconButton(NotesIcons.PALETTE, "Note Color", box = 26) {
            showColorPicker(this, this.note.color) { session.setColor(it) }
        }
        actions.add(paletteBtn)
        actions.add(pinBtn)
        actions.add(scopeBtn)

        if (!mainArea) {
            actions.add(IconButton(NotesIcons.OPEN_IN_EDITOR, "Open formatted note in main editor", box = 26) {
                flush()
                storage.openNoteInMainEditor(this.note)
            })
        }

        actions.add(IconButton(AllIcons.Actions.More, "More options", box = 26) {
            options(this)
        })

        navigation.add(actions, BorderLayout.EAST)
        header.add(navigation, BorderLayout.NORTH)
        header.add(titleField, BorderLayout.CENTER)
        tags.isOpaque = false
        header.add(tags, BorderLayout.SOUTH)
        add(header, BorderLayout.NORTH)

        body.border = JBUI.Borders.emptyTop(8)
        add(body, BorderLayout.CENTER)

        val footer = JPanel(BorderLayout()).apply { border = JBUI.Borders.emptyTop(8) }
        status.foreground = Theme.textMuted
        status.font = Theme.font(11f)
        count.foreground = Theme.textMuted
        count.font = Theme.font(11f)
        footer.add(status, BorderLayout.CENTER)
        footer.add(count, BorderLayout.EAST)
        add(footer, BorderLayout.SOUTH)

        titleField.document = session.titleDocument

        if (!mainArea) {
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "back")
            actionMap.put("back", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) { flush(); onBackToList() }
            })
        }

        renderTags()
        renderBody()
        updateCount()
        session.addListener(sessionListener)
    }

    fun preferredFocusComponent(): JComponent = if (note.title.isBlank()) titleField else (editor ?: this)

    fun focusEditor() {
        SwingUtilities.invokeLater { preferredFocusComponent().requestFocusInWindow() }
    }

    fun flush() { session.flush() }

    fun dispose() {
        if (disposed) return
        disposed = true
        session.removeListener(sessionListener)
        storage.releaseSession(note.id)
    }

    override fun removeNotify() { flush(); super.removeNotify() }

    private fun changed() { session.changed() }

    private fun applyNoteColor() {
        if (displayedColor == note.color) return
        displayedColor = note.color
        val surface = note.color.background
        fun tint(component: Component) {
            if (component is JPanel || component is JViewport || component is JTextPane) component.background = surface
            if (component is Container) component.components.forEach(::tint)
        }
        tint(this)
        repaint()
    }

    private fun updateCount() {
        val nextCount = if (note.type == NoteType.TEXT) {
            val len = editor?.document?.length ?: 0
            val text = runCatching { editor?.document?.getText(0, len) }.getOrNull() ?: ""
            val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
            "$words words • $len chars"
        } else {
            note.getChecklistSummary()
        }
        if (count.text != nextCount) count.text = nextCount
    }

    private fun renderTags() {
        displayedTags = note.tags.toList()
        tags.removeAll()
        note.tags.forEach { tag ->
            tags.add(Chip("#$tag", removable = true).apply {
                onRemove = { note.removeTag(tag); renderTags(); changed() }
            })
        }
        tags.add(InlineTagInput { tag ->
            note.addTag(tag)
            renderTags()
            changed()
        })
        tags.revalidate()
        tags.repaint()
    }

    private fun renderBody() {
        body.removeAll()
        editor = null
        val pane = JTextPane().apply {
            editorKit = HTMLEditorKit()
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = Theme.font(13.5f)
            foreground = Theme.textPrimary
            background = Theme.fieldBg
            caretColor = Theme.textPrimary
            border = JBUI.Borders.empty(10)
            document = session.document
            caretPosition = 1.coerceAtMost(document.length)
        }
        editor = pane
        val undo = session.undo
        val modifier = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx

        fun bind(key: Int, name: String, action: () -> Unit) {
            pane.inputMap.put(KeyStroke.getKeyStroke(key, modifier), name)
            pane.actionMap.put(name, object : AbstractAction() { override fun actionPerformed(e: ActionEvent) { action() } })
        }
        fun style(action: Action) { action.actionPerformed(ActionEvent(pane, 0, "format")); pane.requestFocusInWindow() }

        bind(KeyEvent.VK_Z, "undoNote") { if (undo.canUndo()) undo.undo() }
        bind(KeyEvent.VK_Y, "redoNote") { if (undo.canRedo()) undo.redo() }
        bind(KeyEvent.VK_B, "boldNote") { style(StyledEditorKit.BoldAction()) }
        bind(KeyEvent.VK_I, "italicNote") { style(StyledEditorKit.ItalicAction()) }
        bind(KeyEvent.VK_U, "underlineNote") { style(StyledEditorKit.UnderlineAction()) }

        pane.focusTraversalKeysEnabled = false
        pane.inputMap.put(KeyStroke.getKeyStroke("TAB"), "indentText")
        pane.actionMap.put("indentText", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) { EditorFormattingToolbar.indent(pane, true) }
        })
        pane.inputMap.put(KeyStroke.getKeyStroke("shift TAB"), "outdentText")
        pane.actionMap.put("outdentText", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) { EditorFormattingToolbar.indent(pane, false) }
        })

        val defaultEnter = pane.actionMap.get(javax.swing.text.DefaultEditorKit.insertBreakAction)
        pane.actionMap.put(javax.swing.text.DefaultEditorKit.insertBreakAction, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val pos = pane.caretPosition
                val doc = pane.styledDocument
                val paragraph = doc.getParagraphElement(pos)
                val pStart = paragraph.startOffset
                val pEnd = paragraph.endOffset.coerceAtMost(doc.length)
                val line = doc.getText(pStart, pEnd - pStart).trimEnd('\r', '\n')

                val numMatch = Regex("^(\\s*)(\\d+)\\.\\s*(.*)$").matchEntire(line)
                if (numMatch != null) {
                    val indent = numMatch.groupValues[1]
                    val num = numMatch.groupValues[2].toIntOrNull() ?: 1
                    val content = numMatch.groupValues[3]
                    if (content.isBlank()) {
                        doc.remove(pStart, pEnd - pStart)
                        return
                    } else {
                        defaultEnter?.actionPerformed(e) ?: pane.replaceSelection("\n")
                        pane.replaceSelection("$indent${num + 1}. ")
                        return
                    }
                }

                val bulletMatch = Regex("^(\\s*)([•\\-*])\\s*(.*)$").matchEntire(line)
                if (bulletMatch != null) {
                    val indent = bulletMatch.groupValues[1]
                    val content = bulletMatch.groupValues[3]
                    if (content.isBlank()) {
                        doc.remove(pStart, pEnd - pStart)
                        return
                    } else {
                        defaultEnter?.actionPerformed(e) ?: pane.replaceSelection("\n")
                        pane.replaceSelection("$indent• ")
                        return
                    }
                }

                val checkMatch = Regex("^(\\s*)([○●◉☐☑])\\s*(.*)$").matchEntire(line)
                if (checkMatch != null) {
                    val indent = checkMatch.groupValues[1]
                    val content = checkMatch.groupValues[3]
                    if (content.isBlank()) {
                        doc.remove(pStart, pEnd - pStart)
                        return
                    } else {
                        defaultEnter?.actionPerformed(e) ?: pane.replaceSelection("\n")
                        pane.replaceSelection("$indent○ ")
                        return
                    }
                }

                defaultEnter?.actionPerformed(e) ?: pane.replaceSelection("\n")
            }
        })

        val toolbar = EditorFormattingToolbar(pane) {
            val pos = pane.caretPosition
            val paragraph = pane.styledDocument.getParagraphElement(pos)
            insertText(if (pos > paragraph.startOffset) "\n\u25cb " else "\u25cb ")
        }

        pane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) return
                val docLen = pane.document.length
                if (docLen == 0) return
                val rawOffset = pane.viewToModel2D(e.point)
                if (rawOffset < 0) return
                val candidates = intArrayOf(rawOffset.coerceAtMost(docLen - 1), (rawOffset - 1).coerceIn(0, docLen - 1))
                for (offset in candidates) {
                    val character = pane.document.getText(offset, 1)
                    if (character in setOf("○", "●", "◉", "☐", "☑")) {
                        val attrs = pane.styledDocument.getCharacterElement(offset).attributes.copyAttributes()
                        val next = when (character) {
                            "○" -> "●"
                            "●", "◉" -> "○"
                            "☐" -> "●"
                            "☑" -> "○"
                            else -> "●"
                        }
                        pane.document.remove(offset, 1)
                        pane.document.insertString(offset, next, attrs)
                        break
                    }
                }
            }
        })

        bind(KeyEvent.VK_ENTER, "toggleTask") {
            val paragraph = pane.styledDocument.getParagraphElement(pane.caretPosition)
            val value = pane.document.getText(paragraph.startOffset, paragraph.endOffset.coerceAtMost(pane.document.length) - paragraph.startOffset)
            val index = value.indexOfFirst { it == '○' || it == '●' || it == '◉' || it == '☐' || it == '☑' }
            if (index >= 0) {
                val offset = paragraph.startOffset + index
                val attrs = pane.styledDocument.getCharacterElement(offset).attributes.copyAttributes()
                val curr = value[index]
                val next = when (curr) {
                    '○' -> "●"
                    '●', '◉' -> "○"
                    '☐' -> "●"
                    '☑' -> "○"
                    else -> "●"
                }
                pane.document.remove(offset, 1)
                pane.document.insertString(offset, next, attrs)
            }
        }

        body.add(toolbar, BorderLayout.NORTH)
        body.add(JBScrollPane(pane).apply {
            border = BorderFactory.createEmptyBorder()
            viewport.isOpaque = false
            isOpaque = false
        }, BorderLayout.CENTER)
        body.revalidate()
        body.repaint()
    }

    private fun insertText(text: String) {
        editor?.let { it.replaceSelection(text); it.requestFocusInWindow() }
    }

    private fun options(button: JComponent) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("Change color").apply {
            icon = NotesIcons.PALETTE
            addActionListener { showColorPicker(button, note.color) { session.setColor(it) } }
        })
        menu.add(JMenuItem(if (note.isGlobal) "Move to this project" else "Make available in all projects").apply {
            icon = if (note.isGlobal) AllIcons.Nodes.Folder else AllIcons.General.Web
            addActionListener { flush(); storage.moveNote(note, !note.isGlobal); status.text = "Note moved" }
        })
        menu.addSeparator()
        menu.add(JMenuItem("Delete note...").apply {
            icon = AllIcons.Actions.GC
            addActionListener {
                val res = Messages.showYesNoDialog(
                    this@NoteDetailView,
                    "Delete note \"${note.displayTitle}\"? A recovery copy will be kept in the trash.",
                    "Delete Note",
                    Messages.getQuestionIcon()
                )
                if (res == Messages.YES) {
                    storage.deleteNote(note)
                    onBackToList()
                }
            }
        })
        menu.show(button, 0, button.height)
    }
}
