package com.verisonder.sonderweb.sync

import android.content.Context
import com.verisonder.sonderweb.browser.Browser
import mozilla.appservices.places.BookmarkRoot
import mozilla.appservices.places.PlacesApi
import mozilla.appservices.places.uniffi.BookmarkItem
import mozilla.appservices.places.uniffi.VisitObservation
import mozilla.appservices.places.uniffi.VisitType
import java.io.File

/**
 * Bookmarks and history for Firefox Sync. SonderWeb keeps its own lists (the [Db] the screens
 * read); Firefox Sync works on Mozilla's own database ("places"). This keeps the two the same:
 * every change made in SonderWeb is copied into places right away, and after each sync whatever
 * came from your other devices is copied back.
 *
 * Only ever called on [FxSync]'s background thread.
 */
internal object PlacesMirror {
    private var api: PlacesApi? = null
    private lateinit var dir: File

    /** Bookmarks: the addresses both sides had at the last comparison (to tell adds from deletes). */
    private val lastFile get() = File(dir, "places_bookmarks.txt")
    private val prefs get() = FxSync.appContext().getSharedPreferences("fxsync", Context.MODE_PRIVATE)

    /** Opens places (once) and lets the sync manager see it. */
    fun open(context: Context) {
        if (api != null) return
        dir = context.filesDir
        val path = context.getDatabasePath("places.sqlite")
        path.parentFile?.mkdirs()
        api = PlacesApi(path.path).also { it.registerWithSyncManager() }
        seedHistory()
    }

    private fun writer() = api?.getWriter()

    // ---- Changes made in SonderWeb ----

    fun visited(url: String, title: String?) {
        writer()?.noteObservation(
            VisitObservation(
                url = url,
                title = title?.takeIf { it.isNotBlank() },
                visitType = VisitType.LINK,
                at = System.currentTimeMillis(),
            ),
        )
    }

    fun titled(url: String, title: String) {
        writer()?.noteObservation(VisitObservation(url = url, title = title, visitType = null))
    }

    fun historyRemoved(url: String) {
        writer()?.deleteVisitsFor(url)
    }

    fun historyCleared() {
        writer()?.deleteVisitsBetween(0L, System.currentTimeMillis() + 60_000L)
    }

    fun bookmarked(url: String, title: String) {
        val w = writer() ?: return
        if (w.getBookmarksWithURL(url).isNotEmpty()) return
        w.createBookmarkItem(BookmarkRoot.Mobile.id, url, title, null)
    }

    fun unbookmarked(url: String) {
        val w = writer() ?: return
        val key = norm(url)
        // Every copy, wherever it's filed (Firefox may have it in a folder too).
        allBookmarks().filter { norm(it.url) == key }.forEach { runCatching { w.deleteBookmarkNode(it.guid) } }
    }

    // ---- Bookmarks, both ways ----

    private class Mark(val guid: String, val url: String, val title: String, val added: Long)

    /** Every bookmark in places, from every folder (SonderWeb's list has no folders). */
    private fun allBookmarks(): List<Mark> {
        val root = writer()?.getBookmarksTree(BookmarkRoot.Root.id, recursive = true) ?: return emptyList()
        val out = ArrayList<Mark>()
        fun walk(item: BookmarkItem) {
            when (item) {
                is BookmarkItem.Bookmark -> {
                    val url = item.b.url
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        out += Mark(item.b.guid, url, item.b.title ?: "", item.b.dateAdded)
                    }
                }
                is BookmarkItem.Folder -> item.f.childNodes?.forEach { walk(it) }
                is BookmarkItem.Separator -> Unit
            }
        }
        walk(root)
        return out
    }

    /**
     * Compares SonderWeb's bookmarks with places. Something on one side only was either added
     * there (copied over) or deleted from the other side since the last comparison (deleted here
     * too): the addresses both had last time tell which.
     */
    fun reconcileBookmarks() {
        val w = writer() ?: return
        val db = Browser.db
        val last = runCatching { lastFile.readLines().filter { it.isNotBlank() }.toSet() }.getOrDefault(emptySet())
        val places = allBookmarks()
        val placesKeys = places.map { norm(it.url) }.toSet()
        val local = db.bookmarks()
        val localKeys = local.map { norm(it.url) }.toSet()
        val now = HashSet<String>()

        for (b in local) {
            val key = norm(b.url)
            if (key in placesKeys) {
                now += key
            } else if (key in last) {
                db.removeBookmark(b.url, mirror = false) // deleted on another device
            } else {
                runCatching { w.createBookmarkItem(BookmarkRoot.Mobile.id, b.url, b.title, null) }
                now += key
            }
        }
        val copied = HashSet<String>()
        for (p in places) {
            val key = norm(p.url)
            if (key in localKeys) continue
            if (key in last) {
                runCatching { w.deleteBookmarkNode(p.guid) } // deleted here while it couldn't be copied
            } else if (copied.add(key)) {
                db.addBookmark(p.url, p.title, added = p.added, mirror = false)
                now += key
            }
        }
        runCatching { lastFile.writeText(now.joinToString("\n")) }
    }

    // ---- History ----

    /** The first time: the history SonderWeb already has goes into places (to sync it). */
    private fun seedHistory() {
        if (prefs.getBoolean("history_seeded", false)) return
        val w = writer() ?: return
        for (h in Browser.db.history(limit = 5000).asReversed()) {
            runCatching {
                w.noteObservation(
                    VisitObservation(
                        url = h.url,
                        title = h.title.takeIf { it.isNotBlank() },
                        visitType = VisitType.LINK,
                        at = h.visited,
                    ),
                )
            }
        }
        prefs.edit().putBoolean("history_seeded", true).putLong("history_imported", 0L).apply()
    }

    /** Copies the visits that came from your other devices into SonderWeb's history. */
    fun importHistory() {
        val reader = api?.openReader() ?: return
        val since = prefs.getLong("history_imported", 0L)
        // A week of overlap: synced visits can arrive later than they happened.
        val from = if (since == 0L) 0L else since - 7L * 24 * 3600 * 1000
        val now = System.currentTimeMillis()
        val visits = try {
            reader.getVisitInfos(from, Long.MAX_VALUE, listOf(VisitType.EMBED, VisitType.FRAMED_LINK, VisitType.DOWNLOAD))
        } finally {
            runCatching { reader.close() }
        }
        val remote = visits.filter { it.isRemote && !it.isHidden && (it.url.startsWith("http://") || it.url.startsWith("https://")) }
        Browser.db.importVisits(remote.map { Triple(it.url, it.title, it.timestamp) })
        prefs.edit().putLong("history_imported", now).apply()
    }

    /** The same address, written a little differently (places adds a "/" after a bare host). */
    private fun norm(url: String): String = url.substringBefore('#').trimEnd('/')
}
