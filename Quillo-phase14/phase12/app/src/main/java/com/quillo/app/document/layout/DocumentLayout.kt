package com.quillo.app.document.layout

import com.quillo.app.document.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A renderer-independent physical page produced by the native layout engine. */
data class LayoutDocument(
    val pages: List<LayoutPage>,
    val widthPx: Int,
    val heightPx: Int
)

data class LayoutPage(
    val index: Int,
    val sectionIndex: Int,
    val blocks: List<LayoutBlock>,
    val widthPx: Int,
    val heightPx: Int,
    val contentLeftPx: Int,
    val contentTopPx: Int,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val header: String = "",
    val footer: String = ""
)

sealed interface LayoutBlock {
    val sourceBlock: Int
    val x: Float
    val y: Float
    val width: Float
    val height: Float
}

data class LayoutParagraph(
    override val sourceBlock: Int,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val lines: List<LayoutLine>,
    val alignment: Alignment,
    val style: ParagraphStyle = ParagraphStyle.NORMAL,
    // Phase 14 (Tier B): list marker + where to paint it, plus list metadata.
    val marker: String = "",
    val markerX: Float = 0f,
    val listType: ListType = ListType.NONE,
    val indentLevel: Int = 0
) : LayoutBlock

data class LayoutImage(
    override val sourceBlock: Int,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float
) : LayoutBlock

data class LayoutTable(
    override val sourceBlock: Int,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val rows: Int,
    val columns: Int
) : LayoutBlock

data class LayoutLine(val text: String, val y: Float, val height: Float, val startOffset: Int = 0, val endOffset: Int = 0, val measuredWidth: Float = 0f)

/** Pluggable text measurement. AndroidDocumentView supplies Paint-backed measurement; tests/headless layout use deterministic fallback. */
fun interface TextMeasurer { fun measure(text: String, run: TextRun): Float }
private val deterministicTextMeasurer = TextMeasurer { text, run -> text.length * max(1f, run.fontSizePx * 0.52f) }

data class LayoutEngineConfig(
    val defaultFontSizePx: Float = 16f,
    val defaultLineHeightPx: Float = 24f,
    val paragraphSpacingPx: Float = 8f,
    val imageSpacingPx: Float = 8f,
    val tableRowHeightPx: Float = 28f
)

/**
 * Phase-3 native pagination engine.
 *
 * It consumes only QuilloDocument and produces physical pages. It deliberately
 * does not depend on Android View, WebView, HTML, CSS or DOM measurements.
 * Text measurement is deterministic and replaceable with a platform font shaper
 * in the rendering phase.
 */
