package com.pynotes.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object NotesIcons {
    val NOTES: Icon = IconLoader.getIcon("/icons/notes.svg", NotesIcons::class.java)
    val PIN: Icon = IconLoader.getIcon("/icons/pin.svg", NotesIcons::class.java)
    val PINNED: Icon = IconLoader.getIcon("/icons/pinned.svg", NotesIcons::class.java)
    val CHECKLIST: Icon = IconLoader.getIcon("/icons/checklist.svg", NotesIcons::class.java)
    val PALETTE: Icon = IconLoader.getIcon("/icons/palette.svg", NotesIcons::class.java)
    val OPEN_IN_EDITOR: Icon = IconLoader.getIcon("/icons/openInEditor.svg", NotesIcons::class.java)
}
