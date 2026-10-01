package com.quillo.app.document

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.quillo.app.document.layout.AndroidDocumentView

/** Phase-12 native-first host. Web remains an explicit legacy/import compatibility workspace. */
class NativeEditorHost(context: Context) : LinearLayout(context) {
    val documentView = AndroidDocumentView(context)
    private val horizontal = HorizontalScrollView(context); private val vertical = ScrollView(context)
    private var onBackToWeb:(()->Unit)?=null; private var onPickImage:((Boolean)->Unit)?=null
    private var onOpenNative:(()->Unit)?=null; private var onSaveNative:(()->Unit)?=null; private var onExportDocx:(()->Unit)?=null; private var onExportPdf:(()->Unit)?=null
    private val state=TextView(context).apply{setPadding(12,4,12,4)}
    init {
        orientation=VERTICAL; setBackgroundColor(0xFFE9E9E9.toInt())
        val bar=HorizontalScrollView(context); val toolbar=LinearLayout(context).apply{orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(8,6,8,6);setBackgroundColor(0xFFF7F7F7.toInt())}
        fun b(label:String,action:()->Unit)=Button(context).apply{text=label;minWidth=0;minimumWidth=0;setPadding(12,0,12,0);setOnClickListener{action();updateState()}}
        toolbar.addView(b("Open"){onOpenNative?.invoke()});toolbar.addView(b("Save"){onSaveNative?.invoke()});toolbar.addView(b("Word"){onExportDocx?.invoke()});toolbar.addView(b("PDF"){onExportPdf?.invoke()});toolbar.addView(b("Web"){onBackToWeb?.invoke()}); toolbar.addView(b("↶"){documentView.undo()});toolbar.addView(b("↷"){documentView.redo()})
        toolbar.addView(b("B"){documentView.applyFormatting{DocumentCommand.ToggleBold(it)}}.apply{setTypeface(typeface,Typeface.BOLD)});toolbar.addView(b("I"){documentView.applyFormatting{DocumentCommand.ToggleItalic(it)}}.apply{setTypeface(typeface,Typeface.ITALIC)});toolbar.addView(b("U"){documentView.applyFormatting{DocumentCommand.ToggleUnderline(it)}})
        toolbar.addView(b("S"){documentView.toggleStrike()}.apply{paintFlags=paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG});toolbar.addView(b("x²"){documentView.setVertical(VerticalText.SUPERSCRIPT)});toolbar.addView(b("x₂"){documentView.setVertical(VerticalText.SUBSCRIPT)})
        toolbar.addView(b("Color"){pickColor(false)});toolbar.addView(b("Highlight"){pickColor(true)})
        toolbar.addView(b("H1"){documentView.setParagraphStyle(ParagraphStyle.HEADING_1)});toolbar.addView(b("H2"){documentView.setParagraphStyle(ParagraphStyle.HEADING_2)});toolbar.addView(b("H3"){documentView.setParagraphStyle(ParagraphStyle.HEADING_3)});toolbar.addView(b("¶"){documentView.setParagraphStyle(ParagraphStyle.NORMAL)})
        toolbar.addView(b("• List"){documentView.setListType(ListType.BULLET)});toolbar.addView(b("1. List"){documentView.setListType(ListType.NUMBER)});toolbar.addView(b("⇥"){documentView.indentParagraph()});toolbar.addView(b("⇤"){documentView.outdentParagraph()});toolbar.addView(b("Link"){editLink()})
        toolbar.addView(b("12"){documentView.setFontSize(12f)});toolbar.addView(b("16"){documentView.setFontSize(16f)});toolbar.addView(b("20"){documentView.setFontSize(20f)})
        toolbar.addView(b("L"){documentView.setAlignment(Alignment.LEFT)});toolbar.addView(b("C"){documentView.setAlignment(Alignment.CENTER)});toolbar.addView(b("R"){documentView.setAlignment(Alignment.RIGHT)});toolbar.addView(b("J"){documentView.setAlignment(Alignment.JUSTIFY)})
        toolbar.addView(b("Image+"){onPickImage?.invoke(false)});toolbar.addView(b("Replace Img"){onPickImage?.invoke(true)})
        toolbar.addView(b("Header"){editHeaderFooter(true)});toolbar.addView(b("Footer"){editHeaderFooter(false)})
        toolbar.addView(b("Portrait"){documentView.setOrientation(Orientation.PORTRAIT)});toolbar.addView(b("Landscape"){documentView.setOrientation(Orientation.LANDSCAPE)});toolbar.addView(b("Margins"){editMargins()})
        toolbar.addView(b("Page Break"){documentView.insertPageBreak()});toolbar.addView(b("Section Break"){documentView.insertSectionBreak()})
        toolbar.addView(b("All"){documentView.selectAllText()});toolbar.addView(b("Copy"){documentView.copySelection()});toolbar.addView(b("Cut"){documentView.cutSelection()});toolbar.addView(b("Paste"){documentView.pasteClipboard()})
        toolbar.addView(b("Row+"){documentView.addSelectedTableRow()});toolbar.addView(b("Col+"){documentView.addSelectedTableColumn()});toolbar.addView(b("Borders"){editTableBorder()});toolbar.addView(b("Delete"){documentView.deleteSelectedBlock()})
        bar.addView(toolbar);addView(bar,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));addView(state,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        horizontal.isFillViewport=true;vertical.isFillViewport=true;horizontal.addView(documentView,ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));vertical.addView(horizontal,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));addView(vertical,LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        documentView.onSelectionChanged={updateState()}
    }
    private fun editLink(){
        val cur=documentView.selectedLink()
        val input=EditText(context).apply{setText(cur?:"");hint="https://example.com"}
        AlertDialog.Builder(context).setTitle("Hyperlink").setView(input)
            .setPositiveButton("Apply"){_,_->documentView.setLink(input.text.toString().trim());updateState()}
            .setNeutralButton("Remove"){_,_->documentView.setLink(null);updateState()}
            .setNegativeButton("Cancel",null).show()
    }
    private fun editTableBorder(){
        val cur=documentView.selectedTableBorder()
        if(cur==null){Toast.makeText(context,"Select a table first",Toast.LENGTH_SHORT).show();return}
        val styles=arrayOf("None","Single","Double","Dashed","Dotted")
        val styleVals=arrayOf(BorderStyle.NONE,BorderStyle.SINGLE,BorderStyle.DOUBLE,BorderStyle.DASHED,BorderStyle.DOTTED)
        AlertDialog.Builder(context).setTitle("Table border style").setItems(styles){_,i->val st=styleVals[i];if(st==BorderStyle.NONE){documentView.setTableBorder(cur.copy(style=BorderStyle.NONE));updateState()}else pickBorderWidth(cur,st)}.setNegativeButton("Cancel",null).show()
    }
    private fun pickBorderWidth(cur:TableBorder,style:BorderStyle){
        val input=EditText(context).apply{inputType=2;setText(cur.widthPx.toString())}
        AlertDialog.Builder(context).setTitle("Border width (px)").setView(input)
            .setPositiveButton("Next: color"){_,_->val w=input.text.toString().toIntOrNull()?.coerceIn(1,8)?:1;pickBorderColor(cur.copy(style=style,widthPx=w))}
            .setNegativeButton("Cancel",null).show()
    }
    private fun pickBorderColor(cur:TableBorder){
        val names=arrayOf("Black","Red","Blue","Green","Gray")
        val values=arrayOf("#000000","#E53935","#1E88E5","#43A047","#9E9E9E")
        AlertDialog.Builder(context).setTitle("Border color").setItems(names){_,i->documentView.setTableBorder(cur.copy(color=values[i]));updateState()}.setNegativeButton("Cancel",null).show()
    }
    private fun pickColor(highlight:Boolean){        val names=arrayOf("Black","Red","Orange","Yellow","Green","Blue","Purple","Gray","White","None")
        val values=arrayOf<String?>("#000000","#E53935","#FB8C00","#FDD835","#43A047","#1E88E5","#8E24AA","#9E9E9E","#FFFFFF",null)
        AlertDialog.Builder(context).setTitle(if(highlight)"Highlight color" else "Text color").setItems(names){_,i->val v=values[i];if(highlight)documentView.setHighlight(v) else documentView.setForeground(v?:"#000000");updateState()}.setNegativeButton("Cancel",null).show()
    }
    private fun editHeaderFooter(header:Boolean){val e=documentView.engine?:return;val i=e.currentSectionIndex();val input=EditText(context).apply{setText(if(header)e.document().sections[i].header else e.document().sections[i].footer)};AlertDialog.Builder(context).setTitle(if(header)"Edit header" else "Edit footer").setView(input).setPositiveButton("Apply"){_,_->if(header)e.setSectionHeader(i,input.text.toString()) else e.setSectionFooter(i,input.text.toString());refresh()}.setNegativeButton("Cancel",null).show()}
    private fun editMargins(){val e=documentView.engine?:return;val input=EditText(context).apply{inputType=2;setText(e.document().sections[e.currentSectionIndex()].page.marginLeftPx.toString())};AlertDialog.Builder(context).setTitle("Page margins (px)").setView(input).setPositiveButton("Apply"){_,_->e.setPageMargins(e.currentSectionIndex(),input.text.toString().toIntOrNull()?:96);refresh()}.setNegativeButton("Cancel",null).show()}
    fun attach(engine:NativeDocumentEngine){documentView.engine=engine;updateState()};fun refresh(){documentView.rebuild();updateState()};fun setZoom(value:Float){documentView.zoom=value};fun setOnBackToWeb(l:()->Unit){onBackToWeb=l};fun setOnPickImage(l:(Boolean)->Unit){onPickImage=l}
    fun setOnOpenNative(l:()->Unit){onOpenNative=l};fun setOnSaveNative(l:()->Unit){onSaveNative=l};fun setOnExportDocx(l:()->Unit){onExportDocx=l};fun setOnExportPdf(l:()->Unit){onExportPdf=l}
    fun insertOrReplaceImage(data:String,replace:Boolean){if(replace){val i=documentView.selectedBlockIndex();if(i!=null&&documentView.engine?.block(i) is ImageBlock)documentView.engine?.replaceImage(i,data) else documentView.engine?.insertImage(data)}else documentView.engine?.insertImage(data);refresh()}
    private fun updateState(){val f=documentView.engine?.formattingState();state.text=if(f==null)"Native editor" else buildString{if(f.bold)append("B ");if(f.italic)append("I ");if(f.underline)append("U ");if(f.strike)append("S ");if(f.vertical!=VerticalText.NORMAL)append(if(f.vertical==VerticalText.SUPERSCRIPT)"x² " else "x₂ ");if(f.style!=ParagraphStyle.NORMAL)append(f.style.name.replace("HEADING_","H")+" ");if(f.listType!=ListType.NONE)append(if(f.listType==ListType.BULLET)"• " else "1. ");if(f.indentLevel>0)append("⇥${f.indentLevel} ");if(f.link!=null)append("🔗 ");append("${f.fontSizePx.toInt()}px • ${f.alignment.name.lowercase().replaceFirstChar{it.uppercase()}} • Native")}}
}
