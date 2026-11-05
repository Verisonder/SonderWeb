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
private val YOUTUBE = listOf(
    "https://www.gstatic.com/images/branding/product/1x/youtube_512dp.png",
    "https://www.gstatic.com/youtube/img/branding/favicon/favicon_192x192.png",
    "https://www.gstatic.com/images/branding/product/2x/youtube_48dp.png",
)

private val DRIVE = listOf(
    "https://www.gstatic.com/images/branding/product/1x/drive_2020q4_512dp.png",
    "https://www.gstatic.com/images/branding/product/2x/drive_2020q4_96dp.png",
    "https://ssl.gstatic.com/images/branding/product/2x/drive_2020q4_48dp.png",
    "https://www.gstatic.com/images/branding/product/2x/drive_2020q4_48dp.png",
)

private val GMAIL = listOf(
    "https://www.gstatic.com/images/branding/product/1x/gmail_2020q4_512dp.png",
    "https://www.gstatic.com/images/branding/product/2x/gmail_2020q4_48dp.png",
    "https://ssl.gstatic.com/ui/v1/icons/mail/rfr/gmail.ico",
)

object SiteIcons {
    /** host → icon; a missing key means not loaded yet, a null value means none was found. */
    val icons = mutableStateMapOf<String, Bitmap?>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loading = HashSet<String>()

    fun load(context: Context, host: String) {
        if (host.isEmpty() || icons.containsKey(host) || !loading.add(host)) return
        val dir = File(context.filesDir, "icons6").apply { mkdirs() }
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

    /**
     * Sites whose home page, opened signed out, only shows a sign-in page with another
     * service's icon (Gmail shows Google's "G"): their own app icon, biggest first.
     */
    private val known = mapOf(
        "mail.google.com" to GMAIL,
        "gmail.com" to GMAIL,
        "www.gmail.com" to GMAIL,
        "drive.google.com" to DRIVE,
        "youtube.com" to YOUTUBE,
        "www.youtube.com" to YOUTUBE,
        "m.youtube.com" to YOUTUBE,
    )

    private fun fetch(host: String): Bitmap? {
        // Only a big one counts here; otherwise the site's own icons are tried as usual.
        known[host]?.let { urls ->
            urls.firstNotNullOfOrNull { u -> decode(u)?.let(::trim)?.takeIf { maxOf(it.width, it.height) >= 96 } }?.let { return it }
        }
        val home = "https://$host/"
        val candidates = LinkedHashSet<String>()
        // Big pages (YouTube's is over 1 MB): the icons are in the head, near the start.
        Net.get(home, 400_000, Net.BROWSER_AGENT, partial = true)?.toString(Charsets.UTF_8)?.let { html ->
            candidates += manifestIcons(html, home)
            candidates += headIcons(html, home)
        }
        candidates += "https://$host/apple-touch-icon.png"
        candidates += "https://$host/apple-touch-icon-precomposed.png"
        var best: Bitmap? = null
        for (url in candidates) {
            val bmp = decode(url)?.let(::trim) ?: continue
            if (best == null || bmp.width * bmp.height > best.width * best.height) best = bmp
            // Sharp on any screen (the tile is about 140 px on a phone): stop looking.
            if (minOf(best.width, best.height) >= 180) return best
        }
        if (best != null && maxOf(best.width, best.height) >= 48) return best
        val small = decode("https://icons.duckduckgo.com/ip3/$host.ico")?.let(::trim)
        return listOfNotNull(best, small).maxByOrNull { it.width * it.height }
    }

    /**
     * Cuts away a see-through border, so a logo drawn small in the middle of its picture
     * (YouTube's play button) fills its tile instead of looking tiny and soft.
     */
    private fun trim(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var top = h
        var left = w
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((px[y * w + x] ushr 24) > 24) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) return bmp
        val cw = right - left + 1
        val ch = bottom - top + 1
        // Nothing much to cut: keep it as it is.
        if (cw * ch > w * h * 0.85f) return bmp
        return Bitmap.createBitmap(bmp, left, top, cw, ch)
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
