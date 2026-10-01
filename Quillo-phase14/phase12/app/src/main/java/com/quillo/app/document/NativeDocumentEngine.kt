package com.quillo.app.document

import com.quillo.app.document.layout.*

/** Renderer-independent native editing engine. Phase 6 adds exact run editing and cross-paragraph operations. */
class NativeDocumentEngine(initial: QuilloDocument = QuilloDocument()) : DocumentEngine {
    private var current = initial.deepCopy()
    private var currentSelection: DocumentSelection? = null
    private val undoStack = ArrayDeque<State>(); private val redoStack = ArrayDeque<State>()
    var onDocumentChanged: (() -> Unit)? = null

    override fun document() = current
    override fun selection() = currentSelection
    override fun setSelection(selection: DocumentSelection?) { currentSelection = selection?.let(::normalized) }
    fun replaceDocument(document: QuilloDocument, clearHistory:Boolean=true){ current=document.deepCopy(); currentSelection=null; if(clearHistory){undoStack.clear();redoStack.clear()}; onDocumentChanged?.invoke() }
    fun layout():LayoutDocument=DocumentLayoutEngine().layout(current)
    fun render(renderer:DocumentRenderer=DefaultDocumentRenderer()):List<RenderCommand> = renderer.render(current,layout())

    /** Structured block edits used by the Phase-7 native inspector. */
    fun resizeImage(blockIndex:Int, widthPx:Int, heightPx:Int){ mutate { (block(blockIndex) as? ImageBlock)?.let { it.widthPx=widthPx.coerceAtLeast(24); it.heightPx=heightPx.coerceAtLeast(24) } } }
    fun rotateImage(blockIndex:Int, degrees:Float){ mutate { (block(blockIndex) as? ImageBlock)?.let { it.rotation=(it.rotation+degrees)%360f } } }
    fun addTableRow(blockIndex:Int){ mutate { (block(blockIndex) as? TableBlock)?.let { t -> val cols=t.rows.maxOfOrNull{it.size}?.coerceAtLeast(1)?:1; t.rows += MutableList(cols){TableCell(mutableListOf(ParagraphBlock(mutableListOf(TextRun(""))))) } } } }
    fun addTableColumn(blockIndex:Int){ mutate { (block(blockIndex) as? TableBlock)?.let { t -> if(t.rows.isEmpty()) t.rows.add(mutableListOf()); t.rows.forEach { it += TableCell(mutableListOf(ParagraphBlock(mutableListOf(TextRun(""))))) } } } }
    fun deleteBlock(blockIndex:Int){ mutate { locateBlock(blockIndex)?.let { (section,local) -> section.blocks.removeAt(local) }; currentSelection=null } }
    fun setTableCellText(blockIndex:Int,row:Int,column:Int,text:String){ mutate { val t=block(blockIndex) as? TableBlock ?: return@mutate; val cell=t.rows.getOrNull(row)?.getOrNull(column) ?: return@mutate; cell.blocks.clear(); cell.blocks += ParagraphBlock(mutableListOf(TextRun(text))) } }
    fun tableCellText(blockIndex:Int,row:Int,column:Int):String { val t=block(blockIndex) as? TableBlock ?: return ""; return t.rows.getOrNull(row)?.getOrNull(column)?.blocks?.filterIsInstance<ParagraphBlock>()?.joinToString("\n"){it.runs.joinToString(""){r->r.text}}.orEmpty() }
    fun setSectionHeader(sectionIndex:Int,text:String){ mutate { current.sections.getOrNull(sectionIndex)?.header=text } }
    fun setSectionFooter(sectionIndex:Int,text:String){ mutate { current.sections.getOrNull(sectionIndex)?.footer=text } }
    fun insertImage(source:String,widthPx:Int=320,heightPx:Int=240){ mutate {
        val anchor=currentSelection?.end?.block ?: allBlocks().lastIndex
        val loc=locateBlock(anchor)
        if(loc!=null) loc.first.blocks.add((loc.second+1).coerceAtMost(loc.first.blocks.size),ImageBlock(source,widthPx,heightPx))
        else current.sections.firstOrNull()?.blocks?.add(ImageBlock(source,widthPx,heightPx))
    } }
    fun replaceImage(blockIndex:Int,source:String){ mutate { (block(blockIndex) as? ImageBlock)?.source=source } }
    fun currentSectionIndex():Int { val target=currentSelection?.end?.block ?: 0; var n=0; current.sections.forEachIndexed{i,s->repeat(s.blocks.size){if(n++==target)return i}}; return 0 }
    fun setPageOrientation(sectionIndex:Int,orientation:Orientation){ mutate { current.sections.getOrNull(sectionIndex)?.page?.let{p-> if(p.orientation!=orientation){val w=p.widthPx;p.widthPx=p.heightPx;p.heightPx=w};p.orientation=orientation} } }
    fun setPageMargins(sectionIndex:Int,allPx:Int){ mutate { current.sections.getOrNull(sectionIndex)?.page?.let{p->val v=allPx.coerceIn(0,300);p.marginTopPx=v;p.marginBottomPx=v;p.marginLeftPx=v;p.marginRightPx=v} } }
    fun insertPageBreakAtSelection(){ val b=currentSelection?.end?.block?:return; apply(DocumentCommand.InsertPageBreak(b)) }
    fun insertSectionBreakAtSelection(){ val b=currentSelection?.end?.block?:return; apply(DocumentCommand.InsertSectionBreak(b)) }
    data class FormattingState(val bold:Boolean,val italic:Boolean,val underline:Boolean,val strike:Boolean,val fontSizePx:Float,val alignment:Alignment,val vertical:VerticalText,val style:ParagraphStyle,val foreground:String,val highlight:String?,val listType:ListType,val indentLevel:Int,val link:String?)
    fun formattingState():FormattingState? { val s=currentSelection?:return null; val p=paragraph(s.end.block)?:return null; var at=s.end.offset.coerceAtLeast(0); var n=0; val r=p.runs.firstOrNull{val hit=at<=n+it.text.length;n+=it.text.length;hit} ?: p.runs.lastOrNull() ?: TextRun(""); return FormattingState(r.bold,r.italic,r.underline,r.strike,r.fontSizePx,p.alignment,r.vertical,p.style,r.foreground,r.highlight,p.listType,p.indentLevel,r.link) }
    private fun mutate(action:()->Unit){ val before=State(current.deepCopy(),currentSelection); action(); undoStack.add(before); redoStack.clear(); while(undoStack.size>100)undoStack.removeFirst(); onDocumentChanged?.invoke() }

