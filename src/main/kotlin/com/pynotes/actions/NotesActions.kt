package com.pynotes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.pynotes.model.Note
import com.pynotes.model.NoteColor
import com.pynotes.model.NoteType
import com.pynotes.storage.NotesStorageService
import com.pynotes.ui.NotesToolWindowContent

class AddSelectionToNoteAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) return

        val fileName = file?.name ?: "Snippet"
        val ext = file?.extension?.lowercase() ?: "python"
        val startLine = editor.document.getLineNumber(editor.selectionModel.selectionStart) + 1

        val note = Note("Snippet from $fileName", NoteType.TEXT).apply {
            color = NoteColor.FOG
            addTag("snippet")
            file?.extension?.let { addTag(it) }
            content = buildString {
                append("Reference: `").append(fileName).append("` (line ").append(startLine).append(")\n\n")
                append("```").append(ext).append('\n')
                append(selectedText.trimEnd('\r', '\n')).append('\n')
                append("```\n\n")
                append("- Notes:")
            }
        }

        NotesStorageService.getInstance(project).saveNote(note)

        ToolWindowManager.getInstance(project).getToolWindow("Notes")?.let { window ->
            window.show {
                (window.contentManager.contents.firstOrNull()?.component as? NotesToolWindowContent)?.openNoteDetail(note)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = e.project != null && editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class NewQuickNoteAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val note = Note("Quick Note", NoteType.TEXT)
        NotesStorageService.getInstance(project).saveNote(note)
        ToolWindowManager.getInstance(project).getToolWindow("Notes")?.let { window ->
            window.show {
                (window.contentManager.contents.firstOrNull()?.component as? NotesToolWindowContent)?.openNoteDetail(note)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
