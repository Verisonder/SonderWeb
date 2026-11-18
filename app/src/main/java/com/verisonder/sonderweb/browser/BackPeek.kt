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
    private const val KEEP = 14
    private val pics = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)

    /** The page each tab was on just before its current one: its address and picture. */
    private val left = HashMap<Long, Pair<String, ImageBitmap>>()

    /** The same page whatever its #part or a trailing slash. */
    private fun norm(url: String) = url.substringBefore('#').trimEnd('/')

    private fun key(tab: Tab, url: String) = tab.id.toString() + " " + norm(url)

    fun attach(tab: Tab) {
        tab.session.setHistoryDelegate(
            object : GeckoSession.HistoryDelegate {
                override fun onHistoryStateChange(session: GeckoSession, list: GeckoSession.HistoryDelegate.HistoryList) {
                    tab.backUrl = list.getOrNull(list.currentIndex - 1)?.uri
                }
            },
        )
    }

    /** A full-screen picture of the page at [url], at half size to keep memory low. */
    fun remember(tab: Tab, url: String, shot: Bitmap) {
        if (tab.isPrivate || url.isEmpty()) return
        val w = (shot.width / 2).coerceAtLeast(1)
        val h = (shot.height / 2).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(shot, w, h, true)
        val packed = small.copy(Bitmap.Config.RGB_565, false) ?: small
        if (packed !== small) small.recycle()
        Browser.main.post {
            pics[key(tab, url)] = packed.asImageBitmap()
            trim()
        }
    }

    /** Too many pictures: the oldest go first, but never the tab you're on. */
    private fun trim() {
        val mine = Browser.current?.id?.toString()?.plus(" ")
        while (pics.size > KEEP) {
            val old = pics.keys.firstOrNull { mine == null || !it.startsWith(mine) } ?: pics.keys.first()
            pics.remove(old)
        }
    }

    /** The tab moved on from [from] (a link, or a site changing pages itself): keep its picture at hand. */
    fun leaving(tab: Tab, from: String) {
        if (tab.isPrivate || from.isEmpty()) return
        pics[key(tab, from)]?.let { left[tab.id] = from to it }
    }

    /**
     * The picture of the page back would go to, if there is one. When the address back goes to
     * isn't known yet (the page is still coming in), it's the page you just left.
     */
    fun behind(tab: Tab): ImageBitmap? {
        val back = tab.backUrl
        back?.let { pics[key(tab, it)] }?.let { return it }
        val (from, pic) = left[tab.id] ?: return null
        return if (back == null || norm(back) == norm(from) || norm(back) == norm(tab.url)) pic else null
    }

    fun forget(tab: Tab) {
        val prefix = tab.id.toString() + " "
        pics.keys.removeAll { it.startsWith(prefix) }
        left.remove(tab.id)
    }
}
