package com.verisonder.sonderweb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Small pictures of your normal tabs, kept on the phone so reopened tabs show what they looked
 * like in the tab switcher (instead of a letter) before they load again. Never private tabs.
 */
object Thumbs {
    private val io = Executors.newSingleThreadExecutor()

    private fun dir(context: Context) = File(context.cacheDir, "thumbs").apply { mkdirs() }

    private fun name(url: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return d.joinToString("") { "%02x".format(it) } + ".jpg"
    }

    fun save(context: Context, url: String, bmp: Bitmap) {
        if (url.isEmpty()) return
        val app = context.applicationContext
        io.execute {
            runCatching {
                val f = File(dir(app), name(url))
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            }
        }
    }

    /** Loads the picture for [url] off the main thread; [done] runs on the main thread. */
    fun load(context: Context, url: String, done: (Bitmap) -> Unit) {
        if (url.isEmpty()) return
        val app = context.applicationContext
        io.execute {
            val bmp = runCatching { BitmapFactory.decodeFile(File(dir(app), name(url)).path) }.getOrNull() ?: return@execute
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(bmp) }
        }
    }

    /** Throws away pictures of tabs that are gone. */
    fun keepOnly(context: Context, urls: Collection<String>) {
        val app = context.applicationContext
        val keep = urls.map { name(it) }.toSet()
        io.execute {
            dir(app).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        }
    }
}
