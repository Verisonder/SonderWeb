package com.verisonder.sonderweb.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryItem(val id: Long, val url: String, val title: String, val visited: Long)
data class Bookmark(val id: Long, val url: String, val title: String, val added: Long)
data class DownloadItem(
    val id: Long,
    val name: String,
    val url: String,
    val uri: String,
    val mime: String,
    val size: Long,
    val added: Long,
    val state: Int,
) {
    companion object {
        const val RUNNING = 0
        const val DONE = 1
        const val FAILED = 2
    }
}

/** History, bookmarks and downloads. Private tabs never write here. */
class Db(context: Context) : SQLiteOpenHelper(context, "sonderweb.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, " +
                "title TEXT NOT NULL DEFAULT '', visited INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX history_visited ON history(visited)")
        db.execSQL(
            "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, " +
                "title TEXT NOT NULL DEFAULT '', added INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "url TEXT NOT NULL, uri TEXT NOT NULL DEFAULT '', mime TEXT NOT NULL DEFAULT '', " +
                "size INTEGER NOT NULL DEFAULT 0, added INTEGER NOT NULL, state INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---- History ----

    fun visit(url: String, title: String?) {
        val cv = ContentValues().apply {
            put("url", url)
            put("visited", System.currentTimeMillis())
            if (!title.isNullOrBlank()) put("title", title)
        }
        val db = writableDatabase
        val n = db.update("history", cv, "url = ?", arrayOf(url))
        if (n == 0) db.insert("history", null, cv)
    }

    fun setTitle(url: String, title: String) {
        if (title.isBlank()) return
        writableDatabase.update("history", ContentValues().apply { put("title", title) }, "url = ?", arrayOf(url))
    }

    fun history(query: String = "", limit: Int = 500): List<HistoryItem> {
        val (where, args) = like(query)
        readableDatabase.query("history", null, where, args, null, null, "visited DESC", limit.toString()).use { c ->
            val out = ArrayList<HistoryItem>(c.count)
            while (c.moveToNext()) out += HistoryItem(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            return out
        }
    }

    fun deleteHistory(id: Long) {
        writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    // ---- Bookmarks ----

    fun isBookmarked(url: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url = ?", arrayOf(url)).use { it.moveToFirst() }

    fun addBookmark(url: String, title: String) {
        val cv = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("added", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("bookmarks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeBookmark(url: String) {
        writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
    }

    fun bookmarks(query: String = ""): List<Bookmark> {
        val (where, args) = like(query)
        readableDatabase.query("bookmarks", null, where, args, null, null, "added DESC").use { c ->
            val out = ArrayList<Bookmark>(c.count)
            while (c.moveToNext()) out += Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            return out
        }
    }

    /** Address-bar suggestions: bookmarks first, then history. */
    fun suggest(query: String, limit: Int = 6): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()
        val seen = LinkedHashMap<String, String>()
        bookmarks(query).take(limit).forEach { seen[it.url] = it.title }
        history(query, limit * 2).forEach { if (seen.size < limit) seen.putIfAbsent(it.url, it.title) }
        return seen.entries.take(limit).map { it.key to it.value }
    }

    // ---- Downloads ----

    fun addDownload(name: String, url: String, mime: String): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("url", url)
            put("mime", mime)
            put("added", System.currentTimeMillis())
            put("state", DownloadItem.RUNNING)
        }
        return writableDatabase.insert("downloads", null, cv)
    }

    fun finishDownload(id: Long, uri: String?, size: Long, ok: Boolean) {
        val cv = ContentValues().apply {
            put("uri", uri ?: "")
            put("size", size)
            put("state", if (ok) DownloadItem.DONE else DownloadItem.FAILED)
        }
        writableDatabase.update("downloads", cv, "id = ?", arrayOf(id.toString()))
    }

    fun downloads(): List<DownloadItem> {
        readableDatabase.query("downloads", null, null, null, null, null, "added DESC").use { c ->
            val out = ArrayList<DownloadItem>(c.count)
            while (c.moveToNext()) {
                out += DownloadItem(
                    c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getLong(5), c.getLong(6), c.getInt(7),
                )
            }
            return out
        }
    }

    fun deleteDownload(id: Long) {
        writableDatabase.delete("downloads", "id = ?", arrayOf(id.toString()))
    }

    fun clearDownloads() {
        writableDatabase.delete("downloads", null, null)
    }

    private fun like(query: String): Pair<String?, Array<String>?> {
        val q = query.trim()
        if (q.isEmpty()) return null to null
        val p = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return "(url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\')" to arrayOf(p, p)
    }
}
