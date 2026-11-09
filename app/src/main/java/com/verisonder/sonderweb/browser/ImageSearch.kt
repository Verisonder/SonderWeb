package com.verisonder.sonderweb.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Search with a picture: the photo is made smaller, then sent to Google Lens by a little page
 * that posts it the way Lens's own upload form does. The results page opens in the tab.
 */
object ImageSearch {

    private const val MAX_SIDE = 1280
    private const val MAX_BYTES = 300_000

    /** Prepares [uri] off the main thread, then hands the page to open to [open] (on the main thread). */
    fun search(context: Context, uri: Uri, open: (String) -> Unit) {
        val app = context.applicationContext
        thread(name = "image-search") {
            val jpeg = runCatching { shrink(app, uri) }.getOrNull()
            Browser.main.post {
                if (jpeg == null) Browser.host?.toast("Couldn't open that picture") else open(page(jpeg))
            }
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        // Only the size first, to read a big photo at a fraction of its size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        while (side / (sample * 2) >= MAX_SIDE) sample *= 2
        if (Build.VERSION.SDK_INT >= 28) {
            // Turned the right way up (the photo's orientation tag) by the decoder itself.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { d, _, _ ->
                d.setTargetSampleSize(sample)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun shrink(context: Context, uri: Uri): ByteArray? {
        var bmp = decode(context, uri) ?: return null
        val side = maxOf(bmp.width, bmp.height)
        if (side > MAX_SIDE) {
            val k = MAX_SIDE.toFloat() / side
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
        }
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_BYTES || quality <= 40) return out.toByteArray()
            quality -= 15
        }
    }

    private fun page(jpeg: ByteArray): String {
        val image = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val lang = Locale.getDefault().toLanguageTag()
        val action = "https://lens.google.com/v3/upload?hl=" + Uri.encode(lang) + "&st=" + System.currentTimeMillis()
        val html = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="dark light"><title>Search with a picture</title>
            <style>
              body{margin:0;font:16px sans-serif;display:flex;min-height:100vh;align-items:center;
                justify-content:center;background:#1c1c1c;color:#9a9a9a}
              @media (prefers-color-scheme: light){body{background:#fff;color:#777}}
            </style></head><body><p>Searching…</p>
            <form id="f" method="post" enctype="multipart/form-data" action="$action">
            <input type="file" name="encoded_image" id="i" hidden></form>
            <script>
              const b = atob("$image"), a = new Uint8Array(b.length);
              for (let k = 0; k < b.length; k++) a[k] = b.charCodeAt(k);
              const d = new DataTransfer();
              d.items.add(new File([a], "image.jpg", { type: "image/jpeg" }));
              document.getElementById("i").files = d.files;
              document.getElementById("f").submit();
            </script></body></html>
        """.trimIndent()
        return "data:text/html;base64," + Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
