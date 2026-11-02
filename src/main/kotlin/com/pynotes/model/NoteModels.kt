package com.pynotes.model

import com.intellij.ui.JBColor
import java.awt.Color
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

enum class NoteType {
    TEXT,
    CHECKLIST
}

/** Google Keep palette: soft pastels in light themes, muted deep tones in dark themes. */
enum class NoteColor(val displayName: String, light: Int, dark: Int, accentLight: Int, accentDark: Int) {
    DEFAULT("Default", 0xFFFFFF, 0x1E1F22, 0x3574F0, 0x548AF7),
    CORAL("Coral", 0xFCE8E6, 0x472421, 0xD93829, 0xF28B82),
    PEACH("Peach", 0xFEF0E1, 0x483216, 0xBA6B00, 0xFDD663),
    SAND("Sand", 0xFEF7D6, 0x453C14, 0x9E8600, 0xFFF475),
    MINT("Mint", 0xE6F7E9, 0x1F3F24, 0x2E8B29, 0x81C995),
    SAGE("Sage", 0xE0F2F1, 0x183C37, 0x0F7060, 0x5DBDB3),
    FOG("Fog", 0xE3F2FD, 0x18374D, 0x1B6D85, 0x78D9EC),
    STORM("Storm", 0xE8EAF6, 0x222C4A, 0x1E56A0, 0x8AB4F8),
    DUSK("Dusk", 0xF3E5F5, 0x391E47, 0x68388A, 0xC58AF9),
    BLOSSOM("Blossom", 0xFCE4EC, 0x451A2C, 0x9C276A, 0xF48FB1),
    CLAY("Clay", 0xEFEBE9, 0x362B25, 0x704A1B, 0xD7AE85);

    val background: JBColor = if (name == "DEFAULT") JBColor.namedColor("Panel.background", JBColor(Color(light), Color(dark))) else JBColor(Color(light), Color(dark))
    val isDefault: Boolean get() = this == DEFAULT
    val accent: JBColor = JBColor(Color(accentLight), Color(accentDark))
    val border: JBColor = if (name == "DEFAULT") JBColor(Color(0xDCDFE4), Color(0x3B3E44)) else JBColor(Color(0, 0, 0, 30), Color(255, 255, 255, 35))
    val pillBg: JBColor = JBColor(Color(0, 0, 0, 15), Color(255, 255, 255, 20))
    val pillFg: JBColor get() = accent

    companion object {
        private val aliases = mapOf(
            "YELLOW" to SAND, "AMBER_GOLD" to SAND, "SAND_TAN" to CLAY,
            "GREEN" to MINT, "MINT_SAGE" to MINT,
            "BLUE" to FOG, "OCEAN_BLUE" to FOG,
            "PEACH_CORAL" to PEACH,
            "PURPLE" to DUSK, "LAVENDER_PURPLE" to DUSK,
            "TEAL" to SAGE, "CYAN_TEAL" to SAGE
        )

        fun fromString(name: String?): NoteColor {
            if (name.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: aliases[name.uppercase()]
                ?: DEFAULT
        }
    }
}

data class ChecklistItem(
    var id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var isDone: Boolean = false
) {
    fun copyItem(): ChecklistItem = ChecklistItem(UUID.randomUUID().toString(), text, isDone)
}

class Note(
    var title: String = "",
    var type: NoteType = NoteType.TEXT
) {
    var id: String = UUID.randomUUID().toString()
    var content: String = ""
    var checklistItems: MutableList<ChecklistItem> = mutableListOf()
    var color: NoteColor = NoteColor.DEFAULT
    var isPinned: Boolean = false
    var tags: MutableList<String> = mutableListOf()
    var createdAt: Long = System.currentTimeMillis()
    var updatedAt: Long = System.currentTimeMillis()
    var isGlobal: Boolean = false
    var filePath: String? = null

    @Transient private var plainCacheSource: String? = null
    @Transient private var plainCacheValue: String = ""
    @Transient private var previewCacheSource: String? = null
    @Transient private var previewCacheValue: String = ""
    @Transient private var searchableCacheKey: String? = null
    @Transient private var searchableCacheValue: String = ""

    val displayTitle: String
        get() = title.trim().ifEmpty {
            val dateStr = formatTimestamp(createdAt, TITLE_DATE_FORMAT)
            if (type == NoteType.CHECKLIST) "Untitled checklist ($dateStr)" else "Untitled note ($dateStr)"
        }

    fun getChecklistSummary(): String {
        if (checklistItems.isEmpty()) return ""
        val done = checklistItems.count { it.isDone }
        return "$done/${checklistItems.size} completed"
    }

    fun getFormattedDate(): String {
        val diff = System.currentTimeMillis() - updatedAt
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            isToday(updatedAt) -> formatTimestamp(updatedAt, TIME_FORMAT)
            else -> formatTimestamp(updatedAt, SHORT_DATE_FORMAT)
        }
    }

