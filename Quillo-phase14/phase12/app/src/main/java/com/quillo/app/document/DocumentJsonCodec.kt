package com.quillo.app.document

import org.json.JSONArray
import org.json.JSONObject

/** Phase 10 persistent native document format. Independent of HTML/WebView. */
object DocumentJsonCodec {
    const val VERSION = 11

    fun encode(document: QuilloDocument): String {
        val root = JSONObject().put("format", "quillo").put("version", VERSION)
        val sections = JSONArray()
        document.sections.forEach { s ->
            val page = JSONObject()
                .put("widthPx", s.page.widthPx).put("heightPx", s.page.heightPx)
                .put("marginTopPx", s.page.marginTopPx).put("marginBottomPx", s.page.marginBottomPx)
                .put("marginLeftPx", s.page.marginLeftPx).put("marginRightPx", s.page.marginRightPx)
                .put("orientation", s.page.orientation.name)
            val blocks = JSONArray(); s.blocks.forEach { blocks.put(blockToJson(it)) }
            sections.put(JSONObject().put("page", page).put("header", s.header).put("footer", s.footer).put("blocks", blocks))
        }
        return root.put("sections", sections).toString()
    }

    fun decode(text: String): QuilloDocument {
        val root = JSONObject(text)
        val arr = root.optJSONArray("sections") ?: JSONArray()
        val sections = mutableListOf<QuilloSection>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i); val p = s.optJSONObject("page") ?: JSONObject()
            val page = PageSettings(
                p.optInt("widthPx", 794), p.optInt("heightPx", 1123),
                p.optInt("marginTopPx", 96), p.optInt("marginBottomPx", 96),
                p.optInt("marginLeftPx", 96), p.optInt("marginRightPx", 96),
                runCatching { Orientation.valueOf(p.optString("orientation", "PORTRAIT").uppercase()) }.getOrDefault(Orientation.PORTRAIT)
            )
            val blocks = mutableListOf<QuilloBlock>(); val ba = s.optJSONArray("blocks") ?: JSONArray()
            for (b in 0 until ba.length()) blockFromJson(ba.getJSONObject(b))?.let(blocks::add)
            if (blocks.isEmpty()) blocks += ParagraphBlock(mutableListOf(TextRun("")))
            sections += QuilloSection(page, blocks, s.optString("header"), s.optString("footer"))
        }
        if (sections.isEmpty()) sections += QuilloSection(blocks = mutableListOf(ParagraphBlock(mutableListOf(TextRun("")))))
        return QuilloDocument(sections)
    }

    private fun blockToJson(block: QuilloBlock): JSONObject = when (block) {
        is ParagraphBlock -> JSONObject().put("type", "paragraph").put("alignment", block.alignment.name)
            .put("lineSpacing", block.lineSpacing).put("style", block.style.name)
            .put("listType", block.listType.name).put("indentLevel", block.indentLevel)
            .put("runs", JSONArray().apply { block.runs.forEach { r -> put(JSONObject().put("text",r.text).put("fontFamily",r.fontFamily).put("fontSizePx",r.fontSizePx).put("bold",r.bold).put("italic",r.italic).put("underline",r.underline).put("strike",r.strike).put("foreground",r.foreground).put("highlight",r.highlight).put("vertical",r.vertical.name).put("link",r.link)) } })
        is BreakBlock -> JSONObject().put("type", if (block.section) "sectionBreak" else "pageBreak")
        is ImageBlock -> JSONObject().put("type","image").put("source",block.source).put("widthPx",block.widthPx).put("heightPx",block.heightPx).put("rotation",block.rotation).put("flipHorizontal",block.flipHorizontal).put("flipVertical",block.flipVertical)
        is TableBlock -> JSONObject().put("type","table").put("border", JSONObject().put("style",block.border.style.name).put("widthPx",block.border.widthPx).put("color",block.border.color)).put("rows", JSONArray().apply { block.rows.forEach { row -> put(JSONArray().apply { row.forEach { cell -> put(JSONObject().put("background",cell.background).put("columnSpan",cell.columnSpan).put("verticalMerge",cell.verticalMerge).put("blocks",JSONArray().apply{cell.blocks.forEach{put(blockToJson(it))}})) } }) } })
    }

    private fun blockFromJson(j: JSONObject): QuilloBlock? = when (j.optString("type")) {
        "paragraph" -> ParagraphBlock(
            mutableListOf<TextRun>().apply { val a=j.optJSONArray("runs")?:JSONArray(); for(i in 0 until a.length()){ val r=a.getJSONObject(i); add(TextRun(r.optString("text"),r.optString("fontFamily","Calibri"),r.optDouble("fontSizePx",16.0).toFloat(),r.optBoolean("bold"),r.optBoolean("italic"),r.optBoolean("underline"),r.optBoolean("strike"),r.optString("foreground","#000000"),r.optString("highlight").takeIf{it.isNotBlank()&&it!="null"},runCatching{VerticalText.valueOf(r.optString("vertical","NORMAL").uppercase())}.getOrDefault(VerticalText.NORMAL),r.optString("link").takeIf{it.isNotBlank()&&it!="null"})) } }.ifEmpty { mutableListOf(TextRun("")) },
            runCatching{Alignment.valueOf(j.optString("alignment","LEFT").uppercase())}.getOrDefault(Alignment.LEFT),
            j.optDouble("lineSpacing",1.5).toFloat(),
            runCatching{ParagraphStyle.valueOf(j.optString("style","NORMAL").uppercase())}.getOrDefault(ParagraphStyle.NORMAL),
            runCatching{ListType.valueOf(j.optString("listType","NONE").uppercase())}.getOrDefault(ListType.NONE),
            j.optInt("indentLevel",0).coerceIn(0,MAX_INDENT_LEVEL)
        )
        "pageBreak" -> BreakBlock(false)
        "sectionBreak" -> BreakBlock(true)
        "image" -> ImageBlock(j.optString("source"),j.optInt("widthPx",100),j.optInt("heightPx",100),j.optDouble("rotation",0.0).toFloat(),j.optBoolean("flipHorizontal"),j.optBoolean("flipVertical"))
        "table" -> TableBlock(mutableListOf<MutableList<TableCell>>().apply { val rows=j.optJSONArray("rows")?:JSONArray(); for(r in 0 until rows.length()){ val ra=rows.getJSONArray(r); add(mutableListOf<TableCell>().apply { for(c in 0 until ra.length()){ val cj=ra.getJSONObject(c); val bs=mutableListOf<QuilloBlock>();val ba=cj.optJSONArray("blocks")?:JSONArray();for(k in 0 until ba.length())blockFromJson(ba.getJSONObject(k))?.let(bs::add);add(TableCell(if(bs.isEmpty()) mutableListOf<QuilloBlock>(ParagraphBlock(mutableListOf(TextRun("")))) else bs,cj.optString("background").takeIf{it.isNotBlank()&&it!="null"},cj.optInt("columnSpan",1).coerceAtLeast(1),cj.optString("verticalMerge").takeIf{it.isNotBlank()&&it!="null"})) } }) } }, j.optJSONObject("border")?.let{ bj-> TableBorder(runCatching{BorderStyle.valueOf(bj.optString("style","SINGLE").uppercase())}.getOrDefault(BorderStyle.SINGLE),bj.optInt("widthPx",1).coerceAtLeast(1),bj.optString("color","#000000")) } ?: TableBorder())
        else -> null
    }
}