    override fun apply(command:DocumentCommand){ val before=State(current.deepCopy(),currentSelection); applyCommand(command); undoStack.add(before); redoStack.clear(); while(undoStack.size>100)undoStack.removeFirst(); onDocumentChanged?.invoke() }
    override fun undo(){ val s=undoStack.removeLastOrNull()?:return; redoStack.add(State(current.deepCopy(),currentSelection)); current=s.document.deepCopy();currentSelection=s.selection; onDocumentChanged?.invoke() }
    override fun redo(){ val s=redoStack.removeLastOrNull()?:return; undoStack.add(State(current.deepCopy(),currentSelection));current=s.document.deepCopy();currentSelection=s.selection; onDocumentChanged?.invoke() }

    private fun applyCommand(c:DocumentCommand)=when(c){
        is DocumentCommand.ToggleBold -> transformRuns(c.selection){it.copy(bold=!it.bold)}
        is DocumentCommand.ToggleItalic -> transformRuns(c.selection){it.copy(italic=!it.italic)}
        is DocumentCommand.ToggleUnderline -> transformRuns(c.selection){it.copy(underline=!it.underline)}
        is DocumentCommand.SetFont -> transformRuns(c.selection){it.copy(fontFamily=c.family)}
        is DocumentCommand.SetFontSize -> transformRuns(c.selection){it.copy(fontSizePx=c.sizePx)}
        is DocumentCommand.SetAlignment -> paragraph(c.block)?.let{it.alignment=c.alignment}
        is DocumentCommand.InsertText -> insertText(c.selection,c.text)
        is DocumentCommand.DeleteBackward -> deleteBackward(c.selection)
        is DocumentCommand.DeleteSelection -> deleteSelection(c.selection)
        is DocumentCommand.InsertPageBreak -> insertBreak(c.block,true)
        is DocumentCommand.InsertSectionBreak -> insertBreak(c.block,false)
        is DocumentCommand.ToggleStrike -> transformRuns(c.selection){it.copy(strike=!it.strike)}
        is DocumentCommand.SetForeground -> transformRuns(c.selection){it.copy(foreground=c.color)}
        is DocumentCommand.SetHighlight -> transformRuns(c.selection){it.copy(highlight=c.color)}
        is DocumentCommand.SetVertical -> transformRuns(c.selection){it.copy(vertical=if(it.vertical==c.vertical) VerticalText.NORMAL else c.vertical)}
        is DocumentCommand.SetParagraphStyle -> paragraph(c.block)?.let{it.style=c.style}
        is DocumentCommand.SetListType -> paragraph(c.block)?.let{ p-> p.listType=if(p.listType==c.listType) ListType.NONE else c.listType; if(p.listType!=ListType.NONE && p.indentLevel<0) p.indentLevel=0 }
        is DocumentCommand.Indent -> paragraph(c.block)?.let{it.indentLevel=(it.indentLevel+1).coerceAtMost(MAX_INDENT_LEVEL)}
        is DocumentCommand.Outdent -> paragraph(c.block)?.let{ p-> p.indentLevel=(p.indentLevel-1).coerceAtLeast(0); if(p.indentLevel==0 && p.listType!=ListType.NONE){} }
        is DocumentCommand.SetLink -> transformRuns(c.selection){it.copy(link=c.url?.takeIf{u->u.isNotBlank()})}
        is DocumentCommand.SetTableBorder -> (block(c.block) as? TableBlock)?.let{it.border=c.border.copy()}
    }