    fun editedLabel(): String {
        val diff = System.currentTimeMillis() - updatedAt
        return when {
            diff < 60_000 -> "Edited just now"
            diff < 3_600_000 -> "Edited ${diff / 60_000} min ago"
            isToday(updatedAt) -> "Edited today, " + formatTimestamp(updatedAt, TIME_FORMAT)
            else -> "Edited " + formatTimestamp(updatedAt, FULL_DATE_FORMAT)
        }
    }

    private fun isToday(ts: Long): Boolean {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() == LocalDate.now(zone)
    }

    fun plainContent(): String {
        val rawContent = content
        if (plainCacheSource === rawContent || plainCacheSource == rawContent) {
            return plainCacheValue
        }
        val computed = if (!rawContent.trimStart().startsWith("<html", true)) {
            rawContent
        } else {
            val result = StringBuilder(rawContent.length.coerceAtLeast(16))
            PARSER_DELEGATOR.parse(StringReader(rawContent), object : HTMLEditorKit.ParserCallback() {
                override fun handleText(data: CharArray, pos: Int) { result.append(data).append(' ') }
                override fun handleEndTag(tag: HTML.Tag, pos: Int) {
                    if (tag == HTML.Tag.P || tag == HTML.Tag.PRE) result.append('\n')
                }
            }, true)
            result.toString()
        }
        plainCacheSource = rawContent
        plainCacheValue = computed
        return computed
    }

    fun wordCount(): Int = plainContent().split(WHITESPACE_REGEX).count { it.isNotBlank() }

