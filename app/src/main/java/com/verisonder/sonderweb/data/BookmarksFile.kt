package com.verisonder.sonderweb.data

import android.content.Context
import android.net.Uri
import com.verisonder.sonderweb.core.BookmarksHtml

/**
 * Settings › About › Import / Export bookmarks: the HTML bookmarks file that Chrome, Firefox and
 * the others read and write. Works on the bookmarks of the profile in use. Call off the main thread.
 */
object BookmarksFile {
    /** What an import did: pages added, and pages skipped because they were already bookmarked. */
    data class Result(val added: Int, val skipped: Int)

    /** Reads a bookmarks file and adds its folders and pages at the top level, in their order. */
    fun import(ctx: Context, uri: Uri, db: Db): Result {
        val html = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Couldn't open the file")
        val entries = BookmarksHtml.parse(html)
        require(BookmarksHtml.pageCount(entries) > 0) { "No bookmarks in that file" }
        var added = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        fun add(list: List<BookmarksHtml.Entry>, parent: Long, depth: Int) {
            for (e in list) {
                val time = if (e.added > 0) e.added else now
                if (e.isFolder) {
                    // Very deep nesting is flattened into the last folder rather than lost.
                    val id = if (depth < 32) db.importEntry(e.title, null, parent, time) else parent
                    add(e.children, id, depth + 1)
                } else if (db.importEntry(e.title, e.url, parent, time) >= 0) {
                    added++
                } else {
                    skipped++
                }
            }
        }
        add(entries, 0L, 0)
        db.importDone()
        return Result(added, skipped)
    }

    /** Writes every bookmark, in its folders, to [uri]. Returns how many pages went in. */
    fun export(ctx: Context, uri: Uri, db: Db): Int {
        fun tree(parent: Long, depth: Int): List<BookmarksHtml.Entry> =
            db.bookmarksIn(parent).mapNotNull { b ->
                when {
                    b.folder -> BookmarksHtml.Entry(b.title, null, b.added, if (depth < 32) tree(b.id, depth + 1) else emptyList())
                    b.url.isNotEmpty() -> BookmarksHtml.Entry(b.title, b.url, b.added)
                    else -> null
                }
            }
        val entries = tree(0L, 0)
        val html = BookmarksHtml.write(entries)
        val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't write the file")
        out.use { it.write(html.toByteArray(Charsets.UTF_8)) }
        return BookmarksHtml.pageCount(entries)
    }
}
