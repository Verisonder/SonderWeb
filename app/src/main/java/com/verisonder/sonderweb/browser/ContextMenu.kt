package com.verisonder.sonderweb.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement

/** Long-press on a link, image or video. */
object ContextMenu {
    class Request(val tab: Tab, val link: String?, val linkText: String?, val src: String?, val type: Int, val base: String?)

    var request by mutableStateOf<Request?>(null)
        private set

    fun show(tab: Tab, e: ContextElement) {
        val link = e.linkUri?.takeIf { it.isNotBlank() && !it.startsWith("javascript:") }
        val src = e.srcUri?.takeIf { it.isNotBlank() }
        if (link == null && src == null) return
        request = Request(tab, link, e.linkText ?: e.altText, src, e.type, e.baseUri)
    }

    fun dismiss() {
        request = null
    }

    val isMedia: (Request) -> Boolean = { it.type == ContextElement.TYPE_IMAGE || it.type == ContextElement.TYPE_VIDEO || it.type == ContextElement.TYPE_AUDIO }
}
