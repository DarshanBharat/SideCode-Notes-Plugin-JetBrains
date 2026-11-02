package com.pynotes.storage

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.pynotes.editor.LiveNoteSession
import com.pynotes.editor.NoteTabs
import com.pynotes.model.Note
import com.pynotes.model.NoteColor
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.TreeSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class NotesStorageService(private val project: Project) : Disposable {

    fun interface NotesChangeListener {
        fun onNotesChanged()
    }

    private val notes = CopyOnWriteArrayList<Note>()
    private val listeners = CopyOnWriteArrayList<NotesChangeListener>()
    private val lastSavedMarkdown = ConcurrentHashMap<String, String>()
    private var initialized = false
    @Volatile private var cachedSortedNotes: List<Note>? = null
    @Volatile private var cachedTags: Set<String>? = null
    var lastSaveError: String? = null
        private set

    companion object {
        private val LOG = Logger.getInstance(NotesStorageService::class.java)
        private val SAFE_FILE_NAME_REGEX = Regex("[^a-zA-Z0-9._-]")

        fun getInstance(project: Project): NotesStorageService {
            return project.getService(NotesStorageService::class.java)
        }
    }

    init {
        runCatching {
            if (!project.isDisposed) {
                project.messageBus.connect(this).subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                    override fun projectClosingBeforeSave(closingProject: Project) {
                        if (closingProject == project) {
                            flushAllSessions()
                            closeTransientEditorTabs()
                        }
                    }
                })
            }
        }
    }

    @Synchronized
    fun initializeIfNeeded() {
        if (!initialized) {
            reloadAllNotes()
            initialized = true
        }
    }

    fun addChangeListener(listener: NotesChangeListener) {
        listeners.addIfAbsent(listener)
    }

    fun removeChangeListener(listener: NotesChangeListener) {
        listeners.remove(listener)
    }

    private fun invalidateCaches() {
        cachedSortedNotes = null
        cachedTags = null
    }

    private fun notifyListeners() {
        invalidateCaches()
        val app = ApplicationManager.getApplication() ?: return
        if (app.isDisposed || project.isDisposed) return
        app.invokeLater({
            if (!project.isDisposed) {
                listeners.forEach {
                    runCatching { it.onNotesChanged() }
                }
            }
        }, project.disposed)
    }

    fun getAllNotes(): List<Note> {
        initializeIfNeeded()
        var current = cachedSortedNotes
        if (current == null) {
            current = notes.sortedWith(
                compareByDescending<Note> { it.isPinned }
                    .thenByDescending { it.updatedAt }
            )
            cachedSortedNotes = current
        }
        return current
    }

    fun getAllTags(): Set<String> {
        initializeIfNeeded()
        var current = cachedTags
        if (current == null) {
            val set = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
            notes.forEach { note ->
                set.addAll(note.tags)
            }
            cachedTags = set
            current = set
        }
        return current
    }

    @Synchronized
    fun saveNote(note: Note, touchUpdatedAt: Boolean = true) {
        if (touchUpdatedAt) {
            note.updatedAt = System.currentTimeMillis()
        }
        val idx = notes.indexOfFirst { it.id == note.id }
        if (idx >= 0) {
            if (notes[idx] !== note) {
                notes[idx] = note
            }
        } else {
            notes.add(0, note)
        }

        saveNoteToFile(note)
        notifyListeners()
    }

    fun setNoteColor(note: Note, color: NoteColor) {
        val session = liveSessions[note.id]?.session
        if (session != null) {
            session.setColor(color)
            notifyListeners()
        } else {
            note.color = color
            saveNote(note, touchUpdatedAt = false)
        }
    }

    @Synchronized
    fun deleteNote(note: Note) {
        liveSessions.remove(note.id)?.session?.discard()
        editorTabs.remove(note.id)?.let { tab ->
            runCatching {
                val app = ApplicationManager.getApplication()
                if (app != null && !project.isDisposed) {
                    if (app.isDispatchThread) {
                        FileEditorManager.getInstance(project).closeFile(tab)
                    } else {
                        app.invokeLater({ if (!project.isDisposed) FileEditorManager.getInstance(project).closeFile(tab) }, project.disposed)
                    }
                }
            }
        }
        note.filePath?.let { lastSavedMarkdown.remove(it) }
        val source = note.filePath?.let(::File)
        if (source != null && source.exists()) {
            runCatching {
                val trash = File(source.parentFile, ".trash").apply { mkdirs() }
                val target = File(trash, "${sanitizeFileName(note.id)}-${System.currentTimeMillis()}.md").toPath()
                Files.move(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            }.onFailure {
                source.delete()
            }
        }
        notes.removeIf { it.id == note.id }
        notifyListeners()
    }

    @Synchronized
    fun reloadAllNotes() {
        notes.clear()
        lastSavedMarkdown.clear()
        loadNotesFromDirectory(getProjectNotesDir(), isGlobal = false)
        loadNotesFromDirectory(getGlobalNotesDir(), isGlobal = true)
        notifyListeners()
    }

    private fun loadNotesFromDirectory(dir: File, isGlobal: Boolean) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles { _, name -> name.endsWith(".md", ignoreCase = true) } ?: return

        for (file in files) {
            runCatching {
                val content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
                val id = file.name.substringBeforeLast('.')
                val note = Note.parseMarkdown(content, id)
                note.isGlobal = isGlobal
                note.filePath = file.absolutePath
                lastSavedMarkdown[file.absolutePath] = content
                // A reload must not replace the object backing open live editors.
                notes.add(liveSessions[note.id]?.session?.note ?: note)
            }
        }
    }

    fun saveNoteToFile(note: Note): File? {
        val dir = if (note.isGlobal) getGlobalNotesDir() else getProjectNotesDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val safeName = sanitizeFileName(note.id) + ".md"
        val file = File(dir, safeName)
        val absPath = file.absolutePath
        val markdown = note.toMarkdown()
        if (lastSavedMarkdown[absPath] == markdown && file.exists()) {
            lastSaveError = null
            note.filePath = absPath
            return file
        }
        return try {
            val temporary = dir.toPath().resolve("$safeName.tmp")
            try {
                Files.writeString(temporary, markdown, StandardCharsets.UTF_8)
                try {
                    Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (writeErr: Exception) {
                runCatching { Files.deleteIfExists(temporary) }
                throw writeErr
            }
            lastSaveError = null
            note.filePath = absPath
            lastSavedMarkdown[absPath] = markdown
            file
        } catch (e: Exception) {
            lastSaveError = e.message ?: "Unable to save note"
            LOG.warn("Unable to save note", e)
            null
        }
    }

    @Synchronized
    fun restoreLastDeleted(): Boolean {
        val latest = listOf(getProjectNotesDir(), getGlobalNotesDir()).distinctBy { it.absolutePath }
            .flatMap { dir -> File(dir, ".trash").listFiles()?.filter { it.extension.equals("md", ignoreCase = true) } ?: emptyList() }
            .maxByOrNull { it.lastModified() } ?: return false
        val raw = runCatching { Files.readString(latest.toPath(), StandardCharsets.UTF_8) }.getOrNull() ?: return false
        val note = Note.parseMarkdown(raw, UUID.randomUUID().toString())
        note.isGlobal = latest.parentFile?.parentFile == getGlobalNotesDir()
        if (notes.any { it.id == note.id }) note.id = UUID.randomUUID().toString()
        if (saveNoteToFile(note) == null) return false
        latest.delete()
        notes.add(0, note)
        notifyListeners()
        return true
    }

    @Synchronized
    fun moveNote(note: Note, global: Boolean) {
        val oldPath = note.filePath
        val oldFile = oldPath?.let(::File)
        val oldScope = note.isGlobal
        note.isGlobal = global
        if (saveNoteToFile(note) != null) {
            if (oldPath != null && oldPath != note.filePath) {
                lastSavedMarkdown.remove(oldPath)
                oldFile?.delete()
            }
            notifyListeners()
        } else {
            note.isGlobal = oldScope
        }
    }

    @Suppress("unused")
    private fun deleteNoteFile(note: Note) {
        note.filePath?.let { path ->
            lastSavedMarkdown.remove(path)
            val f = File(path)
            if (f.exists()) {
                f.delete()
                ApplicationManager.getApplication()?.executeOnPooledThread {
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f)
                }
            }
        }
    }

    private data class SessionEntry(val session: LiveNoteSession, var views: Int = 0)
    private val liveSessions = ConcurrentHashMap<String, SessionEntry>()

    @Synchronized
    fun acquireSession(note: Note): LiveNoteSession {
        val entry = liveSessions.getOrPut(note.id) {
            SessionEntry(LiveNoteSession(note) { changed ->
                saveNote(changed)
                lastSaveError
            })
        }
        entry.views++
        return entry.session
    }

    @Synchronized
    fun releaseSession(noteId: String) {
        val entry = liveSessions[noteId] ?: return
        entry.views--
        if (entry.views <= 0) {
            entry.session.flush()
            // Keep a failed save in memory so opening the note again can retry it.
            if (!entry.session.dirty) liveSessions.remove(noteId)
        }
    }

    @Synchronized
    private fun flushAllSessions() {
        liveSessions.values.toList().forEach { entry ->
            runCatching { entry.session.flush() }
        }
    }

    private val editorTabs = ConcurrentHashMap<String, VirtualFile>()

    fun openNoteInMainEditor(note: Note) {
        if (project.isDisposed) return
        val manager = FileEditorManager.getInstance(project)
        val tab = editorTabs.compute(note.id) { _, existing ->
            if (existing != null && existing.isValid) {
                existing.putUserData(NoteTabs.NOTE, note)
                existing
            } else {
                NoteTabs.file(note)
            }
        }!!
        manager.openFile(tab, true)
    }

    fun onEditorTabClosed(noteId: String, file: VirtualFile) {
        editorTabs.remove(noteId, file)
    }

    private fun closeTransientEditorTabs() {
        if (project.isDisposed || editorTabs.isEmpty()) return
        val manager = runCatching { FileEditorManager.getInstance(project) }.getOrNull() ?: return
        val openTabs = editorTabs.values.toList()
        editorTabs.clear()
        openTabs.forEach { tab ->
            runCatching { manager.closeFile(tab) }
        }
    }

    override fun dispose() {
        flushAllSessions()
        liveSessions.values.forEach { it.session.discard() }
        liveSessions.clear()
        listeners.clear()
        editorTabs.clear()
        lastSavedMarkdown.clear()
    }

    private fun getProjectNotesDir(): File {
        val basePath = project.basePath ?: return getGlobalNotesDir()
        val ideaDir = File(basePath, ".idea")
        return if (ideaDir.exists() && ideaDir.isDirectory) {
            File(ideaDir, "notes")
        } else {
            File(basePath, ".notes")
        }
    }

    private fun getGlobalNotesDir(): File {
        val userHome = System.getProperty("user.home")
        return File(userHome, ".pycharm-notes")
    }

    private fun sanitizeFileName(name: String?): String {
        if (name.isNullOrEmpty()) return UUID.randomUUID().toString()
        return name.replace(SAFE_FILE_NAME_REGEX, "_")
    }
}
