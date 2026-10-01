package com.quillo.app.document.layout

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.*
import android.app.AlertDialog
import android.widget.EditText
import android.util.Base64
import android.text.InputType
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.quillo.app.document.*
import kotlin.math.abs
import kotlin.math.max

/** Phase-7 editable Android Canvas document surface with logical caret/selection + IME. */
class AndroidDocumentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var engine: NativeDocumentEngine? = null
        set(value) { field = value; rebuild() }
    var zoom: Float = 1f
        set(value) { field = value.coerceIn(.5f, 3f); requestLayout(); invalidate() }
    var onDocumentChanged: (() -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null
    private var selectedBlock: Int? = null

    private var doc: LayoutDocument? = null
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24000000 }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x334285F4 }
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt(); strokeWidth = 2f }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() }
    private val gap = 24f; private val pad = 20f; private val handleRadius = 9f
    private var dragAnchor: DocumentPosition? = null
    private var draggingHandle: Int = 0
    private var preferredCaretX: Float? = null
    private var composing: DocumentSelection? = null
    private var imageResizeBlock: Int? = null
    private val bitmapCache = mutableMapOf<String, Bitmap?>()

    fun selectedBlockIndex(): Int? = selectedBlock
    fun rotateSelectedImage(){ val i=selectedBlock?:return; engine?.rotateImage(i,90f); rebuild(); onDocumentChanged?.invoke() }
    fun growSelectedImage(){ val i=selectedBlock?:return; val b=engine?.block(i) as? ImageBlock?:return; engine?.resizeImage(i,(b.widthPx*1.1f).toInt(),(b.heightPx*1.1f).toInt()); rebuild(); onDocumentChanged?.invoke() }
    fun shrinkSelectedImage(){ val i=selectedBlock?:return; val b=engine?.block(i) as? ImageBlock?:return; engine?.resizeImage(i,(b.widthPx*.9f).toInt(),(b.heightPx*.9f).toInt()); rebuild(); onDocumentChanged?.invoke() }
    fun addSelectedTableRow(){ selectedBlock?.let{engine?.addTableRow(it)}; rebuild(); onDocumentChanged?.invoke() }
    fun addSelectedTableColumn(){ selectedBlock?.let{engine?.addTableColumn(it)}; rebuild(); onDocumentChanged?.invoke() }
    fun deleteSelectedBlock(){ selectedBlock?.let{engine?.deleteBlock(it)}; selectedBlock=null; rebuild(); onDocumentChanged?.invoke() }
    fun setFontSize(size:Float){ applyFormatting { DocumentCommand.SetFontSize(it,size) } }
    fun toggleStrike(){ applyFormatting { DocumentCommand.ToggleStrike(it) } }
    fun setForeground(color:String){ applyFormatting { DocumentCommand.SetForeground(it,color) } }
    fun setHighlight(color:String?){ applyFormatting { DocumentCommand.SetHighlight(it,color) } }
    fun setVertical(v:VerticalText){ applyFormatting { DocumentCommand.SetVertical(it,v) } }
    fun setParagraphStyle(style:ParagraphStyle){ val b=engine?.selection()?.end?.block ?: return; engine?.apply(DocumentCommand.SetParagraphStyle(b,style)); rebuild(); onDocumentChanged?.invoke(); onSelectionChanged?.invoke() }
    fun setListType(t:ListType){ val b=engine?.selection()?.end?.block ?: return; engine?.apply(DocumentCommand.SetListType(b,t)); rebuild(); onDocumentChanged?.invoke(); onSelectionChanged?.invoke() }
    fun indentParagraph(){ val b=engine?.selection()?.end?.block ?: return; engine?.apply(DocumentCommand.Indent(b)); rebuild(); onDocumentChanged?.invoke(); onSelectionChanged?.invoke() }
    fun outdentParagraph(){ val b=engine?.selection()?.end?.block ?: return; engine?.apply(DocumentCommand.Outdent(b)); rebuild(); onDocumentChanged?.invoke(); onSelectionChanged?.invoke() }
    fun setLink(url:String?){ applyFormatting { DocumentCommand.SetLink(it,url) } }
    fun setTableBorder(border:TableBorder){ val i=selectedBlock ?: return; if(engine?.block(i) is TableBlock){ engine?.apply(DocumentCommand.SetTableBorder(i,border)); rebuild(); onDocumentChanged?.invoke() } }
    fun selectedTableBorder():TableBorder? = (selectedBlock?.let{engine?.block(it)} as? TableBlock)?.border
    fun selectedLink():String? = engine?.formattingState()?.link
    fun setAlignment(alignment:Alignment){ val b=engine?.selection()?.end?.block ?: return; engine?.apply(DocumentCommand.SetAlignment(b,alignment)); rebuild(); onDocumentChanged?.invoke(); onSelectionChanged?.invoke() }
    fun setOrientation(o:Orientation){ val e=engine?:return;e.setPageOrientation(e.currentSectionIndex(),o);rebuild();onDocumentChanged?.invoke() }
    fun insertPageBreak(){engine?.insertPageBreakAtSelection();rebuild();onDocumentChanged?.invoke()}
    fun insertSectionBreak(){engine?.insertSectionBreakAtSelection();rebuild();onDocumentChanged?.invoke()}

    init { isFocusable = true; isFocusableInTouchMode = true }

    private val androidMeasurer = TextMeasurer { text, run ->
        val p=Paint(Paint.ANTI_ALIAS_FLAG); p.textSize=run.fontSizePx; p.typeface=Typeface.create(run.fontFamily, (if(run.bold) Typeface.BOLD else 0) or (if(run.italic) Typeface.ITALIC else 0)); p.measureText(text)
    }
    fun rebuild() { doc = engine?.document()?.let { DocumentLayoutEngine(textMeasurer=androidMeasurer).layout(it) }; requestLayout(); invalidate() }
    fun undo() { engine?.undo(); rebuild(); onDocumentChanged?.invoke() }
    fun redo() { engine?.redo(); rebuild(); onDocumentChanged?.invoke() }
    fun selectAllText() { engine?.selectAll(); selectedBlock=null; invalidate(); restartIme() }
    fun copySelection() { val e=engine?:return; val text=e.selectedText(); if(text.isNotEmpty()) (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Quillo text", text)) }
    fun cutSelection() { val e=engine?:return; val sel=e.selection()?:return; copySelection(); e.apply(DocumentCommand.DeleteSelection(sel)); rebuild(); onDocumentChanged?.invoke(); restartIme() }
    fun pasteClipboard() { val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val text=cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?:return; editInsert(text) }
    fun applyFormatting(command: (DocumentSelection) -> DocumentCommand) {
        val s = engine?.selection() ?: return
        engine?.apply(command(s)); rebuild(); onDocumentChanged?.invoke(); onSelectionChanged?.invoke()
    }

    override fun onCheckIsTextEditor() = true
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean { composing?.let{engine?.setSelection(it)}; composing=null; editInsert(text?.toString().orEmpty()); return true }
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composing?.let{engine?.setSelection(it)}
                val before=engine?.selection()?.start ?: return false
                editInsert(text?.toString().orEmpty())
                val after=engine?.selection()?.end ?: before
                composing=DocumentSelection(before,after); engine?.setSelection(DocumentSelection(after,after)); return true
            }
            override fun finishComposingText(): Boolean { composing=null; return true }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean { if (beforeLength > 0) editBackspace(); return true }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) { editBackspace(); return true }
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) { editInsert("\n"); return true }
                return super.sendKeyEvent(event)
            }
        }
    }

    private fun editInsert(text: String) {
        val e = engine ?: return; val s = e.selection() ?: return
        e.apply(DocumentCommand.InsertText(s, text)); rebuild(); onDocumentChanged?.invoke(); restartIme()
    }
    private fun editBackspace() {
        val e = engine ?: return; val s = e.selection() ?: return
        e.apply(DocumentCommand.DeleteBackward(s)); rebuild(); onDocumentChanged?.invoke(); restartIme()
    }
    private fun restartIme() = (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(this)
    private fun showKeyboard() { requestFocus(); (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }

    override fun onMeasure(w: Int, h: Int) {
        val d = doc ?: return super.onMeasure(w, h)
        val width = d.pages.maxOfOrNull { it.widthPx } ?: d.widthPx
        val height = d.pages.sumOf { it.heightPx.toDouble() }.toFloat() + gap * max(0, d.pages.size - 1)
        setMeasuredDimension(resolveSize(((width + pad * 2) * zoom).toInt(), w), resolveSize(((height + pad * 2) * zoom).toInt(), h))
    }

    override fun onDraw(canvas: Canvas) {
        val d = doc ?: return; canvas.save(); canvas.scale(zoom, zoom); var top = pad
        d.pages.forEach { page ->
            canvas.drawRect(pad + 4, top + 6, pad + page.widthPx + 4, top + page.heightPx + 6, shadow)
            canvas.drawRect(pad, top, pad + page.widthPx, top + page.heightPx, paper)
            textPaint.textSize=12f; textPaint.typeface=Typeface.DEFAULT
            if(page.header.isNotBlank()) canvas.drawText(page.header,pad+page.contentLeftPx,top+max(18f,page.contentTopPx*.45f),textPaint)
            if(page.footer.isNotBlank()) canvas.drawText(page.footer,pad+page.contentLeftPx,top+page.heightPx-max(12f,(page.heightPx-page.contentTopPx-page.contentHeightPx)*.35f),textPaint)
            page.blocks.forEach { drawBlock(canvas, it, pad, top); if(selectedBlock==it.sourceBlock && it !is LayoutParagraph){ val r=RectF(pad+it.x-3,top+it.y-3,pad+it.x+it.width+3,top+it.y+it.height+3); canvas.drawRect(r,caretPaint); if(engine?.block(it.sourceBlock) is ImageBlock){canvas.drawCircle(r.right,r.bottom,handleRadius,handlePaint)} } }
            top += page.heightPx + gap
        }
        drawSelection(canvas, d); canvas.restore()
    }

    private fun drawBlock(canvas: Canvas, block: LayoutBlock, pageLeft: Float, pageTop: Float) {
        when (block) {
            is LayoutParagraph -> block.lines.forEach { line ->
                val p=engine?.paragraph(block.sourceBlock); val style=p?.style ?: ParagraphStyle.NORMAL; val measured=line.measuredWidth; var x = when (block.alignment) { Alignment.LEFT, Alignment.JUSTIFY -> pageLeft + block.x; Alignment.CENTER -> pageLeft + block.x + (block.width-measured)/2; Alignment.RIGHT -> pageLeft + block.x + block.width-measured }
                val lineTop=pageTop+line.y; val lineBottom=pageTop+line.y+line.height
                if(block.marker.isNotEmpty() && line===block.lines.firstOrNull()){ val baseRun=p?.runs?.firstOrNull()?:TextRun(""); val mSize=baseRun.effectiveSizePx(style); textPaint.textSize=mSize; textPaint.typeface=Typeface.create(baseRun.fontFamily,(if(baseRun.effectiveBold(style))Typeface.BOLD else 0)); textPaint.color=runCatching{Color.parseColor(baseRun.foreground)}.getOrDefault(Color.BLACK); canvas.drawText(block.marker,pageLeft+block.markerX,lineTop+line.height*.78f,textPaint) }
                var global=0; p?.runs?.forEach { run -> val rs=global; val re=global+run.text.length; val a=maxOf(line.startOffset,rs); val b=minOf(line.endOffset,re); if(b>a){ val piece=run.text.substring(a-rs,b-rs); val isLink=run.link!=null; val effSize=run.effectiveSizePx(style); textPaint.textSize=effSize; textPaint.typeface=Typeface.create(run.fontFamily,(if(run.effectiveBold(style))Typeface.BOLD else 0) or (if(run.italic)Typeface.ITALIC else 0)); val w=textPaint.measureText(piece); run.highlight?.let{ hl->runCatching{Color.parseColor(hl)}.getOrNull()?.let{ hc->textPaint.color=hc;canvas.drawRect(x,lineTop,x+w,lineBottom,textPaint) } }; textPaint.color=if(isLink)0xFF1A56DB.toInt() else runCatching{Color.parseColor(run.foreground)}.getOrDefault(Color.BLACK); val vShift=when(run.vertical){VerticalText.SUPERSCRIPT->-line.height*.26f;VerticalText.SUBSCRIPT->line.height*.14f;else->0f}; val baseline=lineTop+line.height*.78f+vShift; canvas.drawText(piece,x,baseline,textPaint); if(run.underline||isLink)canvas.drawLine(x,baseline+2,x+w,baseline+2,textPaint); if(run.strike)canvas.drawLine(x,baseline-effSize*.3f,x+w,baseline-effSize*.3f,textPaint); x+=w }; global=re }
            }
            is LayoutTable -> { val rows=max(1,block.rows); val cols=max(1,block.columns); val x=pageLeft+block.x; val y=pageTop+block.y; val cw=block.width/cols; val ch=block.height/rows; val tb=(engine?.block(block.sourceBlock) as? TableBlock)?.border ?: TableBorder(); if(tb.style!=BorderStyle.NONE){ val bp=Paint(Paint.ANTI_ALIAS_FLAG); bp.style=Paint.Style.STROKE; bp.color=runCatching{Color.parseColor(tb.color)}.getOrDefault(Color.BLACK); val bw=tb.widthPx.coerceAtLeast(1).toFloat(); bp.strokeWidth=if(tb.style==BorderStyle.DOUBLE) bw*2f else bw; bp.pathEffect=when(tb.style){BorderStyle.DASHED->DashPathEffect(floatArrayOf(8f,4f),0f);BorderStyle.DOTTED->DashPathEffect(floatArrayOf(2f,3f),0f);else->null}; for(r in 0..rows) canvas.drawLine(x,y+r*ch,x+block.width,y+r*ch,bp); for(c in 0..cols) canvas.drawLine(x+c*cw,y,x+c*cw,y+block.height,bp) } else { for(r in 0..rows) canvas.drawLine(x,y+r*ch,x+block.width,y+r*ch,linePaint); for(c in 0..cols) canvas.drawLine(x+c*cw,y,x+c*cw,y+block.height,linePaint) } }
            is LayoutImage -> { val r=RectF(pageLeft+block.x,pageTop+block.y,pageLeft+block.x+block.width,pageTop+block.y+block.height); val ib=engine?.block(block.sourceBlock) as? ImageBlock; val bm=ib?.source?.let(::decodeBitmap); if(bm!=null){ canvas.save(); canvas.rotate(ib?.rotation?:0f,r.centerX(),r.centerY()); canvas.scale(if(ib?.flipHorizontal==true)-1f else 1f,if(ib?.flipVertical==true)-1f else 1f,r.centerX(),r.centerY()); canvas.drawBitmap(bm,null,r,textPaint); canvas.restore() } else { canvas.drawRect(r,linePaint); canvas.drawLine(r.left,r.top,r.right,r.bottom,linePaint); canvas.drawLine(r.right,r.top,r.left,r.bottom,linePaint) } }
        }
    }

    private fun paragraph(index: Int): ParagraphBlock? = engine?.paragraph(index)

    private data class CaretVisual(val x: Float,val top: Float,val bottom: Float)
    private fun widthTo(block:Int, from:Int, to:Int):Float { val p=paragraph(block)?:return 0f; val style=p.style; var global=0; var out=0f; p.runs.forEach{r->val rs=global;val re=global+r.text.length;val a=maxOf(from,rs);val b=minOf(to,re);if(b>a){val er=r.copy(fontSizePx=r.effectiveSizePx(style), bold=r.effectiveBold(style));out+=androidMeasurer.measure(er.text.substring(a-rs,b-rs),er)};global=re};return out }
    private fun visualFor(pos: DocumentPosition, d: LayoutDocument): CaretVisual? {
        var pageTop=pad
        d.pages.forEach { page -> page.blocks.filterIsInstance<LayoutParagraph>().filter { it.sourceBlock==pos.block }.forEach { p ->
            val line=p.lines.firstOrNull { pos.offset in it.startOffset..it.endOffset } ?: p.lines.lastOrNull() ?: return@forEach
            val local=(pos.offset-line.startOffset).coerceIn(0,line.text.length); val x=pad+p.x+widthTo(pos.block,line.startOffset,line.startOffset+local); return CaretVisual(x,pageTop+line.y,pageTop+line.y+line.height)
        }; pageTop += page.heightPx+gap }; return null
    }

    private fun decodeBitmap(source:String):Bitmap? = bitmapCache.getOrPut(source){ try { if(source.startsWith("data:image/")){ val raw=source.substringAfter(","); val bytes=Base64.decode(raw,Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes,0,bytes.size) } else null } catch(_:Exception){ null } }

    private fun editTableCell(blockIndex:Int,row:Int,col:Int){ val input=EditText(context).apply{setText(engine?.tableCellText(blockIndex,row,col).orEmpty());setSelection(text.length)}; AlertDialog.Builder(context).setTitle("Edit cell ${row+1}, ${col+1}").setView(input).setPositiveButton("Apply"){_,_->engine?.setTableCellText(blockIndex,row,col,input.text.toString());rebuild();onDocumentChanged?.invoke()}.setNegativeButton("Cancel",null).show() }

    private fun tableCellAt(blockIndex:Int,rawX:Float,rawY:Float):Pair<Int,Int>? { val d=doc?:return null; var y=rawY/zoom-pad; d.pages.forEach{page->if(y in 0f..page.heightPx.toFloat()){val x=rawX/zoom-pad;val t=page.blocks.filterIsInstance<LayoutTable>().firstOrNull{it.sourceBlock==blockIndex}?:return@forEach;if(x in t.x..t.x+t.width && y in t.y..t.y+t.height){val rows=max(1,t.rows);val cols=max(1,t.columns);return ((y-t.y)/(t.height/rows)).toInt().coerceIn(0,rows-1) to ((x-t.x)/(t.width/cols)).toInt().coerceIn(0,cols-1)}};y-=page.heightPx+gap};return null }

    private fun drawSelection(canvas: Canvas,d: LayoutDocument) {
        val s=engine?.selection() ?: return; val a=s.start; val b=s.end
        if(a==b){ visualFor(a,d)?.let { canvas.drawLine(it.x,it.top,it.x,it.bottom,caretPaint); canvas.drawCircle(it.x,it.bottom,handleRadius*.65f,handlePaint) }; return }
        val lo=if(a.block<b.block || (a.block==b.block&&a.offset<=b.offset))a else b; val hi=if(lo==a)b else a
        var pageTop=pad
        d.pages.forEach { page -> page.blocks.filterIsInstance<LayoutParagraph>().forEach { p -> if(p.sourceBlock in lo.block..hi.block) p.lines.forEach { line ->
            val from=if(p.sourceBlock==lo.block) max(line.startOffset,lo.offset) else line.startOffset; val to=if(p.sourceBlock==hi.block) minOf(line.endOffset,hi.offset) else line.endOffset
            if(to>from){ val x1=pad+p.x+widthTo(p.sourceBlock,line.startOffset,from); val x2=pad+p.x+widthTo(p.sourceBlock,line.startOffset,to); canvas.drawRect(x1,pageTop+line.y,x2,pageTop+line.y+line.height,selectionPaint) }
        } }; pageTop += page.heightPx+gap }
        visualFor(lo,d)?.let { canvas.drawCircle(it.x,it.bottom,handleRadius,handlePaint) }; visualFor(hi,d)?.let { canvas.drawCircle(it.x,it.bottom,handleRadius,handlePaint) }
    }

    private fun positionAt(rawX:Float,rawY:Float):DocumentPosition? { val d=doc?:return null; val x=rawX/zoom-pad; var y=rawY/zoom-pad
        d.pages.forEach { page -> if(y in 0f..page.heightPx.toFloat()){ val p=page.blocks.filterIsInstance<LayoutParagraph>().minByOrNull { abs((it.y+it.height/2)-y) } ?: return null; val line=p.lines.minByOrNull { abs((it.y+it.height/2)-y) } ?: return DocumentPosition(p.sourceBlock,0); var best=0; var dist=Float.MAX_VALUE; for(i in 0..line.text.length){ val dx=abs((p.x+widthTo(p.sourceBlock,line.startOffset,line.startOffset+i))-x); if(dx<dist){dist=dx;best=i} }; return DocumentPosition(p.sourceBlock,line.startOffset+best) }; y-=page.heightPx+gap }; return null }

    private fun verticalMove(pos:DocumentPosition, direction:Int):DocumentPosition? { val d=doc?:return null; val visual=visualFor(pos,d)?:return null; val targetX=preferredCaretX?:visual.x.also{preferredCaretX=it}; val candidates=mutableListOf<Pair<Float,DocumentPosition>>(); var pageTop=pad; d.pages.forEach{page->page.blocks.filterIsInstance<LayoutParagraph>().forEach{p->p.lines.forEach{line->val cy=pageTop+line.y+line.height/2; val desired=if(direction<0) visual.top-1 else visual.bottom+1; if((direction<0&&cy<visual.top)||(direction>0&&cy>visual.bottom)){ var best=0;var dx=Float.MAX_VALUE;for(i in 0..line.text.length){val xx=pad+p.x+widthTo(p.sourceBlock,line.startOffset,line.startOffset+i);val q=abs(xx-targetX);if(q<dx){dx=q;best=i}}; candidates+=abs(cy-desired) to DocumentPosition(p.sourceBlock,line.startOffset+best)}}};pageTop+=page.heightPx+gap}; return candidates.minByOrNull{it.first}?.second }

    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        val e=engine?:return super.onKeyDown(keyCode,event); val s=e.selection()?:return super.onKeyDown(keyCode,event); val pos=s.end
        fun move(p:DocumentPosition, keepX:Boolean=false){ selectedBlock=null; if(!keepX)preferredCaretX=null; e.setSelection(DocumentSelection(p,p)); invalidate(); restartIme() }
        when(keyCode){
            KeyEvent.KEYCODE_DPAD_LEFT -> { if(pos.offset>0) move(DocumentPosition(pos.block,pos.offset-1)) else e.previousParagraph(pos.block)?.let{move(DocumentPosition(it,e.paragraphText(it).length))}; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { val len=e.paragraphText(pos.block).length; if(pos.offset<len)move(DocumentPosition(pos.block,pos.offset+1)) else e.nextParagraph(pos.block)?.let{move(DocumentPosition(it,0))}; return true }
            KeyEvent.KEYCODE_DPAD_UP -> { verticalMove(pos,-1)?.let{move(it,true)}; return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { verticalMove(pos,1)?.let{move(it,true)}; return true }
            KeyEvent.KEYCODE_MOVE_HOME -> { move(DocumentPosition(pos.block,0)); return true }
            KeyEvent.KEYCODE_MOVE_END -> { move(DocumentPosition(pos.block,e.paragraphText(pos.block).length)); return true }
        }; return super.onKeyDown(keyCode,event)
    }

    private fun imageRect(blockIndex:Int):RectF? { val d=doc?:return null; var pageTop=pad; d.pages.forEach{page->page.blocks.filterIsInstance<LayoutImage>().firstOrNull{it.sourceBlock==blockIndex}?.let{return RectF(pad+it.x,pageTop+it.y,pad+it.x+it.width,pageTop+it.y+it.height)};pageTop+=page.heightPx+gap};return null }
    private fun nearImageResize(rawX:Float,rawY:Float,blockIndex:Int):Boolean { val r=imageRect(blockIndex)?:return false;val dx=rawX/zoom-r.right;val dy=rawY/zoom-r.bottom;return dx*dx+dy*dy<(handleRadius*2.5f)*(handleRadius*2.5f) }

    private fun blockAt(rawX:Float,rawY:Float):Int? { val d=doc?:return null; var y=rawY/zoom-pad; d.pages.forEach{page->if(y in 0f..page.heightPx.toFloat()){ val x=rawX/zoom-pad; return page.blocks.firstOrNull{x>=it.x&&x<=it.x+it.width&&y>=it.y&&y<=it.y+it.height}?.sourceBlock };y-=page.heightPx+gap};return null }

    private fun nearHandle(x:Float,y:Float,pos:DocumentPosition?):Boolean { val v=pos?.let{doc?.let{d->visualFor(it,d)}}?:return false; val dx=x/zoom-v.x; val dy=y/zoom-v.bottom; return dx*dx+dy*dy <= (handleRadius*2.2f)*(handleRadius*2.2f) }
    override fun onTouchEvent(e:MotionEvent):Boolean { when(e.actionMasked){
        MotionEvent.ACTION_DOWN->{ selectedBlock?.let{if(engine?.block(it) is ImageBlock && nearImageResize(e.x,e.y,it)){imageResizeBlock=it;return true}}; val sel=engine?.selection(); if(sel!=null && sel.start!=sel.end){ if(nearHandle(e.x,e.y,sel.start)){draggingHandle=1;return true}; if(nearHandle(e.x,e.y,sel.end)){draggingHandle=2;return true} }; val bi=blockAt(e.x,e.y); if(bi!=null && engine?.block(bi) !is ParagraphBlock){ selectedBlock=bi; engine?.setSelection(null); dragAnchor=null; if(engine?.block(bi) is TableBlock) tableCellAt(bi,e.x,e.y)?.let{editTableCell(bi,it.first,it.second)}; invalidate(); return true }; selectedBlock=null; preferredCaretX=null; val p=positionAt(e.x,e.y)?:return true; dragAnchor=p; engine?.setSelection(DocumentSelection(p,p)); invalidate(); onSelectionChanged?.invoke(); showKeyboard(); return true }
        MotionEvent.ACTION_MOVE->{ imageResizeBlock?.let{bi->val r=imageRect(bi)?:return true;val w=(e.x/zoom-r.left).toInt().coerceAtLeast(24);val h=(e.y/zoom-r.top).toInt().coerceAtLeast(24);engine?.resizeImage(bi,w,h);rebuild();onDocumentChanged?.invoke();return true}; val p=positionAt(e.x,e.y)?:return true; val sel=engine?.selection(); if(draggingHandle==1&&sel!=null){engine?.setSelection(DocumentSelection(p,sel.end));invalidate();return true}; if(draggingHandle==2&&sel!=null){engine?.setSelection(DocumentSelection(sel.start,p));invalidate();return true}; val a=dragAnchor?:return true; engine?.setSelection(DocumentSelection(a,p));invalidate();return true }
        MotionEvent.ACTION_UP->{ imageResizeBlock=null;draggingHandle=0;dragAnchor=null;performClick();return true } }; return true }
    override fun performClick():Boolean { super.performClick(); return true }
}
