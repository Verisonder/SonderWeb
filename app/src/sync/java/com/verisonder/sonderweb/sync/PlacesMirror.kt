package com.verisonder.sonderweb.sync

import android.content.Context
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.data.Bookmark
import mozilla.appservices.places.BookmarkRoot
import mozilla.appservices.places.PlacesApi
import mozilla.appservices.places.PlacesWriterConnection
import mozilla.appservices.places.uniffi.BookmarkItem
import mozilla.appservices.places.uniffi.VisitObservation
import mozilla.appservices.places.uniffi.VisitType
import org.json.JSONObject
import java.io.File

/**
 * Bookmarks and history for Firefox Sync. SonderWeb keeps its own lists (the [Db] the screens
 * read); Firefox Sync works on Mozilla's own database ("places"). Before and after every sync
 * the two are compared and made the same, both ways.
 *
 * Only ever called on [FxSync]'s background thread.
 */
internal object PlacesMirror {
    private var api: PlacesApi? = null
    private lateinit var dir: File

    /** Bookmarks as they were at the last comparison, by id: tells which side changed what. */
    private val lastFile get() = File(dir, "places_bookmarks.json")

    private val MOBILE = BookmarkRoot.Mobile.id

    /** Firefox's other folders, shown in SonderWeb as fixed folders at the top level. */
    private val FIXED = linkedMapOf(
        BookmarkRoot.Toolbar.id to "Bookmarks Toolbar",
        BookmarkRoot.Menu.id to "Bookmarks Menu",
        BookmarkRoot.Unfiled.id to "Other Bookmarks",
    )

    /** Opens places (once) and lets the sync manager see it. */
    fun open(context: Context) {
        if (api != null) return
        dir = context.filesDir
        val path = context.getDatabasePath("places.sqlite")
        path.parentFile?.mkdirs()
        api = PlacesApi(path.path).also { it.registerWithSyncManager() }
    }

    private fun writer(): PlacesWriterConnection? = api?.getWriter()

    // ---- History ----

    fun historyRemoved(url: String) {
        writer()?.deleteVisitsFor(url)
    }

    fun historyCleared() {
        writer()?.deleteVisitsBetween(0L, System.currentTimeMillis() + 60_000L)
    }

    /** Hands Firefox Sync the visits it doesn't have yet. */
    fun pushHistory() {
        val w = writer() ?: return
        val db = Browser.mainDb
        while (true) {
            val rows = db.unsyncedHistory()
            if (rows.isEmpty()) return
            val ok = ArrayList<Long>()
            val refused = ArrayList<Long>()
            for (h in rows) {
                val done = runCatching {
                    w.noteObservation(
                        VisitObservation(
                            url = h.url,
                            title = h.title.takeIf { it.isNotBlank() },
                            visitType = VisitType.LINK,
                            at = h.visited,
                        ),
                    )
                }.isSuccess
                if (done) ok += h.id else refused += h.id
            }
            db.markHistory(ok, 1)
            db.markHistory(refused, 2)
        }
    }

    /** After a sync: visits from your other devices come in; ones deleted there go here too. */
    fun pullHistory(context: Context) {
        val reader = api?.openReader() ?: return
        val prefs = context.getSharedPreferences("fxsync", Context.MODE_PRIVATE)
        val db = Browser.mainDb
        try {
            val since = prefs.getLong("history_imported", 0L)
            // A week of overlap: synced visits can arrive later than they happened.
            val from = if (since == 0L) 0L else since - 7L * 24 * 3600 * 1000
            val now = System.currentTimeMillis()
            val visits = reader.getVisitInfos(
                from,
                Long.MAX_VALUE,
                listOf(VisitType.EMBED, VisitType.FRAMED_LINK, VisitType.DOWNLOAD),
            )
            val remote = visits.filter { it.isRemote && !it.isHidden && web(it.url) }
            db.importVisits(remote.map { Triple(it.url, it.title, it.timestamp) })
            prefs.edit().putLong("history_imported", now).apply()

            val gone = ArrayList<Long>()
            db.syncedHistory().chunked(200).forEach { chunk ->
                val seen = reader.getVisited(chunk.map { it.second })
                chunk.forEachIndexed { i, (id, _) -> if (seen.getOrNull(i) == false) gone += id }
            }
            db.dropHistory(gone)
        } finally {
            runCatching { reader.close() }
        }
    }

    // ---- Bookmarks ----

    /** A bookmark or folder in places. [parent]: its folder (Mobile bookmarks = SonderWeb's top level). */
    private class Node(
        val guid: String,
        val parent: String,
        val folder: Boolean,
        val url: String,
        val title: String,
        val added: Long,
        val position: Int,
        val fixed: Boolean,
    )

    private class Snap(val title: String, val url: String, val parent: String)

    /** Every bookmark and folder SonderWeb shows, in tree order (folders before what's in them). */
    private fun tree(w: PlacesWriterConnection): LinkedHashMap<String, Node> {
        val out = LinkedHashMap<String, Node>()
        fun walk(item: BookmarkItem, parent: String) {
            when (item) {
                is BookmarkItem.Bookmark -> {
                    val b = item.b
                    if (web(b.url)) {
                        out[b.guid] = Node(b.guid, parent, false, b.url, b.title ?: "", b.dateAdded, b.position.toInt(), false)
                    }
                }
                is BookmarkItem.Folder -> {
                    val f = item.f
                    out[f.guid] = Node(f.guid, parent, true, "", f.title ?: "", f.dateAdded, f.position.toInt(), false)
                    f.childNodes?.forEach { walk(it, f.guid) }
                }
                is BookmarkItem.Separator -> Unit
            }
        }
        (w.getBookmarksTree(MOBILE, recursive = true) as? BookmarkItem.Folder)?.f?.childNodes?.forEach { walk(it, MOBILE) }
        for ((id, name) in FIXED) {
            val root = w.getBookmarksTree(id, recursive = true) as? BookmarkItem.Folder ?: continue
            val kids = root.f.childNodes.orEmpty()
            if (kids.isEmpty()) continue
            out[id] = Node(id, MOBILE, true, "", name, root.f.dateAdded, FIXED.keys.indexOf(id), true)
            kids.forEach { walk(it, id) }
        }
        return out
    }

