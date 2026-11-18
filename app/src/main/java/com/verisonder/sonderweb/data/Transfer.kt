package com.verisonder.sonderweb.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.core.content.FileProvider
import com.verisonder.sonderweb.BuildConfig
import com.verisonder.sonderweb.browser.Browser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copying everything between the two apps (SonderWeb and SonderWeb Sync): settings, start page
 * (shortcuts, wallpaper, name, place), history, bookmarks with their folders, and open tabs.
 *
 * Settings are replaced with the other app's; history, bookmarks and tabs are added to what this
 * app already has (nothing here is lost). Not copied: cookies and sign-ins to sites, extensions
 * and their settings, site permissions (they live inside the engine), the Firefox Sync account
 * and its saved passwords, addresses and cards, and the home screen widgets.
 *
 * Only the two apps can hand each other their data: the screen that receives it can only be
 * opened by an app signed with the same key ([PERMISSION]).
 */
object Transfer {
    /** What the receiving screen answers to. */
    const val ACTION = "com.verisonder.sonderweb.action.COPY_IN"

    private const val BASE_ID = "com.verisonder.sonderweb"

    /** The other app's package. */
    val otherPackage: String = if (BuildConfig.FIREFOX_SYNC) BASE_ID else "$BASE_ID.sync"

    /** The other app's name. */
    val otherName: String = if (BuildConfig.FIREFOX_SYNC) "SonderWeb" else "SonderWeb Sync"

    /** Settings that aren't copied: open tabs are added to (not replaced), the rest belongs to this app. */
    private val NOT_COPIED = setOf("tabs", "tab_titles", "tab_colors", "tab_used", "tab_index")

