package com.quillo.app.document.layout

import com.quillo.app.document.*

/** Backend-neutral drawing commands produced from a laid-out document. */
sealed interface RenderCommand {
    data class Page(val pageIndex: Int, val width: Int, val height: Int) : RenderCommand
    data class Text(val text: String, val x: Float, val y: Float, val width: Float, val height: Float, val alignment: Alignment) : RenderCommand
    data class Image(val source: String, val x: Float, val y: Float, val width: Float, val height: Float, val rotation: Float) : RenderCommand
    data class Table(val x: Float, val y: Float, val width: Float, val height: Float, val rows: Int, val columns: Int) : RenderCommand
}

interface DocumentRenderer {
    fun render(document: QuilloDocument, layout: LayoutDocument): List<RenderCommand>
}

/** Produces deterministic draw commands; Android Canvas/WebView are not required. */
class DefaultDocumentRenderer : DocumentRenderer {
    override fun render(document: QuilloDocument, layout: LayoutDocument): List<RenderCommand> {
        val result = mutableListOf<RenderCommand>()
        layout.pages.forEach { page ->
            result += RenderCommand.Page(page.index, page.widthPx, page.heightPx)
            page.blocks.forEach { block ->
                when (block) {
                    is LayoutParagraph -> block.lines.forEach { line ->
                        result += RenderCommand.Text(line.text, block.x, line.y, block.width, line.height, block.alignment)
                    }
                    is LayoutImage -> {
                        val source = findImageSource(document, block.sourceBlock)
                        result += RenderCommand.Image(source, block.x, block.y, block.width, block.height, sourceRotation(document, block.sourceBlock))
                    }
                    is LayoutTable -> result += RenderCommand.Table(block.x, block.y, block.width, block.height, block.rows, block.columns)
                }
            }
        }
        return result
    }

    private fun findImageSource(document: QuilloDocument, globalIndex: Int): String = findBlock(document, globalIndex).let { (it as? ImageBlock)?.source ?: "" }
    private fun sourceRotation(document: QuilloDocument, globalIndex: Int): Float = (findBlock(document, globalIndex) as? ImageBlock)?.rotation ?: 0f
    private fun findBlock(document: QuilloDocument, index: Int): QuilloBlock {
        var i = 0
        document.sections.forEach { section -> section.blocks.forEach { block -> if (i++ == index) return block } }
        return BreakBlock()
    }
}
