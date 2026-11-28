package com.verisonder.sonderweb.browser

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.verisonder.sonderweb.core.AddressInput
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.Db
import com.verisonder.sonderweb.data.Profiles
import com.verisonder.sonderweb.data.Prefs
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
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
    /** The history of the profile in use (the first profile's, when history is shared). */
    val db: Db
        get() = if (prefs.splitHistory) dbOf(Profiles.current) else mainDb

    /** Where a tab's visits go: its own profile's history (shared tabs can be another profile's). */
    fun historyOf(tab: Tab): Db = if (prefs.splitHistory) dbOf(tab.profile) else mainDb

    /** The bookmarks of the profile in use (the first profile's, when bookmarks are shared). */
    val marks: Db
        get() = if (prefs.splitBookmarks) dbOf(Profiles.current) else mainDb

    /** Address-bar suggestions: bookmarks first, then history (each from where it's kept). */
    fun suggest(query: String, limit: Int = 6): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()
        val seen = LinkedHashMap<String, String>()
        if (prefs.suggestBookmarks) marks.bookmarks(query).take(limit).forEach { seen[it.url] = it.title }
        if (prefs.suggestHistory) db.history(query, limit * 2).forEach { if (seen.size < limit) seen.putIfAbsent(it.url, it.title) }
        return seen.entries.take(limit).map { it.key to it.value }
    }

    /** The first profile's: Firefox Sync follows it, and it keeps the downloads list. */
    lateinit var mainDb: Db
        private set

    private val dbs = HashMap<String, Db>()

    private fun dbOf(profile: String): Db =
        if (profile.isEmpty()) mainDb else dbs.getOrPut(profile) { Db(app, Profiles.dbName(profile)) }
    lateinit var runtime: GeckoRuntime
        private set

    val main = Handler(Looper.getMainLooper())

    val tabs = mutableStateListOf<Tab>()

    /** Private tabs are hidden behind your fingerprint / screen lock after you leave the app. */
    var privateLocked by mutableStateOf(false)

    /** Settings › Privacy: allow screenshots of private tabs. Off by default. */
    var privateScreenshots by mutableStateOf(false)
    var current by mutableStateOf<Tab?>(null)
        private set

    /**
     * The tab whose video plays in the small floating window while you're in another tab.
     * Its page stays active; tapping the window goes back to it, × stops it.
     */
    var mini by mutableStateOf<Tab?>(null)

    /** The tab on screen whose floating window you closed while a panel covers it. */
    var miniStopped by mutableStateOf<Tab?>(null)

    /** A video is playing here and picture-in-picture is on: another page shouldn't replace it. */
    fun keepsPlaying(tab: Tab?): Boolean = tab != null && prefs.pip && tab.playing && !tab.isBlank

    /** Set by the activity while it is shown: things that need a screen (pickers, other apps). */
    var host: Host? = null

    private var nextId = 1L
    var started = false
        private set

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
        mainDb = Db(app)
        mainDb.interruptRunning()
        Profiles.load(app, prefs)
        privateScreenshots = prefs.privateScreenshots
        privateLocked = prefs.privateLock

        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(antiTracking())
            .enhancedTrackingProtectionLevel(etpLevel())
            .strictSocialTrackingProtection(prefs.strictTracking)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            // Private tabs: no cross-site cookies at all.
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
            // Trackers' cookies are purged regularly; redirect "bounce" trackers are cleared too.
            .cookiePurging(true)
            .bounceTrackingProtectionMode(ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED)
            .emailTrackerBlockingPrivateMode(true)
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
            // Lets addons.mozilla.org's "Add to Firefox" buttons install extensions here.
            .extensionsWebAPIEnabled(true)
            .preferredColorScheme(colorScheme())
            .allowInsecureConnections(insecureMode())
            .build()

        Bar.setMode(prefs.barHide)
        runtime = GeckoRuntime.create(app, settings)
        applyHardening()
        Extensions.setup()
        Ublock.install(runtime)
        Look.install(runtime)
        AddonsSite.install(runtime)
        YoutubeBar.install(runtime)
        Shields.install(runtime)
        Pip.install(runtime)
        GoogleAccount.install(runtime)
        // SonderWeb Sync: Firefox Sync, if you turned it on (nothing of it loads otherwise).
        if (com.verisonder.sonderweb.BuildConfig.FIREFOX_SYNC) {
            FxaChannel.install(runtime)
            com.verisonder.sonderweb.sync.FxSync.init(app)
            Passwords.applyOption()
            Vpn.install(app, runtime)
        }
        restoreTabs()
    }

    // ---- Settings that apply live ----

    private fun antiTracking() =
        if (prefs.strictTracking) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT

    private fun etpLevel() =
        if (prefs.strictTracking) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT

    private fun colorScheme() = when (prefs.theme) {
        1 -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        2, 3 -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
    }

    private fun insecureMode() =
        // Private tabs always insist on encrypted connections, like Brave's.
        if (prefs.httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE

    /** Encrypted DNS providers for Settings › Privacy (index matches [Prefs.secureDns]). */
    val dnsProviders = listOf(
        "Off" to "",
        "Quad9" to "https://dns.quad9.net/dns-query",
        "Mullvad" to "https://dns.mullvad.net/dns-query",
        "Cloudflare" to "https://mozilla.cloudflare-dns.com/dns-query",
    )

    /** Protections on top of tracking protection and uBlock Origin. */
    private fun applyHardening() {
        val s = runtime.settings
        runCatching {
            // Fingerprinting: sites see slightly randomised details, like Brave's.
            s.setBaselineFingerprintingProtection(true)
            s.setFingerprintingProtection(prefs.blockFingerprinting)
            s.setFingerprintingProtectionPrivateBrowsing(true)
        }
        runCatching {
            // Sites on the internet can't probe your router or other devices on your network.
            s.setLnaEnabled(true)
            s.setLnaBlocking(true)
            s.setLnaBlockTrackers(true)
        }
        runCatching { s.setPostQuantumKeyExchangeEnabled(true) }
        runCatching { s.setLoginAutofillEnabled(false) }
        applyDns()
    }

    private var dnsInUse = -1

    /**
     * Encrypted DNS: your choice in Settings for normal tabs; private tabs always use Quad9
     * (or your chosen provider), switched on while you're in them and back off when you leave.
     */
    fun applyDns() {
        val s = runtime.settings
        val choice = prefs.secureDns
        val want = if (current?.isPrivate == true && choice == 0) 1 else choice
        if (want == dnsInUse) return
        dnsInUse = want
        runCatching {
            val uri = dnsProviders.getOrNull(want)?.second.orEmpty()
            if (uri.isEmpty()) {
                s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
            } else {
                s.setTrustedRecursiveResolverUri(uri)
                s.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_ONLY)
            }
        }
    }

    fun applySettings() {
        applyHardening()
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
        profile: String = Profiles.current,
        state: GeckoSession.SessionState? = null,
    ): Tab {
        val tab = Tab(nextId++, isPrivate, if (isPrivate) "" else profile)
        tab.openerId = opener?.id
        Delegates.attach(tab)
        val at = opener?.let { tabs.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: tabs.size
        tabs.add(at, tab)
        if (open) {
            tab.session.open(runtime)
            tab.ready = true
            if (state != null && !url.isNullOrBlank()) {
                // A closed tab coming back: its pages back and forward too.
                tab.loading = true
                tab.session.restoreState(state)
                tab.url = url
            } else if (!url.isNullOrBlank()) {
                if (lazy) {
                    tab.pendingUrl = url
                } else {
                    tab.loading = true
                    tab.session.loadUri(url)
                }
                tab.url = url
            }
        } else {
            waitUntilOpen(tab)
        }
        if (select) select(tab)
        if (url.isNullOrBlank() && open) dropSpareBlanks(tab)
        saveTabs()
        return tab
    }

    fun select(tab: Tab) {
        val old = current
        if (old === tab) return
        // Leaving a tab that's playing a video: it carries on in the floating window.
        val toMini = old != null && old !== tab && keepsPlaying(old)
        if (old != null && old.session.isOpen && !toMini) {
            old.session.setActive(false)
            runCatching { runtime.webExtensionController.setTabActive(old.session, false) }
        }
        if (toMini) {
            mini?.takeIf { it !== old }?.let { closeMini(it) }
            mini = old
        }
        if (mini === tab) mini = null
        // Leaving the private tabs locks them again: coming back needs your fingerprint.
        if (old?.isPrivate == true && !tab.isPrivate && prefs.privateLock) privateLocked = true
        current = tab
        tab.lastUsed = System.currentTimeMillis()
        if (old?.isPrivate != tab.isPrivate) applyDns()
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
            // Loading from now on: the session's own empty first page isn't the start page.
            tab.loading = true
            tab.session.loadUri(it)
        }
    }

    fun close(tab: Tab) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        tabs.removeAt(i)
        if (mini === tab) mini = null
        Prompts.queue.removeAll { it.tab === tab }
        Bar.forget(tab)
        BackPeek.forget(tab)
        Pip.forget(tab)
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

    // ---- Closing with Undo, and recently closed tabs ----

    /** A tab you closed: enough to bring it back where it was. */
    class Closed(
        val url: String,
        val title: String,
        val isPrivate: Boolean,
        val profile: String,
        val index: Int,
        val openerId: Long?,
        val state: GeckoSession.SessionState?,
        val wasCurrent: Boolean,
    )

    /** Changes whenever the recently closed list does (the start page follows it). */
    var closedVersion by mutableIntStateOf(0)
        private set

    private fun closedOf(tab: Tab) = Closed(
        tab.pendingUrl ?: tab.url, tab.title, tab.isPrivate, tab.profile, tabs.indexOf(tab),
        tab.openerId, tab.state, tab === current,
    )

    /** Keeps a closed normal tab for the start page's "Recently closed" (newest first, 12 at most). */
    private fun keepClosed(c: Closed) {
        if (c.isPrivate || !UrlText.isWeb(c.url)) return
        val list = prefs.closedTabs.filterNot { it.url == c.url && it.profile == c.profile }
        prefs.closedTabs = (listOf(com.verisonder.sonderweb.data.ClosedTab(c.url, c.title, c.profile)) + list).take(12)
        closedVersion++
    }

    /** Recently closed normal tabs of the profile in use. */
    fun recentlyClosed(): List<com.verisonder.sonderweb.data.ClosedTab> =
        prefs.closedTabs.filter { it.profile == Profiles.current }

    fun forgetClosed() {
        prefs.closedTabs = emptyList()
        closedVersion++
    }

    /** You closed tabs (tab list, swipe, Close all): they close, and a notice offers Undo. */
    fun closeByUser(closing: List<Tab>) {
        val gone = closing.filter { it in tabs && !it.isBlank }.map { closedOf(it) }
        closing.forEach { close(it) }
        if (gone.isEmpty()) return
        gone.forEach { keepClosed(it) }
        val text = if (gone.size == 1) "Tab closed" else "${gone.size} tabs closed"
        host?.toast(text, "Undo") {
            // Back in their old places, in order; the one you were on is selected again.
            gone.sortedBy { it.index }.forEach { reopen(it) }
        }
    }

    fun reopen(c: Closed) {
        val tab = newTab(c.url, c.isPrivate, select = c.wasCurrent, profile = c.profile, state = c.state)
        tab.openerId = c.openerId?.takeIf { id -> tabs.any { it.id == id } }
        val from = tabs.indexOf(tab)
        val to = c.index.coerceIn(0, tabs.size - 1)
        if (from >= 0 && from != to) {
            tabs.removeAt(from)
            tabs.add(to, tab)
        }
        if (!c.isPrivate) {
            prefs.closedTabs = prefs.closedTabs.filterNot { it.url == c.url && it.profile == c.profile }
            closedVersion++
        }
        saveTabs()
    }

    /** From the start page's "Recently closed": opens it in the tab on screen (the start page). */
    fun reopenClosed(t: com.verisonder.sonderweb.data.ClosedTab, into: Tab) {
        prefs.closedTabs = prefs.closedTabs.filterNot { it.url == t.url && it.profile == t.profile }
        closedVersion++
        load(into, t.url)
    }

    /** The site a tab is on (example.com for www.example.com). */
    fun siteOf(url: String?): String = siteKey(UrlText.host(url))

    private fun siteKey(host: String): String {
        val parts = host.removePrefix("www.").split('.')
        if (parts.size <= 2) return parts.joinToString(".")
        // example.co.uk, example.com.au: three parts make the site.
        val second = parts[parts.size - 2]
        val keep = if (second.length <= 3 && second in setOf("co", "com", "org", "net", "gov", "ac", "edu", "ne", "or", "go")) 3 else 2
        return parts.takeLast(keep).joinToString(".")
    }

    /**
     * "Shred site data", like Brave's: the site's tabs close and its cookies, storage and cache
     * are deleted at once (history and bookmarks stay).
     */
    fun shred(site: String) {
        if (site.isEmpty()) return
        val open = tabs.filter { !it.isPrivate && siteOf(it.url) == site }
        val hosts = open.map { UrlText.host(it.url) }.toSet()
        open.forEach { close(it) }
        val flags = org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA
        runCatching { runtime.storageController.clearDataFromBaseDomain(site, flags) }
        hosts.forEach { h -> runCatching { runtime.storageController.clearDataFromHost(h, flags) } }
        // Then the start page: an empty tab if there is one, else a new one.
        val blank = tabs.lastOrNull { !it.isPrivate && it.isBlank }
        if (blank != null) select(blank) else newTab(null, isPrivate = false)
    }

    /**
     * A new empty tab replaces the other empty ones (same kind, not in front, nothing to go back
     * or forward to). They aren't listed, so they shouldn't pile up behind the tab count.
     */
    private fun dropSpareBlanks(keep: Tab) {
        tabs.filter {
            it !== keep && it !== current && it !== mini && it.isPrivate == keep.isPrivate &&
                it.isBlank && !it.canGoBack && !it.canGoForward
        }.forEach { close(it) }
    }

    /** How many tabs the tabs button shows: the ones the tab list's grid shows (empty and inactive tabs aren't). */
    fun countFor(isPrivate: Boolean): Int {
        val inactive = if (isPrivate) emptySet() else inactiveTabs().toHashSet()
        return tabs.count { it.isPrivate == isPrivate && !it.isBlank && it !in inactive }
    }

    fun closeAll(isPrivate: Boolean) {
        tabs.filter { it.isPrivate == isPrivate }.forEach { close(it) }
    }

    /**
     * Inactive, like Brave's: normal tabs not used for [INACTIVE_DAYS] days, and older copies of
     * a page that is open in another tab. Never the current tab.
     */
    fun inactiveTabs(): List<Tab> {
        val cutoff = System.currentTimeMillis() - INACTIVE_DAYS * DAY
        val normal = tabs.filter { !it.isPrivate && it !== current }
        val newest = HashMap<String, Tab>()
        tabs.filter { !it.isPrivate && !it.isBlank }.forEach { t ->
            val key = t.pendingUrl ?: t.url
            val n = newest[key]
            if (n == null || t === current || (n !== current && t.lastUsed > n.lastUsed)) newest[key] = t
        }
        return normal.filter { t ->
            if (t.isBlank) return@filter false
            val key = t.pendingUrl ?: t.url
            t.lastUsed < cutoff || newest[key] !== t
        }
    }

    const val INACTIVE_DAYS = 14

    private const val DAY = 24L * 60 * 60 * 1000

    /**
     * Settings › Tabs › Close inactive tabs: inactive tabs left alone for that many more days are
     * closed (old tabs: [INACTIVE_DAYS] + that; older copies of an open page: that since last used).
     * Runs when the app starts and each time you come back to it.
     */
    fun closeOldInactive() {
        val days = prefs.inactiveCloseDays
        if (days <= 0) return
        val now = System.currentTimeMillis()
        val old = now - (INACTIVE_DAYS + days) * DAY
        val copy = now - days * DAY
        val cutoff = now - INACTIVE_DAYS * DAY
        inactiveTabs()
            .filter { it !== mini && it !== current && (it.lastUsed < old || (it.lastUsed >= cutoff && it.lastUsed < copy)) }
            .forEach { close(it) }
    }

    fun privateTabsOpen(): Boolean = tabs.any { it.isPrivate }

    /** You left the app (home, recents, screen off): lock or close the private tabs. */
    fun leftApp() {
        prefs.leftAt = System.currentTimeMillis()
        if (!privateTabsOpen()) return
        when {
            prefs.privateCloseOnLeave -> {
                val wasPrivate = current?.isPrivate == true
                closeAll(true)
                if (wasPrivate) tabs.lastOrNull { !it.isPrivate }?.let { select(it) }
            }
            prefs.privateLock -> privateLocked = true
        }
    }

    /**
     * Whether opening the app now should show the start page (Settings › Tabs). [fresh]: the app
     * is opening after being closed, not just coming back to the front: "Start page" means only
     * then; "after a while" also when you come back after being away that long.
     */
    fun startPageDue(fresh: Boolean): Boolean = when (prefs.openOnLaunch) {
        1 -> fresh
        2 -> fresh || System.currentTimeMillis() - prefs.leftAt >= prefs.startPageAfter * 60_000L
        else -> false
    }

    /** Back in the app: the start page instead of the last site, if that's your choice. Your
     *  tabs stay as they were; an empty tab is reused rather than adding another one. */
    fun openStartPageIfDue(fresh: Boolean) {
        if (!startPageDue(fresh)) return
        val cur = current
        if (cur != null && cur.isBlank && !cur.isPrivate) return
        val blank = tabs.lastOrNull { !it.isPrivate && it.isBlank }
        if (blank != null) select(blank) else newTab(null, isPrivate = false)
    }

    /** Address-bar submit: an address, a search or a !bang. */
    fun go(input: String, tab: Tab? = current) {
        val target = AddressInput.resolve(input, prefs.engine()) ?: return
        val t = tab ?: newTab(null, isPrivate = false)
        load(t, target.url)
    }

    fun load(tab: Tab, url: String) {
        // A video is playing here: the new page opens in its own tab and the video keeps going
        // in the floating window (back from there returns to the video).
        if (keepsPlaying(tab)) {
            newTab(url, isPrivate = tab.isPrivate, opener = tab)
            return
        }
        tab.url = url
        tab.loading = true
        if (tab.ready) tab.session.loadUri(url) else tab.pendingUrl = url
        if (current !== tab) select(tab)
    }

    /** The tab that opened this one (a link that opened in a new tab), if it is still open. */
    fun openerOf(tab: Tab): Tab? =
        tab.openerId?.let { id -> tabs.firstOrNull { it.id == id && it.isPrivate == tab.isPrivate && it !== tab } }

    /**
     * Back with nothing left to go back to in this tab: a tab another site opened closes and
     * that site comes back, like Chrome. Otherwise the start page.
     */
    fun backPastStart(tab: Tab) {
        if (openerOf(tab) != null) close(tab) else home(tab)
    }

    /** The home button: the start page, in a new tab when a video is playing here. */
    fun goHome(tab: Tab?) {
        if (tab == null) return
        if (keepsPlaying(tab)) newTab(null, isPrivate = tab.isPrivate, opener = tab) else home(tab)
    }

    /** The floating window's ×: its video stops, and the page sleeps like any tab in the back. */
    fun closeMini(tab: Tab? = mini) {
        val t = tab ?: return
        if (mini === t) mini = null
        if (current === t) miniStopped = t
        Pip.pauseTab(t)
        if (current !== t && t.session.isOpen) {
            t.session.setActive(false)
            runCatching { runtime.webExtensionController.setTabActive(t.session, false) }
        }
    }

    /** Home: the start page in this tab (back returns to the page you were on). */
    fun home(tab: Tab) {
        if (tab.isBlank) return
        tab.url = ""
        tab.title = ""
        tab.loading = false
        tab.pendingUrl = null
        tab.covered = true
        tab.startedPage = false
        if (tab.ready) tab.session.loadUri("about:blank")
    }

    fun setDesktop(tab: Tab, on: Boolean) {
        tab.desktop = on
        if (on) tab.hostBeforeDesktop = UrlText.host(tab.url)
        val s = tab.session.settings
        s.setUserAgentMode(
            if (on) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE,
        )
        s.setViewportMode(
            if (on) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE,
        )
        // Back to mobile: sites that moved you to their desktop address (m.youtube.com →
        // youtube.com) remember it and keep sending the desktop page, which then shows zoomed
        // in and cut off. So the mobile address you were on is opened again, fresh.
        val before = tab.hostBeforeDesktop
        val now = UrlText.host(tab.url)
        if (!on && before != null && now.isNotEmpty() && now != before && sameSite(before, now)) {
            val back = android.net.Uri.parse(tab.url).buildUpon().authority(before).build().toString()
            tab.session.loadUri(back)
        } else {
            // Fresh from the site, not the copy kept for the other mode.
            tab.session.reload(org.mozilla.geckoview.GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
        }
        if (!on) tab.hostBeforeDesktop = null
    }

    /** The same site, give or take www. or m. (m.youtube.com and youtube.com). */
    private fun sameSite(a: String, b: String): Boolean {
        fun bare(h: String) = h.lowercase().removePrefix("www.").removePrefix("m.")
        return bare(a) == bare(b)
    }

    /**
     * Refresh. The page is also handed to the screen again first: after a while away the
     * surface it draws into can go blank, and reloading alone then just loads into nothing.
     */
    fun refresh(tab: Tab) {
        if (!tab.ready) return
        // The page's process is gone: open it again (that loads the address too).
        if (!tab.session.isOpen) {
            Delegates.recover(tab)
            return
        }
        val view = host?.geckoViewFor(tab)
        if (view != null && view.session === tab.session) {
            view.releaseSession()
            view.setSession(tab.session)
        } else if (current === tab && mini !== tab) {
            // The tab on screen isn't in the engine view at all (it lost it to another view):
            // reloading alone would leave the screen empty.
            host?.mainView()?.let { main ->
                runCatching {
                    main.releaseSession()
                    main.setSession(tab.session)
                }
            }
        }
        tab.checkAfterReload = true
        Unstick.refreshed(tab)
        tab.session.setActive(true)
        tab.session.reload()
    }

    // ---- Persistence (normal tabs only) ----

    fun saveTabs() {
        if (!started) return
        writeTabs(tabs.filter { !it.isPrivate && UrlText.isWeb(it.pendingUrl ?: it.url) })
    }

    /** Saves these tabs as the open tabs (of the profile [Prefs.scope] points at). */
    private fun writeTabs(kept: List<Tab>) {
        prefs.savedTabs = kept.map { it.pendingUrl ?: it.url }
        prefs.savedTabTitles = kept.map { it.title }
        prefs.savedTabUsed = kept.map { it.lastUsed }
        prefs.savedTabProfiles = kept.map { it.profile }
        // Their page colours too, so a reopened tab is covered in its own colours until it draws.
        prefs.savedTabColors = kept.map { it.edgeTop to it.edgeBottom }
        prefs.savedTabIndex = kept.indexOf(current).coerceAtLeast(0)
        // Each tab's picture saved under the address it will reopen with.
        kept.forEach { t ->
            val url = t.pendingUrl ?: t.url
            val pic = t.thumbnail
            if (pic != null && t.thumbUrl != url) {
                com.verisonder.sonderweb.data.Thumbs.save(app, url, pic.asAndroidBitmap())
                t.thumbUrl = url
            }
        }
    }

    /** [fresh]: the app is starting (not switching profiles). */
    private fun restoreTabs(fresh: Boolean = true) {
        val urls = if (prefs.restoreTabs || !fresh) prefs.savedTabs else emptyList()
        if (urls.isEmpty()) {
            newTab(null, isPrivate = false)
            return
        }
        val index = prefs.savedTabIndex.coerceIn(0, urls.size - 1)
        // Opening on the start page: the old tabs come back without loading the last site.
        val startPage = fresh && startPageDue(fresh = true)
        val colors = prefs.savedTabColors
        val titles = prefs.savedTabTitles
        val used = prefs.savedTabUsed
        // Shared tabs come back in their own profiles; separate ones are all this profile's.
        val owners = prefs.savedTabProfiles
        val back = ArrayList<Tab>(urls.size)
        urls.forEachIndexed { i, url ->
            val owner = if (prefs.splitTabs) Profiles.current else owners.getOrNull(i)?.takeIf { id -> Profiles.list.any { it.id == id } } ?: ""
            val tab = newTab(url, isPrivate = false, select = false, lazy = true, profile = owner)
            back += tab
            colors.getOrNull(i)?.let { (top, bottom) ->
                tab.edgeTop = top
                tab.edgeBottom = bottom
            }
            // Its title and picture from last time, so the tab switcher shows the real thing.
            titles.getOrNull(i)?.let { tab.title = it }
            used.getOrNull(i)?.takeIf { it > 0 }?.let { tab.lastUsed = it }
            com.verisonder.sonderweb.data.Thumbs.load(app, url) { bmp ->
                if (tab.thumbnail == null) {
                    tab.thumbnail = bmp.asImageBitmap()
                    tab.thumbUrl = url
                }
            }
        }
        if (fresh) {
            // Pictures of every profile's tabs are kept, not only this one's.
            val all = urls + Profiles.list.filter { it.id != Profiles.current }.flatMap { prefs.savedTabsOf(it.id) }
            com.verisonder.sonderweb.data.Thumbs.keepOnly(app, all)
            com.verisonder.sonderweb.data.Thumbs.dropOld(app)
        }
        if (startPage) {
            newTab(null, isPrivate = false)
            return
        }
        select(back[index])
    }

    // ---- Profiles ----

    /**
     * Switches to another profile: this one's tabs are saved and put away, the other's come back
     * (its own sign-ins, history, bookmarks and shortcuts). Private tabs stay as they are.
     */
    fun switchProfile(id: String) {
        if (id == Profiles.current || Profiles.list.none { it.id == id }) return
        if (!prefs.splitTabs) {
            // Shared tabs: they stay (each in its own profile); a new tab opens in this one.
            Profiles.use(id)
            newTab(null, isPrivate = false)
            return
        }
        saveTabs()
        mini?.let { closeMini(it) }
        mini = null
        miniStopped = null
        val leaving = tabs.filter { !it.isPrivate }
        if (current?.isPrivate != true) current = null
        leaving.forEach { putAway(it) }
        Profiles.use(id)
        restoreTabs(fresh = false)
    }

    /**
     * Settings › Profiles › Open tabs. Shared: every profile's saved tabs join the open ones (each
     * keeps its own profile). Separate: each profile takes its own tabs back; only this one's stay.
     */
    fun changeSplitTabs(separate: Boolean) {
        if (separate == prefs.splitTabs) return
        val here = Profiles.current
        if (!separate) {
            saveTabs()
            Profiles.list.filter { it.id != here }.forEach { pr ->
                prefs.scope = pr.id
                val urls = prefs.savedTabs
                val titles = prefs.savedTabTitles
                val used = prefs.savedTabUsed
                prefs.scope = here
                urls.forEachIndexed { i, url ->
                    val t = newTab(url, isPrivate = false, select = false, lazy = true, profile = pr.id)
                    titles.getOrNull(i)?.let { t.title = it }
                    used.getOrNull(i)?.takeIf { it > 0 }?.let { t.lastUsed = it }
                }
            }
            prefs.splitTabs = false
            saveTabs()
        } else {
            prefs.splitTabs = true
            Profiles.list.filter { it.id != here }.forEach { pr ->
                val theirs = tabs.filter { !it.isPrivate && it.profile == pr.id }
                prefs.scope = pr.id
                writeTabs(theirs.filter { UrlText.isWeb(it.pendingUrl ?: it.url) })
                prefs.scope = here
                theirs.forEach { putAway(it) }
            }
            if (current == null) {
                tabs.lastOrNull { !it.isPrivate }?.let { select(it) } ?: newTab(null, isPrivate = false)
            }
            saveTabs()
        }
    }

    /** Settings › Profiles › Use profiles. Off: back to the first profile (the others are kept). */
    fun turnProfiles(on: Boolean) {
        if (!on) switchProfile("")
        Profiles.turn(on)
    }

    /** Takes a tab out without the rest of closing (no new tab, no saving). */
    private fun putAway(tab: Tab) {
        tabs.remove(tab)
        if (current === tab) current = null
        Prompts.queue.removeAll { it.tab === tab }
        Bar.forget(tab)
        BackPeek.forget(tab)
        Pip.forget(tab)
        if (tab.session.isOpen) tab.session.close()
    }

    /** Deletes a profile (not the first): its sign-ins, site data, tabs, history and bookmarks. */
    fun deleteProfile(id: String) {
        if (id.isEmpty()) return
        if (Profiles.current == id) switchProfile("")
        // Shared tabs: its own tabs close.
        tabs.filter { !it.isPrivate && it.profile == id }.forEach { close(it) }
        Profiles.contextId(id)?.let { runCatching { runtime.storageController.clearDataForSessionContext(it) } }
        dbs.remove(id)?.close()
        app.deleteDatabase(Profiles.dbName(id))
        prefs.forgetProfile(id)
        Profiles.remove(id)
    }

    // ---- Clearing ----

    fun clearData(flags: Long, done: () -> Unit) {
        runtime.storageController.clearData(flags).accept({ main.post(done) }, { main.post(done) })
    }

    /** Every trace of browsing: history, downloads list, cookies, cache and site data. */
    fun clearEverything(done: () -> Unit) {
        Profiles.list.forEach { dbOf(it.id).clearHistory() }
        forgetClosed()
        mainDb.clearDownloads()
        clearData(StorageController.ClearFlags.ALL, done)
    }

    /** Things that need the activity. */
    interface Host {
        /** The engine view, when it is currently showing [tab]. */
        fun geckoViewFor(tab: Tab): org.mozilla.geckoview.GeckoView?

        /** The main engine view, whatever it shows. */
        fun mainView(): org.mozilla.geckoview.GeckoView?
        /** Opens another app; false when it can't be started. */
        fun launch(intent: android.content.Intent): Boolean
        fun share(text: String, title: String?)
        fun setFullscreen(on: Boolean)
        fun applySystemBars()

        /** A video started or stopped: the picture-in-picture window's shape and buttons follow it. */
        fun updatePip()

        /** Opens the picture-in-picture window now; false when it can't. */
        fun enterPip(): Boolean

        /** Asks for the fingerprint or screen lock, then unlocks the private tabs. */
        fun unlockPrivate()
        fun requestAndroidPermissions(perms: Array<String>, result: (Boolean) -> Unit)
        fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, result: (List<android.net.Uri>) -> Unit)

        /** [type] is a PromptDelegate.DateTimePrompt.Type; values are HTML input formats. */
        fun pickDateTime(type: Int, value: String?, min: String?, max: String?, result: (String?) -> Unit)
        fun toast(text: String, action: String? = null, onAction: (() -> Unit)? = null)

        /** Asks Android to make SonderWeb the default browser (or opens the setting for it). */
        fun askDefaultBrowser()
    }
}
