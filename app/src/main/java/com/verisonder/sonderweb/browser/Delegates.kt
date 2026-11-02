package com.verisonder.sonderweb.browser

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.verisonder.sonderweb.core.UrlText
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

/** Wires a tab's engine session to its Compose state. */
object Delegates {

    fun attach(tab: Tab) {
        val s = tab.session
        s.setNavigationDelegate(navigation(tab))
        s.setProgressDelegate(progress(tab))
        s.setContentDelegate(content(tab))
        s.setPermissionDelegate(Permissions.delegate(tab))
        s.setPromptDelegate(Prompts.delegate(tab))
        s.setScrollDelegate(object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                Bar.onScroll(tab, scrollY)
                val top = scrollY <= 0
                if (tab.atTop != top) tab.atTop = top
                // Once scrolling settles, pick up the colours now at the page's edges.
                // Again a moment later: sites often slide their own header back in after you stop.
                Browser.main.removeCallbacks(resample)
                Browser.main.removeCallbacks(resampleLate)
                Browser.main.postDelayed(resample, 220)
                Browser.main.postDelayed(resampleLate, 900)
            }
        })
        Extensions.attach(tab)
    }

    private fun navigation(tab: Tab) = object : NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            // about:blank is the start page, not a site.
            // A restored tab that hasn't loaded yet keeps showing the address it will open.
            // A new session reports its empty first page after the real one was asked for:
            // while that loads it isn't the start page (it used to flash in on launch).
            if (url == null || url == "about:blank") {
                if (!tab.loading || tab.startedPage) tab.url = tab.pendingUrl ?: ""
            } else {
                tab.url = url
            }
            tab.blockedBadge = ""
            Browser.saveTabs()
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            tab.canGoBack = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            tab.canGoForward = canGoForward
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? {
            val uri = request.uri
            if (UrlText.isInternal(uri)) {
                // A link you tapped that an installed app can also open: you're asked first,
                // and the page waits for your answer.
                if (UrlText.isWeb(uri) && request.hasUserGesture && !request.isDirectNavigation &&
                    request.target == NavigationDelegate.TARGET_WINDOW_CURRENT &&
                    AppLinks.askForWebLink(tab, uri)
                ) {
                    return GeckoResult.deny()
                }
                return GeckoResult.allow()
            }
            // tel:, mailto:, intent:, market: … belong to other apps. Only when you tapped
            // something (pages may not launch apps on their own), and only after you say so.
            if (!request.hasUserGesture && !request.isDirectNavigation) return GeckoResult.deny()
            if (!AppLinks.askForAppLink(tab, uri)) Browser.host?.toast("No app can open this link")
            return GeckoResult.deny()
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            val t = Browser.newTab(uri, isPrivate = tab.isPrivate, select = true, opener = tab, open = false)
            t.url = uri
            return GeckoResult.fromValue(t.session)
        }

        override fun onLoadError(
            session: GeckoSession,
            uri: String?,
            error: WebRequestError,
        ): GeckoResult<String>? {
            tab.loading = false
            return GeckoResult.fromValue(ErrorPage.html(uri, error))
        }
    }

    private fun progress(tab: Tab) = object : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            if (url == "about:blank") return
            if (tab === Browser.current) Bar.show()
            tab.loading = true
            tab.startedPage = true
            tab.atTop = true
            tab.progress = 5
            tab.url = url
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            tab.loading = false
            if (tab.startedPage) tab.covered = false
            tab.progress = 100
            if (success && !tab.isPrivate && Browser.prefs.saveHistory && UrlText.isWeb(tab.url)) {
                Browser.db.visit(tab.url, tab.title)
            }
            capture(tab)
            // Pages often finish drawing their header just after loading.
            Browser.main.postDelayed({ if (Browser.current === tab) capture(tab, thumbnail = false) }, 800)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            tab.progress = progress
        }

        override fun onSecurityChange(session: GeckoSession, securityInfo: ProgressDelegate.SecurityInformation) {
            tab.secure = securityInfo.isSecure
        }
    }

    /** Pixels at the bottom of the page view that the bar covers (set by the screen). */
    @Volatile
    var coveredBottom = 0

    private val resample = Runnable { Browser.current?.let { capture(it, thumbnail = false) } }
    private val resampleLate = Runnable { Browser.current?.let { capture(it, thumbnail = false) } }

    private fun content(tab: Tab) = object : ContentDelegate {
        override fun onFirstContentfulPaint(session: GeckoSession) {
            if (tab.covered) reveal(tab) else capture(tab, thumbnail = false)
        }

        override fun onTitleChange(session: GeckoSession, title: String?) {
            tab.title = title ?: ""
            if (!tab.isPrivate && Browser.prefs.saveHistory && UrlText.isWeb(tab.url) && !title.isNullOrBlank()) {
                Browser.db.setTitle(tab.url, title)
            }
        }

        override fun onShowDynamicToolbar(session: GeckoSession) {
            if (tab === Browser.current) Bar.show()
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            Browser.host?.setFullscreen(fullScreen)
        }

        override fun onCloseRequest(session: GeckoSession) {
            Browser.close(tab)
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            val type = response.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value.orEmpty()
            if (type.startsWith("application/x-xpinstall") || response.uri.substringBefore('?').endsWith(".xpi")) {
                // An extension: add it instead of downloading the file.
                runCatching { response.body?.close() }
                Extensions.installFrom(response.uri)
                return
            }
            Downloads.save(response, tab.isPrivate)
        }

        override fun onContextMenu(
            session: GeckoSession,
            screenX: Int,
            screenY: Int,
            element: ContentDelegate.ContextElement,
        ) {
            ContextMenu.show(tab, element)
        }

        override fun onCrash(session: GeckoSession) {
            recover(tab)
        }

        override fun onKill(session: GeckoSession) {
            recover(tab)
        }
    }

    /** The page's process died: reopen the session on the same address. */
    private fun recover(tab: Tab) {
        val url = tab.url
        if (tab.session.isOpen) tab.session.close()
        tab.session.open(Browser.runtime)
        tab.loading = false
        if (UrlText.isWeb(url)) {
            if (Browser.current === tab) tab.session.loadUri(url) else tab.pendingUrl = url
        }
    }

    /** Reads the page's edge colours and, for normal tabs, keeps a small picture for the tab switcher. */
    /**
     * The page has drawn something: lift the cover once it looks like the page we expect.
     * Sites often paint a plain white page first and their real colours a moment later, so with
     * remembered colours we wait (up to ~1.8 s) until the page shows them.
     */
    private fun reveal(tab: Tab, tries: Int = 0) {
        if (!tab.covered) return
        val wantTop = tab.edgeTop
        val wantBottom = tab.edgeBottom
        val view = Browser.host?.geckoViewFor(tab)
        if (view == null || (wantTop == null && wantBottom == null) || tries >= 12) {
            tab.covered = false
            capture(tab, thumbnail = false)
            return
        }
        runCatching {
            view.capturePixels().accept({ bmp: Bitmap? ->
                var top: Int? = null
                var bottom: Int? = null
                if (bmp != null && bmp.width >= 2 && bmp.height >= 4) {
                    val bottomY = (bmp.height - coveredBottom - 3).coerceIn(1, bmp.height - 2)
                    top = runCatching { edgeColor(bmp, 1) }.getOrNull()
                    bottom = runCatching { edgeColor(bmp, bottomY) }.getOrNull()
                }
                Browser.main.post {
                    if (!tab.covered) return@post
                    if (near(top, wantTop) || near(bottom, wantBottom)) {
                        tab.covered = false
                        capture(tab, thumbnail = false)
                    } else {
                        Browser.main.postDelayed({ reveal(tab, tries + 1) }, 150)
                    }
                }
            }, { Browser.main.post { tab.covered = false } })
        }.onFailure { tab.covered = false }
    }

    private fun near(a: Int?, b: Int?): Boolean {
        if (a == null || b == null) return false
        return (0..2).all { i ->
            val shift = i * 8
            kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 28
        }
    }

    fun capture(tab: Tab, thumbnail: Boolean = true) {
        // Still covered: the page hasn't really drawn yet, keep the remembered colours.
        if (tab.covered) return
        val view = Browser.host?.geckoViewFor(tab) ?: return
        runCatching {
            view.capturePixels().accept({ bmp: Bitmap? ->
                if (bmp == null || bmp.width < 2 || bmp.height < 4) return@accept
                val top = runCatching { edgeColor(bmp, 1) }.getOrNull()
                // The page's own bottom, just above the part the bar covers (the engine leaves that unpainted).
                val bottomY = (bmp.height - coveredBottom - 3).coerceIn(1, bmp.height - 2)
                val bottom = runCatching { edgeColor(bmp, bottomY) }.getOrNull()
                val topKnown = top != null
                val flat = runCatching { isFlat(bmp) }.getOrDefault(false)
                // No pictures of private pages, not even in memory: only the two colours.
                val small = if (thumbnail && !tab.isPrivate) {
                    val w = 360
                    val h = (bmp.height * w / bmp.width).coerceAtLeast(1)
                    Bitmap.createScaledBitmap(bmp, w, h, true)
                } else {
                    null
                }
                Browser.main.post {
                    tab.lastFlat = flat
                    // A busy edge (a picture, not a header bar): plain page colour instead.
                    tab.edgeTop = if (topKnown) top else null
                    if (bottom != null) tab.edgeBottom = bottom
                    if (small != null) {
                        tab.thumbnail = small.asImageBitmap()
                        // Kept for next time the app opens (normal tabs only; private ones never get here).
                        com.verisonder.sonderweb.data.Thumbs.save(Browser.app, tab.url, small)
                    }
                }
            }, { })
        }
    }

    /**
     * Back in the app: if a page that had something on it now shows one flat colour, the engine
     * lost its drawing while you were away (it stays empty until a refresh), so it's reloaded.
     */
    fun checkPainted(tab: Tab) {
        if (tab.covered || tab.lastFlat != false || !UrlText.isWeb(tab.url)) return
        val view = Browser.host?.geckoViewFor(tab) ?: return
        runCatching {
            view.capturePixels().accept({ bmp: Bitmap? ->
                if (bmp == null || bmp.width < 2 || bmp.height < 4) return@accept
                val flat = runCatching { isFlat(bmp) }.getOrDefault(false)
                Browser.main.post {
                    if (flat && Browser.current === tab && tab.lastFlat == false && tab.session.isOpen) {
                        tab.lastFlat = null
                        tab.session.reload()
                    }
                }
            }, { })
        }
    }

    /** One colour everywhere (a grid of points over the page, leaving out the part the bar covers). */
    private fun isFlat(bmp: Bitmap): Boolean {
        val bottom = (bmp.height - coveredBottom - 4).coerceIn(2, bmp.height - 1)
        val first = bmp.getPixel(bmp.width / 2, bottom / 2)
        fun near(a: Int, b: Int): Boolean {
            val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
            val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db) <= 12
        }
        for (gy in 1..11) {
            val y = (bottom * gy / 12).coerceIn(0, bmp.height - 1)
            for (gx in 1..7) {
                if (!near(bmp.getPixel((bmp.width - 1) * gx / 8, y), first)) return false
            }
        }
        return true
    }

    /** The colour most of a pixel row has (a few points across it), fully opaque. */
    private fun edgeColor(bmp: Bitmap, y: Int): Int? {
        val points = (1..15).map { i -> bmp.getPixel((bmp.width - 1) * i / 16, y) or 0xFF000000.toInt() }
        // Near-identical shades count as one; the colour must cover most of the row.
        val groups = points.groupBy { it and 0xFFF8F8F8.toInt() }
        val best = groups.maxByOrNull { it.value.size }!!.value
        return if (best.size * 10 >= points.size * 7) best.first() else null
    }
}
