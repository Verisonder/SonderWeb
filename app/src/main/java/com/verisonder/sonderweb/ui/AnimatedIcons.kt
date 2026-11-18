package com.verisonder.sonderweb.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Animated icons for the search widget: GIF, animated WebP and animated PNG (APNG).
 *
 * The home screen can only flip through pictures at one fixed pace, so an animation is turned
 * into a few distinct pictures (frames that look the same are kept once) and the order to show
 * them in, one step per [Animated.tick] ms; a frame shown longer is simply repeated.
 *
 * WebP and APNG keep real (soft-edged) transparency, so they come out smooth like a PNG. A GIF's
 * transparency is all-or-nothing; shrinking it in halves softens its edges as much as it can.
 */
object AnimatedIcons {
    /** Frames are kept this big (px): sharp at the widget's sizes, light enough for the home screen. */
    const val PX = 112

    /** At most this many distinct pictures from one animation. */
    const val MAX_FRAMES = 24

    /** At most this many steps in one loop (a frame shown longer is repeated). */
    const val MAX_STEPS = 150

    /** The home screen flips no faster than this (ms). */
    const val MIN_TICK = 40

    /** Largest animation side read (px): bigger ones aren't worth the memory for a small icon. */
    private const val MAX_SIDE = 2048

    /** An animation made ready for the widget: distinct frames side by side, and the order to play them. */
    class Animated(val strip: Bitmap, val count: Int, val tick: Int, val seq: List<Int>)

    private class Frame(val bmp: Bitmap, val ms: Int)

