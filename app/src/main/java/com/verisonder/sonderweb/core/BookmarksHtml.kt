package com.verisonder.sonderweb.core

/**
 * The bookmarks file every browser reads and writes (the old "Netscape" HTML format): Chrome,
 * Firefox, Edge, Brave and Safari all export it. Folders are `<DT><H3>name</H3>` followed by a
 * `<DL>` with their contents; pages are `<DT><A HREF="…" ADD_DATE="…">title</A>`.
 */
object BookmarksHtml {
    /** A folder ([url] null, with [children]) or a page. [added] in milliseconds, 0 if unknown. */
    data class Entry(
        val title: String,
        val url: String?,
        val added: Long = 0L,
        val children: List<Entry> = emptyList(),
    ) {
        val isFolder: Boolean get() = url == null
    }

    private val tag = Regex("<\\s*(/?)\\s*(dl|h3|a)\\b([^>]*)>", RegexOption.IGNORE_CASE)

    /** Reads a bookmarks file into its top-level entries (empty if it has none). */
    fun parse(html: String): List<Entry> {
        val root = Node("", null, 0L)
        val stack = ArrayList<Node>()
        var pending: Node? = null
        var pos = 0
        while (true) {
            val m = tag.find(html, pos) ?: break
            pos = m.range.last + 1
            val closing = m.groupValues[1].isNotEmpty()
            val name = m.groupValues[2].lowercase()
            val attrs = m.groupValues[3]
            when (name) {
                "dl" -> if (closing) {
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    pending = null
                } else {
                    // A list opens the folder named just before it (the file's first list is the top).
                    stack += pending ?: stack.lastOrNull() ?: root
                    pending = null
                }
                "h3" -> if (!closing) {
                    val end = html.indexOf("</h3", pos, ignoreCase = true).let { if (it < 0) html.length else it }
                    val folder = Node(clean(html.substring(pos, end)).ifEmpty { "Folder" }, null, date(attrs))
                    (stack.lastOrNull() ?: root).children += folder
                    pending = folder
                    pos = end
                }
                "a" -> if (!closing) {
                    val end = html.indexOf("</a", pos, ignoreCase = true).let { if (it < 0) html.length else it }
                    val href = attr(attrs, "href")?.let { decode(it).trim() }
                    if (href != null && keep(href)) {
                        val title = clean(html.substring(pos, end)).ifEmpty { href }
                        (stack.lastOrNull() ?: root).children += Node(title, href, date(attrs))
                    }
                    pos = end
                }
            }
        }
        return root.toEntry().children
    }

    /** Writes [entries] as a bookmarks file. */
    fun write(entries: List<Entry>, title: String = "Bookmarks"): String {
        val out = StringBuilder()
        out.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        out.append("<!-- This is an automatically generated file.\n     It will be read and overwritten.\n     DO NOT EDIT! -->\n")
        out.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        out.append("<TITLE>").append(escape(title)).append("</TITLE>\n")
        out.append("<H1>").append(escape(title)).append("</H1>\n")
        out.append("<DL><p>\n")
        writeList(out, entries, 1)
        out.append("</DL><p>\n")
        return out.toString()
    }

    /** How many pages (not folders) [entries] hold, all the way down. */
    fun pageCount(entries: List<Entry>): Int = entries.sumOf { if (it.isFolder) pageCount(it.children) else 1 }

    private fun writeList(out: StringBuilder, entries: List<Entry>, depth: Int) {
        val pad = "    ".repeat(depth)
        for (e in entries) {
            val date = if (e.added > 0) " ADD_DATE=\"${e.added / 1000}\"" else ""
            if (e.isFolder) {
                out.append(pad).append("<DT><H3").append(date).append('>').append(escape(e.title)).append("</H3>\n")
                out.append(pad).append("<DL><p>\n")
                writeList(out, e.children, depth + 1)
                out.append(pad).append("</DL><p>\n")
            } else {
                out.append(pad).append("<DT><A HREF=\"").append(escape(e.url!!)).append('"').append(date).append('>')
                    .append(escape(e.title)).append("</A>\n")
            }
        }
    }

    private class Node(val title: String, val url: String?, val added: Long) {
        val children = ArrayList<Node>()
        fun toEntry(): Entry = Entry(title, url, added, children.map { it.toEntry() })
    }

    /** Pages worth keeping: not bookmarklets (javascript:) or Firefox's saved searches (place:). */
    private fun keep(url: String): Boolean {
        if (url.isEmpty()) return false
        val l = url.lowercase()
        return !l.startsWith("javascript:") && !l.startsWith("place:")
    }

    private fun attr(attrs: String, name: String): String? {
        val m = Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(attrs) ?: return null
        return m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value
    }

    /** ADD_DATE is in seconds (some tools write microseconds). */
    private fun date(attrs: String): Long {
        val n = attr(attrs, "add_date")?.trim()?.toLongOrNull() ?: return 0L
        return when {
            n <= 0 -> 0L
            n > 100_000_000_000_000L -> n / 1000 // microseconds
            n > 100_000_000_000L -> n // already milliseconds
            else -> n * 1000
        }
    }

    /** Text between tags: inner tags dropped, entities decoded, spaces tidied. */
    private fun clean(s: String): String = decode(s.replace(Regex("<[^>]*>"), "")).replace(Regex("\\s+"), " ").trim()

    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    fun decode(s: String): String = entity.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { cp(it) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { cp(it) } ?: m.value
            else -> when (e.lowercase()) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> m.value
            }
        }
    }

    private fun cp(code: Int): String =
        if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else ""

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
