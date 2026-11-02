package com.pynotes.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class NotesToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = NotesToolWindowContent(project)
        val toolWindowContent = ContentFactory.getInstance().createContent(content, "", false)
        toolWindowContent.setDisposer(content)
        toolWindow.contentManager.addContent(toolWindowContent)
    }
}
