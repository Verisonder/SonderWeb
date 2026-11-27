package com.verisonder.sonderweb.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.verisonder.sonderweb.sync.FxSync

data class HistoryItem(val id: Long, val url: String, val title: String, val visited: Long)

/**
 * A bookmark, or a folder of them ([folder]). [parent] is the folder it's in (0: the top level).
 * [guid] is its id in Firefox Sync (SonderWeb Sync only); [fixed] marks Firefox's own folders
 * (Bookmarks Menu, Toolbar, Other Bookmarks), which can't be renamed or deleted.
 */
data class Bookmark(
    val id: Long,
    val url: String,
    val title: String,
    val added: Long,
    val parent: Long = 0,
    val folder: Boolean = false,
    val guid: String? = null,
    val position: Int = 0,
    val fixed: Boolean = false,
)
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
        const val CANCELLED = 3
    }
}

/**
 * History, bookmarks and downloads. Private tabs never write here. Each profile has its own
 * file ([name]); only the first profile's ("sonderweb.db") is synced and holds the downloads.
 */
class Db(context: Context, name: String = "sonderweb.db") : SQLiteOpenHelper(context, name, null, 2) {

    /** The first profile's: the one Firefox Sync (SonderWeb Sync) follows. */
    private val synced = name == "sonderweb.db"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL UNIQUE, " +
                "title TEXT NOT NULL DEFAULT '', visited INTEGER NOT NULL, synced INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE INDEX history_visited ON history(visited)")
        createBookmarks(db)
        db.execSQL(
            "CREATE TABLE downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "url TEXT NOT NULL, uri TEXT NOT NULL DEFAULT '', mime TEXT NOT NULL DEFAULT '', " +
                "size INTEGER NOT NULL DEFAULT 0, added INTEGER NOT NULL, state INTEGER NOT NULL)",
        )
    }

    private fun createBookmarks(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL DEFAULT '', " +
                "title TEXT NOT NULL DEFAULT '', added INTEGER NOT NULL, parent INTEGER NOT NULL DEFAULT 0, " +
                "folder INTEGER NOT NULL DEFAULT 0, guid TEXT, position INTEGER NOT NULL DEFAULT 0, " +
                "fixed INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE INDEX bookmarks_parent ON bookmarks(parent)")
        db.execSQL("CREATE INDEX bookmarks_url ON bookmarks(url)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Folders (bookmarks now sit in folders, and the same page can be in two of them),
            // and history remembers what Firefox Sync has already been given.
            db.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_v1")
            createBookmarks(db)
            db.execSQL("INSERT INTO bookmarks (url, title, added) SELECT url, title, added FROM bookmarks_v1")
            db.execSQL("DROP TABLE bookmarks_v1")
            db.execSQL("ALTER TABLE history ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ---- History ----

    fun visit(url: String, title: String?) {
        val cv = ContentValues().apply {
            put("url", url)
            put("visited", System.currentTimeMillis())
            put("synced", 0)
            if (!title.isNullOrBlank()) put("title", title)
        }
        val db = writableDatabase
        val n = db.update("history", cv, "url = ?", arrayOf(url))
        if (n == 0) db.insert("history", null, cv)
    }

    fun setTitle(url: String, title: String) {
        if (title.isBlank()) return
        val cv = ContentValues().apply {
            put("title", title)
            put("synced", 0)
        }
        writableDatabase.update("history", cv, "url = ?", arrayOf(url))
    }

    fun history(query: String = "", limit: Int = 500): List<HistoryItem> {
        val (where, args) = like(query)
        readableDatabase.query(
            "history", arrayOf("id", "url", "title", "visited"), where, args, null, null, "visited DESC", limit.toString(),
        ).use { c ->
            val out = ArrayList<HistoryItem>(c.count)
            while (c.moveToNext()) out += HistoryItem(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            return out
        }
    }

    fun deleteHistory(id: Long) {
        val url = readableDatabase.rawQuery("SELECT url FROM history WHERE id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.getString(0) else null }
        writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
        if (url != null && synced) FxSync.noteHistoryRemoved(url)
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
        if (synced) FxSync.noteHistoryCleared()
    }

    // History and Firefox Sync (SonderWeb Sync): 0 not given to it yet, 1 given, 2 it refused.

    /** Visits Firefox Sync hasn't been given yet, oldest first. */
    fun unsyncedHistory(limit: Int = 2000): List<HistoryItem> {
        readableDatabase.query(
            "history", arrayOf("id", "url", "title", "visited"), "synced = 0", null, null, null, "visited ASC", limit.toString(),
        ).use { c ->
            val out = ArrayList<HistoryItem>(c.count)
            while (c.moveToNext()) out += HistoryItem(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            return out
        }
    }

    fun markHistory(ids: Collection<Long>, state: Int) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("synced", state) }
            ids.forEach { db.update("history", cv, "id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** The visits Firefox Sync has (id, address): one it no longer has was deleted on another device. */
    fun syncedHistory(): List<Pair<Long, String>> =
        readableDatabase.rawQuery("SELECT id, url FROM history WHERE synced = 1", null).use { c ->
            val out = ArrayList<Pair<Long, String>>(c.count)
            while (c.moveToNext()) out += c.getLong(0) to c.getString(1)
            out
        }

    /** Deleted on another device (not sent back to Firefox Sync). */
    fun dropHistory(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete("history", "id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Visits from your other devices (Firefox Sync): address, title, when. Kept: the latest. */
    fun importVisits(visits: List<Triple<String, String?, Long>>) = addVisits(visits, synced = 1)

    /**
     * Visits added from elsewhere (the other SonderWeb app, or Firefox Sync when [synced] is 1):
     * address, title, when. A page already in history keeps its latest visit.
     */
    fun addVisits(visits: List<Triple<String, String?, Long>>, synced: Int) {
        if (visits.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((url, title, at) in visits) {
                val cv = ContentValues().apply {
                    put("url", url)
                    put("visited", at)
                    put("synced", synced)
                    if (!title.isNullOrBlank()) put("title", title)
                }
                val n = db.update("history", cv, "url = ? AND visited < ?", arrayOf(url, at.toString()))
                if (n == 0) db.insertWithOnConflict("history", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- Bookmarks ----

    private val cols = arrayOf("id", "url", "title", "added", "parent", "folder", "guid", "position", "fixed")

    private fun readBookmarks(where: String?, args: Array<String>?, order: String = "folder DESC, position ASC, added DESC"): List<Bookmark> =
        readableDatabase.query("bookmarks", cols, where, args, null, null, order).use { c ->
            val out = ArrayList<Bookmark>(c.count)
            while (c.moveToNext()) {
                out += Bookmark(
                    c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4),
                    c.getInt(5) == 1, c.getString(6), c.getInt(7), c.getInt(8) == 1,
                )
            }
            out
        }

    fun isBookmarked(url: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url = ? AND folder = 0", arrayOf(url)).use { it.moveToFirst() }

    /** Bookmarks a page (at the top level unless [parent] says which folder). Already saved: left as it is. */
    fun addBookmark(url: String, title: String, parent: Long = 0) {
        if (isBookmarked(url)) return
        val cv = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("added", System.currentTimeMillis())
            put("parent", parent)
        }
        writableDatabase.insert("bookmarks", null, cv)
        if (synced) FxSync.noteBookmarksChanged()
    }

    /** Un-bookmarks a page (wherever it's filed). */
    fun removeBookmark(url: String) {
        writableDatabase.delete("bookmarks", "url = ? AND folder = 0", arrayOf(url))
        if (synced) FxSync.noteBookmarksChanged()
    }

    /** Every saved page (no folders), for searching and suggestions. */
    fun bookmarks(query: String = ""): List<Bookmark> {
        val (where, args) = like(query)
        return readBookmarks(listOfNotNull("folder = 0", where).joinToString(" AND "), args, "added DESC")
    }

    /** What's in a folder (0: the top level): folders first, then in their order. */
    fun bookmarksIn(parent: Long): List<Bookmark> = readBookmarks("parent = ?", arrayOf(parent.toString()))

    fun bookmark(id: Long): Bookmark? = readBookmarks("id = ?", arrayOf(id.toString())).firstOrNull()

    /** Every folder, for "Move to" (each with the names of the folders it's in). */
    fun folders(): List<Pair<Bookmark, String>> {
        val all = readBookmarks("folder = 1", null, "title COLLATE NOCASE ASC")
        val byId = all.associateBy { it.id }
        fun path(b: Bookmark): String {
            val names = ArrayList<String>()
            var cur: Bookmark? = b
            var guard = 0
            while (cur != null && guard++ < 20) {
                names += cur.title.ifBlank { "Folder" }
                cur = byId[cur.parent]
            }
            return names.asReversed().joinToString(" › ")
        }
        return all.map { it to path(it) }.sortedBy { it.second.lowercase() }
    }

    fun addFolder(title: String, parent: Long): Long {
        val cv = ContentValues().apply {
            put("title", title)
            put("added", System.currentTimeMillis())
            put("parent", parent)
            put("folder", 1)
        }
        val id = writableDatabase.insert("bookmarks", null, cv)
        if (synced) FxSync.noteBookmarksChanged()
        return id
    }

    /**
     * Adds one entry from a bookmarks file at the end of [parent]: a folder when [url] is null
     * (one with the same name already there is reused), else a page (skipped, -1, if that address
     * is already bookmarked). Returns the entry's id. Sync is told once, by [importDone].
     */
    fun importEntry(title: String, url: String?, parent: Long, added: Long): Long {
        val db = writableDatabase
        if (url == null) {
            bookmarksIn(parent).firstOrNull { it.folder && it.title == title }?.let { return it.id }
        } else if (isBookmarked(url)) {
            return -1
        }
        val next = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM bookmarks WHERE parent = ?", arrayOf(parent.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val cv = ContentValues().apply {
            put("title", title)
            put("added", added)
            put("parent", parent)
            put("position", next)
            if (url == null) put("folder", 1) else put("url", url)
        }
        return db.insert("bookmarks", null, cv)
    }

    /** After an import: the Firefox Sync app sends the new bookmarks. */
    fun importDone() {
        if (synced) FxSync.noteBookmarksChanged()
    }

    /** Renames a bookmark or folder (and changes a bookmark's address, if [url] is given). */
    fun editBookmark(id: Long, title: String, url: String? = null) {
        val cv = ContentValues().apply {
            put("title", title)
            if (url != null) put("url", url)
        }
        writableDatabase.update("bookmarks", cv, "id = ? AND fixed = 0", arrayOf(id.toString()))
        if (synced) FxSync.noteBookmarksChanged()
    }

    /** Moves a bookmark or folder into another folder (never into itself or its own folders). */
    fun moveBookmark(id: Long, parent: Long) {
        var cur = parent
        var guard = 0
        while (cur != 0L && guard++ < 50) {
            if (cur == id) return
            cur = bookmark(cur)?.parent ?: 0L
        }
        val cv = ContentValues().apply {
            put("parent", parent)
            put("position", 0)
        }
        writableDatabase.update("bookmarks", cv, "id = ? AND fixed = 0", arrayOf(id.toString()))
        if (synced) FxSync.noteBookmarksChanged()
    }

    /** Deletes a bookmark, or a folder and everything in it. */
    fun deleteBookmark(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            deleteTree(db, id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (synced) FxSync.noteBookmarksChanged()
    }

    private fun deleteTree(db: SQLiteDatabase, id: Long) {
        val kids = db.rawQuery("SELECT id FROM bookmarks WHERE parent = ?", arrayOf(id.toString())).use { c ->
            val out = ArrayList<Long>()
            while (c.moveToNext()) out += c.getLong(0)
            out
        }
        kids.forEach { deleteTree(db, it) }
        db.delete("bookmarks", "id = ? AND fixed = 0", arrayOf(id.toString()))
    }

    // Bookmarks and Firefox Sync (SonderWeb Sync): changes that came from it, not sent back.

    fun allBookmarks(): List<Bookmark> = readBookmarks(null, null)

    fun syncInsert(b: Bookmark): Long {
        val cv = ContentValues().apply {
            put("url", b.url)
            put("title", b.title)
            put("added", b.added)
            put("parent", b.parent)
            put("folder", if (b.folder) 1 else 0)
            put("guid", b.guid)
            put("position", b.position)
            put("fixed", if (b.fixed) 1 else 0)
        }
        return writableDatabase.insert("bookmarks", null, cv)
    }

    fun syncUpdate(id: Long, url: String, title: String, parent: Long, position: Int) {
        val cv = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("parent", parent)
            put("position", position)
        }
        writableDatabase.update("bookmarks", cv, "id = ?", arrayOf(id.toString()))
    }

    fun syncSetGuid(id: Long, guid: String) {
        writableDatabase.update("bookmarks", ContentValues().apply { put("guid", guid) }, "id = ?", arrayOf(id.toString()))
    }

    fun syncDelete(id: Long) {
        writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
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

    fun finishDownload(id: Long, uri: String?, size: Long, state: Int) {
        val cv = ContentValues().apply {
            put("uri", uri ?: "")
            put("size", size)
            put("state", state)
        }
        writableDatabase.update("downloads", cv, "id = ?", arrayOf(id.toString()))
    }

    /** Downloads cut off when the app was closed: they show as failed (and can be retried). */
    fun interruptRunning() {
        val cv = ContentValues().apply { put("state", DownloadItem.FAILED) }
        writableDatabase.update("downloads", cv, "state = ?", arrayOf(DownloadItem.RUNNING.toString()))
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
