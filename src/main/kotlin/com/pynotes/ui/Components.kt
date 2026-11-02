package com.pynotes.ui

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.pynotes.model.NoteColor
import java.awt.*
import java.awt.event.*
import java.awt.font.TextAttribute
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent
import kotlin.math.max
import kotlin.math.min

/** Components that need to know their width before they can report a preferred height. */
interface HeightForWidth {
    fun heightForWidth(width: Int): Int
}

object Theme {
    val textPrimary = JBColor.namedColor("Label.foreground", JBColor.BLACK)
    val textSecondary = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
    val textMuted = JBColor.namedColor("Label.infoForeground", JBColor(Color(0x787878), Color(0x9E9E9E)))
    val border = JBColor.namedColor("Component.borderColor", JBColor(Color(0xE2E4E7), Color(0x393B40)))
    val borderStrong = JBColor.namedColor("Component.focusColor", JBColor.BLUE)
    val cardBorder = JBColor(Color(0, 0, 0, 20), Color(255, 255, 255, 24))
    val cardBorderHover = JBColor(Color(0x3574F0), Color(0x548AF7))
    val cardShadow = JBColor(Color(0, 0, 0, 10), Color(0, 0, 0, 35))
    val fieldBg = JBColor.namedColor("TextField.background", JBColor.WHITE)
    val hover = JBColor(Color(0, 0, 0, 14), Color(255, 255, 255, 18))
    val pressed = JBColor(Color(0, 0, 0, 28), Color(255, 255, 255, 32))
    val chipBg = JBColor(Color(0, 0, 0, 12), Color(255, 255, 255, 16))
    val chipBgHover = JBColor(Color(0, 0, 0, 24), Color(255, 255, 255, 28))
    val accent = JBColor.namedColor("Component.focusColor", JBColor(Color(0x3574F0), Color(0x548AF7)))
    val accentHover = JBColor(Color(0x2B63D3), Color(0x6E9CF8))
    val success = JBColor(Color(0x2E9E5B), Color(0x57C27E))
    val transparent = Color(0, 0, 0, 0)

    val STROKE_1 = BasicStroke(1.0f)
    val STROKE_1_2 = BasicStroke(1.2f)
    val STROKE_1_5 = BasicStroke(1.5f)
    val STROKE_2 = BasicStroke(2.0f)
    val STROKE_CAP_1_3 = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.CAP_ROUND)

    fun font(size: Float, bold: Boolean = false): Font {
        val f = JBUI.Fonts.label(size)
        return if (bold) f.asBold() else f
    }
}

fun Font.struck(): Font = deriveFont(mapOf(TextAttribute.STRIKETHROUGH to TextAttribute.STRIKETHROUGH_ON))

fun Graphics.paint2d(block: (Graphics2D) -> Unit) {
    val g2 = create() as Graphics2D
    try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        UISettings.setupAntialiasing(g2)
        block(g2)
    } finally {
        g2.dispose()
    }
}

fun isPointerInside(c: Component): Boolean {
    if (!c.isShowing) return false
    val p = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return false
    SwingUtilities.convertPointFromScreen(p, c)
    return p.x in 0 until c.width && p.y in 0 until c.height
}

/** Tracks hover over [root] and all of its current descendants without flicker when moving between children. */
fun installHover(root: JComponent, onChange: (Boolean) -> Unit) {
    var hovered = false
    val listener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            if (!hovered) {
                hovered = true
                onChange(true)
            }
        }

        override fun mouseExited(e: MouseEvent) {
            if (!hovered) return
            val src = e.component
            val stillInside = if (src != null && root.isShowing) {
                val pt = SwingUtilities.convertPoint(src, e.point, root)
                pt.x in 0 until root.width && pt.y in 0 until root.height
            } else {
                isPointerInside(root)
            }
            if (!stillInside) {
                hovered = false
                onChange(false)
            }
        }
    }

    fun attach(c: Component) {
        c.addMouseListener(listener)
        if (c is Container) c.components.forEach { attach(it) }
    }
    attach(root)
}

