package com.quillo.app.document

/** Native-ready document model. The current WebView adapter serializes to HTML;
 * this model defines the stable shape that a future native renderer will consume. */
data class QuilloDocument(
    val sections: MutableList<QuilloSection> = mutableListOf(QuilloSection())
)

data class QuilloSection(
    var page: PageSettings = PageSettings(),
    val blocks: MutableList<QuilloBlock> = mutableListOf(),
    var header: String = "",
    var footer: String = ""
)

data class PageSettings(
    var widthPx: Int = 794,
    var heightPx: Int = 1123,
    var marginTopPx: Int = 96,
    var marginBottomPx: Int = 96,
    var marginLeftPx: Int = 96,
    var marginRightPx: Int = 96,
    var orientation: Orientation = Orientation.PORTRAIT
)

enum class Orientation { PORTRAIT, LANDSCAPE }

sealed interface QuilloBlock

data class BreakBlock(val section: Boolean = false) : QuilloBlock

data class ParagraphBlock(
    val runs: MutableList<TextRun> = mutableListOf(),
    var alignment: Alignment = Alignment.LEFT,
    var lineSpacing: Float = 1.5f,
    var style: ParagraphStyle = ParagraphStyle.NORMAL,
    // Phase 14 (Tier B): list membership + indent nesting shared by lists and
    // plain-paragraph indent/outdent. `indentLevel` nests both.
    var listType: ListType = ListType.NONE,
    var indentLevel: Int = 0
) : QuilloBlock

data class ImageBlock(
    var source: String,
    var widthPx: Int = 100,
    var heightPx: Int = 100,
    var rotation: Float = 0f,
    var flipHorizontal: Boolean = false,
    var flipVertical: Boolean = false
) : QuilloBlock

data class TableBlock(
    val rows: MutableList<MutableList<TableCell>> = mutableListOf(),
    // Phase 14 (Tier B): table border styling.
    var border: TableBorder = TableBorder()
) : QuilloBlock

data class TableBorder(
    var style: BorderStyle = BorderStyle.SINGLE,
    var widthPx: Int = 1,
    var color: String = "#000000"
)

data class TableCell(
    val blocks: MutableList<QuilloBlock> = mutableListOf(),
    var background: String? = null,
    var columnSpan: Int = 1,
    var verticalMerge: String? = null
)

data class TextRun(
    var text: String,
    var fontFamily: String = "Calibri",
    var fontSizePx: Float = 16f,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var strike: Boolean = false,
    var foreground: String = "#000000",
    var highlight: String? = null,
    var vertical: VerticalText = VerticalText.NORMAL,
    // Phase 14 (Tier B): hyperlink target for this run (null = not a link).
    var link: String? = null
)

enum class Alignment { LEFT, CENTER, RIGHT, JUSTIFY }
enum class ParagraphStyle { NORMAL, HEADING_1, HEADING_2, HEADING_3 }
enum class VerticalText { NORMAL, SUBSCRIPT, SUPERSCRIPT }
enum class ListType { NONE, BULLET, NUMBER }
enum class BorderStyle { NONE, SINGLE, DOUBLE, DASHED, DOTTED }

data class DocumentPosition(val block: Int, val offset: Int)
data class DocumentSelection(val start: DocumentPosition, val end: DocumentPosition)

/* ---------------------------------------------------------------------------
 * Phase 13 (Tier A) shared display metrics.
 * Layout, canvas rendering and the DOCX/PDF exporters must agree on how a run
 * is sized once paragraph style (headings) and vertical alignment
 * (sub/superscript) are taken into account. Keeping the maths in one place
 * guarantees caret positions, selection rectangles and painted glyphs line up.
 * ------------------------------------------------------------------------- */

/** Visual scale factor applied to a run's font size for heading paragraphs. */
fun ParagraphStyle.headingScale(): Float = when (this) {
    ParagraphStyle.HEADING_1 -> 1.9f
    ParagraphStyle.HEADING_2 -> 1.5f
    ParagraphStyle.HEADING_3 -> 1.17f
    ParagraphStyle.NORMAL -> 1f
}

/** True for any heading level; such paragraphs render bold by convention. */
fun ParagraphStyle.isHeading(): Boolean = this != ParagraphStyle.NORMAL

/** Sub/superscript runs render at a reduced size. */
fun TextRun.verticalScale(): Float = if (vertical == VerticalText.NORMAL) 1f else 0.72f

/** Effective on-screen font size for a run given its enclosing paragraph style. */
fun TextRun.effectiveSizePx(style: ParagraphStyle): Float =
    fontSizePx * style.headingScale() * verticalScale()

/** Effective bold flag, folding in heading styling. */
fun TextRun.effectiveBold(style: ParagraphStyle): Boolean = bold || style.isHeading()

/* ---------------------------------------------------------------------------
 * Phase 14 (Tier B) shared layout metrics for lists / indent.
 * Layout, canvas rendering and the exporters derive list indentation and the
 * marker gutter from these so caret geometry and painted glyphs stay aligned.
 * ------------------------------------------------------------------------- */

/** Horizontal space (px) added per indent / list-nesting level. */
const val INDENT_STEP_PX: Float = 36f

/** Width (px) of the gutter reserved to the left of list text for the marker. */
const val LIST_MARKER_GUTTER_PX: Float = 28f

/** Maximum indent / list nesting depth. */
const val MAX_INDENT_LEVEL: Int = 8

/** Left indent (px) applied to a paragraph before any list marker gutter. */
fun ParagraphBlock.baseIndentPx(): Float = indentLevel.coerceAtLeast(0) * INDENT_STEP_PX

/** Extra left offset (px) for text once the list marker gutter is accounted for. */
fun ParagraphBlock.textIndentPx(): Float =
    baseIndentPx() + if (listType != ListType.NONE) LIST_MARKER_GUTTER_PX else 0f
