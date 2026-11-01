package com.verisonder.sonderweb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressInputTest {
    private val ddg = SearchEngines.resolve("duckduckgo", null)

    private fun url(s: String) = AddressInput.resolve(s, ddg)?.url

    @Test fun empty() {
        assertNull(AddressInput.resolve("   ", ddg))
    }

    @Test fun addresses() {
        assertEquals("https://example.com", url("example.com"))
        assertEquals("https://en.wikipedia.org/wiki/Tangier", url("en.wikipedia.org/wiki/Tangier"))
        assertEquals("https://example.com", url("https://example.com"))
        assertEquals("http://localhost:8080/x", url("localhost:8080/x"))
        assertEquals("http://192.168.1.1", url("192.168.1.1"))
        assertEquals("about:config", url("about:config"))
        assertEquals("https://sub.domain.co.uk:8443/p?q=1", url("sub.domain.co.uk:8443/p?q=1"))
    }

    @Test fun searches() {
        assertEquals("https://duckduckgo.com/?q=hello+world", url("hello world"))
        assertEquals("https://duckduckgo.com/?q=kotlin", url("kotlin"))
        assertEquals("https://duckduckgo.com/?q=1.5", url("1.5"))
        assertEquals("https://duckduckgo.com/?q=999.1.1.1", url("999.1.1.1"))
        assertEquals("https://duckduckgo.com/?q=caf%C3%A9+%26+th%C3%A9", url("café & thé"))
        assertTrue(AddressInput.resolve("what is example.com", ddg) is Resolved.Search)
    }

    @Test fun bangs() {
        assertEquals("https://en.wikipedia.org/wiki/Special:Search?search=Tangier", url("!w Tangier"))
        assertEquals("https://en.wikipedia.org/wiki/Special:Search?search=Tangier", url("Tangier !w"))
        assertEquals("https://www.youtube.com", url("!yt"))
        assertEquals("https://github.com/search?q=sonder+web", url("!GH sonder web"))
        // Unknown bang: DuckDuckGo resolves it.
        assertEquals("https://duckduckgo.com/?q=%21zzq+cats", url("!zzq cats"))
        // A lone "!" is not a bang.
        assertEquals("https://duckduckgo.com/?q=%21+hi", url("! hi"))
    }

    @Test fun customEngine() {
        val e = SearchEngines.resolve(SearchEngines.CUSTOM_ID, "https://searx.example/search?q={q}")
        assertEquals("https://searx.example/search?q=a+b", e.urlFor("a b"))
        // Invalid template falls back to the default.
        assertEquals("duckduckgo", SearchEngines.resolve(SearchEngines.CUSTOM_ID, "nope").id)
    }

    @Test fun hostText() {
        assertEquals("en.wikipedia.org", UrlText.host("https://en.wikipedia.org/wiki/X"))
        assertEquals("example.com", UrlText.host("https://www.example.com/"))
        assertEquals("", UrlText.host("about:blank"))
        assertEquals("example.com/a", UrlText.editable("https://example.com/a"))
    }

    @Test fun fileNames() {
        assertEquals("report.pdf", UrlText.fileName("attachment; filename=\"report.pdf\"", "https://x/y", null))
        assertEquals("résumé.pdf", UrlText.fileName("attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf", "https://x/y", null))
        assertEquals("app.apk", UrlText.fileName(null, "https://x/files/app.apk?token=1", null))
        assertEquals("download.pdf", UrlText.fileName(null, "https://x/", "application/pdf"))
        assertEquals("a_b.txt", UrlText.fileName("attachment; filename=\"a/b.txt\"", "https://x/", null))
    }
}
