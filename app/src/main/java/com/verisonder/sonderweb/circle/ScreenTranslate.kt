package com.verisonder.sonderweb.circle

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Translate the screen, like Circle to Search: every line of text on the screenshot is
 * translated (Google Translate) and painted over the original, on a patch of its own background.
 */
object ScreenTranslate {
    /** One line on the screenshot: where it is, its words, its translation and the colours to paint it in. */
    data class Line(val box: Rect, val text: String, val translated: String, val background: Int, val ink: Int)

    /** The words grouped back into their lines (in reading order). */
    fun lines(words: List<Ocr.Word>): List<Pair<Rect, String>> =
        words.groupBy { it.line }.toSortedMap().values.map { ws ->
            val box = Rect(ws.first().box)
            ws.forEach { box.union(it.box) }
            box to ws.sortedBy { it.order }.joinToString(" ") { it.text }
        }

    /** Translates every line into the phone's language. Off the main thread; null if it failed. */
    suspend fun translate(shot: Bitmap, words: List<Ocr.Word>): List<Line>? = withContext(Dispatchers.IO) {
        val lines = lines(words).filter { it.second.isNotBlank() }
        if (lines.isEmpty()) return@withContext emptyList()
        val target = Locale.getDefault().language
        // All lines in one go, one per row; if the rows don't come back one for one, line by line.
        val joined = runCatching { request(lines.joinToString("\n") { it.second }, target) }.getOrNull()
        val rows = joined?.split('\n')?.map { it.trim() }
        val translated = if (rows != null && rows.size == lines.size) {
            rows
        } else {
            lines.take(80).map { (_, t) -> runCatching { request(t, target).trim() }.getOrDefault(t) }
        }
        if (translated.isEmpty()) return@withContext null
        lines.zip(translated).map { (line, tr) ->
            val bg = background(shot, line.first)
            Line(line.first, line.second, tr, bg, ink(bg))
        }
    }

    /** Google Translate's free endpoint (the one its own web widgets use). */
    private fun request(text: String, target: String): String {
        val url = URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" +
                Uri.encode(target) + "&dt=t&q=" + Uri.encode(text.take(4800)),
        )
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SonderWeb")
        }
        try {
            if (c.responseCode != 200) throw IllegalStateException("Translate answered ${c.responseCode}")
            val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val parts = JSONArray(body).getJSONArray(0)
            val out = StringBuilder()
            for (i in 0 until parts.length()) out.append(parts.getJSONArray(i).optString(0))
            return out.toString()
        } finally {
            c.disconnect()
        }
    }

    /** The colour around a line: the average of a thin frame just outside it. */
    private fun background(shot: Bitmap, box: Rect): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        fun take(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= shot.width || y >= shot.height) return
            val p = shot.getPixel(x, y)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
        val pad = 3
        val step = (box.width() / 12).coerceAtLeast(1)
        var x = box.left - pad
        while (x <= box.right + pad) {
            take(x, box.top - pad)
            take(x, box.bottom + pad)
            x += step
        }
        val stepY = (box.height() / 4).coerceAtLeast(1)
        var y = box.top
        while (y <= box.bottom) {
            take(box.left - pad, y)
            take(box.right + pad, y)
            y += stepY
        }
        if (n == 0) return 0xFF202124.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /** Black or white text, whichever reads on [bg]. */
    private fun ink(bg: Int): Int {
        val l = 0.299 * ((bg shr 16) and 0xFF) + 0.587 * ((bg shr 8) and 0xFF) + 0.114 * (bg and 0xFF)
        return if (l > 150) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
    }
}
