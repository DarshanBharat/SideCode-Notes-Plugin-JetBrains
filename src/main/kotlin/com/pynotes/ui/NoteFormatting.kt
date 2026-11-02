package com.pynotes.ui

/** Shared by sidebar and main editor. Never includes storage front matter. */
object NoteFormatting {
    private val HEADING_REGEX = Regex("^(#{1,6}) (.*)$")
    private val TASK_REGEX = Regex("^(\\s*)- \\[([ xX])\\] ?(.*)$")
    private val NUMBERED_REGEX = Regex("^(\\s*)(\\d+)\\.\\s+(.*)$")
    private val BULLET_REGEX = Regex("^(\\s*)([-*•])\\s+(.*)$")
    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
    private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
    private val ITALIC_REGEX = Regex("\\*(.+?)\\*")
    private val STRIKE_REGEX = Regex("~~(.+?)~~")
    private val CODE_RESTORE_REGEX = Regex("\u0001(\\d+)\u0001")
    private val HEAD_TAG_REGEX = Regex("(?is)<head\\b[^>]*>.*?</head>")
    private val DANGEROUS_BLOCK_REGEX = Regex("(?is)<(script|style|iframe|object)\\b[^>]*>.*?</\\1>")
    private val HTML_TAG_REGEX = Regex("(?is)<(/?)([a-z0-9]+)([^>]*)>")
    private val SAFE_ATTR_REGEX = Regex("(?i)(style|color|size|face)\\s*=\\s*\"([^\"]*)\"")
    private val UNSAFE_CSS_REGEX = Regex("(?i)url|expression|javascript|@import")
    private val ALLOWED_TAGS = setOf(
        "html", "body", "p", "br", "b", "strong", "i", "em", "u", "s", "strike",
        "span", "font", "pre", "code", "h1", "h2", "h3", "h4", "h5", "h6",
        "ul", "ol", "li", "hr", "div"
    )

    fun serialize(document: javax.swing.text.html.HTMLDocument): String {
        val output = java.io.StringWriter()
        val writer = object : javax.swing.text.html.HTMLWriter(output, document) {
            override fun text(element: javax.swing.text.Element) {
                val attrs = element.attributes
                val explicit = attrs.getAttribute(javax.swing.text.StyleConstants.Background) as? java.awt.Color
                val background = explicit ?: if (attrs.isDefined(javax.swing.text.html.CSS.Attribute.BACKGROUND_COLOR)) javax.swing.text.StyleConstants.getBackground(attrs) else null
                val styles = mutableListOf<String>()
                if (background != null) styles.add("background-color:#%02x%02x%02x".format(background.red, background.green, background.blue))
                if (attrs.isDefined(javax.swing.text.StyleConstants.FontSize) || attrs.isDefined(javax.swing.text.html.CSS.Attribute.FONT_SIZE)) {
                    styles.add("font-size:${javax.swing.text.StyleConstants.getFontSize(attrs)}pt")
                }
                if (styles.isNotEmpty()) write("<span style=\"${styles.joinToString(";")}\">")
                super.text(element)
                if (styles.isNotEmpty()) write("</span>")
            }
        }
        writer.write()
        return output.toString()
    }

    fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun toNbsp(spaces: String): String = spaces.replace("    ", "&nbsp;&nbsp;&nbsp;&nbsp;").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;").replace("  ", "&nbsp;&nbsp;")

    fun html(content: String): String {
        if (content.trimStart().startsWith("<html", true)) return sanitize(content)
        val out = StringBuilder(content.length + 64).append("<html><body>")
        var code = false
        for (line in content.lines()) {
            if (line.trimStart().startsWith("```")) {
                out.append(if (code) "</pre>" else "<pre>")
                code = !code
                continue
            }
            if (code) { out.append(escape(line)).append("\n"); continue }
            val heading = HEADING_REGEX.matchEntire(line)
            val task = TASK_REGEX.matchEntire(line)
            val numbered = NUMBERED_REGEX.matchEntire(line)
            val bullet = BULLET_REGEX.matchEntire(line)
            when {
                heading != null -> {
                    val level = heading.groupValues[1].length
                    out.append("<h$level>${inline(heading.groupValues[2])}</h$level>")
                }
                task != null -> {
                    val indent = toNbsp(task.groupValues[1])
                    val icon = if (task.groupValues[2].equals("x", true)) "●" else "○"
                    out.append("<p>$indent$icon ${inline(task.groupValues[3])}</p>")
                }
                numbered != null -> {
                    val indent = toNbsp(numbered.groupValues[1])
                    val num = numbered.groupValues[2]
                    out.append("<p>$indent$num. ${inline(numbered.groupValues[3])}</p>")
                }
                bullet != null -> {
                    val indent = toNbsp(bullet.groupValues[1])
                    out.append("<p>$indent• ${inline(bullet.groupValues[3])}</p>")
                }
                line.trim() == "---" -> out.append("<hr>")
                else -> {
                    if (line.isBlank()) {
                        out.append("<p><br></p>")
                    } else {
                        val leading = line.takeWhile { it == ' ' || it == '\t' }
                        val indent = toNbsp(leading)
                        out.append("<p>$indent${inline(line.substring(leading.length))}</p>")
                    }
                }
            }
        }
        if (code) out.append("</pre>")
        return out.append("</body></html>").toString()
    }

    private fun inline(text: String): String {
        var value = escape(text)
        // Protect inline code from the emphasis replacements.
        val code = mutableListOf<String>()
        value = INLINE_CODE_REGEX.replace(value) { code.add(it.groupValues[1]); "\u0001${code.lastIndex}\u0001" }
        value = BOLD_REGEX.replace(value, "<b>$1</b>")
        value = ITALIC_REGEX.replace(value, "<i>$1</i>")
        value = STRIKE_REGEX.replace(value, "<s>$1</s>")
        value = value.replace("&lt;u&gt;", "<u>").replace("&lt;/u&gt;", "</u>")
            .replace("&lt;mark&gt;", "<span style=\"background-color:#fff59d;color:#202020\">").replace("&lt;/mark&gt;", "</span>")
        return if (code.isEmpty()) value else CODE_RESTORE_REGEX.replace(value) { "<code>${code[it.groupValues[1].toInt()]}</code>" }
    }

    private fun sanitize(html: String): String {
        var value = html.replace(HEAD_TAG_REGEX, "")
            .replace(DANGEROUS_BLOCK_REGEX, "")
        value = HTML_TAG_REGEX.replace(value) { match ->
            val tag = match.groupValues[2].lowercase()
            if (tag !in ALLOWED_TAGS) "" else {
                val rawAttrs = match.groupValues[3]
                val attrs = if (rawAttrs.isBlank()) "" else SAFE_ATTR_REGEX.findAll(rawAttrs)
                    .filter { !it.groupValues[2].contains(UNSAFE_CSS_REGEX) }
                    .joinToString("") { " ${it.groupValues[1]}=\"${escape(it.groupValues[2])}\"" }
                "<${match.groupValues[1]}$tag$attrs>"
            }
        }
        return value
    }
}
