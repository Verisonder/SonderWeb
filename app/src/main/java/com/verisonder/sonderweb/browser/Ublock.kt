package com.verisonder.sonderweb.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/**
 * uBlock Origin, shipped inside the app as a built-in extension (unpacked into
 * assets/extensions/ublock at build time). Its popup opens in a sheet.
 */
object Ublock {
    const val ID = "uBlock0@raymondhill.net"
    private const val LOCATION = "resource://android/assets/extensions/ublock/"

    var extension by mutableStateOf<WebExtension?>(null)
        private set

    /** The popup's engine session while the sheet is open. */
    var popup by mutableStateOf<GeckoSession?>(null)
        private set

    /** The latest toolbar action per tab (it carries the tab's blocked count). */
    private val actions = HashMap<Long, WebExtension.Action>()
    private var defaultAction: WebExtension.Action? = null

    fun install(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, ID).accept({ ext ->
            if (ext == null) return@accept
            Browser.main.post { ready(runtime, ext) }
        }, { _ -> })
    }

    private fun ready(runtime: GeckoRuntime, ext: WebExtension) {
        extension = ext
        runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
        ext.setActionDelegate(actionDelegate(null))
        ext.setTabDelegate(tabDelegate)
        Browser.tabs.forEach { attach(it) }
    }

    /** Hooks a tab's session up to uBlock Origin (its badge, and tabs it closes). */
    fun attach(tab: Tab) {
        val ext = extension ?: return
        val c = tab.session.webExtensionController
        c.setActionDelegate(ext, actionDelegate(tab))
        c.setTabDelegate(ext, object : WebExtension.SessionTabDelegate {
            override fun onCloseTab(source: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
                Browser.close(tab)
                return GeckoResult.allow()
            }

            override fun onUpdateTab(
                extension: WebExtension,
                session: GeckoSession,
                updateDetails: WebExtension.UpdateTabDetails,
            ): GeckoResult<AllowOrDeny> {
                if (updateDetails.active == true) Browser.select(tab)
                updateDetails.url?.let { Browser.load(tab, it) }
                return GeckoResult.allow()
            }
        })
    }

    private fun actionDelegate(tab: Tab?) = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (tab == null) {
                defaultAction = action
            } else {
                val full = defaultAction?.let { action.withDefault(it) } ?: action
                actions[tab.id] = full
                tab.blockedBadge = full.badgeText ?: ""
            }
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopupSession()

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopupSession()
    }

    private fun openPopupSession(): GeckoResult<GeckoSession> {
        popup?.let { if (it.isOpen) it.close() }
        val s = GeckoSession()
        s.open(Browser.runtime)
        popup = s
        return GeckoResult.fromValue(s)
    }

    /** Opens uBlock Origin's panel for the current tab. */
    fun showPopup(tab: Tab) {
        val action = actions[tab.id] ?: defaultAction ?: return
        action.click()
    }

    fun closePopup() {
        val s = popup ?: return
        popup = null
        if (s.isOpen) s.close()
    }

    /** uBlock Origin's settings (filter lists, my filters …) in a new tab. */
    fun openDashboard() {
        val ext = extension ?: return
        val url = ext.metaData.optionsPageUrl ?: (ext.metaData.baseUrl + "dashboard.html")
        Browser.newTab(url, isPrivate = false)
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(
            source: WebExtension,
            createDetails: WebExtension.CreateTabDetails,
        ): GeckoResult<GeckoSession>? {
            closePopup()
            val opener = Browser.current
            val t = Browser.newTab(
                null,
                isPrivate = opener?.isPrivate ?: false,
                select = createDetails.active != false,
                opener = opener,
                open = false,
            )
            t.url = createDetails.url ?: ""
            return GeckoResult.fromValue(t.session)
        }

        override fun onOpenOptionsPage(source: WebExtension) {
            closePopup()
            openDashboard()
        }
    }
}
