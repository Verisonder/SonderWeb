package com.verisonder.sonderweb.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.verisonder.sonderweb.core.AddressInput
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.Db
import com.verisonder.sonderweb.data.Prefs
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController

/**
 * The browser: one engine, the tabs, and everything the UI shows about them.
 * Lives as long as the app process; the activity only displays it.
 */
object Browser {
    lateinit var app: Context
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var db: Db
        private set
    lateinit var runtime: GeckoRuntime
        private set

    val main = Handler(Looper.getMainLooper())

    val tabs = mutableStateListOf<Tab>()
    var current by mutableStateOf<Tab?>(null)
        private set

    /** Set by the activity while it is shown: things that need a screen (pickers, other apps). */
    var host: Host? = null

    private var nextId = 1L
    private var started = false

    private val stripParams = arrayOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "igshid", "mc_eid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id", "__s",
        "vero_id", "wickedid", "rb_clickid", "s_cid", "_openstat", "srsltid",
    )

    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        prefs = Prefs(app)
        db = Db(app)

        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(antiTracking())
            .enhancedTrackingProtectionLevel(etpLevel())
            .strictSocialTrackingProtection(prefs.strictTracking)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .queryParameterStrippingStripList(*stripParams)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .contentBlocking(blocking)
            .aboutConfigEnabled(true)
            .consoleOutput(false)
            .remoteDebuggingEnabled(false)
            .doubleTapZoomingEnabled(true)
            .forceUserScalableEnabled(true)
            .globalPrivacyControlEnabled(true)
            .loginAutofillEnabled(false)
            .webManifest(true)
            .extensionsProcessEnabled(true)
            .preferredColorScheme(colorScheme())
            .allowInsecureConnections(insecureMode())
            .build()

        Bar.setMode(prefs.barHide)
        runtime = GeckoRuntime.create(app, settings)
        Ublock.install(runtime)
        restoreTabs()
    }

    // ---- Settings that apply live ----

    private fun antiTracking() =
        if (prefs.strictTracking) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT

    private fun etpLevel() =
        if (prefs.strictTracking) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT

    private fun colorScheme() = when (prefs.theme) {
        1 -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        2 -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
    }

    private fun insecureMode() =
        if (prefs.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL

    fun applySettings() {
        val s = runtime.settings
        s.contentBlocking
            .setAntiTracking(antiTracking())
            .setEnhancedTrackingProtectionLevel(etpLevel())
        s.setPreferredColorScheme(colorScheme())
        s.setAllowInsecureConnections(insecureMode())
    }

    // ---- Tabs ----

    /**
     * Opens a tab. [open] is false only when the engine asked for the tab (a link with
     * target=_blank, or an extension): it then opens the session itself.
     */
    fun newTab(
        url: String?,
        isPrivate: Boolean,
        select: Boolean = true,
        opener: Tab? = null,
        open: Boolean = true,
        lazy: Boolean = false,
    ): Tab {
        val tab = Tab(nextId++, isPrivate)
        tab.openerId = opener?.id
        Delegates.attach(tab)
        val at = opener?.let { tabs.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: tabs.size
        tabs.add(at, tab)
        if (open) {
            tab.session.open(runtime)
            tab.ready = true
            if (!url.isNullOrBlank()) {
                if (lazy) tab.pendingUrl = url else tab.session.loadUri(url)
                tab.url = url
            }
        } else {
            waitUntilOpen(tab)
        }
        if (select) select(tab)
        saveTabs()
        return tab
    }

    fun select(tab: Tab) {
        val old = current
        if (old === tab) return
        if (old != null && old.session.isOpen) {
            old.session.setActive(false)
            runCatching { runtime.webExtensionController.setTabActive(old.session, false) }
        }
        current = tab
        Bar.show()
        if (tab.ready) activate(tab)
        saveTabs()
    }

    /** The engine opens sessions it asked for itself, a moment after we hand them over. */
    private fun waitUntilOpen(tab: Tab, tries: Int = 0) {
        main.postDelayed({
            when {
                !tabs.contains(tab) -> Unit
                tab.session.isOpen -> {
                    tab.ready = true
                    if (current === tab) activate(tab)
                }
                tries < 100 -> waitUntilOpen(tab, tries + 1)
            }
        }, 30)
    }

    private fun activate(tab: Tab) {
        tab.session.setActive(true)
        runCatching { runtime.webExtensionController.setTabActive(tab.session, true) }
        tab.pendingUrl?.let {
            tab.pendingUrl = null
            tab.session.loadUri(it)
        }
    }

    fun close(tab: Tab) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        tabs.removeAt(i)
        Prompts.queue.removeAll { it.tab === tab }
        Bar.forget(tab)
        if (current === tab) {
            current = null
            val next = tabs.firstOrNull { it.id == tab.openerId && it.isPrivate == tab.isPrivate }
                ?: tabs.getOrNull(i.coerceAtMost(tabs.size - 1))
                ?: tabs.lastOrNull()
            if (next != null) select(next)
        }
        if (tab.session.isOpen) tab.session.close()
        if (tabs.isEmpty()) newTab(null, isPrivate = false)
        saveTabs()
    }

    fun closeAll(isPrivate: Boolean) {
        tabs.filter { it.isPrivate == isPrivate }.forEach { close(it) }
    }

    fun privateTabsOpen(): Boolean = tabs.any { it.isPrivate }

    /** Address-bar submit: an address, a search or a !bang. */
    fun go(input: String, tab: Tab? = current) {
        val target = AddressInput.resolve(input, prefs.engine()) ?: return
        val t = tab ?: newTab(null, isPrivate = false)
        load(t, target.url)
    }

    fun load(tab: Tab, url: String) {
        tab.url = url
        tab.loading = true
        if (tab.ready) tab.session.loadUri(url) else tab.pendingUrl = url
        if (current !== tab) select(tab)
    }

    /** Home: the start page in this tab (back returns to the page you were on). */
    fun home(tab: Tab) {
        if (tab.isBlank) return
        tab.url = ""
        tab.title = ""
        tab.loading = false
        tab.pendingUrl = null
        if (tab.ready) tab.session.loadUri("about:blank")
    }

    fun setDesktop(tab: Tab, on: Boolean) {
        tab.desktop = on
        val s = tab.session.settings
        s.setUserAgentMode(
            if (on) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE,
        )
        s.setViewportMode(
            if (on) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
        )
        tab.session.reload()
    }

    // ---- Persistence (normal tabs only) ----

    fun saveTabs() {
        if (!started) return
        val normal = tabs.filter { !it.isPrivate }
        prefs.savedTabs = normal.map { it.pendingUrl ?: it.url }.filter { UrlText.isWeb(it) }
        prefs.savedTabIndex = normal.indexOf(current).coerceAtLeast(0)
    }

    private fun restoreTabs() {
        val urls = if (prefs.restoreTabs) prefs.savedTabs else emptyList()
        if (urls.isEmpty()) {
            newTab(null, isPrivate = false)
            return
        }
        val index = prefs.savedTabIndex.coerceIn(0, urls.size - 1)
        urls.forEach { newTab(it, isPrivate = false, select = false, lazy = true) }
        select(tabs[index])
    }

    // ---- Clearing ----

    fun clearData(flags: Long, done: () -> Unit) {
        runtime.storageController.clearData(flags).accept({ main.post(done) }, { main.post(done) })
    }

    /** Every trace of browsing: history, downloads list, cookies, cache and site data. */
    fun clearEverything(done: () -> Unit) {
        db.clearHistory()
        db.clearDownloads()
        clearData(StorageController.ClearFlags.ALL, done)
    }

    /** Things that need the activity. */
    interface Host {
        /** The engine view, when it is currently showing [tab]. */
        fun geckoViewFor(tab: Tab): org.mozilla.geckoview.GeckoView?
        fun openExternal(url: String): Boolean
        fun share(text: String, title: String?)
        fun setFullscreen(on: Boolean)
        fun applySystemBars()
        fun requestAndroidPermissions(perms: Array<String>, result: (Boolean) -> Unit)
        fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, result: (List<android.net.Uri>) -> Unit)

        /** [type] is a PromptDelegate.DateTimePrompt.Type; values are HTML input formats. */
        fun pickDateTime(type: Int, value: String?, min: String?, max: String?, result: (String?) -> Unit)
        fun toast(text: String, action: String? = null, onAction: (() -> Unit)? = null)
    }
}
