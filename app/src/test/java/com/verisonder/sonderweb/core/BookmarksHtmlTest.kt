package com.verisonder.sonderweb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksHtmlTest {
    private val sample = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmark import test</TITLE>
        <H1>Bookmark import test</H1>
        <DL><p>
          <DT><H3>Projects</H3>
          <DL><p>
            <DT><H3>Reading</H3>
            <DL><p>
              <DT><A HREF="https://example.com/notes?x=1&amp;y=2">Café &amp; notes</A>
              <DT><A HREF="https://example.org/notes">Café &amp; notes</A>
            </DL><p>
          </DL><p>
          <DT><A HREF="https://example.net/start">Start here</A>
        </DL><p>
    """.trimIndent()

    /** Every page as "folder/folder|title|url", so two files can be compared whatever their layout. */
    private fun flat(entries: List<BookmarksHtml.Entry>, path: String = ""): List<String> =
        entries.flatMap { e ->
            if (e.isFolder) flat(e.children, if (path.isEmpty()) e.title else "$path/${e.title}")
            else listOf("$path|${e.title}|${e.url}")
        }

    @Test fun readsFoldersAndPages() {
        val pages = flat(BookmarksHtml.parse(sample))
        assertEquals(
            listOf(
                "Projects/Reading|Café & notes|https://example.com/notes?x=1&y=2",
                "Projects/Reading|Café & notes|https://example.org/notes",
                "|Start here|https://example.net/start",
            ),
            pages,
        )
    }

    @Test fun roundTrip() {
        val first = BookmarksHtml.parse(sample)
        val again = BookmarksHtml.parse(BookmarksHtml.write(first))
        assertEquals(flat(first), flat(again))
        assertEquals(3, BookmarksHtml.pageCount(again))
    }

    @Test fun chromeStyle() {
        val html = """
            <DL><p>
                <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
                <DL><p>
                    <DT><A HREF="https://a.example/" ADD_DATE="1700000001" ICON="data:image/png;base64,AAA">A &lt;one&gt;</A>
                    <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
                </DL><p>
                <DT><a href='https://b.example/'>B</a>
            </DL><p>
        """.trimIndent()
        val entries = BookmarksHtml.parse(html)
        assertEquals(listOf("Bookmarks bar|A <one>|https://a.example/", "|B|https://b.example/"), flat(entries))
        assertEquals(1_700_000_001_000L, entries[0].children[0].added)
    }

    @Test fun escapesWhenWriting() {
        val out = BookmarksHtml.write(listOf(BookmarksHtml.Entry("Tom & \"Jerry\"", "https://x.example/?a=1&b=2")))
        assertTrue(out.contains("HREF=\"https://x.example/?a=1&amp;b=2\""))
        assertTrue(out.contains(">Tom &amp; &quot;Jerry&quot;</A>"))
    }
}