class DocumentLayoutEngine(private val config: LayoutEngineConfig = LayoutEngineConfig(), private val textMeasurer: TextMeasurer = deterministicTextMeasurer) {
    fun layout(document: QuilloDocument): LayoutDocument {
        val pages = mutableListOf<LayoutPage>()
        var globalBlockIndex = 0
        // Phase 14 (Tier B): ordered-list counters per nesting level, continued
        // across pages/sections until interrupted. resetFrom(l) clears level l
        // and deeper so shallower numbering survives nested interruptions.
        val listCounters = IntArray(MAX_INDENT_LEVEL + 2)
        fun resetFrom(level: Int) { for (l in level..MAX_INDENT_LEVEL + 1) if (l in listCounters.indices) listCounters[l] = 0 }
        val bulletGlyphs = arrayOf("\u2022", "\u25E6", "\u25AA")
        fun markerFor(block: ParagraphBlock): String {
            val lvl = block.indentLevel.coerceIn(0, MAX_INDENT_LEVEL)
            return when (block.listType) {
                ListType.NONE -> { resetFrom(lvl); "" }
                ListType.BULLET -> { resetFrom(lvl + 1); bulletGlyphs[lvl % bulletGlyphs.size] }
                ListType.NUMBER -> { resetFrom(lvl + 1); listCounters[lvl] += 1; "${listCounters[lvl]}." }
            }
        }

        document.sections.forEachIndexed { sectionIndex, section ->
            val pageWidth = section.page.widthPx
            val pageHeight = section.page.heightPx
            val left = section.page.marginLeftPx
            val top = section.page.marginTopPx
            val right = section.page.marginRightPx
            val bottom = section.page.marginBottomPx
            val contentWidth = max(1, pageWidth - left - right)
            val contentHeight = max(1, pageHeight - top - bottom)

            var pageBlocks = mutableListOf<LayoutBlock>()
            var cursorY = 0f

            fun finishPage() {
                pages += LayoutPage(
                    index = pages.size,
                    sectionIndex = sectionIndex,
                    blocks = pageBlocks.toList(),
                    widthPx = pageWidth,
                    heightPx = pageHeight,
                    contentLeftPx = left,
                    contentTopPx = top,
                    contentWidthPx = contentWidth,
                    contentHeightPx = contentHeight,
                    header = section.header, footer = section.footer
                )
                pageBlocks = mutableListOf()
                cursorY = 0f
            }

            fun ensureSpace(height: Float) {
                if (cursorY > 0f && cursorY + height > contentHeight) finishPage()
            }

            section.blocks.forEach { block ->
                when (block) {
                    is BreakBlock -> {
                        resetFrom(0)
                        if (pageBlocks.isNotEmpty() || pages.isEmpty()) finishPage()
                    }
                    is ParagraphBlock -> {
                        val marker = markerFor(block)
                        val baseIndent = block.baseIndentPx()
                        val textIndent = block.textIndentPx()
                        val paraX = left + textIndent
                        val paraWidth = max(1, (contentWidth - textIndent).toInt())
                        val markerX = left + baseIndent
                        val maxEff = block.runs.maxOfOrNull { it.effectiveSizePx(block.style) } ?: config.defaultFontSizePx
                        val baseLine = max(1f, config.defaultLineHeightPx * block.lineSpacing / 1.5f)
                        val lineHeight = max(baseLine, maxEff * 1.4f * (block.lineSpacing / 1.5f))
                        val lines = wrapParagraph(block, paraWidth, lineHeight)
                        var lineStart = 0
                        while (lineStart < lines.size) {
                            val available = max(1, floorLines(contentHeight - cursorY, lineHeight))
                            val take = min(available, lines.size - lineStart)
                            val chunk = lines.subList(lineStart, lineStart + take)
                            val height = chunk.size * lineHeight + config.paragraphSpacingPx
                            ensureSpace(height)
                            val actualY = cursorY
                            pageBlocks += LayoutParagraph(
                                sourceBlock = globalBlockIndex,
                                x = paraX,
                                y = actualY,
                                width = paraWidth.toFloat(),
                                height = height,
                                lines = chunk.mapIndexed { i, line -> line.copy(y = actualY + i * lineHeight) },
                                alignment = block.alignment,
                                style = block.style,
                                marker = if (lineStart == 0) marker else "",
                                markerX = markerX,
                                listType = block.listType,
                                indentLevel = block.indentLevel
                            )
                            cursorY += height
                            lineStart += take
                            if (lineStart < lines.size) finishPage()
                        }
                        if (lines.isEmpty()) {
                            ensureSpace(lineHeight)
                            pageBlocks += LayoutParagraph(globalBlockIndex, paraX, cursorY, paraWidth.toFloat(), lineHeight, listOf(LayoutLine("", cursorY, lineHeight)), block.alignment, block.style, marker, markerX, block.listType, block.indentLevel)
                            cursorY += lineHeight
                        }
                    }
                    is ImageBlock -> {
                        resetFrom(0)
                        val w = min(block.widthPx.toFloat(), contentWidth.toFloat())
                        val h = if (block.widthPx > 0) block.heightPx * (w / block.widthPx) else block.heightPx.toFloat()
                        ensureSpace(h + config.imageSpacingPx)
                        pageBlocks += LayoutImage(globalBlockIndex, left.toFloat(), cursorY, w, h)
                        cursorY += h + config.imageSpacingPx
                    }
                    is TableBlock -> {
                        resetFrom(0)
                        val rows = block.rows.size
                        val cols = block.rows.maxOfOrNull { it.size } ?: 0
                        val height = max(config.tableRowHeightPx, rows * config.tableRowHeightPx)
                        ensureSpace(height)
                        pageBlocks += LayoutTable(globalBlockIndex, left.toFloat(), cursorY, contentWidth.toFloat(), height, rows, cols)
                        cursorY += height + config.paragraphSpacingPx
                    }
                }
                globalBlockIndex++
            }
            if (pageBlocks.isNotEmpty()) finishPage()
        }

        if (pages.isEmpty()) {
            pages += LayoutPage(0, 0, emptyList(), 794, 1123, 96, 96, 602, 931)
        }
        return LayoutDocument(pages, pages.first().widthPx, pages.first().heightPx)
    }

    private fun wrapParagraph(block: ParagraphBlock, width: Int, lineHeight: Float): List<LayoutLine> {
        val text = block.runs.joinToString("") { it.text }
        if (text.isEmpty()) return emptyList()
        fun runAt(offset:Int): TextRun { var n=0; for(r in block.runs){ if(offset < n+r.text.length) return r; n+=r.text.length }; return block.runs.lastOrNull() ?: TextRun("") }
        fun measured(a:Int,b:Int):Float { var x=0f; var i=a; while(i<b){ val r=runAt(i); var n=0; var runStart=0; for(rr in block.runs){ val e=runStart+rr.text.length; if(i in runStart until e){ n=e; break }; runStart=e }; val e=min(b, if(n>i)n else i+1); val er=r.copy(fontSizePx=r.effectiveSizePx(block.style), bold=r.effectiveBold(block.style)); x += textMeasurer.measure(text.substring(i,e),er); i=e }; return x }
        val lines=mutableListOf<LayoutLine>(); var start=0
        while(start<text.length){
            if(text[start]=='\n'){ lines += LayoutLine("",0f,lineHeight,start,start,0f); start++; continue }
            var end=start; var lastSpace=-1
            while(end<text.length && text[end]!='\n'){ val candidate=end+1; if(measured(start,candidate)>width && end>start) break; if(text[end].isWhitespace()) lastSpace=candidate; end=candidate }
            if(end<text.length && text[end]!='\n' && lastSpace>start) end=lastSpace
            if(end<=start) end=min(text.length,start+1)
            val shown=text.substring(start,end); lines += LayoutLine(shown,0f,lineHeight,start,end,measured(start,end)); start=end
            if(start<text.length && text[start]=='\n') start++
        }
        return lines
    }

    private fun floorLines(remaining: Float, lineHeight: Float): Int = max(1, (remaining / lineHeight).toInt())
}