fun JTextComponent.onTextChange(block: () -> Unit) {
    document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = block()
    })
}

/** A transparent, borderless text field. Height comes from its font, so text is never clipped. */
fun borderlessField(text: String = "", placeholder: String = "", font: Font = Theme.font(13f)): JBTextField =
    JBTextField(text).apply {
        this.font = font
        emptyText.text = placeholder
        border = JBUI.Borders.empty(3, 2)
        isOpaque = false
        background = Theme.transparent
        foreground = Theme.textPrimary
    }

/** A FlowLayout panel that wraps onto multiple lines and re-measures itself when resized. */
fun wrapPanel(hgap: Int, vgap: Int): JPanel = JPanel(WrapLayout(FlowLayout.LEFT, hgap, vgap)).apply {
    isOpaque = false
    addComponentListener(object : ComponentAdapter() {
        private var lastWidth = -1
        override fun componentResized(e: ComponentEvent) {
            if (width != lastWidth) {
                lastWidth = width
                SwingUtilities.invokeLater { revalidate() }
            }
        }
    })
}

class Debouncer(delayMs: Int, private val action: () -> Unit) {
    private val timer = Timer(delayMs) { action() }.apply { isRepeats = false }
    fun poke() = timer.restart()
    fun cancel() = timer.stop()
    fun flush() {
        if (timer.isRunning) {
            timer.stop()
            action()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Clickable primitives
// ---------------------------------------------------------------------------------------------

abstract class ClickableComponent : JComponent() {
    protected var hovered = false
    protected var pressed = false
    var onAction: (() -> Unit)? = null

    init {
        isOpaque = false
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        for (key in listOf("ENTER", "SPACE")) {
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "activate")
        }
        actionMap.put("activate", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                if (isEnabled && (this@ClickableComponent !is IconButton || this@ClickableComponent.revealed)) onAction?.invoke()
            }
        })
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent) { repaint() }
            override fun focusLost(e: java.awt.event.FocusEvent) { repaint() }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hovered = true
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                hovered = false
                pressed = false
                repaint()
            }

            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressed = true
                    repaint()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                val fire = isEnabled && pressed && contains(e.point)
                pressed = false
                repaint()
                if (fire) handleClick(e)
            }
        })
    }

    protected open fun handleClick(e: MouseEvent) {
        onAction?.invoke()
    }

    override fun paintBorder(g: Graphics) {
        if (hasFocus()) {
            g.color = Theme.accent
            g.drawRoundRect(1, 1, width - 3, height - 3, JBUI.scale(8), JBUI.scale(8))
        }
    }

    override fun getMinimumSize(): Dimension = preferredSize
    override fun getMaximumSize(): Dimension = preferredSize
}

enum class NotesViewMode {
    GRID,
    LIST
}

class IconButton(
    icon: Icon,
    private var tip: String,
    private val box: Int = 26,
    action: (() -> Unit)? = null
) : ClickableComponent() {

    var icon: Icon = icon
        set(value) {
            if (field !== value) {
                field = value
                repaint()
            }
        }

    /** When false the button keeps its space in the layout but is invisible and inert (hover-reveal). */
    var revealed = true
        set(value) {
            if (field == value) return
            field = value
            toolTipText = if (value) tip else null
            cursor = Cursor.getPredefinedCursor(if (value) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
            repaint()
        }

    var selected: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                repaint()
            }
        }

    var tooltip: String
        get() = tip
        set(value) {
            if (tip == value) return
            tip = value
            toolTipText = value
            accessibleContext?.accessibleName = value
        }

    init {
        toolTipText = tip
        accessibleContext?.accessibleName = tip
        onAction = action
    }

    override fun handleClick(e: MouseEvent) {
        if (revealed) onAction?.invoke()
    }

    override fun getPreferredSize(): Dimension = JBUI.size(box, box)

    override fun paintComponent(g: Graphics) {
        if (!revealed) return
        g.paint2d { g2 ->
            val arc = JBUI.scale(8)
            if (selected) {
                g2.color = Theme.accent
                g2.fillRoundRect(0, 0, width, height, arc, arc)
            } else if (hovered || pressed) {
                g2.color = if (pressed) Theme.pressed else Theme.hover
                g2.fillRoundRect(0, 0, width, height, arc, arc)
            }
        }
        icon.paintIcon(this, g, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
    }
}

