package com.verisonder.sonderweb.browser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Files a page asked for (an <input type="file">), copied from Android's picker into the cache
 * so the engine gets real file:// paths. Each pick replaces the last one's copies.
 */
object PickedFiles {
    private const val DIR = "picked"

    fun copy(ctx: Context, uris: List<Uri>): List<Uri> {
        val root = File(ctx.cacheDir, DIR)
        root.deleteRecursively()
        return uris.mapIndexedNotNull { i, uri ->
            if (uri.scheme == "file") return@mapIndexedNotNull uri
            runCatching {
                // A folder per file, so two picked files with the same name both keep it.
                val dir = File(root, i.toString()).apply { mkdirs() }
                val out = File(dir, nameOf(ctx, uri, i))
                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                Uri.fromFile(out)
            }.getOrNull()
        }
    }

    private fun nameOf(ctx: Context, uri: Uri, i: Int): String {
        val shown = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        // Only a plain file name: no folders, nothing that climbs out of the cache.
        val clean = shown?.substringAfterLast('/')?.replace(Regex("[\\\\:*?\"<>|\\u0000-\\u001f]"), "_")?.trim()
        val name = clean?.takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: "file$i"
        // Pages judge a file by its type, which comes from its extension (uBlock Origin only
        // restores .txt and .json backups): a name without one gets the picker's type's.
        if (name.substringAfterLast('.', "").isNotEmpty()) return name
        val ext = ctx.contentResolver.getType(uri)?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext.isNullOrEmpty()) name else "$name.$ext"
    }
}
