package com.verisonder.sonderweb.data

import android.content.Context
import android.net.Uri
import com.verisonder.sonderweb.BuildConfig
import com.verisonder.sonderweb.core.BookmarksHtml
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Settings › About › Import / Export: settings, the start page and bookmarks, each chosen or not,
 * in one file. History and open tabs are never in it. Bookmarks alone are saved as a plain HTML
 * bookmarks file (other browsers read it); an HTML bookmarks file from another browser can be
 * imported too. Call off the main thread.
 */
object Backup {
    enum class Part(val label: String) { SETTINGS("Settings"), START("Start page"), BOOKMARKS("Bookmarks") }

    /** What an import did, and whether the app should restart to use it. */
    class Result(val message: String, val restart: Boolean)

    private const val FORMAT = 1
    private const val DATA = "sonderweb-backup.json"
    private const val WALLPAPER = "start_wallpaper.jpg"

    /** The start page's own settings (the rest of the settings file is "Settings"). */
    private val START_KEYS = setOf(
        "shortcuts", "shortcuts_on", "shortcut_add", "shortcut_names", "collections",
        "show_clock", "show_greeting", "name", "clock_lift", "rest_lift", "search_field", "search_button",
        "weather", "fahrenheit", "place_name", "place_lat", "place_lon",
        "start_bg_color", "start_dim", "start_wallpaper",
        "start_collections", "start_bookmarks", "start_most", "start_recent", "start_closed",
    )

    /** Never in the file: open tabs, profiles, recently closed, and what the app notes for itself. */
    private val NEVER = setOf(
        "tabs", "tab_titles", "tab_colors", "tab_used", "tab_index", "tab_profiles",
        "profiles", "profile", "profiles_on", "profile_google_pictures",
        "split_tabs", "split_history", "split_bookmarks", "split_shortcuts",
        "closed_tabs", "last_weather", "left_at", "asked_notifications", "ext_setup",
    )

    private fun partOf(key: String): Part? = when {
        '@' in key || key in NEVER -> null
        key in START_KEYS -> Part.START
        else -> Part.SETTINGS
    }

    private fun prefsFile(context: Context) = context.getSharedPreferences("sonderweb", Context.MODE_PRIVATE)

    private fun wallpaperFile(context: Context) = File(context.filesDir, WALLPAPER)

    // ---- Export ----

    /** The file name to suggest: bookmarks alone are an HTML bookmarks file. */
    fun fileName(parts: Set<Part>): String =
        if (parts == setOf(Part.BOOKMARKS)) "bookmarks.html" else "sonderweb-backup.zip"

    fun mimeOf(parts: Set<Part>): String = if (parts == setOf(Part.BOOKMARKS)) "text/html" else "application/zip"

