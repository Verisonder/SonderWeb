package com.verisonder.sondercircle

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the GitHub releases, like SonderWeb's own: SonderCircle-X.Y.Z.apk from the latest
 * release (same signing key), installed with Android's installer in one tap.
 */
object Updater {
    private const val REPO = "https://github.com/Verisonder/SonderWeb"

    /** The newer version on GitHub (null: up to date or not checked yet). */
    var available by mutableStateOf<String?>(null)
        private set

    /** 0..1 while downloading, else null. */
    var progress by mutableStateOf<Float?>(null)
        private set

    var checking by mutableStateOf(false)
        private set

    fun current(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    /** a > b, comparing numbers part by part (5.10.0 > 5.9.9). */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun connect(u: URL) = (u.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "SonderCircle")
    }

    /**
     * Asks which release is the latest (github.com/…/releases/latest redirects to its tag; the
     * API would limit us to a few dozen questions an hour). Returns the newer version, or null.
     */
    suspend fun check(ctx: Context): String? = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { checking = true }
        try {
            val c = connect(URL("$REPO/releases/latest"))
            val location = try {
                if (c.responseCode !in 300..399) throw IllegalStateException("GitHub answered ${c.responseCode}")
                c.getHeaderField("Location") ?: throw IllegalStateException("No release")
            } finally {
                c.disconnect()
            }
            val version = location.substringAfter("/releases/tag/", "").substringBefore('?').trim('/').removePrefix("v")
            val newer = version.takeIf { it.isNotEmpty() && newer(it, current(ctx)) }
            withContext(Dispatchers.Main) { available = newer }
            newer
        } finally {
            withContext(Dispatchers.Main) { checking = false }
        }
    }

    /** Downloads the newer version and opens Android's installer (asks for the install permission first). */
    suspend fun install(ctx: Context) {
        val version = available ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        if (progress != null) return
        progress = 0f
        val file = try {
            withContext(Dispatchers.IO) { download(ctx, version) }
        } finally {
            progress = null
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".shots", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun download(ctx: Context, version: String): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "SonderCircle-$version.apk")
        if (out.exists() && out.length() > 0) return out
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, out.name + ".part")
        // GitHub answers with a redirect to its file host.
        var c = connect(URL("$REPO/releases/download/v$version/SonderCircle-$version.apk"))
        var hops = 0
        while (c.responseCode in 300..399 && hops++ < 5) {
            val next = URL(c.url, c.getHeaderField("Location"))
            c.disconnect()
            c = connect(next)
        }
        if (c.responseCode != 200) throw IllegalStateException("Download failed (${c.responseCode})")
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            part.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var shown = 0f
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val f = (done.toFloat() / total).coerceIn(0f, 1f)
                        if (f - shown >= 0.01f) {
                            shown = f
                            withContext(Dispatchers.Main) { progress = f }
                        }
                    }
                }
            }
        }
        c.disconnect()
        if (!part.renameTo(out)) throw IllegalStateException("Couldn't save the update")
        return out
    }

    /** Once installed, the downloaded file isn't needed. */
    fun cleanUp(ctx: Context) {
        val now = current(ctx)
        File(ctx.cacheDir, "updates").listFiles()?.forEach { f ->
            val v = f.name.removePrefix("SonderCircle-").removeSuffix(".part").removeSuffix(".apk")
            if (!newer(v, now)) f.delete()
        }
    }
}
