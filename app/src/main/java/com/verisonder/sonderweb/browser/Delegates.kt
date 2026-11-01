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
            tab.url = if (url == null || url == "about:blank") "" else url
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
            if (UrlText.isInternal(uri)) return GeckoResult.allow()
            // tel:, mailto:, intent:, market: … belong to other apps. Only when you tapped
            // something: pages may not launch apps on their own.
            if (!request.hasUserGesture && !request.isDirectNavigation) return GeckoResult.deny()
            val opened = Browser.host?.openExternal(uri) ?: false
            if (!opened) Browser.host?.toast("No app can open this link")
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
            tab.progress = 5
            tab.url = url
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            tab.loading = false
            tab.progress = 100
            if (success && !tab.isPrivate && Browser.prefs.saveHistory && UrlText.isWeb(tab.url)) {
                Browser.db.visit(tab.url, tab.title)
            }
            capture(tab)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            tab.progress = progress
        }

        override fun onSecurityChange(session: GeckoSession, securityInfo: ProgressDelegate.SecurityInformation) {
            tab.secure = securityInfo.isSecure
        }
    }

    private fun content(tab: Tab) = object : ContentDelegate {
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

    /** A small picture of the page for the tab switcher. */
    fun capture(tab: Tab) {
        // No pictures of private pages, not even in memory.
        if (tab.isPrivate) return
        val view = Browser.host?.geckoViewFor(tab) ?: return
        runCatching {
            view.capturePixels().accept({ bmp: Bitmap? ->
                if (bmp != null) {
                    val w = 360
                    val h = (bmp.height * w / bmp.width.coerceAtLeast(1)).coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(bmp, w, h, true)
                    Browser.main.post { tab.thumbnail = small.asImageBitmap() }
                }
            }, { })
        }
    }
}
