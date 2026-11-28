package com.verisonder.sonderweb.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.verisonder.sonderweb.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * SonderCircle: circle anything on the screen to search it. Its own small app, in the same
 * releases as SonderWeb and with the same version; Settings installs, updates and opens it.
 */
object CircleApp {
    const val PACKAGE = "com.verisonder.sondercircle"
    private const val SETTINGS = "com.verisonder.sondercircle.SetupActivity"
    private const val REPO = "https://github.com/Verisonder/SonderWeb"

    /** 0..1 while downloading; null otherwise. */
    var progress by mutableStateOf<Float?>(null)
        private set

    /** The installed SonderCircle's version, or null if it isn't installed. */
    fun installedVersion(ctx: Context): String? = runCatching {
        ctx.packageManager.getPackageInfo(PACKAGE, 0).versionName
    }.getOrNull()

    /** Installed but older than this SonderWeb (both come from the same release). */
    fun outdated(ctx: Context): Boolean =
        installedVersion(ctx)?.let { AppUpdate.newer(BuildConfig.VERSION_NAME, it) } == true

    /** Opens SonderCircle's own settings page. */
    fun open(ctx: Context) {
        ctx.startActivity(Intent().setClassName(PACKAGE, SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Downloads SonderCircle from this version's release and opens Android's installer. */
    suspend fun install(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        if (progress != null) return
        progress = 0f
        val file = try {
            withContext(Dispatchers.IO) { download(ctx) }
        } finally {
            progress = null
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun download(ctx: Context): File {
        val version = BuildConfig.VERSION_NAME
        val dir = File(ctx.cacheDir, "circle").apply { mkdirs() }
        val out = File(dir, "SonderCircle-$version.apk")
        if (out.exists() && out.length() > 0) return out
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, out.name + ".part")
        fun open(u: URL) = (u.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SonderWeb")
        }
        // GitHub answers with a redirect to its file host.
        var c = open(URL("$REPO/releases/download/v$version/SonderCircle-$version.apk"))
        var hops = 0
        while (c.responseCode in 300..399 && hops++ < 5) {
            val next = URL(c.url, c.getHeaderField("Location"))
            c.disconnect()
            c = open(next)
        }
        if (c.responseCode == 404) throw IllegalStateException("SonderCircle isn't in this release yet")
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
        if (!part.renameTo(out)) throw IllegalStateException("Couldn't save SonderCircle")
        return out
    }
}
