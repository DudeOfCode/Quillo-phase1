package com.quillo.app.document

import android.content.Context
import java.io.File

/** Native lifecycle store. Autosave is app-private; explicit .quillo files use the Storage Access Framework. */
class DocumentRepository(context: Context) {
    private val autosave = File(context.filesDir, "autosave.quillo")
    fun saveAutosave(document: QuilloDocument) { autosave.writeText(DocumentJsonCodec.encode(document), Charsets.UTF_8) }
    fun loadAutosave(): QuilloDocument? = runCatching { if (autosave.exists()) DocumentJsonCodec.decode(autosave.readText()) else null }.getOrNull()
    fun clearAutosave() { runCatching { autosave.delete() } }
}