    /** Plain-text preview with Markdown syntax softened into readable glyphs. */
    fun previewText(maxLines: Int = 6, maxChars: Int = 240): String {
        val rawContent = content
        if (maxLines == 6 && maxChars == 240 && (previewCacheSource === rawContent || previewCacheSource == rawContent)) {
            return previewCacheValue
        }
        val out = mutableListOf<String>()
        var inCode = false
        for (raw in plainContent().lines()) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                continue
            }
            var l = line
            if (!inCode) {
                l = l.replace(HEADING_PREFIX_REGEX, "")
                    .replace(CHECKED_TASK_REGEX, "\$1● ")
                    .replace(UNCHECKED_TASK_REGEX, "\$1○ ")
                    .replace(BULLET_REGEX, "\$1• ")
                    .replace("**", "").replace("__", "").replace("~~", "").replace("`", "")
            }
            if (l.isBlank() && (out.isEmpty() || out.last().isBlank())) continue
            out.add(l)
            if (out.size >= maxLines) break
        }
        var text = out.joinToString("\n").trim()
        if (text.length > maxChars) text = text.take(maxChars - 1).trimEnd() + "…"
        if (maxLines == 6 && maxChars == 240) {
            previewCacheSource = rawContent
            previewCacheValue = text
        }
        return text
    }

    fun searchableText(): String {
        val key = "$title\u0000$content\u0000${tags.joinToString(",")}\u0000${checklistItems.joinToString(",") { it.text }}"
        if (searchableCacheKey == key) {
            return searchableCacheValue
        }
        val computed = (title + " " + plainContent() + " " + tags.joinToString(" ") + " " + checklistItems.joinToString(" ") { it.text }).lowercase()
        searchableCacheKey = key
        searchableCacheValue = computed
        return computed
    }

    fun addTag(tag: String) {
        val clean = tag.trim().removePrefix("#").lowercase()
        if (clean.isNotEmpty() && !tags.contains(clean)) tags.add(clean)
    }

    fun removeTag(tag: String) {
        tags.remove(tag.trim().removePrefix("#").lowercase())
    }

    fun convertToChecklist() {
        if (type == NoteType.CHECKLIST) return
        val items = mutableListOf<ChecklistItem>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            var done = false
            var text = trimmed
            when {
                trimmed.startsWith("- [x] ", true) -> {
                    done = true
                    text = trimmed.substring(6)
                }
                trimmed.startsWith("- [ ] ") -> text = trimmed.substring(6)
                trimmed.startsWith("● ") || trimmed.startsWith("◉ ") || trimmed.startsWith("☑ ") -> {
                    done = true
                    text = trimmed.substring(2)
                }
                trimmed.startsWith("○ ") || trimmed.startsWith("☐ ") -> {
                    done = false
                    text = trimmed.substring(2)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> text = trimmed.substring(2)
            }
            items.add(ChecklistItem(text = text.trim(), isDone = done))
        }
        checklistItems = items
        type = NoteType.CHECKLIST
    }

    fun convertToText() {
        if (type == NoteType.TEXT) return
        content = checklistItems.joinToString("\n") { (if (it.isDone) "- [x] " else "- [ ] ") + it.text }
        type = NoteType.TEXT
    }

    /** Title + body as Markdown, without front matter (for clipboard export). */
    fun bodyMarkdown(): String = buildString {
        if (title.isNotBlank()) append("# ").append(title.trim()).append("\n\n")
        if (type == NoteType.CHECKLIST) {
            checklistItems.forEach { append(if (it.isDone) "- [x] " else "- [ ] ").append(it.text).append("\n") }
        } else {
            append(content)
        }
    }

    fun toMarkdown(): String = buildString {
        append("---\n")
        append("id: $id\n")
        append("type: ${type.name}\n")
        append("color: ${color.name}\n")
        append("pinned: $isPinned\n")
        append("global: $isGlobal\n")
        append("createdAt: $createdAt\n")
        append("updatedAt: $updatedAt\n")
        if (tags.isNotEmpty()) append("tags: [${tags.joinToString(", ")}]\n")
        append("---\n\n")
        append(bodyMarkdown())
    }

    companion object {
        private val PARSER_DELEGATOR = ParserDelegator()
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val HEADING_PREFIX_REGEX = Regex("^\\s*#{1,6}\\s+")
        private val CHECKED_TASK_REGEX = Regex("^(\\s*)- \\[[xX]\\] ")
        private val UNCHECKED_TASK_REGEX = Regex("^(\\s*)- \\[ \\] ")
        private val BULLET_REGEX = Regex("^(\\s*)[-*] ")

        private val TITLE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        private val SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        private val FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

        private fun formatTimestamp(ts: Long, formatter: DateTimeFormatter): String =
            Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(formatter)

        fun parseMarkdown(fullContent: String, defaultId: String): Note {
            val note = Note()
            note.id = defaultId
            if (fullContent.isBlank()) return note

            var body = fullContent
            if (fullContent.startsWith("---")) {
                val end = fullContent.indexOf("\n---", 3)
                if (end != -1) {
                    val yaml = fullContent.substring(3, end)
                    body = fullContent.substring(end + 4).trim('\n', '\r')
                    yaml.lines().forEach { line ->
                        val colon = line.indexOf(':')
                        if (colon == -1) return@forEach
                        val key = line.substring(0, colon).trim()
                        val value = line.substring(colon + 1).trim()
                        when (key) {
                            "id" -> if (value.isNotEmpty()) note.id = value
                            "type" -> runCatching { note.type = NoteType.valueOf(value.uppercase()) }
                            "color" -> note.color = NoteColor.fromString(value)
                            "pinned" -> note.isPinned = value.toBoolean()
                            "global" -> note.isGlobal = value.toBoolean()
                            "createdAt" -> value.toLongOrNull()?.let { note.createdAt = it }
                            "updatedAt" -> value.toLongOrNull()?.let { note.updatedAt = it }
                            "tags" -> value.removePrefix("[").removeSuffix("]").split(",").forEach { note.addTag(it) }
                        }
                    }
                }
            }

            val lines = body.lines().toMutableList()
            while (lines.isNotEmpty() && lines.first().isBlank()) lines.removeAt(0)
            if (lines.isNotEmpty() && lines.first().startsWith("# ")) {
                note.title = lines.removeAt(0).substring(2).trim()
            }
            val rest = lines.joinToString("\n").trim('\n', '\r')

            if (note.type == NoteType.CHECKLIST) {
                note.checklistItems = rest.lines().mapNotNull { line ->
                    val t = line.trim()
                    when {
                        t.startsWith("- [x] ", true) -> ChecklistItem(text = t.substring(6), isDone = true)
                        t.startsWith("- [ ] ") -> ChecklistItem(text = t.substring(6))
                        t == "- [ ]" -> ChecklistItem(text = "")
                        t.isNotEmpty() -> ChecklistItem(text = t.removePrefix("- "))
                        else -> null
                    }
                }.toMutableList()
            } else {
                note.content = rest
            }
            return note
        }
    }
}