    /** The picked picture as an animation, or null if it isn't one (or can't be read). */
    fun read(context: Context, uri: Uri): Animated? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val frames = when {
            isGif(bytes) -> gif(bytes)
            isWebp(bytes) -> webp(bytes)
            isPng(bytes) -> apng(bytes)
            else -> null
        } ?: return null
        if (frames.size < 2) return null
        pack(frames)
    }.getOrNull()

    private fun isGif(b: ByteArray) = b.size > 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()

    private fun isWebp(b: ByteArray) = b.size > 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(b, 8, 4, Charsets.US_ASCII) == "WEBP"

    private fun isPng(b: ByteArray) = b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() &&
        b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()

    // ---- Making frames small ----

    /** Makes a square picture [PX] wide in halves, smoothing at each step (soft edges, no skipped pixels). */
    private fun shrink(src: Bitmap): Bitmap {
        var b = src
        while (b.width / 2 >= PX) {
            val half = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
            if (b !== src) b.recycle()
            b = half
        }
        if (b.width == PX && b !== src) return b
        val out = Bitmap.createScaledBitmap(b, PX, PX, true)
        if (b !== src) b.recycle()
        return out
    }

    /** The middle square of the whole picture (canvas), made small. */
    private fun snapshot(canvasBmp: Bitmap): Bitmap {
        val side = minOf(canvasBmp.width, canvasBmp.height)
        val square = Bitmap.createBitmap(canvasBmp, (canvasBmp.width - side) / 2, (canvasBmp.height - side) / 2, side, side)
        val small = shrink(square)
        if (square !== canvasBmp && square !== small) square.recycle()
        return if (small === canvasBmp) small.copy(Bitmap.Config.ARGB_8888, false) else small
    }

    // ---- GIF ----

    /** A GIF, looked at every [MIN_TICK] ms (the engine works out each moment's picture). */
    @Suppress("DEPRECATION")
    private fun gif(bytes: ByteArray): List<Frame>? {
        val movie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val w = movie.width()
        val h = movie.height()
        if (w <= 0 || h <= 0) return null
        val duration = movie.duration()
        if (duration <= 0) return null
        // Drawn at its own size (up to 1024 px), then made small smoothly.
        val side = minOf(w, h)
        val big = minOf(side, 1024)
        val scale = big.toFloat() / side
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val canvasBmp = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val tick = maxOf(MIN_TICK, ceil(duration / 600.0).toInt())
        val out = ArrayList<Frame>()
        var t = 0
        while (t < duration) {
            movie.setTime(t)
            canvasBmp.eraseColor(Color.TRANSPARENT)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.translate(-(w - side) / 2f, -(h - side) / 2f)
            movie.draw(canvas, 0f, 0f, paint)
            canvas.restore()
            out += Frame(snapshot(canvasBmp), minOf(tick, duration - t))
            t += tick
        }
        canvasBmp.recycle()
        return out
    }

    // ---- Animated WebP ----

    private fun le24(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
        ((b[at + 2].toInt() and 0xFF) shl 16)

    private fun le32(b: ByteArray, at: Int) = le24(b, at) or ((b[at + 3].toInt() and 0xFF) shl 24)

    /** RIFF chunks in [b] from [from] to [to]: (name, data start, data length). */
    private fun chunks(b: ByteArray, from: Int, to: Int): List<Triple<String, Int, Int>> {
        val out = ArrayList<Triple<String, Int, Int>>()
        var at = from
        while (at + 8 <= to) {
            val name = String(b, at, 4, Charsets.US_ASCII)
            val len = le32(b, at + 4)
            if (len < 0 || at + 8 + len > to) break
            out += Triple(name, at + 8, len)
            at += 8 + len + (len and 1)
        }
        return out
    }

    private fun riffChunk(name: String, data: ByteArray, from: Int, len: Int, out: ByteArrayOutputStream) {
        out.write(name.toByteArray(Charsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(len).array())
        out.write(data, from, len)
        if (len and 1 == 1) out.write(0)
    }

    /** One animation frame's data as a WebP picture of its own. */
    private fun webpFrame(b: ByteArray, parts: List<Triple<String, Int, Int>>, w: Int, h: Int): Bitmap? {
        val body = ByteArrayOutputStream()
        val alph = parts.firstOrNull { it.first == "ALPH" }
        val image = parts.firstOrNull { it.first == "VP8 " || it.first == "VP8L" } ?: return null
        if (alph != null && image.first == "VP8 ") {
            // Lossy with transparency: needs the extended header.
            val vp8x = ByteArray(10)
            vp8x[0] = 0x10 // has transparency
            vp8x[4] = ((w - 1) and 0xFF).toByte(); vp8x[5] = (((w - 1) shr 8) and 0xFF).toByte(); vp8x[6] = (((w - 1) shr 16) and 0xFF).toByte()
            vp8x[7] = ((h - 1) and 0xFF).toByte(); vp8x[8] = (((h - 1) shr 8) and 0xFF).toByte(); vp8x[9] = (((h - 1) shr 16) and 0xFF).toByte()
            riffChunk("VP8X", vp8x, 0, 10, body)
            riffChunk("ALPH", b, alph.second, alph.third, body)
        }
        riffChunk(image.first, b, image.second, image.third, body)
        val inner = body.toByteArray()
        val file = ByteArrayOutputStream()
        file.write("RIFF".toByteArray(Charsets.US_ASCII))
        file.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(4 + inner.size).array())
        file.write("WEBP".toByteArray(Charsets.US_ASCII))
        file.write(inner)
        val all = file.toByteArray()
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = true
        }
        return BitmapFactory.decodeByteArray(all, 0, all.size, opts)
    }

    private fun webp(b: ByteArray): List<Frame>? {
        val top = chunks(b, 12, minOf(b.size, 8 + le32(b, 4)))
        val vp8x = top.firstOrNull { it.first == "VP8X" } ?: return null
        val flags = b[vp8x.second].toInt()
        if (flags and 0x02 == 0) return null // not animated
        val cw = le24(b, vp8x.second + 4) + 1
        val ch = le24(b, vp8x.second + 7) + 1
        if (cw > MAX_SIDE || ch > MAX_SIDE) return null
        val canvasBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val over = Paint(Paint.FILTER_BITMAP_FLAG)
        val replace = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val out = ArrayList<Frame>()
        var disposeRect: Rect? = null
        for ((name, at, len) in top) {
            if (name != "ANMF" || len < 16) continue
            disposeRect?.let { canvas.drawRect(it, clear) }
            disposeRect = null
            val x = le24(b, at) * 2
            val y = le24(b, at + 3) * 2
            val fw = le24(b, at + 6) + 1
            val fh = le24(b, at + 9) + 1
            val ms = le24(b, at + 12)
            val bits = b[at + 15].toInt()
            val noBlend = bits and 0x02 != 0
            val dispose = bits and 0x01 != 0
            val frame = webpFrame(b, chunks(b, at + 16, at + len), fw, fh) ?: continue
            canvas.drawBitmap(frame, x.toFloat(), y.toFloat(), if (noBlend) replace else over)
            frame.recycle()
            out += Frame(snapshot(canvasBmp), if (ms <= 0) 100 else ms)
            if (dispose) disposeRect = Rect(x, y, x + fw, y + fh)
        }
        canvasBmp.recycle()
        return out
    }

    // ---- Animated PNG ----

    private fun be32(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
        ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun be16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun pngChunk(type: String, data: ByteArray, from: Int, len: Int, out: ByteArrayOutputStream) {
        out.write(ByteBuffer.allocate(4).putInt(len).array())
        val t = type.toByteArray(Charsets.US_ASCII)
        out.write(t)
        out.write(data, from, len)
        val crc = CRC32()
        crc.update(t)
        crc.update(data, from, len)
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }

    private class Fctl(val w: Int, val h: Int, val x: Int, val y: Int, val ms: Int, val dispose: Int, val blend: Int)

    private fun apng(b: ByteArray): List<Frame>? {
        // The PNG's chunks: (type, data start, data length).
        val list = ArrayList<Triple<String, Int, Int>>()
        var at = 8
        while (at + 12 <= b.size) {
            val len = be32(b, at)
            if (len < 0 || at + 12 + len > b.size) break
            val type = String(b, at + 4, 4, Charsets.US_ASCII)
            list += Triple(type, at + 8, len)
            at += 12 + len
            if (type == "IEND") break
        }
        if (list.none { it.first == "acTL" }) return null // a still PNG
        val ihdr = list.firstOrNull { it.first == "IHDR" } ?: return null
        val cw = be32(b, ihdr.second)
        val ch = be32(b, ihdr.second + 4)
        if (cw <= 0 || ch <= 0 || cw > MAX_SIDE || ch > MAX_SIDE) return null
        // Chunks every frame's picture needs (palette, transparency, colour info).
        val firstData = list.indexOfFirst { it.first == "IDAT" }
        val shared = list.take(if (firstData < 0) list.size else firstData)
            .filter { it.first != "IHDR" && it.first != "acTL" && it.first != "fcTL" }

        // Frames: each fcTL with the image data after it (IDAT for the first, fdAT after).
        class Raw(val ctl: Fctl, val data: MutableList<Pair<Int, Int>>)
        val raws = ArrayList<Raw>()
        var cur: Raw? = null
        for ((type, start, len) in list) {
            when (type) {
                "fcTL" -> if (len >= 26) {
                    val num = be16(b, start + 20)
                    val den = be16(b, start + 22).let { if (it == 0) 100 else it }
                    cur = Raw(
                        Fctl(
                            be32(b, start + 4), be32(b, start + 8), be32(b, start + 12), be32(b, start + 16),
                            (num * 1000.0 / den).roundToInt(), b[start + 24].toInt(), b[start + 25].toInt(),
                        ),
                        ArrayList(),
                    ).also { raws += it }
                }
                "IDAT" -> cur?.data?.add(start to len)
                "fdAT" -> if (len > 4) cur?.data?.add(start + 4 to len - 4)
            }
        }
        if (raws.size < 2) return null

        val canvasBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val over = Paint()
        val replace = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val out = ArrayList<Frame>()
        raws.forEachIndexed { i, r ->
            val c = r.ctl
            if (r.data.isEmpty() || c.w <= 0 || c.h <= 0) return@forEachIndexed
            // This frame as a PNG of its own.
            val png = ByteArrayOutputStream()
            png.write(b, 0, 8)
            val hdr = b.copyOfRange(ihdr.second, ihdr.second + ihdr.third)
            ByteBuffer.wrap(hdr).putInt(0, c.w).putInt(4, c.h)
            pngChunk("IHDR", hdr, 0, hdr.size, png)
            shared.forEach { (type, start, len) -> pngChunk(type, b, start, len, png) }
            r.data.forEach { (start, len) -> pngChunk("IDAT", b, start, len, png) }
            pngChunk("IEND", ByteArray(0), 0, 0, png)
            val bytes = png.toByteArray()
            val frame = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEachIndexed
            val area = Rect(c.x, c.y, c.x + c.w, c.y + c.h)
            // "Back to before": what's under this frame, to put back once it has been shown.
            val before = if (c.dispose == 2 && i > 0) Bitmap.createBitmap(canvasBmp, area.left, area.top, minOf(c.w, cw - c.x), minOf(c.h, ch - c.y)) else null
            canvas.drawBitmap(frame, c.x.toFloat(), c.y.toFloat(), if (c.blend == 1) over else replace)
            frame.recycle()
            out += Frame(snapshot(canvasBmp), if (c.ms <= 0) 100 else c.ms)
            when {
                c.dispose == 1 || (c.dispose == 2 && before == null) -> canvas.drawRect(area, clear)
                before != null -> {
                    canvas.drawBitmap(before, c.x.toFloat(), c.y.toFloat(), replace)
                    before.recycle()
                }
            }
        }
        canvasBmp.recycle()
        return out
    }

    // ---- For the home screen ----

    /**
     * Frames that look the same are kept once; at most [MAX_FRAMES] distinct ones; then one step
     * every [Animated.tick] ms, a frame shown longer repeated, at most [MAX_STEPS] steps a loop.
     */
    private fun pack(frames: List<Frame>): Animated? {
        // Distinct pictures, and the order with how long each shows.
        val unique = ArrayList<Bitmap>()
        val order = ArrayList<Pair<Int, Int>>()
        for (f in frames) {
            var idx = unique.indexOfFirst { it.sameAs(f.bmp) }
            if (idx < 0) {
                unique += f.bmp
                idx = unique.lastIndex
            } else {
                f.bmp.recycle()
            }
            if (order.isNotEmpty() && order.last().first == idx) {
                order[order.lastIndex] = idx to order.last().second + f.ms
            } else {
                order += idx to f.ms
            }
        }
        if (unique.size < 2) return null
        // Too many distinct pictures: keep every k-th, the others shown as the one kept before them.
        var kept = unique.indices.toList()
        if (unique.size > MAX_FRAMES) {
            val k = ceil(unique.size / MAX_FRAMES.toDouble()).toInt()
            kept = unique.indices.filter { it % k == 0 }
            val remapped = ArrayList<Pair<Int, Int>>()
            for ((idx, ms) in order) {
                val to = kept.indexOf(idx - idx % k)
                if (remapped.isNotEmpty() && remapped.last().first == to) {
                    remapped[remapped.lastIndex] = to to remapped.last().second + ms
                } else {
                    remapped += to to ms
                }
            }
            order.clear()
            order += remapped
        } else {
            for (i in order.indices) order[i] = kept.indexOf(order[i].first) to order[i].second
        }
        val pics = kept.map { unique[it] }
        unique.filterIndexed { i, _ -> i !in kept }.forEach { it.recycle() }

        // The pace: the shortest showing, but no faster than the home screen flips.
        val total = order.sumOf { it.second }
        var tick = maxOf(MIN_TICK, order.minOf { it.second })
        fun steps(t: Int) = order.sumOf { maxOf(1, (it.second.toDouble() / t).roundToInt()) }
        while (steps(tick) > MAX_STEPS) tick = maxOf(tick + 10, (tick * 1.25).toInt())
        if (tick > total) tick = total
        val seq = ArrayList<Int>()
        order.forEach { (idx, ms) -> repeat(maxOf(1, (ms.toDouble() / tick).roundToInt())) { seq += idx } }

        val strip = Bitmap.createBitmap(PX * pics.size, PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        pics.forEachIndexed { i, bmp ->
            canvas.drawBitmap(bmp, (i * PX).toFloat(), 0f, null)
            bmp.recycle()
        }
        return Animated(strip, pics.size, tick, seq)
    }
}
