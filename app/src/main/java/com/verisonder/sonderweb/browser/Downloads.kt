package com.verisonder.sonderweb.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Files the engine hands over (downloads), saved to the phone's Downloads folder. */
object Downloads {
    private val io = Executors.newFixedThreadPool(2)

    /** A download on its way: bytes so far, the total (-1 = unknown) and the speed (bytes/s). */
    class Live(val received: Long, val total: Long, val speed: Long)

    /** Downloads in progress, by id, for the Downloads page. */
    val live = mutableStateMapOf<Long, Live>()

    /** Goes up whenever the list changes (a download starts, ends or is removed). */
    var changes by mutableIntStateOf(0)
        private set

    private val cancelled: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** Cancels a download (by its id; private downloads have negative ones). */
    fun cancel(id: Long) {
        cancelled.add(id)
    }

    /** Private downloads aren't in the list: they get their own (negative) numbers. */
    private var nextPrivate = -1L

    /** Set when a notification asks for the Downloads page. */
    var showPage by androidx.compose.runtime.mutableStateOf(false)

    /** "32 MB of 94 MB · 4.1 MB/s · 15 s left" (size unknown: "32 MB · 4.1 MB/s"). */
    fun progressText(context: Context, received: Long, total: Long, speed: Long): String {
        val fmt = { b: Long -> android.text.format.Formatter.formatShortFileSize(context, b) }
        return buildList {
            add(if (total > 0) fmt(received) + " of " + fmt(total) else fmt(received))
            if (speed > 0) add(fmt(speed) + "/s")
            if (total > 0 && speed > 0) add(timeLeft((total - received).coerceAtLeast(0) / speed) + " left")
        }.joinToString(" · ")
    }

    /** "25 s", "3 min", "1 h 5 min". */
    private fun timeLeft(seconds: Long): String = when {
        seconds < 60 -> "${seconds.coerceAtLeast(1)} s"
        seconds < 3600 -> "${(seconds + 30) / 60} min"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }

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
        fetch(WebRequest.Builder(url).referrer(referrer).build(), isPrivate)
    }

    /** Downloads [req] (also for extensions); [done] says whether it got the file. */
    fun fetch(req: WebRequest, isPrivate: Boolean, done: (Boolean) -> Unit = {}) {
        val flags = if (isPrivate) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        GeckoWebExecutor(Browser.runtime).fetch(req, flags).accept({ r ->
            Browser.main.post {
                if (r == null) {
                    Browser.host?.toast("Download failed")
                    done(false)
                } else {
                    save(r, isPrivate)
                    done(true)
                }
            }
        }, { _ ->
            Browser.main.post {
                Browser.host?.toast("Download failed")
                done(false)
            }
        })
    }

    private fun guessMime(url: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun write(body: InputStream, name: String, mime: String, url: String, length: Long, isPrivate: Boolean) {
        val id = if (isPrivate) -1L else Browser.db.addDownload(name, url, mime)
        // Identifies it for Cancel and its notification.
        val key = if (id >= 0) id else nextPrivate--
        if (id >= 0) {
            live[id] = Live(0, length, 0)
            changes++
        }
        // "More" opens the Downloads page (progress, speed, time left, and what you can do with it).
        Browser.host?.toast("Downloading $name", "More") { showPage = true }
        DownloadNotifications.askOnce()
        DownloadNotifications.progress(key, name, isPrivate, 0, length, 0)
        // Keeps going if you leave the app.
        DownloadService.started(Browser.app)
        io.execute {
            var uri: Uri? = null
            var size = 0L
            val ok = runCatching {
                uri = target(name, mime)
                val out = Browser.app.contentResolver.openOutputStream(uri!!) ?: error("no output")
                body.use { input ->
                    out.use {
                        val buf = ByteArray(64 * 1024)
                        var lastAt = android.os.SystemClock.elapsedRealtime()
                        var lastSize = 0L
                        var speed = 0L
                        var notifiedAt = 0L
                        while (true) {
                            if (key in cancelled) error("cancelled")
                            val n = input.read(buf)
                            if (n < 0) break
                            it.write(buf, 0, n)
                            size += n
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastAt >= 400) {
                                val instant = (size - lastSize) * 1000 / (now - lastAt)
                                speed = if (speed == 0L) instant else (speed * 6 + instant * 4) / 10
                                lastAt = now
                                lastSize = size
                                if (id >= 0) {
                                    val report = Live(size, length, speed)
                                    Browser.main.post { if (live.containsKey(id)) live[id] = report }
                                }
                                // The notification is updated about once a second (Android limits it).
                                if (now - notifiedAt >= 1000) {
                                    notifiedAt = now
                                    DownloadNotifications.progress(key, name, isPrivate, size, length, speed)
                                }
                            }
                        }
                    }
                }
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
            val stopped = cancelled.remove(key)
            Browser.main.post {
                DownloadService.ended(Browser.app)
                val state = when {
                    ok -> DownloadItem.DONE
                    stopped -> DownloadItem.CANCELLED
                    else -> DownloadItem.FAILED
                }
                if (id >= 0) {
                    Browser.db.finishDownload(id, if (ok) uri?.toString() else null, size, state)
                    live.remove(id)
                    changes++
                }
                when (state) {
                    DownloadItem.DONE -> {
                        val done = uri!!
                        DownloadNotifications.done(key, name, isPrivate, done, mime)
                        Browser.host?.toast("Downloaded $name", "Open") { open(done, mime) }
                    }
                    DownloadItem.CANCELLED -> DownloadNotifications.remove(key)
                    else -> {
                        DownloadNotifications.failed(key, name, isPrivate)
                        Browser.host?.toast("Download of $name failed", "More") { showPage = true }
                    }
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

    /** Sends the file to another app. */
    fun share(item: DownloadItem) {
        if (item.uri.isEmpty()) return
        val i = Intent(Intent.ACTION_SEND)
            .setType(item.mime.ifEmpty { "*/*" })
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            Browser.app.startActivity(Intent.createChooser(i, item.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Browser.host?.toast("No app can take this file") }
    }

    fun copyLink(item: DownloadItem) {
        val cm = Browser.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(item.name, item.url))
        Browser.host?.toast("Link copied")
    }

    /** Downloads it again (the old entry goes). */
    fun retry(item: DownloadItem) {
        remove(item)
        fetch(item.url, isPrivate = false, referrer = null)
    }

    /** Off the list; the file stays on the phone. */
    fun remove(item: DownloadItem) {
        Browser.db.deleteDownload(item.id)
        changes++
    }

    /** Deletes the file from the phone and takes it off the list. */
    fun deleteFile(item: DownloadItem) {
        if (item.uri.isNotEmpty()) {
            runCatching { Browser.app.contentResolver.delete(Uri.parse(item.uri), null, null) }
        }
        remove(item)
        Browser.host?.toast("Deleted ${item.name}")
    }

    fun clear() {
        Browser.db.clearDownloads()
        changes++
    }
}
