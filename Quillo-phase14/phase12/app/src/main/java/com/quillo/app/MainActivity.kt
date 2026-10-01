package com.quillo.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.provider.DocumentsContract
import org.json.JSONObject
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.view.View
import android.widget.FrameLayout
import com.quillo.app.document.NativeDocumentEngine
import com.quillo.app.document.NativeEditorHost
import com.quillo.app.document.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var nativeHost: NativeEditorHost
    private val nativeEngine = NativeDocumentEngine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repository by lazy { DocumentRepository(this) }
    private var autosaveJob: Runnable? = null
    private var webReady = false
    private var pendingCompatibilityAction: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var nativeReplaceImage = false

    // Data parked for a pending pre-Q save that needs the storage permission.
    private var pendingBytes: ByteArray? = null
    private var pendingName: String? = null
    private var pendingMime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        nativeHost = NativeEditorHost(this).apply {
            visibility = View.GONE
            attach(nativeEngine)
            setOnBackToWeb { showWebEditor() }
            setOnPickImage { replace -> pickNativeImage(replace) }
            setOnOpenNative { openNativeDocument() }
            setOnSaveNative { saveNativeDocument() }
            setOnExportDocx { exportNative("docx") }
            setOnExportPdf { exportNative("pdf") }
        }
        val root = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(nativeHost, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)

        // Phase 10: native lifecycle is authoritative from startup.
        nativeEngine.replaceDocument(repository.loadAutosave() ?: QuilloDocument(mutableListOf(QuilloSection(blocks = mutableListOf(ParagraphBlock(mutableListOf(TextRun(""))))))))
        nativeEngine.onDocumentChanged = { scheduleAutosave() }
        nativeHost.refresh()
        nativeHost.visibility = View.VISIBLE
        web.visibility = View.GONE

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            textZoom = 100
            minimumFontSize = 1
            minimumLogicalFontSize = 1
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            mediaPlaybackRequiresUserGesture = false
        }

        web.addJavascriptInterface(SaverBridge(), "AndroidSaver")
        web.addJavascriptInterface(NativeEditorBridge(), "AndroidNativeEditor")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                val r = assetLoader.shouldInterceptRequest(request.url)
                val path = request.url.path ?: ""
                if (r != null) when {
                    path.endsWith(".woff2") -> r.mimeType = "font/woff2"
                    path.endsWith(".js") -> r.mimeType = "text/javascript"
                }
                return r
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(BLOB_HOOK, null)
                webReady = true
                pendingCompatibilityAction?.let { action -> pendingCompatibilityAction = null; pushNativeToWeb(action) }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val intent = params.createIntent()
                // Honour accept types declared by <input accept="..."> (.docx / image/*)
                val accept = params.acceptTypes?.filter { it.isNotBlank() }
                if (!accept.isNullOrEmpty()) {
                    intent.type = "*/*"
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, resolveMimes(accept))
                }
                return try {
                    startActivityForResult(Intent.createChooser(intent, "Select file"), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState == null) {
            web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun scheduleAutosave() {
        autosaveJob?.let(mainHandler::removeCallbacks)
        autosaveJob = Runnable { runCatching { repository.saveAutosave(nativeEngine.document()) } }
        mainHandler.postDelayed(autosaveJob!!, 650)
    }

    override fun onPause() {
        super.onPause()
        runCatching { repository.saveAutosave(nativeEngine.document()) }
    }

    private fun openNativeDocument() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "application/octet-stream", "text/plain")) }
        try { startActivityForResult(i, REQ_NATIVE_OPEN) } catch (e: Exception) { toast("No file picker available") }
    }

    private fun saveNativeDocument() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "document.quillo") }
        try { startActivityForResult(i, REQ_NATIVE_SAVE) } catch (e: Exception) { toast("No file picker available") }
    }

    private fun exportNative(kind: String) {
        runCatching {
            val out = java.io.ByteArrayOutputStream()
            if (kind == "docx") NativeDocxExporter.write(nativeEngine.document(), out) else NativePdfExporter.write(nativeEngine.document(), out)
            asBytes = out.toByteArray()
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = if(kind=="docx") "application/vnd.openxmlformats-officedocument.wordprocessingml.document" else "application/pdf"; putExtra(Intent.EXTRA_TITLE, if(kind=="docx") "Quillo-document.docx" else "Quillo-document.pdf") }
            startActivityForResult(i, REQ_SAVEAS)
        }.onFailure { toast("Native export failed: ${it.message}") }
    }

    private fun exportViaCompatibility(kind: String) {
        if (!webReady) { pendingCompatibilityAction = kind; if (web.url == null) web.loadUrl("https://appassets.androidplatform.net/assets/index.html"); return }
        pushNativeToWeb(kind)
    }

    private fun pushNativeToWeb(action: String? = null) {
        val payload = nativeModelJson().toString()
        val jsAction = when (action) {
            "docx" -> ";setTimeout(function(){ if(window.expDocx) expDocx(true); },80)"
            "pdf" -> ";setTimeout(function(){ if(window.expPdf) expPdf(true); },80)"
            else -> ""
        }
        web.evaluateJavascript("window.applyNativeDocumentModel&&window.applyNativeDocumentModel(" + JSONObject.quote(payload) + ")" + jsAction, null)
    }

    private fun resolveMimes(accept: List<String>): Array<String> = accept.map {
        when {
            it.startsWith(".") -> mimeForExt(it.removePrefix("."))
            else -> it
        }
    }.toTypedArray()

    private fun pickNativeImage(replace:Boolean) {
        nativeReplaceImage=replace
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="image/*" }
        startActivityForResult(i,REQ_NATIVE_IMAGE)
    }

    private fun imageDataUri(uri:Uri):String? = try {
        val mime=contentResolver.getType(uri) ?: "image/png"
        val bytes=contentResolver.openInputStream(uri)?.use{it.readBytes()} ?: return null
        "data:$mime;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
    } catch(e:Exception){ toast("Image could not be opened: ${e.message}"); null }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_NATIVE_OPEN) {
            val uri=data?.data
            if(resultCode==Activity.RESULT_OK && uri!=null) {
                runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { DocumentJsonCodec.decode(it.readText()) } ?: error("Empty document") }
                    .onSuccess { nativeEngine.replaceDocument(it); nativeHost.refresh(); toast("Quillo document opened") }
                    .onFailure { toast("Open failed: ${it.message}") }
            }
            return
        }
        if (requestCode == REQ_NATIVE_SAVE) {
            val uri=data?.data
            if(resultCode==Activity.RESULT_OK && uri!=null) {
                runCatching { contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(DocumentJsonCodec.encode(nativeEngine.document())) } ?: error("Could not open destination") }
                    .onSuccess { toast("Quillo document saved") }.onFailure { toast("Save failed: ${it.message}") }
            }
            return
        }
        if (requestCode == REQ_NATIVE_IMAGE) {
            if(resultCode==Activity.RESULT_OK) data?.data?.let{u->imageDataUri(u)?.let{nativeHost.insertOrReplaceImage(it,nativeReplaceImage)}}
            return
        }
        if (requestCode == REQ_SAVEAS) {
            val b = asBytes; asBytes = null; val u = data?.data
            if (resultCode == Activity.RESULT_OK && u != null && b != null) {
                try { contentResolver.openOutputStream(u, "wt")?.use { it.write(b) }; toast("Saved") }
                catch (e: Exception) { toast("Save failed: ${e.message}") }
            } else toast("Save cancelled")
            return
        }
        if (requestCode == REQ_TREE) {
            val u = data?.data
            if (resultCode == Activity.RESULT_OK && u != null) {
                try {
                    contentResolver.takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: Exception) { }
                prefs.edit().putString("tree", u.toString()).apply()
                notifyFolder(); toast("Save folder: " + folderLabel())
            }
            return
        }
        if (requestCode == REQ_FILE) {
            val cb = fileCallback
            fileCallback = null
            val result: Array<Uri>? = if (resultCode == Activity.RESULT_OK && data != null) {
                data.data?.let { arrayOf(it) }
            } else null
            cb?.onReceiveValue(result)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showWebEditor() {
        pushNativeToWeb()
        web.visibility = View.VISIBLE
        nativeHost.visibility = View.GONE
    }

    private fun nativeModelJson(): JSONObject {
        val root=JSONObject(); root.put("version",8); val sections=org.json.JSONArray()
        nativeEngine.document().sections.forEach { section -> val sj=JSONObject(); val page=JSONObject(); page.put("widthPx",section.page.widthPx);page.put("heightPx",section.page.heightPx);page.put("marginTopPx",section.page.marginTopPx);page.put("marginBottomPx",section.page.marginBottomPx);page.put("marginLeftPx",section.page.marginLeftPx);page.put("marginRightPx",section.page.marginRightPx);page.put("orientation",section.page.orientation.name.lowercase());sj.put("page",page);sj.put("header",section.header);sj.put("footer",section.footer); val blocks=org.json.JSONArray()
            section.blocks.forEach { b -> val j=JSONObject(); when(b){
                is ParagraphBlock -> {j.put("type","paragraph");j.put("alignment",b.alignment.name.lowercase());j.put("lineSpacing",b.lineSpacing);j.put("style",b.style.name.lowercase());j.put("listType",b.listType.name.lowercase());j.put("indentLevel",b.indentLevel); val runs=org.json.JSONArray();b.runs.forEach{r->val x=JSONObject();x.put("text",r.text);x.put("fontFamily",r.fontFamily);x.put("fontSizePx",r.fontSizePx);x.put("bold",r.bold);x.put("italic",r.italic);x.put("underline",r.underline);x.put("strike",r.strike);x.put("foreground",r.foreground);if(r.highlight!=null)x.put("highlight",r.highlight);x.put("vertical",r.vertical.name.lowercase());if(r.link!=null)x.put("link",r.link);runs.put(x)};j.put("runs",runs)}
                is BreakBlock -> j.put("type",if(b.section)"sectionBreak" else "pageBreak")
                is ImageBlock -> {j.put("type","image");j.put("src",b.source);j.put("width",b.widthPx);j.put("height",b.heightPx);j.put("rotation",b.rotation);j.put("flipHorizontal",b.flipHorizontal);j.put("flipVertical",b.flipVertical)}
                is TableBlock -> {j.put("type","table");j.put("border",JSONObject().put("style",b.border.style.name.lowercase()).put("widthPx",b.border.widthPx).put("color",b.border.color));val rows=org.json.JSONArray();b.rows.forEach{row->val rr=org.json.JSONArray();row.forEach{cell->val c=JSONObject();c.put("text",cell.blocks.filterIsInstance<ParagraphBlock>().joinToString("\n"){it.runs.joinToString(""){r->r.text}});if(cell.background!=null)c.put("background",cell.background);rr.put(c)};rows.put(rr)};j.put("rows",rows)}
            };blocks.put(j)};sj.put("blocks",blocks);sections.put(sj)};root.put("sections",sections);return root
    }

    private fun showNativeEditor() {
        nativeHost.refresh()
        nativeHost.visibility = View.VISIBLE
        web.visibility = View.GONE
    }

    /** Phase-8 bridge: compatibility WebView + editable native surface. */
    inner class NativeEditorBridge {
        @JavascriptInterface fun showNative() = runOnUiThread {
            showNativeEditor()
        }
        @JavascriptInterface fun showWeb() = runOnUiThread {
            showWebEditor()
        }
        @JavascriptInterface fun syncModel(json: String) {
            try {
                val root = JSONObject(json)
                val sectionsJson = root.optJSONArray("sections")
                val sections = mutableListOf<QuilloSection>()
                if (sectionsJson != null) for (i in 0 until sectionsJson.length()) {
                    val sourceSection = sectionsJson.getJSONObject(i)
                    val blocksJson = sourceSection.optJSONArray("blocks")
                    val blocks = mutableListOf<QuilloBlock>()
                    if (blocksJson != null) for (b in 0 until blocksJson.length()) {
                        val source = blocksJson.getJSONObject(b)
                        when (source.optString("type")) {
                            "paragraph" -> {
                                val runs = mutableListOf<TextRun>()
                                source.optJSONArray("runs")?.let { arr -> for (r in 0 until arr.length()) {
                                    val run = arr.getJSONObject(r)
                                    runs += TextRun(run.optString("text"), run.optString("fontFamily", "Calibri"), run.optDouble("fontSizePx", 16.0).toFloat(), run.optBoolean("bold"), run.optBoolean("italic"), run.optBoolean("underline"), run.optBoolean("strike"), run.optString("foreground", "#000000"), run.optString("highlight").takeIf { it.isNotBlank() && it != "null" }, runCatching{VerticalText.valueOf(run.optString("vertical","normal").uppercase())}.getOrDefault(VerticalText.NORMAL), run.optString("link").takeIf { it.isNotBlank() && it != "null" })
                                }}
                                val alignment = when (source.optString("alignment").lowercase()) { "center" -> Alignment.CENTER; "right" -> Alignment.RIGHT; "justify" -> Alignment.JUSTIFY; else -> Alignment.LEFT }
                                val style=runCatching{ParagraphStyle.valueOf(source.optString("style","normal").uppercase())}.getOrDefault(ParagraphStyle.NORMAL)
                                val listType=runCatching{ListType.valueOf(source.optString("listType","none").uppercase())}.getOrDefault(ListType.NONE)
                                val indentLevel=source.optInt("indentLevel",0).coerceIn(0,MAX_INDENT_LEVEL)
                                blocks += ParagraphBlock(runs.ifEmpty { mutableListOf(TextRun(source.optString("text"))) }, alignment, source.optDouble("lineSpacing",1.5).toFloat(), style, listType, indentLevel)
                            }
                            "pageBreak" -> blocks += BreakBlock(false)
                            "image" -> blocks += ImageBlock(source.optString("src"), source.optInt("width", 100), source.optInt("height", 100), source.optDouble("rotation",0.0).toFloat(), source.optBoolean("flipHorizontal"), source.optBoolean("flipVertical"))
                            "table" -> {
                                val rows = mutableListOf<MutableList<TableCell>>()
                                source.optJSONArray("rows")?.let { rr -> for (ri in 0 until rr.length()) {
                                    val row = mutableListOf<TableCell>(); val cells = rr.getJSONArray(ri)
                                    for (ci in 0 until cells.length()) { val cellJson=cells.getJSONObject(ci); row += TableCell(mutableListOf(ParagraphBlock(mutableListOf(TextRun(cellJson.optString("text"))))), cellJson.optString("background").takeIf{it.isNotBlank()&&it!="null"}) }
                                    rows += row
                                }}
                                blocks += TableBlock(rows, source.optJSONObject("border")?.let{ bj-> TableBorder(runCatching{BorderStyle.valueOf(bj.optString("style","single").uppercase())}.getOrDefault(BorderStyle.SINGLE),bj.optInt("widthPx",1).coerceAtLeast(1),bj.optString("color","#000000")) } ?: TableBorder())
                            }
                        }
                    }
                    val pj=sourceSection.optJSONObject("page")
                    val ps=if(pj!=null) PageSettings(pj.optInt("widthPx",794),pj.optInt("heightPx",1123),pj.optInt("marginTopPx",96),pj.optInt("marginBottomPx",96),pj.optInt("marginLeftPx",96),pj.optInt("marginRightPx",96),if(pj.optString("orientation").equals("landscape",true)) Orientation.LANDSCAPE else Orientation.PORTRAIT) else PageSettings()
                    sections += QuilloSection(page=ps, blocks=blocks, header=sourceSection.optString("header"), footer=sourceSection.optString("footer"))
                }
                nativeEngine.replaceDocument(QuilloDocument(sections.ifEmpty { mutableListOf(QuilloSection()) }))
                runOnUiThread { nativeHost.refresh() }
            } catch (e: Exception) { runOnUiThread { toast("Native sync failed: ${e.message}") } }
        }
        @JavascriptInterface fun refresh() = runOnUiThread { nativeHost.refresh() }
        @JavascriptInterface fun setZoom(value: Float) = runOnUiThread { nativeHost.setZoom(value) }
    }

    /** Bridge the web app calls to hand a generated file (docx / pdf) back to Android. */
    inner class SaverBridge {
        @JavascriptInterface
        fun saveAs(base64: String, filename: String, mime: String) {
            val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (e: Exception) {
                runOnUiThread { toast("Could not read the file") }; return }
            val name = sanitize(filename)
            val mt = if (mime.isNotBlank()) mime else mimeForExt(name.substringAfterLast('.', ""))
            runOnUiThread {
                asBytes = bytes
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mt
                    putExtra(Intent.EXTRA_TITLE, name)
                    treeUri()?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                }
                try { startActivityForResult(i, REQ_SAVEAS) } catch (e: Exception) { asBytes = null; toast("No file picker available") }
            }
        }

        @JavascriptInterface
        fun chooseFolder() = runOnUiThread {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            try { startActivityForResult(i, REQ_TREE) } catch (e: Exception) { toast("No folder picker available") }
        }

        @JavascriptInterface
        fun resetFolder() = runOnUiThread {
            prefs.edit().remove("tree").apply(); notifyFolder(); toast("Using Downloads/Quillo")
        }

        @JavascriptInterface
        fun getFolder(): String = folderLabel()

        @JavascriptInterface
        fun saveBase64(base64: String, filename: String, mime: String) {
            val bytes = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: Exception) {
                runOnUiThread { toast("Could not read the file") }
                return
            }
            val name = sanitize(filename)
            val type = if (mime.isNotBlank()) mime else mimeForExt(name.substringAfterLast('.', ""))
            runOnUiThread { saveDefault(bytes, name, type) }
        }
    }

    private var asBytes: ByteArray? = null
    private val prefs by lazy { getSharedPreferences("quillo", MODE_PRIVATE) }
    private fun treeUri(): Uri? = prefs.getString("tree", null)?.let { Uri.parse(it) }

    private fun folderLabel(): String {
        val t = treeUri() ?: return "Downloads/Quillo"
        return try {
            DocumentsContract.getTreeDocumentId(t).replaceFirst("primary:", "Internal storage/").replace(":", "/")
        } catch (e: Exception) { "Custom folder" }
    }

    private fun notifyFolder() {
        web.evaluateJavascript("window.onFolder&&window.onFolder(" + JSONObject.quote(folderLabel()) + ")", null)
    }

    private fun saveDefault(bytes: ByteArray, name: String, mime: String) {
        val t = treeUri()
        if (t != null) {
            try {
                val parent = DocumentsContract.buildDocumentUriUsingTree(t, DocumentsContract.getTreeDocumentId(t))
                val doc = DocumentsContract.createDocument(contentResolver, parent, mime, name)
                if (doc != null) {
                    contentResolver.openOutputStream(doc)?.use { it.write(bytes) }
                    toast("Saved to ${folderLabel()}/$name")
                    return
                }
            } catch (e: Exception) { /* fall through to Downloads */ }
            toast("Chosen folder unavailable – saving to Downloads/Quillo")
        }
        saveToDownloads(bytes, name, mime)
    }

    private fun saveToDownloads(bytes: ByteArray, name: String, mime: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Quillo")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: run { toast("Save failed"); return }
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                toast("Saved to Downloads/Quillo/$name")
            } catch (e: Exception) {
                toast("Save failed: ${e.message}")
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                pendingBytes = bytes; pendingName = name; pendingMime = mime
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_PERM)
                return
            }
            writeLegacy(bytes, name)
        }
    }

    private fun writeLegacy(bytes: ByteArray, name: String) {
        try {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Quillo"
            )
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, name)
            FileOutputStream(out).use { it.write(bytes) }
            toast("Saved to Download/Quillo/$name")
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM) {
            val b = pendingBytes; val n = pendingName
            pendingBytes = null; pendingName = null; pendingMime = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && b != null && n != null) {
                writeLegacy(b, n)
            } else {
                toast("Storage permission is needed to save the file")
            }
        }
    }

    private fun sanitize(n: String): String =
        n.ifBlank { "document" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun mimeForExt(ext: String): String = when (ext.lowercase()) {
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_FILE = 1001
        private const val REQ_PERM = 1002
        private const val REQ_SAVEAS = 1003
        private const val REQ_TREE = 1004
        private const val REQ_NATIVE_IMAGE = 1005
        private const val REQ_NATIVE_OPEN = 1006
        private const val REQ_NATIVE_SAVE = 1007

        // Intercepts the editor's blob-based download links (Save .docx / Export PDF)
        // and routes the bytes to Android so they land in the Downloads folder.
        private const val BLOB_HOOK = """
(function(){
  if (window.__quilloHook) return; window.__quilloHook = true;
  document.addEventListener('click', function(e){
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (!a) return;
    var href = a.getAttribute('href') || '';
    if (href.indexOf('blob:') !== 0 && href.indexOf('data:') !== 0) return;
    e.preventDefault(); e.stopPropagation();
    var name = a.getAttribute('download') || 'document';
    fetch(href).then(function(r){ return r.blob(); }).then(function(b){
      var fr = new FileReader();
      fr.onload = function(){
        var d = String(fr.result); var i = d.indexOf(',');
        try { AndroidSaver.saveBase64(d.substring(i + 1), name, b.type || ''); } catch (err) {}
      };
      fr.readAsDataURL(b);
    }).catch(function(){});
  }, true);
})();
"""
    }
}
