package com.verisonder.sonderweb.browser

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.DownloadItem
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/** Files the engine hands over (downloads), saved to the phone's Downloads folder. */
object Downloads {
    private val io = Executors.newFixedThreadPool(2)

    private fun header(r: WebResponse, name: String): String? =
        r.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun save(response: WebResponse, isPrivate: Boolean) {
        val body = response.body ?: run {
            Browser.host?.toast("Download failed")
            return
        }
        val mime = header(response, "Content-Type")?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }
            ?: guessMime(response.uri)
        val name = UrlText.fileName(header(response, "Content-Disposition"), response.uri, mime)
        val length = header(response, "Content-Length")?.toLongOrNull() ?: -1L
        write(body, name, mime, response.uri, length, isPrivate)
    }

    /** Downloads a link or image from the long-press menu, with the page's cookies. */
    fun fetch(url: String, isPrivate: Boolean, referrer: String?) {
        if (url.startsWith("data:")) {
            Browser.host?.toast("This file can't be saved")
            return
        }
        val req = WebRequest.Builder(url).referrer(referrer).build()
        val flags = if (isPrivate) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        GeckoWebExecutor(Browser.runtime).fetch(req, flags).accept({ r ->
            if (r == null) {
                Browser.main.post { Browser.host?.toast("Download failed") }
            } else {
                Browser.main.post { save(r, isPrivate) }
            }
        }, { _ -> Browser.main.post { Browser.host?.toast("Download failed") } })
    }

    private fun guessMime(url: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun write(body: InputStream, name: String, mime: String, url: String, length: Long, isPrivate: Boolean) {
        val id = if (isPrivate) -1L else Browser.db.addDownload(name, url, mime)
        Browser.host?.toast("Downloading $name")
        io.execute {
            var uri: Uri? = null
            var size = 0L
            val ok = runCatching {
                uri = target(name, mime)
                val out = Browser.app.contentResolver.openOutputStream(uri!!) ?: error("no output")
                body.use { input -> out.use { size = input.copyTo(it, 64 * 1024) } }
                if (Build.VERSION.SDK_INT >= 29) {
                    Browser.app.contentResolver.update(
                        uri!!,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                if (length > 0 && size < length) error("short download")
            }.isSuccess
            if (!ok) uri?.let { u -> runCatching { Browser.app.contentResolver.delete(u, null, null) } }
            Browser.main.post {
                if (id >= 0) Browser.db.finishDownload(id, uri?.toString(), size, ok)
                if (ok) {
                    val done = uri!!
                    Browser.host?.toast("Downloaded $name", "Open") { open(done, mime) }
                } else {
                    Browser.host?.toast("Download of $name failed")
                }
            }
        }
    }

    private fun target(name: String, mime: String): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            return Browser.app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: error("MediaStore refused the file")
        }
        val dir = File(Browser.app.getExternalFilesDir(null), "Download").apply { mkdirs() }
        var f = File(dir, name)
        var n = 1
        while (f.exists()) {
            f = File(dir, name.substringBeforeLast('.') + " ($n)" +
                (if (name.contains('.')) "." + name.substringAfterLast('.') else ""))
            n++
        }
        f.createNewFile()
        return FileProvider.getUriForFile(Browser.app, Browser.app.packageName + ".files", f)
    }

    fun open(uri: Uri, mime: String) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { Browser.app.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Browser.host?.toast("No app can open this file") }
    }

    fun open(item: DownloadItem) {
        if (item.uri.isEmpty()) return
        open(Uri.parse(item.uri), item.mime.ifEmpty { "*/*" })
    }
}