    fun export(context: Context, uri: Uri, parts: Set<Part>, db: Db): String {
        require(parts.isNotEmpty()) { "Nothing chosen" }
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't write the file")
        if (parts == setOf(Part.BOOKMARKS)) {
            val (html, n) = BookmarksFile.exportHtml(db)
            out.use { it.write(html.toByteArray(Charsets.UTF_8)) }
            return "Exported $n bookmarks"
        }
        val json = JSONObject().put("format", FORMAT).put("app", "SonderWeb").put("version", BuildConfig.VERSION_NAME)
        val chosen = JSONArray()
        parts.forEach { chosen.put(it.name) }
        json.put("parts", chosen)
        Part.entries.filter { it != Part.BOOKMARKS && it in parts }.forEach { part ->
            val prefs = JSONObject()
            prefsFile(context).all.forEach { (key, value) -> if (partOf(key) == part) typed(value)?.let { prefs.put(key, it) } }
            json.put(part.name.lowercase(), prefs)
        }
        var pages = 0
        if (Part.BOOKMARKS in parts) {
            val (html, n) = BookmarksFile.exportHtml(db)
            json.put("bookmarks", html)
            pages = n
        }
        val wallpaper = wallpaperFile(context).takeIf { Part.START in parts && it.exists() }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(json.toString().toByteArray())
            zip.closeEntry()
            if (wallpaper != null) {
                zip.putNextEntry(ZipEntry(WALLPAPER))
                wallpaper.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return "Exported " + names(parts, pages)
    }

    private fun typed(value: Any?): JSONObject? = when (value) {
        is String -> JSONObject().put("s", value)
        is Int -> JSONObject().put("i", value)
        is Long -> JSONObject().put("l", value)
        is Boolean -> JSONObject().put("b", value)
        is Float -> JSONObject().put("f", value.toDouble())
        is Set<*> -> JSONObject().put("ss", JSONArray(value.map { it.toString() }))
        else -> null
    }

    private fun names(parts: Set<Part>, pages: Int): String {
        val list = Part.entries.filter { it in parts }.map {
            if (it == Part.BOOKMARKS) "$pages bookmarks" else it.label.lowercase()
        }
        return when (list.size) {
            1 -> list[0]
            else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
        }
    }

    // ---- Import ----

    /** Reads [uri] and brings in the chosen parts it holds. An HTML bookmarks file brings bookmarks. */
    fun import(context: Context, uri: Uri, parts: Set<Part>, db: Db): Result {
        require(parts.isNotEmpty()) { "Nothing chosen" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open the file")
        // A zip starts with "PK"; anything else is taken as an HTML bookmarks file.
        if (bytes.size < 2 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
            require(Part.BOOKMARKS in parts) { "That file only has bookmarks" }
            return Result(bookmarksMessage(BookmarksFile.importHtml(bytes.toString(Charsets.UTF_8), db)), false)
        }
        var json: JSONObject? = null
        var wallpaper: ByteArray? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when (e.name) {
                    DATA -> json = JSONObject(String(zip.readBytes()))
                    WALLPAPER -> wallpaper = zip.readBytes()
                }
            }
        }
        val j = json ?: error("Not a SonderWeb file")
        require(j.optInt("format") == FORMAT) { "Made by a newer SonderWeb: update first" }
        val inFile = j.optJSONArray("parts")?.let { a -> (0 until a.length()).mapNotNull { i -> Part.entries.firstOrNull { it.name == a.getString(i) } } }.orEmpty().toSet()
        val taking = parts intersect inFile
        require(taking.isNotEmpty()) { "That file has " + Part.entries.filter { it in inFile }.joinToString(", ") { it.label.lowercase() } + " only" }

        val sp = prefsFile(context)
        val e = sp.edit()
        Part.entries.filter { it != Part.BOOKMARKS && it in taking }.forEach { part ->
            // This part's settings are replaced: the old ones go, the file's come in.
            sp.all.keys.filter { partOf(it) == part }.forEach { e.remove(it) }
            val prefs = j.optJSONObject(part.name.lowercase()) ?: JSONObject()
            prefs.keys().forEach { k ->
                if (partOf(k) != part) return@forEach
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
        e.commit()
        if (Part.START in taking) {
            val file = wallpaperFile(context)
            val w = wallpaper
            if (w != null) {
                val tmp = File(context.filesDir, "start_wallpaper.tmp")
                tmp.writeBytes(w)
                tmp.renameTo(file)
            } else {
                file.delete()
            }
        }
        var pages = 0
        var bookmarksNote = ""
        if (Part.BOOKMARKS in taking) {
            // A file saved with no bookmarks in it brings none (not an error).
            val html = j.optString("bookmarks")
            if (BookmarksHtml.pageCount(BookmarksHtml.parse(html)) > 0) {
                val r = BookmarksFile.importHtml(html, db)
                pages = r.added
                if (r.skipped > 0) bookmarksNote = " (${r.skipped} were already here)"
            }
        }
        val restart = Part.SETTINGS in taking || Part.START in taking
        return Result("Imported " + names(taking, pages) + bookmarksNote, restart)
    }

    private fun bookmarksMessage(r: BookmarksFile.Result): String = when {
        r.added == 0 -> "Import done: those bookmarks are already here"
        r.skipped > 0 -> "Imported ${r.added} bookmarks (${r.skipped} were already here)"
        else -> "Imported ${r.added} bookmarks"
    }
}