class PillButton(
    text: String,
    private val kind: Kind = Kind.SECONDARY,
    icon: Icon? = null,
    action: (() -> Unit)? = null
) : ClickableComponent() {

    enum class Kind { PRIMARY, SECONDARY, GHOST }

    var icon: Icon? = icon
        set(value) {
            if (field !== value) {
                field = value
                revalidate()
                repaint()
            }
        }

    var text: String = text
        set(value) {
            if (field != value) {
                field = value
                revalidate()
                repaint()
            }
        }

    var textFont: Font = Theme.font(12f, kind == Kind.PRIMARY)
    var foregroundColor: Color? = null
    var underline: Boolean = false

    init {
        onAction = action
    }

    private fun iconSpace(): Int = icon?.let { it.iconWidth + if (text.isNotEmpty()) JBUI.scale(5) else 0 } ?: 0

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(textFont)
        val padX = JBUI.scale(if (kind == Kind.GHOST) 7 else 12)
        val h = JBUI.scale(if (kind == Kind.GHOST) 24 else 28)
        return Dimension(fm.stringWidth(text) + iconSpace() + padX * 2, h)
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val bg: Color? = when (kind) {
                Kind.PRIMARY -> if (hovered) Theme.accentHover else Theme.accent
                Kind.SECONDARY -> if (pressed) Theme.pressed else if (hovered) Theme.chipBgHover else Theme.chipBg
                Kind.GHOST -> if (pressed) Theme.pressed else if (hovered) Theme.hover else null
            }
            if (bg != null) {
                g2.color = bg
                val arc = if (kind == Kind.GHOST) JBUI.scale(8) else height
                g2.fillRoundRect(0, 0, width, height, arc, arc)
            }
            g2.font = textFont
            val fm = g2.fontMetrics
            val contentW = fm.stringWidth(text) + iconSpace()
            var x = (width - contentW) / 2
            icon?.let {
                it.paintIcon(this, g2, x, (height - it.iconHeight) / 2)
                x += iconSpace()
            }
            g2.color = foregroundColor ?: if (kind == Kind.PRIMARY) Color.WHITE else Theme.textPrimary
            val textY = (height - fm.height) / 2 + fm.ascent
            g2.drawString(text, x, textY)
            if (underline) {
                g2.stroke = BasicStroke(JBUIScale.scale(1.2f))
                g2.drawLine(x, textY + JBUI.scale(2), x + fm.stringWidth(text), textY + JBUI.scale(2))
            }
        }
    }
}

class Chip(
    text: String,
    selected: Boolean = false,
    private val removable: Boolean = false,
    var marginRight: Int = 0
) : ClickableComponent() {

    var text: String = text
        set(value) {
            if (field != value) {
                field = value
                revalidate()
                repaint()
            }
        }

    var selected: Boolean = selected
        set(value) {
            if (field != value) {
                field = value
                repaint()
            }
        }

    var onRemove: (() -> Unit)? = null
    private val chipFont = Theme.font(11f)

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(chipFont)
        val extra = if (removable) JBUI.scale(14) else 0
        return Dimension(fm.stringWidth(text) + JBUI.scale(20) + extra + JBUI.scale(marginRight), JBUI.scale(22))
    }

    override fun handleClick(e: MouseEvent) {
        val activeWidth = width - JBUI.scale(marginRight)
        if (e.x > activeWidth) return
        if (removable && e.x > activeWidth - JBUI.scale(20)) onRemove?.invoke() else onAction?.invoke()
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val w = width - JBUI.scale(marginRight)
            g2.color = when {
                selected -> Theme.accent
                hovered -> Theme.chipBgHover
                else -> Theme.chipBg
            }
            g2.fillRoundRect(0, 0, w, height, height, height)
            if (!selected) {
                g2.color = Theme.cardBorder
                g2.stroke = Theme.STROKE_1
                g2.drawRoundRect(0, 0, w - 1, height - 1, height, height)
            }
            g2.font = chipFont
            val fm = g2.fontMetrics
            g2.color = if (selected) Color.WHITE else Theme.textSecondary
            g2.drawString(text, JBUI.scale(10), (height - fm.height) / 2 + fm.ascent)
            if (removable) {
                g2.color = if (selected) Color.WHITE else if (hovered) Theme.textPrimary else Theme.textMuted
                g2.stroke = Theme.STROKE_CAP_1_3
                val cx = w - JBUI.scale(13)
                val cy = height / 2
                val r = JBUI.scale(3)
                g2.drawLine(cx - r, cy - r, cx + r, cy + r)
                g2.drawLine(cx - r, cy + r, cx + r, cy - r)
            }
        }
    }
}

