package com.verisonder.sonderweb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Icons for start page shortcuts, as sharp as the site offers: the biggest icon its web app
 * manifest or page head lists (the ones phones put on the home screen), then its apple-touch-icon,
 * and only then its small favicon through DuckDuckGo's icon service. Saved on the phone, fetched
 * once a month.
 */
object SiteIcons {
    /** host → icon; a missing key means not loaded yet, a null value means none was found. */
    val icons = mutableStateMapOf<String, Bitmap?>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loading = HashSet<String>()

    fun load(context: Context, host: String) {
        if (host.isEmpty() || icons.containsKey(host) || !loading.add(host)) return
        val dir = File(context.filesDir, "icons2").apply { mkdirs() }
        val file = File(dir, host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                val cached = if (file.exists() && System.currentTimeMillis() - file.lastModified() < 30L * 86_400_000L) {
                    BitmapFactory.decodeFile(file.path)
                } else {
                    null
                }
                cached ?: fetch(host)?.also { b ->
                    runCatching { file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
            }
            loading.remove(host)
            icons[host] = bmp
        }
    }

    private fun fetch(host: String): Bitmap? {
        val home = "https://$host/"
        val candidates = LinkedHashSet<String>()
        Net.get(home, 1_500_000, Net.BROWSER_AGENT)?.toString(Charsets.UTF_8)?.let { html ->
            candidates += manifestIcons(html, home)
            candidates += headIcons(html, home)
        }
        candidates += "https://$host/apple-touch-icon.png"
        candidates += "https://$host/apple-touch-icon-precomposed.png"
        var best: Bitmap? = null
        for (url in candidates) {
            val bmp = decode(url) ?: continue
            // Big enough to look sharp on the start page: take it.
            if (bmp.width >= 120) return bmp
            if (best == null || bmp.width > best.width) best = bmp
        }
        if (best != null && best.width >= 48) return best
        val small = decode("https://icons.duckduckgo.com/ip3/$host.ico")
        return listOfNotNull(best, small).maxByOrNull { it.width }
    }

    private fun decode(url: String): Bitmap? {
        if (url.endsWith(".svg", ignoreCase = true)) return null
        val bytes = Net.get(url, 1_000_000, Net.BROWSER_AGENT) ?: return null
        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        return bmp.takeIf { it.width >= 16 && it.height >= 16 }
    }

    private val linkTag = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE).find(tag)
            ?.groupValues?.let { g -> g[2].ifEmpty { g[3] }.ifEmpty { g[4] } }
            ?.replace("&amp;", "&")

    private fun resolve(base: String, href: String): String? =
        runCatching { java.net.URL(java.net.URL(base), href).toString() }.getOrNull()
            ?.takeIf { it.startsWith("https://") }

    /** Largest side of a "sizes" value like "180x180" (0 when unknown). */
    private fun side(sizes: String?): Int =
        sizes?.split(' ')?.mapNotNull { it.substringBefore('x').toIntOrNull() }?.maxOrNull() ?: 0

    /** apple-touch-icon and sized icons from the page's head, biggest first. */
    private fun headIcons(html: String, base: String): List<String> =
        linkTag.findAll(html).mapNotNull { m ->
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase() ?: return@mapNotNull null
            if (!rel.contains("icon") || rel.contains("mask")) return@mapNotNull null
            val href = attr(tag, "href") ?: return@mapNotNull null
            val url = resolve(base, href) ?: return@mapNotNull null
            val score = side(attr(tag, "sizes")) + if (rel.contains("apple-touch")) 180 else 0
            url to score
        }.sortedByDescending { it.second }.map { it.first }.toList()

    /** Icons from the web app manifest (192 and 512 px PNGs on most sites), biggest first. */
    private fun manifestIcons(html: String, base: String): List<String> {
        val tag = linkTag.findAll(html).map { it.value }
            .firstOrNull { attr(it, "rel")?.lowercase()?.contains("manifest") == true } ?: return emptyList()
        val url = attr(tag, "href")?.let { resolve(base, it) } ?: return emptyList()
        val json = Net.get(url, 300_000, Net.BROWSER_AGENT)?.toString(Charsets.UTF_8) ?: return emptyList()
        return runCatching {
            val icons = org.json.JSONObject(json).optJSONArray("icons") ?: return emptyList()
            (0 until icons.length()).mapNotNull { i ->
                val o = icons.optJSONObject(i) ?: return@mapNotNull null
                if (o.optString("purpose").contains("monochrome")) return@mapNotNull null
                val src = o.optString("src").ifEmpty { return@mapNotNull null }
                val full = resolve(url, src) ?: return@mapNotNull null
                full to side(o.optString("sizes"))
            }.sortedByDescending { it.second }.map { it.first }
        }.getOrDefault(emptyList())
    }

    fun forget(host: String) {
        icons.remove(host)
    }
}
