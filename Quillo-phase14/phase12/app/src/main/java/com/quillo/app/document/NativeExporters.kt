package com.quillo.app.document

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.quillo.app.document.layout.*
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NativeDocxExporter {
    private const val W="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun tw(px:Int)=(px*15).coerceAtLeast(0)
    private fun MutableList<QuilloBlock>.anyParagraph(pred:(ParagraphBlock)->Boolean):Boolean = any{ b-> when(b){ is ParagraphBlock->pred(b); is TableBlock->b.rows.any{row->row.any{c->c.blocks.anyParagraph(pred)}}; else->false } }
    private fun tblBordersXml(border:TableBorder):String {
        val v=when(border.style){BorderStyle.NONE->"nil";BorderStyle.SINGLE->"single";BorderStyle.DOUBLE->"double";BorderStyle.DASHED->"dashed";BorderStyle.DOTTED->"dotted"}
        val sz=(border.widthPx.coerceAtLeast(1)*6).coerceAtLeast(2)
        val color=border.color.removePrefix("#").takeIf{it.isNotBlank()}?:"000000"
        val edge={ e:String-> if(border.style==BorderStyle.NONE)"<w:$e w:val=\"nil\"/>" else "<w:$e w:val=\"$v\" w:sz=\"$sz\" w:space=\"0\" w:color=\"$color\"/>" }
        return "<w:tblBorders>"+edge("top")+edge("left")+edge("bottom")+edge("right")+edge("insideH")+edge("insideV")+"</w:tblBorders>"
    }
    private fun numberingXml():String {
        val bulletLevels=(0..8).joinToString(""){ l-> "<w:lvl w:ilvl=\"$l\"><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"\u2022\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"${(l+1)*360}\" w:hanging=\"360\"/></w:pPr></w:lvl>" }
        val numberLevels=(0..8).joinToString(""){ l-> "<w:lvl w:ilvl=\"$l\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%${l+1}.\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"${(l+1)*360}\" w:hanging=\"360\"/></w:pPr></w:lvl>" }
        return """<?xml version="1.0"?><w:numbering xmlns:w="$W"><w:abstractNum w:abstractNumId="0">$bulletLevels</w:abstractNum><w:abstractNum w:abstractNumId="1">$numberLevels</w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num></w:numbering>"""
    }
    private data class Media(val name:String,val bytes:ByteArray,val rid:String,val ext:String)
    fun write(document:QuilloDocument,out:OutputStream){
        val media=mutableListOf<Media>(); var nextImage=1; var nextRid=10
        // Phase 14 (Tier B): emit a numbering.xml part only when lists are present.
        val hasLists = document.sections.any{ s-> s.blocks.anyParagraph{ it.listType!=ListType.NONE } }
        fun runXml(r:TextRun, style:ParagraphStyle):String {
            val bold=r.bold||style.isHeading()
            val szHalf=(r.fontSizePx*style.headingScale()*1.5f).toInt().coerceAtLeast(1)
            val isLink=r.link?.isNotBlank()==true
            val color=(if(isLink)"1A56DB" else r.foreground.removePrefix("#").takeIf{it.isNotBlank()&&!it.equals("000000",true)})
            val hl=r.highlight?.removePrefix("#")?.takeIf{it.isNotBlank()}
            val vert=when(r.vertical){VerticalText.SUPERSCRIPT->"superscript";VerticalText.SUBSCRIPT->"subscript";else->null}
            val underline=r.underline||isLink
            val core="<w:r><w:rPr>"+(if(bold)"<w:b/>" else "")+(if(r.italic)"<w:i/>" else "")+(if(underline)"<w:u w:val=\"single\"/>" else "")+(if(r.strike)"<w:strike/>" else "")+(if(color!=null)"<w:color w:val=\"$color\"/>" else "")+(if(hl!=null)"<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$hl\"/>" else "")+(if(vert!=null)"<w:vertAlign w:val=\"$vert\"/>" else "")+"<w:rFonts w:ascii=\"${esc(r.fontFamily)}\" w:hAnsi=\"${esc(r.fontFamily)}\"/><w:sz w:val=\"$szHalf\"/></w:rPr><w:t xml:space=\"preserve\">${esc(r.text)}</w:t></w:r>"
            // Self-contained HYPERLINK field (no external relationship needed).
            return if(isLink) "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> HYPERLINK \"${esc(r.link!!)}\" </w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>$core<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>" else core
        }
        fun imageXml(b:ImageBlock):String { val m=Regex("data:image/([^;]+);base64,(.*)",RegexOption.DOT_MATCHES_ALL).matchEntire(b.source)?:return "<w:p><w:r><w:t>[Image]</w:t></w:r></w:p>"; val ext=if(m.groupValues[1].contains("jpeg"))"jpg" else "png"; val bytes=runCatching{Base64.decode(m.groupValues[2],Base64.DEFAULT)}.getOrNull()?:return "<w:p/>"; val rid="rId${nextRid++}";val name="image${nextImage++}.$ext";media+=Media(name,bytes,rid,ext);val cx=b.widthPx.toLong()*9525;val cy=b.heightPx.toLong()*9525;return """<w:p><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="${nextImage}" name="Picture"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="0" name="$name"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
        }
        fun block(b:QuilloBlock):String=when(b){
            is ParagraphBlock->{val ol=when(b.style){ParagraphStyle.HEADING_1->0;ParagraphStyle.HEADING_2->1;ParagraphStyle.HEADING_3->2;ParagraphStyle.NORMAL->null}
                val lvl=b.indentLevel.coerceIn(0,8)
                val numPr=when(b.listType){ListType.BULLET->"<w:numPr><w:ilvl w:val=\"$lvl\"/><w:numId w:val=\"1\"/></w:numPr>";ListType.NUMBER->"<w:numPr><w:ilvl w:val=\"$lvl\"/><w:numId w:val=\"2\"/></w:numPr>";ListType.NONE->""}
                val ind=if(b.listType==ListType.NONE && lvl>0)"<w:ind w:left=\"${(lvl*INDENT_STEP_PX).toInt()*15}\"/>" else ""
                "<w:p><w:pPr><w:jc w:val=\"${b.alignment.name.lowercase()}\"/>$numPr$ind"+(if(ol!=null)"<w:outlineLvl w:val=\"$ol\"/>" else "")+"</w:pPr>${b.runs.joinToString(""){runXml(it,b.style)}}</w:p>"}
            is BreakBlock->"<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"
            is ImageBlock->imageXml(b)
            is TableBlock->"<w:tbl><w:tblPr>${tblBordersXml(b.border)}</w:tblPr>"+b.rows.joinToString(""){row->"<w:tr>"+row.joinToString(""){c->val tcPr=buildString{append("<w:tcPr>");if(c.columnSpan>1)append("<w:gridSpan w:val=\"${c.columnSpan}\"/>");c.verticalMerge?.let{append("<w:vMerge w:val=\"${esc(it)}\"/>")};c.background?.removePrefix("#")?.let{append("<w:shd w:fill=\"$it\"/>")};append("</w:tcPr>")};"<w:tc>$tcPr${c.blocks.joinToString(""){block(it)}}</w:tc>"}+"</w:tr>"}+"</w:tbl>"
        }
        val headers=mutableListOf<Pair<String,String>>();val footers=mutableListOf<Pair<String,String>>()
        val body=document.sections.mapIndexed{idx,s->val hr="rIdH$idx";val fr="rIdF$idx";headers+=hr to s.header;footers+=fr to s.footer;s.blocks.joinToString(""){block(it)}+"<w:sectPr><w:headerReference w:type=\"default\" r:id=\"$hr\"/><w:footerReference w:type=\"default\" r:id=\"$fr\"/><w:pgSz w:w=\"${tw(s.page.widthPx)}\" w:h=\"${tw(s.page.heightPx)}\"/><w:pgMar w:top=\"${tw(s.page.marginTopPx)}\" w:right=\"${tw(s.page.marginRightPx)}\" w:bottom=\"${tw(s.page.marginBottomPx)}\" w:left=\"${tw(s.page.marginLeftPx)}\"/></w:sectPr>"}.joinToString("")
        val doc="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="$W" xmlns:r="$R"><w:body>$body</w:body></w:document>"""
        ZipOutputStream(out).use{z->fun put(n:String,s:String){z.putNextEntry(ZipEntry(n));z.write(s.toByteArray());z.closeEntry()};fun putB(n:String,b:ByteArray){z.putNextEntry(ZipEntry(n));z.write(b);z.closeEntry()}
            val defaults=media.map{it.ext}.distinct().joinToString(""){"<Default Extension=\"$it\" ContentType=\"image/${if(it=="jpg")"jpeg" else it}\"/>"}
            put("[Content_Types].xml","""<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>$defaults<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>${if(hasLists)"<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" else ""}${headers.indices.joinToString(""){"<Override PartName=\"/word/header${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>"}}${footers.indices.joinToString(""){"<Override PartName=\"/word/footer${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>"}}</Types>""")
            put("_rels/.rels","""<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$R/officeDocument" Target="word/document.xml"/></Relationships>""")
            put("word/document.xml",doc)
            val rels=media.joinToString(""){"<Relationship Id=\"${it.rid}\" Type=\"$R/image\" Target=\"media/${it.name}\"/>"}+headers.mapIndexed{i,x->"<Relationship Id=\"${x.first}\" Type=\"$R/header\" Target=\"header${i+1}.xml\"/>"}.joinToString("")+footers.mapIndexed{i,x->"<Relationship Id=\"${x.first}\" Type=\"$R/footer\" Target=\"footer${i+1}.xml\"/>"}.joinToString("")+(if(hasLists)"<Relationship Id=\"rIdNum\" Type=\"$R/numbering\" Target=\"numbering.xml\"/>" else "")
            put("word/_rels/document.xml.rels","""<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
            if(hasLists)put("word/numbering.xml",numberingXml())
            media.forEach{putB("word/media/${it.name}",it.bytes)}
            headers.forEachIndexed{i,x->put("word/header${i+1}.xml","""<?xml version="1.0"?><w:hdr xmlns:w="$W"><w:p><w:r><w:t>${esc(x.second)}</w:t></w:r></w:p></w:hdr>""")}
            footers.forEachIndexed{i,x->put("word/footer${i+1}.xml","""<?xml version="1.0"?><w:ftr xmlns:w="$W"><w:p><w:r><w:t>${esc(x.second)}</w:t></w:r><w:r><w:tab/></w:r><w:r><w:fldChar w:fldCharType="begin"/><w:instrText> PAGE </w:instrText><w:fldChar w:fldCharType="end"/></w:r></w:p></w:ftr>""")}
        }
    }
}

object NativePdfExporter {
    fun write(document:QuilloDocument,out:OutputStream){
        val layout=DocumentLayoutEngine().layout(document);val pdf=PdfDocument();val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val flat=document.sections.flatMap{it.blocks}
        layout.pages.forEachIndexed{i,p->val page=pdf.startPage(PdfDocument.PageInfo.Builder(p.widthPx.coerceAtLeast(1),p.heightPx.coerceAtLeast(1),i+1).create());val c=page.canvas;c.drawColor(Color.WHITE);paint.color=Color.DKGRAY;paint.textSize=11f;c.drawText(p.header,p.contentLeftPx.toFloat(),(p.contentTopPx/2f).coerceAtLeast(14f),paint)
            p.blocks.forEach{b->when(b){is LayoutParagraph->{var y=b.y+(b.lines.firstOrNull()?.height?:16f);val scale=b.style.headingScale();paint.textSize=14f*scale;paint.isFakeBoldText=b.style.isHeading();paint.color=Color.BLACK;if(b.marker.isNotEmpty())c.drawText(b.marker,b.markerX,y,paint);b.lines.forEach{ln->paint.textSize=14f*scale;paint.isFakeBoldText=b.style.isHeading();paint.color=Color.BLACK;c.drawText(ln.text,b.x,y,paint);y+=ln.height};paint.isFakeBoldText=false};is LayoutTable->{val tb=(flat.getOrNull(b.sourceBlock) as? TableBlock)?.border?:TableBorder();if(tb.style!=BorderStyle.NONE){paint.style=Paint.Style.STROKE;paint.color=runCatching{Color.parseColor(tb.color)}.getOrDefault(Color.BLACK);paint.strokeWidth=tb.widthPx.coerceAtLeast(1).toFloat();val rows=b.rows.coerceAtLeast(1);val cols=b.columns.coerceAtLeast(1);val cw=b.width/cols;val ch=b.height/rows;for(r in 0..rows)c.drawLine(b.x,b.y+r*ch,b.x+b.width,b.y+r*ch,paint);for(cc in 0..cols)c.drawLine(b.x+cc*cw,b.y,b.x+cc*cw,b.y+b.height,paint);paint.strokeWidth=1f;paint.style=Paint.Style.FILL;paint.color=Color.BLACK}};else->{}}};paint.color=Color.DKGRAY;paint.textSize=11f;c.drawText("${p.footer}    ${i+1}",p.contentLeftPx.toFloat(),(p.heightPx-20).toFloat(),paint);pdf.finishPage(page)}
        pdf.writeTo(out);pdf.close()
    }
}
