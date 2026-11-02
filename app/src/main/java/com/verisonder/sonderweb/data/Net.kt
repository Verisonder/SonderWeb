package com.verisonder.sonderweb.data

import java.net.HttpURLConnection
import java.net.URL

/** Small plain HTTPS GETs for the start page (weather, place search, site icons). No cookies. */
object Net {
    /** A phone browser's name, for pages that only send their full head (with icons) to browsers. */
    const val BROWSER_AGENT = "Mozilla/5.0 (Android 14; Mobile; rv:159.0) Gecko/159.0 Firefox/159.0"

    fun get(url: String, maxBytes: Int = 2_000_000, agent: String = "SonderWeb"): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.instanceFollowRedirects = true
        c.useCaches = false
        c.setRequestProperty("User-Agent", agent)
        try {
            if (c.responseCode !in 200..299) return@runCatching null
            c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) return@runCatching null
                }
                out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    fun text(url: String): String? = get(url)?.toString(Charsets.UTF_8)
}
