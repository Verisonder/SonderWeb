package com.verisonder.sonderweb.browser

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.mozilla.geckoview.GeckoSession

/**
 * What the back gesture shows behind the page while you swipe: a picture of the page you'd go
 * back to, taken when you were last on it. Kept in memory only, a handful at a time, never for
 * private tabs (they show their plain colour instead).
 */
object BackPeek {
    private const val KEEP = 10
    private val pics = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > KEEP
    }

    private fun key(tab: Tab, url: String) = tab.id.toString() + " " + url

    fun attach(tab: Tab) {
        tab.session.setHistoryDelegate(
            object : GeckoSession.HistoryDelegate {
                override fun onHistoryStateChange(session: GeckoSession, list: GeckoSession.HistoryDelegate.HistoryList) {
                    tab.backUrl = list.getOrNull(list.currentIndex - 1)?.uri
                }
            },
        )
    }

    /** A full-screen picture of the page as it looks now, at half size to keep memory low. */
    fun remember(tab: Tab, url: String, shot: Bitmap) {
        if (tab.isPrivate || url.isEmpty()) return
        val w = (shot.width / 2).coerceAtLeast(1)
        val h = (shot.height / 2).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(shot, w, h, true)
        val packed = small.copy(Bitmap.Config.RGB_565, false) ?: small
        if (packed !== small) small.recycle()
        Browser.main.post { pics[key(tab, url)] = packed.asImageBitmap() }
    }

    /** The picture of the page back would go to, if there is one. */
    fun behind(tab: Tab): ImageBitmap? = tab.backUrl?.let { pics[key(tab, it)] }

    fun forget(tab: Tab) {
        val prefix = tab.id.toString() + " "
        pics.keys.removeAll { it.startsWith(prefix) }
    }
}
