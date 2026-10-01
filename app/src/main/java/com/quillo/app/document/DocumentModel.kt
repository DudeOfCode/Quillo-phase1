package com.quillo.app.document

/** Native-ready document model. The current WebView adapter serializes to HTML;
 * this model defines the stable shape that a future native renderer will consume. */
data class QuilloDocument(
    val sections: MutableList<QuilloSection> = mutableListOf(QuilloSection()),
    val comments: MutableList<DocumentComment> = mutableListOf(),
    val footnotes: MutableList<DocumentNote> = mutableListOf(),
    val endnotes: MutableList<DocumentNote> = mutableListOf(),
    val bookmarks: MutableList<DocumentBookmark> = mutableListOf(),
    val revisions: MutableList<DocumentRevision> = mutableListOf(),
    var properties: DocumentProperties = DocumentProperties()
)

data class DocumentComment(val id: Int, var author: String = "", var text: String = "")
data class DocumentNote(val id: Int, var text: String = "")
data class DocumentProperties(var title:String="", var subject:String="", var author:String="", var keywords:String="", var description:String="", var category:String="", var company:String="", var manager:String="")
data class DocumentBookmark(val id:Int, var name:String, var start:DocumentPosition, var end:DocumentPosition)
enum class RevisionKind { INSERTION, DELETION }
data class DocumentRevision(val id:Int, var kind:RevisionKind, var author:String="", var timestamp:String="", var start:DocumentPosition, var end:DocumentPosition)

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
    var list: ListInfo? = null
) : QuilloBlock

data class ImageBlock(
    var source: String,
    var widthPx: Int = 100,
    var heightPx: Int = 100,
    var rotation: Float = 0f,
    var flipHorizontal: Boolean = false,
    var flipVertical: Boolean = false,
    var placement: ImagePlacement = ImagePlacement.INLINE,
    var horizontalPositionPx: Int = 0,
    var verticalPositionPx: Int = 0,
    var wrap: ImageWrap = ImageWrap.SQUARE
) : QuilloBlock

enum class ImagePlacement { INLINE, ANCHORED }
enum class ImageWrap { SQUARE, TIGHT, TOP_BOTTOM, BEHIND_TEXT, IN_FRONT_OF_TEXT }

data class TableBlock(
    val rows: MutableList<MutableList<TableCell>> = mutableListOf()
) : QuilloBlock

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
    var commentId: Int? = null,
    var noteId: Int? = null,
    var noteKind: NoteKind? = null,
    var field: WordField? = null,
    var hyperlink: String? = null
)

enum class NoteKind { FOOTNOTE, ENDNOTE }
enum class WordField { PAGE, NUM_PAGES, DATE, TIME, FILE_NAME }
enum class ListKind { BULLET, NUMBERED }
data class ListInfo(var kind:ListKind=ListKind.BULLET, var level:Int=0, var start:Int=1)

enum class Alignment { LEFT, CENTER, RIGHT, JUSTIFY }
enum class ParagraphStyle { NORMAL, HEADING_1, HEADING_2, HEADING_3 }
enum class VerticalText { NORMAL, SUBSCRIPT, SUPERSCRIPT }

data class DocumentPosition(val block: Int, val offset: Int)
data class DocumentSelection(val start: DocumentPosition, val end: DocumentPosition)
