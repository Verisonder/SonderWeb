package com.verisonder.sonderweb.core

/** What the address bar turns typed text into. */
sealed class Resolved {
    abstract val url: String
    data class Address(override val url: String) : Resolved()
    data class Search(override val url: String, val query: String) : Resolved()
    data class BangSearch(override val url: String, val bang: String, val query: String) : Resolved()
}

object AddressInput {
    private val schemes = listOf(
        "http://", "https://", "about:", "file://", "data:", "view-source:",
        "moz-extension://", "resource://", "blob:",
    )
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val hostLabel = Regex("""^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$""", RegexOption.IGNORE_CASE)

    fun resolve(raw: String, engine: SearchEngine): Resolved? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        bang(text)?.let { return it }

        val lower = text.lowercase()
        if (schemes.any { lower.startsWith(it) } && !text.contains(' ')) return Resolved.Address(text)

        if (!text.contains(' ')) {
            hostTarget(text)?.let { return Resolved.Address(it) }
        }
        return Resolved.Search(engine.urlFor(text), text)
    }

    private fun bang(text: String): Resolved? {
        val parts = text.split(Regex("\\s+"))
        val idx = when {
            Bangs.isBangToken(parts.first()) -> 0
            parts.size > 1 && Bangs.isBangToken(parts.last()) -> parts.size - 1
            else -> return null
        }
        val trigger = parts[idx].substring(1)
        val query = parts.filterIndexed { i, _ -> i != idx }.joinToString(" ")
        val bang = Bangs.find(trigger)
            // Unknown here: DuckDuckGo resolves thousands of bangs itself.
            ?: return Resolved.BangSearch("https://duckduckgo.com/?q=" + encodeQuery(text), trigger, query)
        val url = if (query.isEmpty()) bang.home else bang.template.replace("{q}", encodeQuery(query))
        return Resolved.BangSearch(url, bang.trigger, query)
    }

    /** "example.com/x", "localhost:8080", "192.168.1.1" → a URL; anything else → null. */
    fun hostTarget(text: String): String? {
        val hostPort = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPort.isEmpty() || hostPort.contains('@')) return null
        val host: String
        val port: String?
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end < 0) return null
            host = hostPort.substring(0, end + 1)
            port = hostPort.substring(end + 1).removePrefix(":").ifEmpty { null }
        } else {
            host = hostPort.substringBefore(':')
            port = if (hostPort.contains(':')) hostPort.substringAfter(':') else null
        }
        if (port != null && (port.isEmpty() || !port.all { it.isDigit() } || port.length > 5)) return null

        val local = host.equals("localhost", true) || host.startsWith("[") ||
            (ipv4.matches(host) && host.split('.').all { it.toInt() <= 255 })
        if (local) return "http://$text"

        val labels = host.split('.')
        if (labels.size < 2) return null
        if (!labels.all { hostLabel.matches(it) }) return null
        val tld = labels.last()
        if (tld.length < 2 || !tld.all { it.isLetter() }) return null
        return "https://$text"
    }
}
