package com.pynotes.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.pynotes.model.Note
import com.pynotes.storage.NotesStorageService
import com.pynotes.ui.NoteDetailView
import java.beans.PropertyChangeListener
import javax.swing.JComponent

object NoteTabs {
    val NOTE: Key<Note> = Key.create("sidenotes.editor.note")
    fun file(note: Note): VirtualFile = LightVirtualFile("${note.displayTitle}.sidenote").apply {
        putUserData(NOTE, note)
    }
}

class NoteEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file is LightVirtualFile && file.getUserData(NoteTabs.NOTE) != null

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = NoteEditor(project, file)
    override fun getEditorTypeId(): String = "sidenotes.formatted"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private class NoteEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {
    private val note = requireNotNull(file.getUserData(NoteTabs.NOTE))
    private val view = NoteDetailView(
        project,
        note,
        onBackToList = {
            if (!project.isDisposed) {
                FileEditorManager.getInstance(project).closeFile(file)
            }
        },
        mainArea = true
    )

    override fun getComponent(): JComponent = view
    override fun getPreferredFocusedComponent(): JComponent = view.preferredFocusComponent()
    override fun getName(): String = "Note"
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun deselectNotify() { view.flush() }
    override fun dispose() {
        view.dispose()
        if (!project.isDisposed) {
            NotesStorageService.getInstance(project).onEditorTabClosed(note.id, file)
        }
    }
}
