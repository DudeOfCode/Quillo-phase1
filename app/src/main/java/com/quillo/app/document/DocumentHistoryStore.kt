package com.quillo.app.document
import android.content.Context
import java.io.File

class DocumentHistoryStore(context: Context) {
    private val dir=File(context.filesDir,"versions").apply{mkdirs()}
    fun snapshot(document:QuilloDocument, keep:Int=8){
        File(dir,"snapshot-${System.currentTimeMillis()}.quillo").writeText(DocumentJsonCodec.encode(document))
        dir.listFiles()?.sortedByDescending{it.lastModified()}?.drop(keep)?.forEach{it.delete()}
    }
    fun versions():List<File> = dir.listFiles()?.sortedByDescending{it.lastModified()} ?: emptyList()
    fun restore(file:File):QuilloDocument = DocumentJsonCodec.decode(file.readText())
}
