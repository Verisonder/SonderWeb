package com.verisonder.sonderweb.core

/** A search engine. `{q}` in [template] is replaced with the encoded query. */
data class SearchEngine(val id: String, val name: String, val template: String) {
    fun urlFor(query: String): String = template.replace("{q}", encodeQuery(query))
}

object SearchEngines {
    const val CUSTOM_ID = "custom"
    const val DEFAULT_ID = "duckduckgo"

    val builtIn: List<SearchEngine> = listOf(
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q={q}"),
        SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q={q}"),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/do/search?q={q}"),
        SearchEngine("qwant", "Qwant", "https://www.qwant.com/?q={q}"),
        SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q={q}"),
        SearchEngine("kagi", "Kagi", "https://kagi.com/search?q={q}"),
        SearchEngine("google", "Google", "https://www.google.com/search?q={q}"),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q={q}"),
    )

    /** The engine for [id]; a custom template is used when [id] is [CUSTOM_ID] and it is valid. */
    fun resolve(id: String?, customTemplate: String?): SearchEngine {
        if (id == CUSTOM_ID && isValidTemplate(customTemplate)) {
            return SearchEngine(CUSTOM_ID, "Custom", customTemplate!!.trim())
        }
        return builtIn.firstOrNull { it.id == id } ?: builtIn.first()
    }

    fun isValidTemplate(t: String?): Boolean {
        val s = t?.trim() ?: return false
        return (s.startsWith("https://") || s.startsWith("http://")) && s.contains("{q}")
    }
}

/** Encodes a query for a URL query string (spaces as +, like every search engine expects). */
fun encodeQuery(q: String): String {
    val sb = StringBuilder()
    for (b in q.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        when {
            c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code -> sb.append(c.toChar())
            c == ' '.code -> sb.append('+')
            else -> sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }
    return sb.toString()
}