class InlineTagInput(
    private val onAddTag: (String) -> Unit
) : JPanel(BorderLayout()) {
    private var isEditing = false
    private val addChip = Chip("+ Tag", selected = false, marginRight = 4).apply {
        onAction = { startEditing() }
    }
    private val textField = JBTextField().apply {
        font = Theme.font(11f)
        emptyText.text = "tag name..."
        border = JBUI.Borders.empty(2, 6)
        preferredSize = JBUI.size(80, 22)
        addActionListener {
            val t = text.trim().removePrefix("#")
            if (t.isNotEmpty()) {
                onAddTag(t)
            }
            stopEditing()
        }
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel")
        actionMap.put("cancel", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) { stopEditing() }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                val t = text.trim().removePrefix("#")
                if (t.isNotEmpty()) {
                    onAddTag(t)
                }
                stopEditing()
            }
        })
    }

    init {
        isOpaque = false
        add(addChip, BorderLayout.CENTER)
    }

    fun startEditing() {
        if (isEditing) return
        isEditing = true
        removeAll()
        textField.text = ""
        add(textField, BorderLayout.CENTER)
        revalidate()
        repaint()
        SwingUtilities.invokeLater { textField.requestFocusInWindow() }
    }

    fun stopEditing() {
        if (!isEditing) return
        isEditing = false
        removeAll()
        add(addChip, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
}

class SegmentedControl(
    val items: List<String>,
    initialIndex: Int = 0,
    private val onSelectionChange: (Int, String) -> Unit
) : JComponent() {
    var selectedIndex: Int = initialIndex
        set(value) {
            if (field != value && value in items.indices) {
                field = value
                repaint()
                onSelectionChange(value, items[value])
            }
        }

    private var hoveredIndex: Int = -1
    private val controlFont = Theme.font(11.5f)

    init {
        isOpaque = false
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val idx = indexAt(e.x)
                if (hoveredIndex != idx) {
                    hoveredIndex = idx
                    repaint()
                }
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                if (hoveredIndex != -1) {
                    hoveredIndex = -1
                    repaint()
                }
            }
            override fun mousePressed(e: MouseEvent) {
                val idx = indexAt(e.x)
                if (idx != -1 && idx != selectedIndex) {
                    selectedIndex = idx
                }
            }
        })
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "prev")
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("RIGHT"), "next")
        actionMap.put("prev", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (selectedIndex > 0) selectedIndex--
            }
        })
        actionMap.put("next", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (selectedIndex < items.size - 1) selectedIndex++
            }
        })
    }

    private fun indexAt(x: Int): Int {
        if (width <= 0 || items.isEmpty()) return -1
        val segW = width / items.size
        return (x / segW).coerceIn(0, items.size - 1)
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(controlFont)
        val maxTextW = items.maxOfOrNull { fm.stringWidth(it) } ?: 40
        val totalW = (maxTextW + JBUI.scale(22)) * items.size
        return Dimension(totalW, JBUI.scale(26))
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val arc = JBUI.scale(8)
            val w = width
            val h = height

            g2.color = Theme.chipBg
            g2.fillRoundRect(0, 0, w, h, arc, arc)
            g2.color = Theme.cardBorder
            g2.stroke = Theme.STROKE_1
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc)

            if (items.isEmpty()) return@paint2d
            val segW = w / items.size
            g2.font = controlFont
            val fm = g2.fontMetrics

            for (i in items.indices) {
                val x = i * segW
                val isSel = i == selectedIndex
                val isHov = i == hoveredIndex && !isSel

                if (isSel) {
                    val thumbArc = JBUI.scale(6)
                    val inset = JBUI.scale(2)
                    g2.color = Theme.fieldBg
                    g2.fillRoundRect(x + inset, inset, segW - inset * 2, h - inset * 2, thumbArc, thumbArc)
                    g2.color = Theme.cardBorder
                    g2.drawRoundRect(x + inset, inset, segW - inset * 2, h - inset * 2, thumbArc, thumbArc)
                } else if (isHov) {
                    val thumbArc = JBUI.scale(6)
                    val inset = JBUI.scale(2)
                    g2.color = Theme.hover
                    g2.fillRoundRect(x + inset, inset, segW - inset * 2, h - inset * 2, thumbArc, thumbArc)
                }

                val text = items[i]
                val tw = fm.stringWidth(text)
                val tx = x + (segW - tw) / 2
                val ty = (h - fm.height) / 2 + fm.ascent

                g2.color = if (isSel) Theme.textPrimary else Theme.textSecondary
                if (isSel) g2.font = controlFont.deriveFont(Font.BOLD) else g2.font = controlFont
                g2.drawString(text, tx, ty)
            }
        }
    }
}

