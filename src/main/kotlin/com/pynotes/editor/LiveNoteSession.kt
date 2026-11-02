package com.pynotes.editor

import com.pynotes.model.Note
import com.pynotes.model.NoteType
import com.pynotes.ui.Debouncer
import com.pynotes.ui.NoteFormatting
import java.io.StringReader
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.PlainDocument
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import javax.swing.undo.UndoManager

/** One live document and undo history per note. Views never serialize edits to each other. */
class LiveNoteSession(val note: Note, private val save: (Note) -> String?) {
    val document: HTMLDocument
    val titleDocument = PlainDocument()
    val undo = UndoManager()
    var dirty = false
        private set
    var error: String? = null
        private set
    private var discarded = false
    private val listeners = linkedSetOf<() -> Unit>()
    private val saveDelay = Debouncer(350) { persist() }

    init {
        if (note.type == NoteType.CHECKLIST) note.convertToText()
        val kit = HTMLEditorKit()
        document = kit.createDefaultDocument() as HTMLDocument
        // Read synchronously: no delayed parser work or document swaps after a view attaches.
        document.asynchronousLoadPriority = -1
        kit.read(StringReader(NoteFormatting.html(note.content)), document, 0)
        titleDocument.insertString(0, note.title, null)
        document.addUndoableEditListener(undo)
        val edits = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = changed()
            override fun removeUpdate(e: DocumentEvent) = changed()
            override fun changedUpdate(e: DocumentEvent) = changed()
        }
        document.addDocumentListener(edits)
        titleDocument.addDocumentListener(object : DocumentListener {
            private fun update() {
                note.title = titleDocument.getText(0, titleDocument.length)
                changed()
            }
            override fun insertUpdate(e: DocumentEvent) = update()
            override fun removeUpdate(e: DocumentEvent) = update()
            override fun changedUpdate(e: DocumentEvent) = update()
        })
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener); listener() }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyViews() { listeners.toList().forEach { it() } }

    fun setColor(color: com.pynotes.model.NoteColor) {
        note.color = color
        changed()
    }

    fun changed() {
        if (discarded) return
        dirty = true
        error = null
        saveDelay.poke()
        notifyViews()
    }

    fun flush() {
        saveDelay.cancel()
        if (!discarded && dirty) persist()
    }

    /** Cancels pending saves and prevents any further persistence (e.g., when a note is deleted). */
    fun discard() {
        discarded = true
        dirty = false
        saveDelay.cancel()
        listeners.clear()
    }

    private fun persist() {
        if (discarded) return
        note.content = NoteFormatting.serialize(document)
        error = try { save(note) } catch (exception: Exception) { exception.message ?: "Could not save note" }
        if (error == null) dirty = false
        notifyViews()
    }
}
