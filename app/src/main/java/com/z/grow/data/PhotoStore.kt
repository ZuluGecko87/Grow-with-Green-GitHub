package com.z.grow.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Journal photos are copied into the app's own storage so they survive gallery changes and go into backups. */
object PhotoStore {
    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    fun newFile(context: Context): File = File(dir(context), "IMG_${UUID.randomUUID()}.jpg")

    /** A content:// URI the camera app can write to. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Copies a picked image into the photos folder; returns its file name, or null on failure. */
    fun import(context: Context, uri: Uri): String? {
        val target = newFile(context)
        return runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target.name
        }.onFailure { target.delete() }.getOrNull()
    }

    fun delete(context: Context, fileNames: Collection<String>) {
        fileNames.forEach { file(context, it).delete() }
    }
}