/** Apple-Notes style circular checkbox. */
class RoundCheck(
    checked: Boolean,
    private val box: Int = 18,
    private val onToggle: (Boolean) -> Unit
) : ClickableComponent() {

    var checked: Boolean = checked
        set(value) {
            if (field != value) {
                field = value
                repaint()
            }
        }

    init {
        onAction = {
            this.checked = !this.checked
            onToggle(this.checked)
        }
    }

    override fun getPreferredSize(): Dimension = JBUI.size(box + 6, box + 6)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val s = JBUI.scale(box).toFloat()
            val x = (width - s) / 2f
            val y = (height - s) / 2f
            if (checked) {
                g2.color = Theme.accent
                g2.fill(Ellipse2D.Float(x, y, s, s))
                g2.color = Color.WHITE
                g2.stroke = BasicStroke(max(1.4f, s / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val p = Path2D.Float()
                p.moveTo(x + s * 0.28f, y + s * 0.52f)
                p.lineTo(x + s * 0.44f, y + s * 0.68f)
                p.lineTo(x + s * 0.73f, y + s * 0.35f)
                g2.draw(p)
            } else {
                val o = JBUIScale.scale(0.75f)
                g2.color = if (hovered) Theme.accent else Theme.borderStrong
                g2.stroke = BasicStroke(JBUIScale.scale(1.5f))
                g2.draw(Ellipse2D.Float(x + o, y + o, s - 2 * o, s - 2 * o))
            }
        }
    }
}