    fun block(index:Int):QuilloBlock? { var i=0; current.sections.forEach{s->s.blocks.forEach{b->if(i++==index)return b}}; return null }
    fun paragraph(index:Int)=block(index) as? ParagraphBlock
    fun paragraphText(index:Int)=paragraph(index)?.runs?.joinToString(""){it.text}.orEmpty()
    fun firstParagraphIndex():Int?=allBlocks().indexOfFirst{it is ParagraphBlock}.takeIf{it>=0}
    fun lastParagraphIndex():Int?=allBlocks().indexOfLast{it is ParagraphBlock}.takeIf{it>=0}
    fun previousParagraph(index:Int):Int?=(index-1 downTo 0).firstOrNull{block(it) is ParagraphBlock}
    fun nextParagraph(index:Int):Int?=(index+1 until allBlocks().size).firstOrNull{block(it) is ParagraphBlock}
    fun selectAll():DocumentSelection? { val a=firstParagraphIndex()?:return null; val b=lastParagraphIndex()?:return null; return DocumentSelection(DocumentPosition(a,0),DocumentPosition(b,paragraphText(b).length)).also{currentSelection=it} }
    fun selectedText():String { val selection=currentSelection?:return ""; return selectedText(selection) }
    fun selectedText(selection:DocumentSelection):String { val s=normalized(selection); if(s.start.block==s.end.block)return paragraphText(s.start.block).substringSafe(s.start.offset,s.end.offset); val out=mutableListOf<String>(); var i=s.start.block; while(i<=s.end.block){ val p=paragraph(i); if(p!=null){val t=p.runs.joinToString(""){it.text}; out+=when(i){s.start.block->t.substringSafe(s.start.offset,t.length);s.end.block->t.substringSafe(0,s.end.offset);else->t}};i++ }; return out.joinToString("\n") }

    private fun allBlocks()=current.sections.flatMap{it.blocks}
    private fun normalized(s:DocumentSelection):DocumentSelection = if(s.start.block<s.end.block || (s.start.block==s.end.block&&s.start.offset<=s.end.offset))s else DocumentSelection(s.end,s.start)

    private fun transformRuns(selection:DocumentSelection, transform:(TextRun)->TextRun){
        val s=normalized(selection)
        for(i in s.start.block..s.end.block){ val p=paragraph(i)?:continue; val len=p.runs.sumOf{it.text.length}; val from=if(i==s.start.block)s.start.offset.coerceIn(0,len) else 0; val to=if(i==s.end.block)s.end.offset.coerceIn(from,len) else len
            if(from==to) continue
            p.runs.replaceAllRange(from,to,transform)
        }
    }

    private fun insertText(selection:DocumentSelection,value:String){ var s=normalized(selection); if(s.start!=s.end){deleteSelection(s);s=currentSelection?:s}; val p=paragraph(s.start.block)?:return; val at=s.start.offset.coerceIn(0,p.runs.sumOf{it.text.length}); val parts=value.split('\n')
        if(parts.size==1){p.runs.insertStyled(at,value); val c=DocumentPosition(s.start.block,at+value.length);currentSelection=DocumentSelection(c,c);return}
        val before=p.runs.sliceRange(0,at); val after=p.runs.sliceRange(at,p.runs.sumOf{it.text.length}); p.runs.clear();p.runs.addAll(before);p.runs.insertStyled(p.runs.sumOf{it.text.length},parts.first())
        val loc=locateBlock(s.start.block)?:return; var insertAt=loc.second+1; var global=s.start.block
        for(k in 1 until parts.size){ val runs=mutableListOf<TextRun>(); val style=(after.firstOrNull()?:before.lastOrNull()?:TextRun("")).copy(text=""); runs+=style.copy(text=parts[k]); if(k==parts.lastIndex)runs.addAll(after); loc.first.blocks.add(insertAt++,ParagraphBlock(runs, p.alignment,p.lineSpacing,p.style,p.listType,p.indentLevel));global++ }
        val c=DocumentPosition(global,parts.last().length);currentSelection=DocumentSelection(c,c)
    }

