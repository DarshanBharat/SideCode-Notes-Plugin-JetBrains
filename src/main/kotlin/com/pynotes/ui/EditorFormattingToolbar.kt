package com.pynotes.ui

import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.text.*
import javax.swing.text.html.*

/** Compact, icon-rich formatting toolbar designed to fit seamlessly in narrow tool windows. */
class EditorFormattingToolbar(
    private val editor: JTextPane,
    private val checklist: () -> Unit
) : JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3))) {

    companion object {
        fun indent(editor: JTextPane, forward: Boolean) {
            val doc = editor.document
            val selStart = minOf(editor.selectionStart, editor.selectionEnd)
            val selEnd = maxOf(editor.selectionStart, editor.selectionEnd)
            val styled = editor.styledDocument

            val startElem = styled.getParagraphElement(selStart)
            val endOffset = if (selEnd > selStart) (selEnd - 1).coerceAtLeast(selStart) else selStart
            val endElem = styled.getParagraphElement(endOffset)

            val pStart = startElem.startOffset
            val pEnd = endElem.endOffset.coerceAtMost(doc.length)
            if (pEnd < pStart) return

            val text = doc.getText(pStart, pEnd - pStart)
            val lineStartOffsets = mutableListOf<Int>()
            lineStartOffsets.add(pStart)
            for (i in 0 until text.length) {
                if (text[i] == '\n' && pStart + i + 1 < pEnd) {
                    lineStartOffsets.add(pStart + i + 1)
                }
            }

            for (lineOffset in lineStartOffsets.reversed()) {
                val lineLen = doc.length - lineOffset
                val sampleLen = minOf(4, lineLen)
                val sample = if (sampleLen > 0) doc.getText(lineOffset, sampleLen) else ""

                if (forward) {
                    doc.insertString(lineOffset, "    ", null)
                } else {
                    val toRemove = when {
                        sample.startsWith("    ") -> 4
                        sample.startsWith("\t") -> 1
                        sample.startsWith("  ") -> 2
                        sample.startsWith(" ") -> 1
                        else -> 0
                    }
                    if (toRemove > 0) {
                        doc.remove(lineOffset, toRemove)
                    }
                }
            }
            editor.requestFocusInWindow()
        }

        fun insertNumbered(editor: JTextPane) {
            val doc = editor.document
            val selStart = minOf(editor.selectionStart, editor.selectionEnd)
            val selEnd = maxOf(editor.selectionStart, editor.selectionEnd)
            val styled = editor.styledDocument

            if (selStart != selEnd) {
                val startElem = styled.getParagraphElement(selStart)
                val endElem = styled.getParagraphElement((selEnd - 1).coerceAtLeast(selStart))
                val pStart = startElem.startOffset
                val pEnd = endElem.endOffset.coerceAtMost(doc.length)
                val text = doc.getText(pStart, pEnd - pStart)
                val lineStartOffsets = mutableListOf<Int>()
                lineStartOffsets.add(pStart)
                for (i in 0 until text.length) {
                    if (text[i] == '\n' && pStart + i + 1 < pEnd) {
                        lineStartOffsets.add(pStart + i + 1)
                    }
                }
                for ((idx, lineOffset) in lineStartOffsets.withIndex().reversed()) {
                    val currLine = doc.getText(lineOffset, minOf(30, doc.length - lineOffset))
                    val match = Regex("^(\\s*)(\\d+\\.|[-*•○●◉☐☑])\\s*").find(currLine)
                    val prefix = "${idx + 1}. "
                    if (match != null) {
                        doc.remove(lineOffset, match.value.length)
                        doc.insertString(lineOffset, match.groupValues[1] + prefix, null)
                    } else {
                        doc.insertString(lineOffset, prefix, null)
                    }
                }
                editor.requestFocusInWindow()
                return
            }

            val pos = editor.caretPosition
            val paragraph = styled.getParagraphElement(pos)
            var nextNum = 1
            if (paragraph.startOffset > 0) {
                val prevElem = styled.getParagraphElement(paragraph.startOffset - 1)
                val prevText = doc.getText(prevElem.startOffset, prevElem.endOffset - prevElem.startOffset).trim()
                val match = Regex("^(\\d+)\\.").find(prevText)
                if (match != null) {
                    nextNum = (match.groupValues[1].toIntOrNull() ?: 0) + 1
                }
            }
            val insertStr = if (pos > paragraph.startOffset) "\n$nextNum. " else "$nextNum. "
            editor.replaceSelection(insertStr)
            editor.requestFocusInWindow()
        }

        fun applyCode(editor: JTextPane) {
            val attrs = SimpleAttributeSet()
            val currentFont = StyleConstants.getFontFamily(editor.characterAttributes)
            val isCode = currentFont.equals("JetBrains Mono", ignoreCase = true) || currentFont.equals("Monospaced", ignoreCase = true)
            if (isCode) {
                StyleConstants.setFontFamily(attrs, "Dialog")
                val copied = SimpleAttributeSet(editor.characterAttributes)
                copied.removeAttribute(StyleConstants.Background)
                copied.removeAttribute(CSS.Attribute.BACKGROUND_COLOR)
                editor.setCharacterAttributes(copied, true)
            } else {
                StyleConstants.setFontFamily(attrs, "JetBrains Mono")
                val bg = Theme.chipBg
                (editor.document as HTMLDocument).styleSheet.addCSSAttribute(attrs, CSS.Attribute.BACKGROUND_COLOR, "#%02x%02x%02x".format(bg.red, bg.green, bg.blue))
                StyleConstants.setBackground(attrs, bg)
                editor.setCharacterAttributes(attrs, false)
            }
            editor.requestFocusInWindow()
        }

        fun insertTimestamp(editor: JTextPane) {
            val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            editor.replaceSelection("[$timeStr] ")
            editor.requestFocusInWindow()
        }
    }

    private var synchronizing = false
    private val fontSize = JSpinner(SpinnerNumberModel(14, 6, 200, 1))
    private val toggles = mutableListOf<Pair<JToggleButton, (AttributeSet) -> Boolean>>()

    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 0, 6, 0)

        fun separator() = JPanel().apply {
            preferredSize = Dimension(JBUI.scale(1), JBUI.scale(18))
            maximumSize = Dimension(JBUI.scale(1), JBUI.scale(18))
            background = Theme.cardBorder
            border = JBUI.Borders.empty(0, 3)
        }

        // 1. Font size
        val sizeGroup = group()
        fontSize.toolTipText = "Font size in points (6-200). Press Enter to apply."
        fontSize.accessibleContext.accessibleName = "Font size in points"
        fontSize.font = Theme.font(11.5f)
        fontSize.editor = JSpinner.NumberEditor(fontSize, "0")
        val sizeField = (fontSize.editor as JSpinner.NumberEditor).textField
        sizeField.columns = 2
        sizeField.font = Theme.font(11.5f)
        sizeField.focusLostBehavior = JFormattedTextField.COMMIT_OR_REVERT
        fontSize.addChangeListener {
            if (!synchronizing) {
                val attributes = SimpleAttributeSet()
                (editor.document as HTMLDocument).styleSheet.addCSSAttribute(
                    attributes, CSS.Attribute.FONT_SIZE, "${(fontSize.value as Number).toInt()}pt")
                editor.setCharacterAttributes(attributes, false)
            }
        }
        sizeField.addActionListener {
            try { fontSize.commitEdit(); editor.requestFocusInWindow() }
            catch (_: java.text.ParseException) { sizeField.value = fontSize.value }
        }
        sizeGroup.add(fontSize)
        sizeGroup.add(JLabel("pt").apply {
            font = Theme.font(10.5f)
            foreground = Theme.textMuted
            border = JBUI.Borders.empty(0, 2)
        })
        add(sizeGroup)
        add(separator())

        // 2. Bold / Italic / Underline / Strike
        val emphasis = group()
        fun toggle(text: String, label: String, test: (AttributeSet) -> Boolean, action: () -> Unit) {
            val button = object : JToggleButton(text) {
                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        val arc = JBUI.scale(6)
                        if (isSelected) {
                            g2.color = Theme.accent
                            g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                            foreground = Color.WHITE
                        } else if (model.isPressed) {
                            g2.color = Theme.pressed
                            g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                            foreground = Theme.textPrimary
                        } else if (model.isRollover) {
                            g2.color = Theme.hover
                            g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                            foreground = Theme.textPrimary
                        } else {
                            foreground = Theme.textPrimary
                        }
                    } finally { g2.dispose() }
                    super.paintComponent(g)
                }
            }.apply {
                configure(this, label)
                font = when (text) {
                    "B" -> font.deriveFont(Font.BOLD)
                    "i", "I" -> font.deriveFont(Font.ITALIC)
                    "U" -> font.deriveFont(mapOf(java.awt.font.TextAttribute.UNDERLINE to java.awt.font.TextAttribute.UNDERLINE_ON))
                    "S" -> font.deriveFont(mapOf(java.awt.font.TextAttribute.STRIKETHROUGH to java.awt.font.TextAttribute.STRIKETHROUGH_ON))
                    else -> font
                }
                addActionListener { action(); updateSelection() }
            }
            toggles.add(button to test)
            emphasis.add(button)
        }
        toggle("B", "Bold (Ctrl+B)", StyleConstants::isBold) { apply(StyledEditorKit.BoldAction()) }
        toggle("i", "Italic (Ctrl+I)", StyleConstants::isItalic) { apply(StyledEditorKit.ItalicAction()) }
        toggle("U", "Underline (Ctrl+U)", StyleConstants::isUnderline) { apply(StyledEditorKit.UnderlineAction()) }
        toggle("S", "Strikethrough", StyleConstants::isStrikeThrough) {
            val attrs = SimpleAttributeSet()
            StyleConstants.setStrikeThrough(attrs, !StyleConstants.isStrikeThrough(editor.characterAttributes))
            editor.setCharacterAttributes(attrs, false)
            editor.requestFocusInWindow()
        }
        add(emphasis)
        add(separator())

        // 3. Highlight color
        val highlightGroup = group()
        val highlightBtn = object : JButton(MarkerIcon()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val arc = JBUI.scale(6)
                    if (model.isPressed) {
                        g2.color = Theme.pressed
                        g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                    } else if (model.isRollover) {
                        g2.color = Theme.hover
                        g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                    }
                } finally { g2.dispose() }
                super.paintComponent(g)
            }
        }.apply {
            configure(this, "Highlight color")
            addActionListener {
                val menu = JPopupMenu()
                listOf("Yellow" to 0xfff59d, "Green" to 0xc8e6c9, "Blue" to 0xbbdefb, "Pink" to 0xf8bbd0).forEach { (label, rgb) ->
                    menu.add(JMenuItem(label, SwatchIcon(Color(rgb))).apply { addActionListener { highlight(Color(rgb)) } })
                }
                menu.addSeparator()
                menu.add(JMenuItem("Remove highlight").apply { addActionListener { highlight(null) } })
                menu.show(this, 0, height)
            }
        }
        highlightGroup.add(highlightBtn)
        add(highlightGroup)
        add(separator())

        // 4. Lists & Code group
        val insertGroup = group()
        fun makeIconButton(icon: Icon, tip: String, action: () -> Unit): JButton =
            object : JButton(icon) {
                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                        val arc = JBUI.scale(6)
                        if (model.isPressed) {
                            g2.color = Theme.pressed
                            g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                        } else if (model.isRollover) {
                            g2.color = Theme.hover
                            g2.fillRoundRect(1, 1, width - 2, height - 2, arc, arc)
                        }
                    } finally { g2.dispose() }
                    super.paintComponent(g)
                }
            }.apply {
                configure(this, tip)
                addActionListener { action() }
            }

        insertGroup.add(makeIconButton(ChecklistIcon(), "Insert checklist item") { checklist() })
        insertGroup.add(makeIconButton(BulletIcon(), "Insert bullet item") {
            val pos = editor.caretPosition
            val paragraph = editor.styledDocument.getParagraphElement(pos)
            insert(if (pos > paragraph.startOffset) "\n\u2022 " else "\u2022 ")
        })
        insertGroup.add(makeIconButton(NumberedIcon(), "Insert numbered item") { insertNumbered(editor) })
        insertGroup.add(makeIconButton(CodeIcon(), "Code / Monospace") { applyCode(editor) })
        add(insertGroup)
        add(separator())

        // 5. Indent & Date group
        val toolsGroup = group()
        toolsGroup.add(makeIconButton(IndentIcon(true), "Indent (Tab)") { indent(editor, true) })
        toolsGroup.add(makeIconButton(IndentIcon(false), "Outdent (Shift+Tab)") { indent(editor, false) })
        toolsGroup.add(makeIconButton(ClockIcon(), "Insert current timestamp") { insertTimestamp(editor) })
        add(toolsGroup)

        editor.addCaretListener { updateSelection() }
        updateSelection()
    }

    private fun group() = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }

    private fun configure(button: AbstractButton, label: String) {
        button.toolTipText = label
        button.accessibleContext.accessibleName = label
        button.isContentAreaFilled = false
        button.isBorderPainted = false
        button.isRolloverEnabled = true
        button.margin = JBUI.insets(2, 4)
        button.preferredSize = JBUI.size(26, 26)
    }

    private fun updateSelection() {
        if (synchronizing) return
        synchronizing = true
        try {
            val attrs = editor.characterAttributes
            toggles.forEach { (button, test) ->
                val active = test(attrs)
                if (button.isSelected != active) button.isSelected = active
            }
            val targetSize = StyleConstants.getFontSize(attrs).coerceIn(6, 200)
            if ((fontSize.value as? Number)?.toInt() != targetSize) {
                fontSize.value = targetSize
            }
        } finally { synchronizing = false }
    }

    private fun apply(action: Action) {
        action.actionPerformed(ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "format"))
        editor.requestFocusInWindow()
    }

    private fun insert(text: String) { editor.replaceSelection(text); editor.requestFocusInWindow() }

    private fun highlight(color: Color?) {
        val attrs = SimpleAttributeSet()
        (editor.document as HTMLDocument).styleSheet.addCSSAttribute(attrs, CSS.Attribute.BACKGROUND_COLOR,
            if (color == null) "transparent" else "#%02x%02x%02x".format(color.red, color.green, color.blue))
        if (color != null) {
            StyleConstants.setBackground(attrs, color)
            StyleConstants.setForeground(attrs, Color(0x202020))
        } else {
            val copied = SimpleAttributeSet(editor.characterAttributes)
            copied.removeAttribute(StyleConstants.Background)
            copied.removeAttribute(CSS.Attribute.BACKGROUND_COLOR)
            copied.removeAttribute(StyleConstants.Foreground)
            copied.removeAttribute(CSS.Attribute.COLOR)
            editor.setCharacterAttributes(copied, true)
            editor.requestFocusInWindow()
            return
        }
        editor.setCharacterAttributes(attrs, false)
        editor.requestFocusInWindow()
    }

    // --- Vector Icons ---

    private class SwatchIcon(private val color: Color) : Icon {
        override fun getIconWidth() = JBUI.scale(14)
        override fun getIconHeight() = JBUI.scale(14)
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            g.color = color; g.fillRoundRect(x, y, iconWidth, iconHeight, JBUI.scale(4), JBUI.scale(4))
        }
    }

    private class MarkerIcon : Icon {
        override fun getIconWidth() = JBUI.scale(16)
        override fun getIconHeight() = JBUI.scale(16)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 16.0, iconHeight / 16.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawPolygon(intArrayOf(4, 10, 14, 8), intArrayOf(9, 3, 7, 13), 4)
                g.drawLine(3, 10, 6, 13)
                g.color = Color(0xe6bb35)
                g.fillRoundRect(1, 14, 14, 2, 2, 2)
            } finally { g.dispose() }
        }
    }

    private class ChecklistIcon : Icon {
        override fun getIconWidth() = JBUI.scale(15)
        override fun getIconHeight() = JBUI.scale(15)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 15.0, iconHeight / 15.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawRoundRect(1, 1, 13, 13, 3, 3)
                g.drawLine(4, 7, 6, 10)
                g.drawLine(6, 10, 11, 4)
            } finally { g.dispose() }
        }
    }

    private class BulletIcon : Icon {
        override fun getIconWidth() = JBUI.scale(15)
        override fun getIconHeight() = JBUI.scale(15)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 15.0, iconHeight / 15.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.fillOval(1, 3, 3, 3)
                g.fillOval(1, 8, 3, 3)
                g.fillOval(1, 13, 3, 3)
                g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawLine(6, 4, 14, 4)
                g.drawLine(6, 9, 14, 9)
                g.drawLine(6, 14, 14, 14)
            } finally { g.dispose() }
        }
    }

    private class NumberedIcon : Icon {
        override fun getIconWidth() = JBUI.scale(15)
        override fun getIconHeight() = JBUI.scale(15)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 15.0, iconHeight / 15.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.font = Font(Font.SANS_SERIF, Font.BOLD, 9)
                g.drawString("1.", 0, 7)
                g.drawString("2.", 0, 14)
                g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawLine(8, 5, 14, 5)
                g.drawLine(8, 12, 14, 12)
            } finally { g.dispose() }
        }
    }

    private class CodeIcon : Icon {
        override fun getIconWidth() = JBUI.scale(15)
        override fun getIconHeight() = JBUI.scale(15)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 15.0, iconHeight / 15.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                // Left angle bracket <
                g.drawLine(4, 4, 1, 7)
                g.drawLine(1, 7, 4, 10)
                // Slash /
                g.drawLine(6, 12, 9, 3)
                // Right angle bracket >
                g.drawLine(11, 4, 14, 7)
                g.drawLine(14, 7, 11, 10)
            } finally { g.dispose() }
        }
    }

    private class IndentIcon(private val forward: Boolean) : Icon {
        override fun getIconWidth() = JBUI.scale(15)
        override fun getIconHeight() = JBUI.scale(15)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 15.0, iconHeight / 15.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                if (forward) {
                    g.drawLine(1, 3, 14, 3)
                    g.drawLine(6, 7, 14, 7)
                    g.drawLine(6, 11, 14, 11)
                    // Arrow ->
                    g.drawLine(1, 9, 4, 9)
                    g.drawLine(3, 7, 5, 9)
                    g.drawLine(3, 11, 5, 9)
                } else {
                    g.drawLine(1, 3, 14, 3)
                    g.drawLine(6, 7, 14, 7)
                    g.drawLine(6, 11, 14, 11)
                    // Arrow <-
                    g.drawLine(5, 9, 2, 9)
                    g.drawLine(3, 7, 1, 9)
                    g.drawLine(3, 11, 1, 9)
                }
            } finally { g.dispose() }
        }
    }

    private class ClockIcon : Icon {
        override fun getIconWidth() = JBUI.scale(15)
        override fun getIconHeight() = JBUI.scale(15)
        override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
            val g = graphics.create() as Graphics2D
            try {
                g.translate(x, y)
                g.scale(iconWidth / 15.0, iconHeight / 15.0)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = c.foreground
                g.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawOval(1, 1, 13, 13)
                g.drawLine(7, 4, 7, 7)
                g.drawLine(7, 7, 10, 7)
            } finally { g.dispose() }
        }
    }
}
