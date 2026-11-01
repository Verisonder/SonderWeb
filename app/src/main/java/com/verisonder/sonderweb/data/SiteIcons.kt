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
 * Icons for start page shortcuts: the site's own app icon (apple-touch-icon) when it has one,
 * else its favicon through DuckDuckGo's icon service. Saved on the phone, fetched once a month.
 */
object SiteIcons {
    /** host → icon; a missing key means not loaded yet, a null value means none was found. */
    val icons = mutableStateMapOf<String, Bitmap?>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loading = HashSet<String>()

    fun load(context: Context, host: String) {
        if (host.isEmpty() || icons.containsKey(host) || !loading.add(host)) return
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
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
        val candidates = listOf(
            "https://$host/apple-touch-icon.png",
            "https://$host/apple-touch-icon-precomposed.png",
            "https://icons.duckduckgo.com/ip3/$host.ico",
        )
        for (url in candidates) {
            val bytes = Net.get(url, 1_000_000) ?: continue
            val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: continue
            if (bmp.width >= 16 && bmp.height >= 16) return bmp
        }
        return null
    }

    fun forget(host: String) {
        icons.remove(host)
    }
}
