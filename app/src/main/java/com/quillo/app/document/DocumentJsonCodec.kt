package com.quillo.app.document

import org.json.JSONArray
import org.json.JSONObject

/** Versioned native Quillo document format. Phase 14 adds revisions, bookmarks, links, lists and metadata. */
object DocumentJsonCodec {
    const val VERSION = 14

    fun encode(document: QuilloDocument): String {
        val root = JSONObject().put("format", "quillo").put("version", VERSION)
        root.put("comments", JSONArray().apply { document.comments.forEach { put(JSONObject().put("id",it.id).put("author",it.author).put("text",it.text)) } })
        root.put("footnotes", notesToJson(document.footnotes)).put("endnotes", notesToJson(document.endnotes))
        root.put("properties", propertiesToJson(document.properties))
        root.put("bookmarks", JSONArray().apply { document.bookmarks.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("start",positionToJson(it.start)).put("end",positionToJson(it.end))) } })
        root.put("revisions", JSONArray().apply { document.revisions.forEach { put(JSONObject().put("id",it.id).put("kind",it.kind.name).put("author",it.author).put("timestamp",it.timestamp).put("start",positionToJson(it.start)).put("end",positionToJson(it.end))) } })
        root.put("sections", JSONArray().apply { document.sections.forEach { s ->
            val p=s.page
            val page=JSONObject().put("widthPx",p.widthPx).put("heightPx",p.heightPx).put("marginTopPx",p.marginTopPx).put("marginBottomPx",p.marginBottomPx).put("marginLeftPx",p.marginLeftPx).put("marginRightPx",p.marginRightPx).put("orientation",p.orientation.name)
            put(JSONObject().put("page",page).put("header",s.header).put("footer",s.footer).put("blocks",JSONArray().apply{s.blocks.forEach{put(blockToJson(it))}}))
        } })
        return root.toString()
    }

    fun decode(text:String):QuilloDocument {
        val root=JSONObject(text); val sections=mutableListOf<QuilloSection>(); val a=root.optJSONArray("sections")?:JSONArray()
        for(i in 0 until a.length()) { val s=a.getJSONObject(i); val p=s.optJSONObject("page")?:JSONObject(); val page=PageSettings(p.optInt("widthPx",794),p.optInt("heightPx",1123),p.optInt("marginTopPx",96),p.optInt("marginBottomPx",96),p.optInt("marginLeftPx",96),p.optInt("marginRightPx",96),enumOr(p.optString("orientation"),Orientation.PORTRAIT)); val blocks=mutableListOf<QuilloBlock>(); val ba=s.optJSONArray("blocks")?:JSONArray(); for(k in 0 until ba.length()) blockFromJson(ba.getJSONObject(k))?.let(blocks::add); if(blocks.isEmpty())blocks+=ParagraphBlock(mutableListOf(TextRun(""))); sections+=QuilloSection(page,blocks,s.optString("header"),s.optString("footer")) }
        if(sections.isEmpty())sections+=QuilloSection(blocks=mutableListOf(ParagraphBlock(mutableListOf(TextRun(""))))
        val comments=mutableListOf<DocumentComment>(); val ca=root.optJSONArray("comments")?:JSONArray(); for(i in 0 until ca.length()){val j=ca.getJSONObject(i);comments+=DocumentComment(j.optInt("id"),j.optString("author"),j.optString("text"))}
        val bookmarks=mutableListOf<DocumentBookmark>(); val bm=root.optJSONArray("bookmarks")?:JSONArray(); for(i in 0 until bm.length()){val j=bm.getJSONObject(i);bookmarks+=DocumentBookmark(j.optInt("id"),j.optString("name"),positionFromJson(j.optJSONObject("start")),positionFromJson(j.optJSONObject("end")))}
        val revisions=mutableListOf<DocumentRevision>(); val rv=root.optJSONArray("revisions")?:JSONArray(); for(i in 0 until rv.length()){val j=rv.getJSONObject(i);revisions+=DocumentRevision(j.optInt("id"),enumOr(j.optString("kind"),RevisionKind.INSERTION),j.optString("author"),j.optString("timestamp"),positionFromJson(j.optJSONObject("start")),positionFromJson(j.optJSONObject("end")))}
        return QuilloDocument(sections,comments,notesFromJson(root.optJSONArray("footnotes")),notesFromJson(root.optJSONArray("endnotes")),bookmarks,revisions,propertiesFromJson(root.optJSONObject("properties")))
    }

    private fun propertiesToJson(p:DocumentProperties)=JSONObject().put("title",p.title).put("subject",p.subject).put("author",p.author).put("keywords",p.keywords).put("description",p.description).put("category",p.category).put("company",p.company).put("manager",p.manager)
    private fun propertiesFromJson(j:JSONObject?):DocumentProperties { val p=j?:return DocumentProperties(); return DocumentProperties(p.optString("title"),p.optString("subject"),p.optString("author"),p.optString("keywords"),p.optString("description"),p.optString("category"),p.optString("company"),p.optString("manager")) }
    private fun positionToJson(p:DocumentPosition)=JSONObject().put("block",p.block).put("offset",p.offset)
    private fun positionFromJson(j:JSONObject?)=DocumentPosition(j?.optInt("block",0)?:0,j?.optInt("offset",0)?:0)
    private fun notesToJson(n:List<DocumentNote>)=JSONArray().apply{n.forEach{put(JSONObject().put("id",it.id).put("text",it.text))}}
    private fun notesFromJson(a:JSONArray?):MutableList<DocumentNote>{val out=mutableListOf<DocumentNote>();val x=a?:return out;for(i in 0 until x.length()){val j=x.getJSONObject(i);out+=DocumentNote(j.optInt("id"),j.optString("text"))};return out}
    private inline fun <reified T:Enum<T>> enumOr(v:String,d:T)=runCatching{enumValueOf<T>(v.uppercase())}.getOrDefault(d)

    private fun blockToJson(b:QuilloBlock):JSONObject=when(b){
        is ParagraphBlock->JSONObject().put("type","paragraph").put("alignment",b.alignment.name).put("lineSpacing",b.lineSpacing).put("style",b.style.name).put("list",b.list?.let{JSONObject().put("kind",it.kind.name).put("level",it.level).put("start",it.start)}).put("runs",JSONArray().apply{b.runs.forEach{r->put(JSONObject().put("text",r.text).put("fontFamily",r.fontFamily).put("fontSizePx",r.fontSizePx).put("bold",r.bold).put("italic",r.italic).put("underline",r.underline).put("strike",r.strike).put("foreground",r.foreground).put("highlight",r.highlight).put("vertical",r.vertical.name).put("commentId",r.commentId).put("noteId",r.noteId).put("noteKind",r.noteKind?.name).put("field",r.field?.name).put("hyperlink",r.hyperlink))}})
        is BreakBlock->JSONObject().put("type",if(b.section)"sectionBreak" else "pageBreak")
        is ImageBlock->JSONObject().put("type","image").put("source",b.source).put("widthPx",b.widthPx).put("heightPx",b.heightPx).put("rotation",b.rotation).put("flipHorizontal",b.flipHorizontal).put("flipVertical",b.flipVertical).put("placement",b.placement.name).put("horizontalPositionPx",b.horizontalPositionPx).put("verticalPositionPx",b.verticalPositionPx).put("wrap",b.wrap.name)
        is TableBlock->JSONObject().put("type","table").put("rows",JSONArray().apply{b.rows.forEach{row->put(JSONArray().apply{row.forEach{c->put(JSONObject().put("background",c.background).put("columnSpan",c.columnSpan).put("verticalMerge",c.verticalMerge).put("blocks",JSONArray().apply{c.blocks.forEach{put(blockToJson(it))}}))}})}})
    }

    private fun blockFromJson(j:JSONObject):QuilloBlock?=when(j.optString("type")){
        "paragraph"->{ val list=j.optJSONObject("list")?.let{ListInfo(enumOr(it.optString("kind"),ListKind.BULLET),it.optInt("level",0),it.optInt("start",1))}; ParagraphBlock(mutableListOf<TextRun>().apply{val a=j.optJSONArray("runs")?:JSONArray();for(i in 0 until a.length()){val r=a.getJSONObject(i);add(TextRun(r.optString("text"),r.optString("fontFamily","Calibri"),r.optDouble("fontSizePx",16.0).toFloat(),r.optBoolean("bold"),r.optBoolean("italic"),r.optBoolean("underline"),r.optBoolean("strike"),r.optString("foreground","#000000"),nullableString(r,"highlight"),enumOr(r.optString("vertical"),VerticalText.NORMAL),nullableInt(r,"commentId"),nullableInt(r,"noteId"),nullableString(r,"noteKind")?.let{enumOr(it,NoteKind.FOOTNOTE)},nullableString(r,"field")?.let{enumOr(it,WordField.PAGE)},nullableString(r,"hyperlink")))}}.ifEmpty{mutableListOf(TextRun(""))},enumOr(j.optString("alignment"),Alignment.LEFT),j.optDouble("lineSpacing",1.5).toFloat(),enumOr(j.optString("style"),ParagraphStyle.NORMAL),list) }
        "pageBreak"->BreakBlock(false); "sectionBreak"->BreakBlock(true)
        "image"->ImageBlock(j.optString("source"),j.optInt("widthPx",100),j.optInt("heightPx",100),j.optDouble("rotation",0.0).toFloat(),j.optBoolean("flipHorizontal"),j.optBoolean("flipVertical"),enumOr(j.optString("placement"),ImagePlacement.INLINE),j.optInt("horizontalPositionPx",0),j.optInt("verticalPositionPx",0),enumOr(j.optString("wrap"),ImageWrap.SQUARE))
        "table"->TableBlock(mutableListOf<MutableList<TableCell>>().apply{val rows=j.optJSONArray("rows")?:JSONArray();for(r in 0 until rows.length()){val ra=rows.getJSONArray(r);add(mutableListOf<TableCell>().apply{for(c in 0 until ra.length()){val cj=ra.getJSONObject(c);val bs=mutableListOf<QuilloBlock>();val ba=cj.optJSONArray("blocks")?:JSONArray();for(k in 0 until ba.length())blockFromJson(ba.getJSONObject(k))?.let(bs::add);add(TableCell(if(bs.isEmpty())mutableListOf(ParagraphBlock(mutableListOf(TextRun("")))) else bs,nullableString(cj,"background"),cj.optInt("columnSpan",1).coerceAtLeast(1),nullableString(cj,"verticalMerge")))}})}})
        else->null
    }
    private fun nullableString(j:JSONObject,k:String)=if(j.has(k)&&!j.isNull(k))j.optString(k).takeIf{it.isNotBlank()&&it!="null"} else null
    private fun nullableInt(j:JSONObject,k:String)=if(j.has(k)&&!j.isNull(k))j.optInt(k) else null
}
