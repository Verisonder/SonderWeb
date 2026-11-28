package com.verisonder.sondercircle

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands the screenshot to SonderWeb, which shows the circle and the results with your own
 * sign-ins. SonderCircle itself only takes the screenshot.
 */
object Handoff {
    const val SONDERWEB = "com.verisonder.sonderweb"
    const val SONDERWEB_SYNC = "com.verisonder.sonderweb.sync"
    private const val CIRCLE = "com.verisonder.sonderweb.circle.CircleActivity"

    fun installed(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    /** Which SonderWeb opens the circle: the one you picked, else whichever is installed. */
    fun target(ctx: Context): String? {
        val picked = ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).getString("target", null)
        if (picked != null && installed(ctx, picked)) return picked
        return listOf(SONDERWEB, SONDERWEB_SYNC).firstOrNull { installed(ctx, it) }
    }

    fun choose(ctx: Context, pkg: String) {
        ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).edit().putString("target", pkg).apply()
    }

    /** Text searches: "" follows SonderWeb's own search engine, else one engine (SonderWeb's ids). */
    val textEngines = listOf(
        "" to "Same as SonderWeb",
        "duckduckgo" to "DuckDuckGo",
        "brave" to "Brave Search",
        "startpage" to "Startpage",
        "qwant" to "Qwant",
        "ecosia" to "Ecosia",
        "kagi" to "Kagi",
        "google" to "Google",
        "bing" to "Bing",
    )

    /** Circled pictures: search by picture (DuckDuckGo can't). */
    val imageEngines = listOf(
        "google" to "Google Lens",
        "bing" to "Bing Visual Search",
        "yandex" to "Yandex Images",
    )

    fun textEngine(ctx: Context): String =
        ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).getString("text_engine", "") ?: ""

    fun imageEngine(ctx: Context): String =
        ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).getString("image_engine", "google") ?: "google"

    fun setTextEngine(ctx: Context, id: String) {
        ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).edit().putString("text_engine", id).apply()
    }

    /** Settings › Advanced: seconds to wait before the screenshot (0: none), to set the screen up first. */
    fun delay(ctx: Context): Int =
        ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).getInt("delay", 0).coerceIn(0, MAX_DELAY)

    fun changeDelay(ctx: Context, seconds: Int) {
        ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).edit().putInt("delay", seconds.coerceIn(0, MAX_DELAY)).apply()
    }

    const val MAX_DELAY = 10

    fun setImageEngine(ctx: Context, id: String) {
        ctx.getSharedPreferences("circle", Context.MODE_PRIVATE).edit().putString("image_engine", id).apply()
    }

    /** Saves [shot] and returns the intent that opens the circle on it (null: no SonderWeb). Off the main thread. */
    fun prepare(ctx: Context, shot: Bitmap): Intent? {
        val pkg = target(ctx) ?: return null
        val dir = File(ctx.cacheDir, "shots").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "screen-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { shot.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".shots", file)
        return Intent()
            .setClassName(pkg, CIRCLE)
            .setData(uri)
            .putExtra("text_engine", textEngine(ctx))
            .putExtra("image_engine", imageEngine(ctx))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
