package com.verisonder.sonderweb.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The address bar slides away while you scroll down a page and comes back when you scroll up,
 * reach the top, switch tabs or a page starts loading. The page gets the whole screen meanwhile.
 */
object Bar {
    var hidden by mutableStateOf(false)
        private set

    private val lastY = HashMap<Long, Int>()
    private var travel = 0

    /** Scroll distance (in page pixels) before the bar reacts, so small jitters don't flicker it. */
    private const val THRESHOLD = 24

    fun onScroll(tab: Tab, y: Int) {
        val prev = lastY.put(tab.id, y) ?: y
        if (tab !== Browser.current) return
        if (y <= 0) {
            show()
            return
        }
        val dy = y - prev
        if (dy == 0) return
        // Count travel in one direction; reset when the direction flips.
        travel = if ((dy > 0) == (travel > 0)) travel + dy else dy
        if (travel > THRESHOLD) hidden = true
        if (travel < -THRESHOLD) hidden = false
    }

    fun show() {
        travel = 0
        hidden = false
    }

    fun forget(tab: Tab) {
        lastY.remove(tab.id)
    }
}