    fun otherInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(otherPackage, 0)
        true
    }.getOrDefault(false)

    /** The other app is signed with the same key (so it really is the other SonderWeb). */
    fun sameSigner(context: Context): Boolean =
        context.packageManager.checkSignatures(context.packageName, otherPackage) == PackageManager.SIGNATURE_MATCH

    // ---- Sending ----

    /**
     * Everything to copy, packed into one file; the address the other app may read it from.
     * Off the main thread, after [Browser.saveTabs] on it (so the open tabs are current).
     */
    fun pack(context: Context): Uri {
        val dir = File(context.cacheDir, "transfer").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "sonderweb-data.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(snapshot(context).toString().toByteArray())
            zip.closeEntry()
            val wallpaper = File(context.filesDir, "start_wallpaper.jpg")
            if (wallpaper.exists()) {
                zip.putNextEntry(ZipEntry("start_wallpaper.jpg"))
                wallpaper.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    private fun snapshot(context: Context): JSONObject {
        val out = JSONObject()
        out.put("format", 1)
        out.put("from", context.packageName)
        out.put("version", BuildConfig.VERSION_NAME)

        // Settings, each with its kind.
        val prefs = JSONObject()
        context.getSharedPreferences("sonderweb", Context.MODE_PRIVATE).all.forEach { (key, value) ->
            val v = when (value) {
                is String -> JSONObject().put("s", value)
                is Int -> JSONObject().put("i", value)
                is Long -> JSONObject().put("l", value)
                is Boolean -> JSONObject().put("b", value)
                is Float -> JSONObject().put("f", value.toDouble())
                is Set<*> -> JSONObject().put("ss", JSONArray(value.map { it.toString() }))
                else -> null
            }
            if (v != null) prefs.put(key, v)
        }
        out.put("prefs", prefs)
        out.put("squircle", AppIcon.isSquircle(context))

        val db = Browser.db
        out.put(
            "history",
            JSONArray().apply {
                db.history(limit = 100_000).forEach { h ->
                    put(JSONObject().put("u", h.url).put("t", h.title).put("v", h.visited))
                }
            },
        )
        out.put(
            "bookmarks",
            JSONArray().apply {
                db.allBookmarks().forEach { b ->
                    put(
                        JSONObject().put("id", b.id).put("u", b.url).put("t", b.title).put("a", b.added)
                            .put("p", b.parent).put("f", b.folder).put("pos", b.position),
                    )
                }
            },
        )
        val p = Browser.prefs
        out.put(
            "tabs",
            JSONArray().apply {
                val titles = p.savedTabTitles
                val used = p.savedTabUsed
                p.savedTabs.forEachIndexed { i, url ->
                    put(JSONObject().put("u", url).put("t", titles.getOrNull(i) ?: "").put("used", used.getOrNull(i) ?: 0L))
                }
            },
        )
        return out
    }

    /** Opens the other app's "copy in" screen with everything (from [Activity] so it can be granted). */
    fun send(activity: Activity, uri: Uri) {
        val intent = Intent(ACTION)
            .setPackage(otherPackage)
            .setDataAndType(uri, "application/zip")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    // ---- Receiving ----

    /** What came over, for the question before copying. */
    class Incoming(
        val from: String,
        val version: String,
        val json: JSONObject,
        val wallpaper: ByteArray?,
    ) {
        val historyCount: Int get() = json.optJSONArray("history")?.length() ?: 0
        val bookmarkCount: Int get() = json.optJSONArray("bookmarks")?.let { a -> (0 until a.length()).count { !a.getJSONObject(it).optBoolean("f") } } ?: 0
        val tabCount: Int get() = json.optJSONArray("tabs")?.length() ?: 0
    }

    fun read(input: InputStream): Incoming? = runCatching {
        var json: JSONObject? = null
        var wallpaper: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when (e.name) {
                    "data.json" -> json = JSONObject(String(zip.readBytes()))
                    "start_wallpaper.jpg" -> wallpaper = zip.readBytes()
                }
            }
        }
        val j = json ?: return null
        if (j.optInt("format") != 1) return null
        Incoming(j.optString("from"), j.optString("version"), j, wallpaper)
    }.getOrNull()

    /** Copies everything in (off the main thread). Settings replaced; history, bookmarks and tabs added. */
    fun apply(context: Context, inc: Incoming) {
        val j = inc.json

        // Settings: this app's are replaced by the other's (its open tabs are kept, see below).
        val sp = context.getSharedPreferences("sonderweb", Context.MODE_PRIVATE)
        val keep = NOT_COPIED.associateWith { sp.all[it] }
        val e = sp.edit().clear()
        keep.forEach { (k, v) ->
            when (v) {
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Boolean -> e.putBoolean(k, v)
            }
        }
        j.optJSONObject("prefs")?.let { prefs ->
            prefs.keys().forEach { k ->
                if (k in NOT_COPIED) return@forEach
                val v = prefs.getJSONObject(k)
                when {
                    v.has("s") -> e.putString(k, v.getString("s"))
                    v.has("i") -> e.putInt(k, v.getInt("i"))
                    v.has("l") -> e.putLong(k, v.getLong("l"))
                    v.has("b") -> e.putBoolean(k, v.getBoolean("b"))
                    v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat())
                    v.has("ss") -> v.getJSONArray("ss").let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                }
            }
        }

        // Open tabs: the other app's added after this app's own (ones already open aren't doubled).
        val tabs = j.optJSONArray("tabs")
        if (tabs != null && tabs.length() > 0) {
            val p = Prefs(context)
            val urls = p.savedTabs.toMutableList()
            val titles = p.savedTabTitles.toMutableList().also { while (it.size < urls.size) it += "" }
            val used = p.savedTabUsed.toMutableList().also { while (it.size < urls.size) it += 0L }
            val colors = p.savedTabColors.toMutableList().also { while (it.size < urls.size) it += (null to null) }
            for (i in 0 until tabs.length()) {
                val t = tabs.getJSONObject(i)
                val url = t.optString("u")
                if (url.isEmpty() || url in urls) continue
                urls += url
                titles += t.optString("t")
                used += t.optLong("used")
                colors += (null to null)
            }
            e.putString("tabs", JSONArray(urls).toString())
            e.putString("tab_titles", JSONArray(titles).toString())
            e.putString("tab_used", JSONArray(used).toString())
            e.putString(
                "tab_colors",
                JSONArray().apply {
                    colors.forEach { (top, bottom) ->
                        put(JSONArray().put(top ?: JSONObject.NULL).put(bottom ?: JSONObject.NULL))
                    }
                }.toString(),
            )
        }
        e.commit()

        // The home screen icon's shape, and the start page's wallpaper.
        runCatching { AppIcon.set(context, j.optBoolean("squircle")) }
        val wallpaper = File(context.filesDir, "start_wallpaper.jpg")
        if (inc.wallpaper != null) {
            val tmp = File(context.filesDir, "start_wallpaper.tmp")
            tmp.writeBytes(inc.wallpaper)
            tmp.renameTo(wallpaper)
        } else {
            wallpaper.delete()
        }

        // This app may not be running (no browser started): its own lists, opened here.
        val db = Db(context)
        // History: added; a page in both keeps its latest visit.
        j.optJSONArray("history")?.let { a ->
            val visits = (0 until a.length()).map {
                val h = a.getJSONObject(it)
                Triple(h.optString("u"), h.optString("t").ifEmpty { null }, h.optLong("v"))
            }.filter { it.first.isNotEmpty() }
            db.addVisits(visits, synced = 0)
        }

        // Bookmarks: added with their folders (a folder of the same name in the same place is
        // reused; a page already bookmarked isn't added twice).
        j.optJSONArray("bookmarks")?.let { a ->
            val items = (0 until a.length()).map { a.getJSONObject(it) }
            val newId = HashMap<Long, Long>().apply { put(0L, 0L) }
            val byParent = items.groupBy { it.optLong("p") }
            fun place(parentOld: Long) {
                val parentNew = newId[parentOld] ?: return
                byParent[parentOld].orEmpty().sortedBy { it.optInt("pos") }.forEach { b ->
                    val title = b.optString("t")
                    if (b.optBoolean("f")) {
                        val existing = db.bookmarksIn(parentNew).firstOrNull { it.folder && it.title == title }
                        newId[b.optLong("id")] = existing?.id ?: db.addFolder(title, parentNew)
                        place(b.optLong("id"))
                    } else {
                        val url = b.optString("u")
                        if (url.isNotEmpty()) db.addBookmark(url, title, parentNew)
                    }
                }
            }
            place(0L)
        }
        runCatching { db.close() }
    }

    /** Starts the app afresh, so everything copied in is used. */
    fun restart(activity: Activity) {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.finishAffinity()
        if (launch != null) activity.startActivity(launch)
        Process.killProcess(Process.myPid())
    }
}
