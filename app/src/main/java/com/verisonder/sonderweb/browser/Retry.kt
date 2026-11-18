package com.verisonder.sonderweb.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.verisonder.sonderweb.core.UrlText
import org.mozilla.geckoview.WebRequestError

/**
 * Pages that failed to load only because the network wasn't there: the app was away (the phone
 * cuts a background app's network) or had just come back (the connection or VPN still waking up).
 * They load again by themselves once you're in the app and online, instead of staying on the
 * error page until you refresh.
 */
object Retry {
    /** How long after coming back a failed load still counts as "the network waking up". */
    private const val GRACE = 15_000L

    /** Waits before each new try, in ms. */
    private val waits = longArrayOf(700, 2000, 4000, 7000)

    @Volatile
    private var inFront = true

    @Volatile
    private var backAt = 0L

    private val network = setOf(
        WebRequestError.ERROR_UNKNOWN_HOST,
        WebRequestError.ERROR_OFFLINE,
        WebRequestError.ERROR_NET_RESET,
        WebRequestError.ERROR_NET_INTERRUPT,
        WebRequestError.ERROR_NET_TIMEOUT,
    )

    /** A page failed to load. */
    fun failed(tab: Tab, uri: String?, code: Int) {
        tab.loadFailed = true
        if (uri == null || !UrlText.isWeb(uri) || code !in network) return
        val now = System.currentTimeMillis()
        if (inFront && now - backAt > GRACE) return
        if (tab.retryUrl != uri) {
            tab.retryUrl = uri
            tab.retries = 0
        }
        if (inFront) schedule(tab)
    }

    /** A load finished. After a failure this is the error page finishing: keep trying. */
    fun stopped(tab: Tab, success: Boolean) {
        if (tab.loadFailed) {
            tab.loadFailed = false
            return
        }
        if (success) {
            tab.retryUrl = null
            tab.retries = 0
        }
    }

    /** Back in the app: the pages that failed while you were away load again. */
    fun back() {
        inFront = true
        backAt = System.currentTimeMillis()
        Browser.tabs.filter { it.retryUrl != null }.forEach { schedule(it) }
    }

    fun away() {
        inFront = false
    }

    private fun schedule(tab: Tab) {
        val n = tab.retries
        if (n >= waits.size) {
            tab.retryUrl = null
            return
        }
        tab.retries = n + 1
        Browser.main.postDelayed({ attempt(tab) }, waits[n])
    }

    private fun attempt(tab: Tab) {
        val url = tab.retryUrl ?: return
        if (!inFront) return
        // You've gone somewhere else meanwhile, or closed the tab.
        if (UrlText.host(tab.url) != UrlText.host(url) || tab !in Browser.tabs || !tab.session.isOpen) {
            tab.retryUrl = null
            return
        }
        // Still no connection: wait for the next try without loading the error page again.
        if (!online()) {
            schedule(tab)
            return
        }
        tab.session.reload()
    }

    private fun online(): Boolean = runCatching {
        val cm = Browser.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}
