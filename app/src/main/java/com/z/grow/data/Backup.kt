package com.z.grow.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backups are a single zip holding the database and every journal photo. They are written
 * through the system file picker, so the user can save them to Google Drive (or anywhere else).
 */
object Backup {
    private const val DB_ENTRY = "grow.db"
    private const val PHOTO_PREFIX = "photos/"

    suspend fun write(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = GrowDatabase.get(context)
        db.checkpoint()
        val dbFile = context.getDatabasePath(GrowDatabase.NAME)
        openOutput(context, uri).use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                PhotoStore.dir(context).listFiles()?.filter { it.isFile }?.forEach { photo ->
                    zip.putNextEntry(ZipEntry(PHOTO_PREFIX + photo.name))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Replaces all data with the backup. The app must be restarted afterwards with [restartApp]. */
    suspend fun restore(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        val stagedDb = File(staging, DB_ENTRY)
        val stagedPhotos = File(staging, "photos").apply { mkdirs() }
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = when {
                        entry.isDirectory -> null
                        entry.name == DB_ENTRY -> stagedDb
                        // Only keep the bare file name so a crafted zip can't write outside the folder.
                        entry.name.startsWith(PHOTO_PREFIX) -> File(stagedPhotos, File(entry.name).name)
                        else -> null
                    }
                    target?.outputStream()?.use { zip.copyTo(it) }
                }
            }
            require(stagedDb.exists()) { "That file isn't a Grow with Green backup." }
            val version = runCatching {
                SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            }.getOrElse { throw IllegalArgumentException("The backup's database is damaged.") }
            require(version in 1..GrowDatabase.VERSION) {
                "This backup was made by a newer version of the app. Update the app and try again."
            }

            GrowDatabase.close()
            val dbFile = context.getDatabasePath(GrowDatabase.NAME)
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            dbFile.parentFile?.mkdirs()
            stagedDb.copyTo(dbFile, overwrite = true)

            val photos = PhotoStore.dir(context)
            photos.deleteRecursively()
            photos.mkdirs()
            stagedPhotos.listFiles()?.forEach { it.copyTo(File(photos, it.name), overwrite = true) }
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Relaunches the app in a fresh process so every screen reloads the restored data. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun openOutput(context: Context, uri: Uri): OutputStream {
        val resolver = context.contentResolver
        // "wt" truncates an existing backup; some providers only support plain "w".
        return runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw FileNotFoundException("Couldn't open the backup file.")
    }
}
