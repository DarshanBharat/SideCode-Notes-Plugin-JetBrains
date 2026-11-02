package com.pynotes

import com.pynotes.model.*

/** Standalone regression checks; no network or test framework required. */
object NoteRegression {
    @JvmStatic fun main(args: Array<String>) {
        val original = Note("Unicode and Markdown", NoteType.TEXT).apply {
            content = "## Investigation\n\n**Keep bold** and `code`\n\n```python\nprint('hello')\n```"
            isGlobal = true
            isPinned = true
            color = NoteColor.MINT
            addTag("Debug"); addTag("debug")
        }
        val restored = Note.parseMarkdown(original.toMarkdown(), "fallback")
        check(restored.id == original.id)
        check(restored.content == original.content)
        check(restored.title == original.title)
        check(restored.isGlobal && restored.isPinned)
        check(restored.color == NoteColor.MINT)
        check(restored.tags == listOf("debug"))
        val checklist = Note.parseMarkdown("# Tasks\n\n- [ ] Pending\n- [x] Complete", "tasks")
        checklist.convertToChecklist()
        check(checklist.checklistItems.size == 2)
        check(!checklist.checklistItems[0].isDone && checklist.checklistItems[1].isDone)
        val roundtrip = Note.parseMarkdown(checklist.toMarkdown(), "fallback")
        check(roundtrip.checklistItems.map { it.text to it.isDone } == checklist.checklistItems.map { it.text to it.isDone })
        roundtrip.convertToText()
        check(roundtrip.content == "- [ ] Pending\n- [x] Complete")
        check(restored.searchableText().contains("investigation"))
        val rendered = com.pynotes.ui.NoteFormatting.html("## Heading\n**Bold** and `**literal**`\n- [ ] Task\n- [x] Done")
        check(rendered.contains("<h2>Heading</h2>"))
        check(rendered.contains("<b>Bold</b>"))
        check(rendered.contains("<code>**literal**</code>"))
        check(rendered.contains("○ Task") && rendered.contains("● Done"))
        val hostile = com.pynotes.ui.NoteFormatting.html("<html><body><img src=\"https://example.com/tracker\"><script>alert(1)</script><b>Safe</b></body></html>")
        check(!hostile.contains("<img") && !hostile.contains("<script") && hostile.contains("<b>Safe</b>"))
        javax.swing.SwingUtilities.invokeAndWait {
            val pane = javax.swing.JTextPane().apply {
                editorKit = javax.swing.text.html.HTMLEditorKit()
                text = rendered
            }
            val taskOffset = pane.document.getText(0, pane.document.length).indexOf("Task")
            pane.select(taskOffset, taskOffset + 4)
            val attributes = javax.swing.text.SimpleAttributeSet()
            javax.swing.text.StyleConstants.setUnderline(attributes, true)
            javax.swing.text.StyleConstants.setBackground(attributes, java.awt.Color.YELLOW)
            (pane.document as javax.swing.text.html.HTMLDocument).styleSheet.addCSSAttribute(attributes, javax.swing.text.html.CSS.Attribute.BACKGROUND_COLOR, "#ffff00")
            pane.setCharacterAttributes(attributes, false)
            val serialized = com.pynotes.ui.NoteFormatting.serialize(pane.document as javax.swing.text.html.HTMLDocument)
            val rich = Note("Rich").apply { content = serialized }
            val saved = Note.parseMarkdown(rich.toMarkdown(), "fallback")
            java.io.File("build/rich-roundtrip.html").writeText(saved.content)
            check(saved.content.contains("<b>Bold</b>"))
            check(saved.plainContent().contains("Task"))
            val reopened = javax.swing.JTextPane().apply {
                editorKit = javax.swing.text.html.HTMLEditorKit()
                text = com.pynotes.ui.NoteFormatting.html(saved.content)
            }
            val reopenedOffset = reopened.document.getText(0, reopened.document.length).indexOf("Task")
            val reopenedStyle = reopened.styledDocument.getCharacterElement(reopenedOffset).attributes
            check(javax.swing.text.StyleConstants.isUnderline(reopenedStyle))
            check(javax.swing.text.StyleConstants.getBackground(reopenedStyle) == java.awt.Color.YELLOW)
            reopened.setSize(560, 400)
            val image = java.awt.image.BufferedImage(560, 400, java.awt.image.BufferedImage.TYPE_INT_RGB)
            image.createGraphics().let { graphics -> reopened.paint(graphics); graphics.dispose() }
            javax.imageio.ImageIO.write(image, "png", java.io.File("build/rich-editor-check.png"))
            val toolbar = com.pynotes.ui.EditorFormattingToolbar(reopened) { reopened.replaceSelection("\u25cb ") }
            reopened.select(reopenedOffset, reopenedOffset + 4)
            fun descendants(component: java.awt.Component): List<java.awt.Component> =
                listOf(component) + if (component is java.awt.Container) component.components.flatMap { descendants(it) } else emptyList()
            val bold = descendants(toolbar).filterIsInstance<javax.swing.JToggleButton>().first { it.text == "B" }
            bold.doClick()
            check(javax.swing.text.StyleConstants.isBold(reopened.styledDocument.getCharacterElement(reopenedOffset).attributes))
            val italic = descendants(toolbar).filterIsInstance<javax.swing.JToggleButton>().first { it.text == "i" }
            check(italic.toolTipText == "Italic")
            italic.doClick()
            check(javax.swing.text.StyleConstants.isItalic(reopened.styledDocument.getCharacterElement(reopenedOffset).attributes))
            val size = descendants(toolbar).filterIsInstance<javax.swing.JSpinner>().single()
            val sizeInput = (size.editor as javax.swing.JSpinner.NumberEditor).textField
            sizeInput.text = "27"
            sizeInput.postActionEvent()
            check(javax.swing.text.StyleConstants.getFontSize(reopened.styledDocument.getCharacterElement(reopenedOffset).attributes) == 27)
            val customHtml = com.pynotes.ui.NoteFormatting.serialize(reopened.document as javax.swing.text.html.HTMLDocument)
            val customReopened = javax.swing.JTextPane().apply {
                editorKit = javax.swing.text.html.HTMLEditorKit()
                text = com.pynotes.ui.NoteFormatting.html(customHtml)
            }
            val customOffset = customReopened.document.getText(0, customReopened.document.length).indexOf("Task")
            check(javax.swing.text.StyleConstants.getFontSize(customReopened.styledDocument.getCharacterElement(customOffset).attributes) == 27)
            check(descendants(toolbar).filterIsInstance<javax.swing.JButton>().none { it.text == "Insert" })
            val direct = descendants(toolbar).filterIsInstance<javax.swing.JButton>()
            check(listOf("Checklist", "Bullet", "Numbered", "Indent", "Outdent").all { name -> direct.any { it.text == name } })
            check(direct.none { it.text == "Code" || it.text == "Date/time" })
            reopened.caretPosition = reopened.document.length
            val lengthBefore = reopened.document.length
            direct.single { it.text == "Bullet" }.doClick()
            check(reopened.document.length > lengthBefore)
            check(reopened.document.getText(0, reopened.document.length).contains("\u2022"))
            val lenBeforeNum = reopened.document.length
            direct.single { it.text == "Numbered" }.doClick()
            check(reopened.document.length > lenBeforeNum)
            check(reopened.document.getText(0, reopened.document.length).contains("1. "))
            val lenBeforeIndent = reopened.document.length
            direct.single { it.text == "Indent" }.doClick()
            check(reopened.document.length == lenBeforeIndent + 4)
            direct.single { it.text == "Outdent" }.doClick()
            check(reopened.document.length == lenBeforeIndent)
            val checkBtn = direct.single { it.text == "Checklist" }
            val lenBeforeCheck = reopened.document.length
            checkBtn.doClick()
            check(reopened.document.length > lenBeforeCheck)
            check(reopened.document.getText(0, reopened.document.length).contains("\u25cb"))
            val formattedNum = com.pynotes.ui.NoteFormatting.html("1. First\n2. Second\n    3. Indented")
            check(formattedNum.contains("1. First"))
            check(formattedNum.contains("2. Second"))
            check(formattedNum.contains("&nbsp;&nbsp;&nbsp;&nbsp;3. Indented"))
            reopened.document.insertString(reopened.document.length, "\nApple\nBanana", null)
            val applePos = reopened.document.getText(0, reopened.document.length).indexOf("Apple")
            reopened.select(applePos, reopened.document.length)
            com.pynotes.ui.EditorFormattingToolbar.insertNumbered(reopened)
            check(reopened.document.getText(0, reopened.document.length).contains("1. Apple"))
            check(reopened.document.getText(0, reopened.document.length).contains("2. Banana"))
            reopened.select(applePos, reopened.document.length)
            com.pynotes.ui.EditorFormattingToolbar.indent(reopened, true)
            check(reopened.document.getText(0, reopened.document.length).contains("    1. Apple"))
            check(reopened.document.getText(0, reopened.document.length).contains("    2. Banana"))
            com.pynotes.ui.EditorFormattingToolbar.indent(reopened, false)
            check(reopened.document.getText(0, reopened.document.length).contains("1. Apple"))
            check(reopened.document.getText(0, reopened.document.length).contains("2. Banana"))
            val searchFirst = saved.searchableText()
            val searchSecond = saved.searchableText()
            check(searchFirst === searchSecond)
            for (width in listOf(280, 640)) {
                toolbar.setSize(width, 100)
                val height = toolbar.preferredSize.height
                toolbar.setSize(width, height)
                toolbar.doLayout()
                check(toolbar.components.all { it.x >= 0 && it.x + it.width <= width })
                val surface = javax.swing.JPanel(java.awt.BorderLayout())
                surface.add(toolbar, java.awt.BorderLayout.NORTH)
                surface.add(reopened, java.awt.BorderLayout.CENTER)
                surface.setSize(width, 350)
                surface.doLayout()
                descendants(surface).filterIsInstance<java.awt.Container>().forEach { it.doLayout() }
                val preview = java.awt.image.BufferedImage(width, 350, java.awt.image.BufferedImage.TYPE_INT_RGB)
                preview.createGraphics().let { graphics -> surface.paint(graphics); graphics.dispose() }
                javax.imageio.ImageIO.write(preview, "png", java.io.File("build/toolbar-$width.png"))
            }
            check(!saved.previewText().contains("<html>"))
            check(!com.pynotes.ui.NoteFormatting.html(saved.content).contains("id:"))
        }
        javax.swing.SwingUtilities.invokeAndWait {
            val note = Note("Shared title").apply { content = "**Live** edits" }
            var writes = 0
            val live = com.pynotes.editor.LiveNoteSession(note) { writes++; null }
            fun view() = javax.swing.JTextPane().apply {
                editorKit = javax.swing.text.html.HTMLEditorKit()
                document = live.document
            }
            val side = view()
            val main = view()
            val sideTitle = javax.swing.JTextField().apply { document = live.titleDocument }
            val mainTitle = javax.swing.JTextField().apply { document = live.titleDocument }
            var updates = 0
            val listener: () -> Unit = { updates++ }
            live.addListener(listener)
            check(!live.dirty && writes == 0)
            val originalDocument = main.document
            main.caretPosition = 1
            val start = side.document.length
            side.document.insertString(start, " from sidebar", null)
            // Check before autosave or another event-loop turn: sync must be synchronous.
            check(main.document.getText(0, main.document.length).contains("from sidebar"))
            check(main.document === originalDocument && main.caretPosition == 1)
            main.document.insertString(main.document.length, " from main", null)
            check(side.document.getText(0, side.document.length).contains("from main"))
            val attrs = javax.swing.text.SimpleAttributeSet()
            javax.swing.text.StyleConstants.setBold(attrs, true)
            main.select(1, 5)
            main.setCharacterAttributes(attrs, false)
            check(javax.swing.text.StyleConstants.isBold(side.styledDocument.getCharacterElement(1).attributes))
            mainTitle.text = "Title changed from main"
            check(sideTitle.text == mainTitle.text && note.title == mainTitle.text)
            sideTitle.text = "Title changed from sidebar"
            check(mainTitle.text == sideTitle.text)
            val updatesBeforeColor = updates
            live.setColor(NoteColor.CORAL)
            check(note.color == NoteColor.CORAL && updates > updatesBeforeColor)
            check(writes == 0 && live.dirty && updates > 1)
            live.flush()
            check(writes == 1 && !live.dirty)
            check(note.plainContent().contains("from main"))
            check(Note.parseMarkdown(note.toMarkdown(), note.id).color == NoteColor.CORAL)
            live.flush()
            check(writes == 1)
            check(live.undo.canUndo())
            live.undo.undo()
            check(side.document === main.document)
            check(side.document.getText(0, side.document.length) == main.document.getText(0, main.document.length))
            if (live.undo.canRedo()) live.undo.redo()
            live.flush()
            live.removeListener(listener)
            val updatesBefore = updates
            live.changed()
            check(updates == updatesBefore)
            live.flush()
            var fail = true
            val retry = com.pynotes.editor.LiveNoteSession(Note("Retry")) { if (fail) "Disk unavailable" else null }
            retry.changed(); retry.flush()
            check(retry.dirty && retry.error == "Disk unavailable")
            fail = false
            retry.flush()
            check(!retry.dirty && retry.error == null)
        }
        println("PASS: immediate two-way text/title/formatting sync, stable document/caret, shared undo, coalesced saves and retry")
        println("PASS: rich formatting, mixed checklists, sanitization, HTML persistence and previews")
        println("PASS: Markdown persistence, identity, scope, tags, checklist conversion and search")
    }
}