    private fun deleteBackward(selection:DocumentSelection){ val s=normalized(selection); if(s.start!=s.end){deleteSelection(s);return}; if(s.start.offset>0){deleteSelection(DocumentSelection(DocumentPosition(s.start.block,s.start.offset-1),s.start));return}; val prev=previousParagraph(s.start.block)?:return; val prevLen=paragraphText(prev).length; deleteSelection(DocumentSelection(DocumentPosition(prev,prevLen),s.start)) }

    private fun deleteSelection(selection:DocumentSelection){ val s=normalized(selection); val a=paragraph(s.start.block)?:return; val b=paragraph(s.end.block)?:return
        if(s.start.block==s.end.block){a.runs.deleteRange(s.start.offset,s.end.offset); val c=DocumentPosition(s.start.block,s.start.offset.coerceAtMost(a.runs.sumOf{it.text.length}));currentSelection=DocumentSelection(c,c);return}
        val prefix=a.runs.sliceRange(0,s.start.offset); val suffix=b.runs.sliceRange(s.end.offset,b.runs.sumOf{it.text.length}); a.runs.clear();a.runs.addAll(prefix);a.runs.addAll(suffix); removeParagraphBlocksBetween(s.start.block,s.end.block); val c=DocumentPosition(s.start.block,s.start.offset.coerceAtMost(a.runs.sumOf{it.text.length}));currentSelection=DocumentSelection(c,c)
    }

    private fun locateBlock(global:Int):Pair<QuilloSection,Int>?{var n=0;current.sections.forEach{s->s.blocks.indices.forEach{i->if(n++==global)return s to i}};return null}
    private fun removeParagraphBlocksBetween(start:Int,end:Int){ for(i in end downTo start+1){val loc=locateBlock(i)?:continue;if(loc.first.blocks[loc.second] is ParagraphBlock)loc.first.blocks.removeAt(loc.second)} }
    private fun insertBreak(blockIndex:Int,pageBreak:Boolean){ val loc=locateBlock(blockIndex)?:return;loc.first.blocks.add((loc.second+1).coerceAtMost(loc.first.blocks.size),BreakBlock(!pageBreak)) }
    private data class State(val document:QuilloDocument,val selection:DocumentSelection?)
}

private fun String.substringSafe(a:Int,b:Int):String=substring(a.coerceIn(0,length),b.coerceIn(a.coerceIn(0,length),length))
private fun MutableList<TextRun>.sliceRange(from:Int,to:Int):MutableList<TextRun>{ val out=mutableListOf<TextRun>();var p=0;for(r in this){val e=p+r.text.length;val a=maxOf(from,p);val b=minOf(to,e);if(b>a)out+=r.copy(text=r.text.substring(a-p,b-p));p=e};return out }
private fun MutableList<TextRun>.deleteRange(from:Int,to:Int){val len=sumOf{it.text.length};val a=from.coerceIn(0,len);val b=to.coerceIn(a,len);val out=mutableListOf<TextRun>();out+=sliceRange(0,a);out+=sliceRange(b,len);clear();addAll(out.ifEmpty{mutableListOf(TextRun(""))})}
private fun MutableList<TextRun>.insertStyled(at:Int,text:String){if(text.isEmpty())return;val len=sumOf{it.text.length};val p=at.coerceIn(0,len);val before=sliceRange(0,p);val after=sliceRange(p,len);val style=(before.lastOrNull()?:after.firstOrNull()?:TextRun("")).copy(text=text);clear();addAll(before);add(style);addAll(after)}
private fun MutableList<TextRun>.replaceAllRange(from:Int,to:Int,fn:(TextRun)->TextRun){val len=sumOf{it.text.length};val a=from.coerceIn(0,len);val b=to.coerceIn(a,len);val before=sliceRange(0,a);val mid=sliceRange(a,b).map{fn(it)};val after=sliceRange(b,len);clear();addAll(before);addAll(mid);addAll(after)}
private fun QuilloDocument.deepCopy()=QuilloDocument(sections.map{it.deepCopy()}.toMutableList())
private fun QuilloSection.deepCopy()=QuilloSection(page.copy(),blocks.map{it.deepCopy()}.toMutableList(),header,footer)
private fun QuilloBlock.deepCopy():QuilloBlock=when(this){is ParagraphBlock->copy(runs=runs.map{it.copy()}.toMutableList());is ImageBlock->copy();is TableBlock->copy(rows=rows.map{row->row.map{cell->cell.copy(blocks=cell.blocks.map{it.deepCopy()}.toMutableList())}.toMutableList()}.toMutableList(),border=border.copy());is BreakBlock->copy()}
