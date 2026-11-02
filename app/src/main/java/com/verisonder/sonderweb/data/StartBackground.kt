package com.verisonder.sonderweb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The start page's wallpaper: a picture you choose, copied into the app (sized for the screen,
 * turned the right way up), so it stays even if the original is moved or deleted.
 */
object StartBackground {
    /** The wallpaper, once loaded (null: none, or not loaded yet). */
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadedStamp = -1L

    private fun file(context: Context) = File(context.filesDir, "start_wallpaper.jpg")

    /** Loads the saved wallpaper (off the main thread), unless it's already loaded. */
    fun load(context: Context) {
        val f = file(context)
        if (!f.exists()) {
            image = null
            loadedStamp = -1L
            return
        }
        val stamp = f.lastModified()
        if (stamp == loadedStamp) return
        loadedStamp = stamp
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() }
            image = bmp?.asImageBitmap()
        }
    }

    /** Copies the picture at [uri] in as the wallpaper; [done] says whether it worked. */
    fun set(context: Context, uri: Uri, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { copyIn(app, uri) }.getOrDefault(false) }
            if (ok) {
                loadedStamp = -1L
                load(app)
            }
            done(ok)
        }
    }

    fun clear(context: Context) {
        file(context).delete()
        image = null
        loadedStamp = -1L
    }

    private fun copyIn(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        // About the size of the screen: sharp, without holding a huge picture in memory.
        val screen = context.resources.displayMetrics
        val target = maxOf(screen.widthPixels, screen.heightPixels).coerceAtLeast(1080)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        // Photos are often stored sideways with a note saying which way is up.
        val turn = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (turn != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(turn) }, true)
        }
        val tmp = File(context.filesDir, "start_wallpaper.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return tmp.renameTo(file(context))
    }
}