    private fun readLast(): Map<String, Snap> = runCatching {
        val o = JSONObject(lastFile.readText())
        o.keys().asSequence().associateWith { k ->
            val v = o.getJSONObject(k)
            Snap(v.optString("t"), v.optString("u"), v.optString("p"))
        }
    }.getOrDefault(emptyMap())

    private fun writeLast(nodes: Collection<Node>) {
        val o = JSONObject()
        nodes.forEach { n -> o.put(n.guid, JSONObject().put("t", n.title).put("u", n.url).put("p", n.parent)) }
        runCatching { lastFile.writeText(o.toString()) }
    }

    /**
     * Makes SonderWeb's bookmarks and places the same. What changed since the last comparison
     * ([lastFile]) is carried to the other side: added, renamed, moved, deleted. When both sides
     * changed the same bookmark, your other devices' version wins.
     */
    fun reconcileBookmarks(): Boolean {
        val w = writer() ?: return false
        val db = Browser.mainDb
        val last = readLast()
        var changedHere = false

        // 1. New here: into places (folders before what's in them).
        run {
            val rows = db.allBookmarks()
            val byId = rows.associateBy { it.id }
            fun depth(b: Bookmark): Int {
                var d = 0
                var cur = b
                while (cur.parent != 0L && d < 50) {
                    cur = byId[cur.parent] ?: break
                    d++
                }
                return d
            }
            val guids = HashMap<Long, String>()
            rows.forEach { r -> r.guid?.let { guids[r.id] = it } }
            for (r in rows.filter { it.guid == null }.sortedBy { depth(it) }) {
                val parentGuid = if (r.parent == 0L) MOBILE else guids[r.parent] ?: continue
                val guid = runCatching {
                    if (r.folder) {
                        w.createFolder(parentGuid, r.title, 0u)
                    } else {
                        w.createBookmarkItem(parentGuid, r.url, r.title, 0u)
                    }
                }.getOrNull() ?: continue
                guids[r.id] = guid
                db.syncSetGuid(r.id, guid)
            }
        }

        var places = tree(w)
        var rows = db.allBookmarks()
        val byGuid = HashMap<String, Bookmark>()
        rows.forEach { r -> r.guid?.let { byGuid[it] = r } }

        // 2. In places only: deleted here (gone from places too), or new from your other devices.
        val ids = HashMap<String, Long>().apply { put(MOBILE, 0L) }
        byGuid.forEach { (g, r) -> ids[g] = r.id }
        for (n in places.values) {
            if (n.guid in byGuid) continue
            if (n.guid in last && !n.fixed) {
                runCatching { w.deleteBookmarkNode(n.guid) }
                continue
            }
            val parentId = ids[n.parent] ?: continue
            val id = db.syncInsert(
                Bookmark(0, n.url, n.title, n.added, parentId, n.folder, n.guid, n.position, n.fixed),
            )
            ids[n.guid] = id
            changedHere = true
        }

        places = tree(w)
        rows = db.allBookmarks()
        val rowsById = rows.associateBy { it.id }

        // 3. On both sides, or here only: changes either way, and deletions from your other devices.
        for (r in rows) {
            val guid = r.guid ?: continue
            val n = places[guid]
            if (n == null) {
                db.syncDelete(r.id) // deleted on another device
                changedHere = true
                continue
            }
            val parentGuid = if (r.parent == 0L) MOBILE else rowsById[r.parent]?.guid ?: MOBILE
            val snap = last[guid]
            val localChanged = snap == null || r.title != snap.title || r.url != snap.url || parentGuid != snap.parent
            val remoteChanged = snap == null || n.title != snap.title || n.url != snap.url || n.parent != snap.parent
            if (localChanged && !remoteChanged && !r.fixed) {
                runCatching {
                    w.updateBookmark(
                        guid,
                        if (parentGuid != n.parent) parentGuid else null,
                        null,
                        if (r.title != n.title) r.title else null,
                        if (!r.folder && r.url != n.url) r.url else null,
                    )
                }
            } else {
                val parentId = ids[n.parent] ?: rows.firstOrNull { it.guid == n.parent }?.id ?: 0L
                if (r.title != n.title || r.url != n.url || r.parent != parentId || r.position != n.position) {
                    db.syncUpdate(r.id, n.url, n.title, parentId, n.position)
                    changedHere = true
                }
            }
        }

        // Anything left in a folder that no longer exists goes to the top level.
        val all = db.allBookmarks()
        val present = all.map { it.id }.toHashSet()
        all.filter { it.parent != 0L && it.parent !in present }.forEach {
            db.syncUpdate(it.id, it.url, it.title, 0L, it.position)
            changedHere = true
        }

        writeLast(tree(w).values)
        return changedHere
    }

    private fun web(url: String) = url.startsWith("http://") || url.startsWith("https://")
}
