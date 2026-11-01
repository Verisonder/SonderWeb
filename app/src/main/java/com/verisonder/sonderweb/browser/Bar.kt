package com.verisonder.sonderweb.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How the address bar gets out of the way (Settings › Appearance):
 *  0  always shown;
 *  1  slides away while you scroll down, back when you scroll up, reach the top, switch tabs
 *     or a page starts loading;
 *  2  shrinks into a small button while you scroll down; tap it, or scroll back to the top
 *     of the page, to bring the bar back.
 */
object Bar {
    const val ALWAYS = 0
    const val SLIDE = 1
    const val BUTTON = 2

    var hidden by mutableStateOf(false)
        private set

    var mode = SLIDE
        private set

    private val lastY = HashMap<Long, Int>()
    private var travel = 0

    /** Scroll distance (in page pixels) before the bar reacts, so small jitters don't flicker it. */
    private const val THRESHOLD = 24

    fun setMode(m: Int) {
        if (m == mode) return
        mode = m
        travel = 0
        hidden = m == BUTTON
    }

    fun onScroll(tab: Tab, y: Int) {
        val prev = lastY.put(tab.id, y) ?: y
        if (tab !== Browser.current || mode == ALWAYS) return
        if (y <= 0) {
            // Back at the top of the page: the whole bar returns (in button mode only when you
            // scrolled there, not when a page simply opens at the top).
            if (mode == BUTTON && prev > 0) expand() else show()
            return
        }
        val dy = y - prev
        if (dy == 0) return
        // Count travel in one direction; reset when the direction flips.
        travel = if ((dy > 0) == (travel > 0)) travel + dy else dy
        if (travel > THRESHOLD) hidden = true
        if (travel < -THRESHOLD && mode == SLIDE) hidden = false
    }

    /** Automatic reasons to bring the bar back (top of page, new page, other tab): slide mode only. */
    fun show() {
        travel = 0
        if (mode == SLIDE) hidden = false
    }

    /** You tapped the small button. */
    fun expand() {
        travel = 0
        hidden = false
    }

    /** You swiped the bar sideways: out of the way (into the button, or slid off). */
    fun compact() {
        if (mode == ALWAYS) return
        travel = 0
        hidden = true
    }

    /** Back into the button (button mode), e.g. after you searched. */
    fun collapse() {
        if (mode == BUTTON) hidden = true
    }

    fun forget(tab: Tab) {
        lastY.remove(tab.id)
    }
}
