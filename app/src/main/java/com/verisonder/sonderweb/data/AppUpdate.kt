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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the GitHub releases (the same signed APK the release page has).
 * SonderWeb checks by itself when it opens (at most every few hours); a new version shows as
 * a small blue dot on the menu button and on Settings, and installs from Settings › About
 * in one tap (Android asks to confirm, as for any app update).
 */
object AppUpdate {

    data class Info(val version: String, val notes: String, val apkUrl: String, val size: Long)

    /** The newer version, when there is one (null = up to date or not checked yet). */
    var available by mutableStateOf<Info?>(null)
        private set

    /** Download progress 0..1 while an update downloads, else null. */
    var progress by mutableStateOf<Float?>(null)
        private set

    private const val API = "https://api.github.com/repos/Verisonder/SonderWeb/releases/latest"
    private const val PREFS = "app_update"
    private const val AUTO_EVERY_MS = 6 * 3_600_000L

    /** On app open: the last known result straight away, then a fresh check if it's been a while. */
    suspend fun autoCheck(ctx: Context) {
        restore(ctx)
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong("checkedAt", 0L) < AUTO_EVERY_MS) return
        runCatching { check(ctx) }
    }

    private fun restore(ctx: Context) {
        if (available != null) return
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = sp.getString("version", null) ?: return
        if (newer(v, BuildConfig.VERSION_NAME)) {
            available = Info(v, sp.getString("notes", "") ?: "", sp.getString("url", "") ?: "", sp.getLong("size", 0L))
        }
    }

    private const val REPO = "https://github.com/Verisonder/SonderWeb"
    private const val RAW = "https://raw.githubusercontent.com/Verisonder/SonderWeb"

    /**
     * Asks GitHub for the latest release. Returns the newer version, or null when up to date.
     * Reads the release page's address first: GitHub's API allows only a few dozen questions an
     * hour from one network (phones on mobile data share theirs) and then answers 403.
     */
    suspend fun check(ctx: Context): Info? = withContext(Dispatchers.IO) {
        val found = runCatching { fromReleasePage() }.getOrNull() ?: fromApi()
        val info = found?.takeIf { it.apkUrl.isNotEmpty() && newer(it.version, BuildConfig.VERSION_NAME) }
        remember(ctx, info)
        info
    }

    /** github.com/…/releases/latest redirects to the latest release's tag; the APK and its notes
     *  are at fixed addresses from there. */
    private fun fromReleasePage(): Info? {
        val c = URL("$REPO/releases/latest").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "SonderWeb")
        val location = try {
            if (c.responseCode !in 300..399) throw IllegalStateException("GitHub answered ${c.responseCode}")
            c.getHeaderField("Location") ?: throw IllegalStateException("No release")
        } finally {
            c.disconnect()
        }
        val tag = location.substringAfter("/releases/tag/", "").substringBefore('?').trim('/')
        if (tag.isEmpty()) return null
        val version = tag.removePrefix("v")
        val notes = Net.text("$RAW/$tag/docs/releasenote/release_notes_$tag.md")
            ?.lines()?.dropWhile { it.isBlank() || it.startsWith("## ") }?.joinToString("\n")?.trim() ?: ""
        return Info(version, notes, "$REPO/releases/download/$tag/SonderWeb-$version.apk", 0L)
    }

    /** The same through GitHub's API (also gives the download's size). */
    private fun fromApi(): Info? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "SonderWeb")
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val version = j.optString("tag_name").removePrefix("v")
            var url = ""
            var size = 0L
            val assets = j.optJSONArray("assets")
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                val name = a.optString("name")
                if (name.startsWith("SonderWeb-") && name.endsWith(".apk")) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                }
            }
            return Info(version, j.optString("body"), url, size)
        } finally {
            c.disconnect()
        }
    }

    private suspend fun remember(ctx: Context, info: Info?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("checkedAt", System.currentTimeMillis())
            .putString("version", info?.version).putString("notes", info?.notes)
            .putString("url", info?.apkUrl).putLong("size", info?.size ?: 0L).apply()
        withContext(Dispatchers.Main) { available = info }
    }

    /** Downloads the APK (progress in [progress]) and opens Android's installer. */
    suspend fun install(ctx: Context, info: Info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            // One-time switch in Android's settings: "Allow from this source".
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        progress = 0f
        val file = try {
            withContext(Dispatchers.IO) { download(ctx, info) }
        } finally {
            progress = null
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun download(ctx: Context, info: Info): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "SonderWeb-${info.version}.apk")
        try {
            fun open(u: URL) = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "SonderWeb")
            }
            // GitHub answers with a redirect to its file host (sometimes two).
            var c = open(URL(info.apkUrl))
            var hops = 0
            while (c.responseCode in 300..399 && hops++ < 5) {
                val next = URL(c.url, c.getHeaderField("Location"))
                c.disconnect()
                c = open(next)
            }
            if (c.responseCode != 200) throw IllegalStateException("Download failed (${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            c.inputStream.use { input ->
                out.outputStream().use { o ->
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
            return out
        } catch (e: Exception) {
            out.delete()
            throw e
        }
    }

    /** a > b, comparing dotted numbers ("1.2.0" > "1.1.9"). */
    fun newer(a: String, b: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size, 3)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }
}