open class RoundedPanel(
    layout: LayoutManager = BorderLayout(),
    var fill: Color? = null,
    var stroke: Color? = null,
    var arc: Int = 10
) : JPanel(layout) {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val a = JBUI.scale(arc).toFloat()
            val shape = RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, a, a)
            fill?.let {
                g2.color = it
                g2.fill(shape)
            }
            stroke?.let {
                g2.color = it
                g2.stroke = BasicStroke(JBUIScale.scale(1f))
                g2.draw(shape)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Layouts
// ---------------------------------------------------------------------------------------------

/**
 * Vertical stack that gives every child the full width and asks it for its height at that width.
 * With maxColumns > 1 it becomes a masonry grid (Google Keep style) once there is room.
 * Children with client property [FULL_WIDTH] always span all columns (section headers).
 */
open class StackPanel(
    var maxColumns: Int = 1,
    var minColumnWidth: Int = 220,
    var gap: Int = 8
) : JPanel(null), Scrollable {

    companion object {
        const val FULL_WIDTH = "sidenotes.fullWidth"
    }

    init {
        isOpaque = false
        layout = object : LayoutManager {
            override fun addLayoutComponent(name: String?, comp: Component?) {}
            override fun removeLayoutComponent(comp: Component?) {}
            override fun preferredLayoutSize(parent: Container): Dimension {
                val w = if (parent.width > 0) parent.width else JBUI.scale(320)
                return Dimension(w, compute(w, false))
            }

            override fun minimumLayoutSize(parent: Container): Dimension = Dimension(JBUI.scale(120), 0)
            override fun layoutContainer(parent: Container) {
                compute(parent.width, true)
            }
        }
        addComponentListener(object : ComponentAdapter() {
            private var lastWidth = -1
            override fun componentResized(e: ComponentEvent) {
                val w = this@StackPanel.width
                if (w != lastWidth) {
                    lastWidth = w
                    revalidate()
                }
            }
        })
    }

    private var cachedWidth = -1
    private var cachedTotalHeight = 0
    private var cachedBounds = mutableListOf<Rectangle>()
    private var cachedComponentsHash = 0

    fun columnCount(availableWidth: Int): Int {
        if (maxColumns <= 1) return 1
        val g = JBUI.scale(gap)
        return max(1, min(maxColumns, (availableWidth + g) / (JBUI.scale(minColumnWidth) + g)))
    }

    private fun compute(totalWidth: Int, apply: Boolean): Int {
        val ins = insets
        val avail = max(1, totalWidth - ins.left - ins.right)
        val g = JBUI.scale(gap)
        val cols = columnCount(avail)
        val colW = (avail - g * (cols - 1)) / cols

        var compsHash = 1
        var visibleCount = 0
        for (c in components) {
            if (c.isVisible) {
                compsHash = 31 * compsHash + System.identityHashCode(c)
                visibleCount++
            }
        }

        if (totalWidth == cachedWidth && compsHash == cachedComponentsHash && cachedBounds.size == visibleCount) {
            if (apply) {
                var idx = 0
                for (c in components) {
                    if (!c.isVisible) continue
                    if (idx < cachedBounds.size) {
                        val b = cachedBounds[idx++]
                        c.setBounds(b.x, b.y, b.width, b.height)
                    }
                }
            }
            return cachedTotalHeight
        }

        val bottoms = IntArray(cols) { ins.top }
        var count = 0
        val newBounds = if (apply) null else mutableListOf<Rectangle>()
        for (c in components) {
            if (!c.isVisible) continue
            val full = cols == 1 || (c as? JComponent)?.getClientProperty(FULL_WIDTH) == true
            if (full) {
                val y = bottoms.max()
                val h = heightOf(c, avail)
                if (apply) {
                    c.setBounds(ins.left, y, avail, h)
                } else {
                    newBounds?.add(Rectangle(ins.left, y, avail, h))
                }
                bottoms.fill(y + h + g)
            } else {
                var col = 0
                for (i in 1 until cols) if (bottoms[i] < bottoms[col]) col = i
                val h = heightOf(c, colW)
                val x = ins.left + col * (colW + g)
                val y = bottoms[col]
                if (apply) {
                    c.setBounds(x, y, colW, h)
                } else {
                    newBounds?.add(Rectangle(x, y, colW, h))
                }
                bottoms[col] += h + g
            }
            count++
        }
        val computedHeight = bottoms.max() - (if (count > 0) g else 0) + ins.bottom
        if (!apply && newBounds != null) {
            cachedWidth = totalWidth
            cachedTotalHeight = computedHeight
            cachedBounds = newBounds
            cachedComponentsHash = compsHash
        }
        return computedHeight
    }

    private fun heightOf(c: Component, w: Int): Int {
        if (c is HeightForWidth) return c.heightForWidth(w)
        if (c.width != w) c.setSize(w, max(c.height, 1))
        return c.preferredSize.height
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
        max(visibleRect.height - JBUI.scale(32), JBUI.scale(16))

    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

/** FlowLayout that wraps and reports the correct multi-line preferred height. */
class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)
    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension = synchronized(target.treeLock) {
        val targetWidth = if (target.width > 0) target.width else Int.MAX_VALUE / 2
        val ins = target.insets
        val maxWidth = targetWidth - (ins.left + ins.right + hgap * 2)
        val dim = Dimension(0, 0)
        var rowWidth = 0
        var rowHeight = 0
        for (m in target.components) {
            if (!m.isVisible) continue
            val d = if (preferred) m.preferredSize else m.minimumSize
            if (rowWidth > 0 && rowWidth + hgap + d.width > maxWidth) {
                addRow(dim, rowWidth, rowHeight)
                rowWidth = 0
                rowHeight = 0
            }
            if (rowWidth != 0) rowWidth += hgap
            rowWidth += d.width
            rowHeight = max(rowHeight, d.height)
        }
        addRow(dim, rowWidth, rowHeight)
        dim.width += ins.left + ins.right + hgap * 2
        dim.height += ins.top + ins.bottom + vgap * 2
        dim
    }

    private fun addRow(dim: Dimension, rowWidth: Int, rowHeight: Int) {
        dim.width = max(dim.width, rowWidth)
        if (dim.height > 0) dim.height += vgap
        dim.height += rowHeight
    }
}

// ---------------------------------------------------------------------------------------------
// Color picker
// ---------------------------------------------------------------------------------------------

class ColorSwatch(
    private val noteColor: NoteColor,
    private val selected: Boolean,
    action: () -> Unit
) : ClickableComponent() {

    init {
        onAction = action
        toolTipText = noteColor.displayName
    }

    override fun getPreferredSize(): Dimension = JBUI.size(30, 30)

    override fun paintComponent(g: Graphics) {
        g.paint2d { g2 ->
            val s = JBUI.scale(22).toFloat()
            val x = (width - s) / 2f
            val y = (height - s) / 2f
            g2.color = noteColor.background
            g2.fill(Ellipse2D.Float(x, y, s, s))
            g2.stroke = BasicStroke(JBUIScale.scale(1f))
            g2.color = Theme.border
            g2.draw(Ellipse2D.Float(x, y, s, s))
            if (noteColor.isDefault && !selected) {
                g2.color = Theme.textMuted
                g2.drawLine((x + s * 0.25f).toInt(), (y + s * 0.75f).toInt(), (x + s * 0.75f).toInt(), (y + s * 0.25f).toInt())
            }
            if (selected || hovered) {
                val r = JBUIScale.scale(3f)
                g2.color = if (selected) Theme.accent else Theme.borderStrong
                g2.stroke = BasicStroke(JBUIScale.scale(2f))
                g2.draw(Ellipse2D.Float(x - r, y - r, s + 2 * r, s + 2 * r))
            }
            if (selected) {
                g2.color = Theme.textPrimary
                g2.stroke = BasicStroke(JBUIScale.scale(1.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val p = Path2D.Float()
                p.moveTo(x + s * 0.30f, y + s * 0.52f)
                p.lineTo(x + s * 0.45f, y + s * 0.67f)
                p.lineTo(x + s * 0.72f, y + s * 0.36f)
                g2.draw(p)
            }
        }
    }
}

fun showColorPicker(anchor: JComponent, current: NoteColor, onPick: (NoteColor) -> Unit) {
    val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(1), JBUI.scale(1))).apply {
        border = JBUI.Borders.empty(6, 8)
    }
    var popup: JBPopup? = null
    NoteColor.entries.forEach { c ->
        panel.add(ColorSwatch(c, c == current) {
            onPick(c)
            popup?.cancel()
        })
    }
    popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, null)
        .setRequestFocus(false)
        .setCancelOnClickOutside(true)
        .createPopup()
    popup.showUnderneathOf(anchor)
}
