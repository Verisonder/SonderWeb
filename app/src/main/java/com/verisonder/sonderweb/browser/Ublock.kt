package com.verisonder.sonderweb.browser

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import com.verisonder.sonderweb.core.UrlText

/**
 * uBlock Origin, shipped inside the app as a built-in extension (unpacked into
 * assets/extensions/ublock at build time). Its shield sits in the address bar.
 */
object Ublock {
    const val ID = "uBlock0@raymondhill.net"
    private const val LOCATION = "resource://android/assets/extensions/ublock/"

    var extension by mutableStateOf<WebExtension?>(null)
        private set

    /** Why uBlock Origin couldn't start, shown in Extensions (null while fine or still starting). */
    var error by mutableStateOf<String?>(null)
        private set

    val popup: GeckoSession? get() = Extensions.popup

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept({ ext ->
            Browser.main.post {
                if (ext == null) {
                    error = "The engine returned no extension"
                } else {
                    extension = ext
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                    Extensions.refresh()
                }
            }
        }, { e ->
            Browser.main.post { error = e?.message ?: e?.javaClass?.simpleName ?: "Unknown error" }
        })
    }

    fun showPopup(tab: Tab) {
        extension?.let { Extensions.showPopup(it, tab) }
    }

    fun closePopup() = Extensions.closePopup()

    fun openDashboard() {
        extension?.let { Extensions.openOptions(it) }
    }
}

/**
 * Every extension: uBlock Origin plus the ones you add from addons.mozilla.org.
 * Their toolbar buttons open as a sheet; their settings open in a tab.
 */
object Extensions {
    const val GET_MORE = "https://addons.mozilla.org/android/"

    val list = mutableStateListOf<WebExtension>()

    /** Extension icons for the Extensions screen, by id. */
    val icons = mutableStateMapOf<String, Bitmap>()

    var popup by mutableStateOf<GeckoSession?>(null)
        private set

    /** An add-on asking to be installed, waiting for you to allow it. */
    class InstallRequest(val ext: WebExtension, val permissions: List<String>, val result: GeckoResult<WebExtension.PermissionPromptResponse>)

    var pending by mutableStateOf<InstallRequest?>(null)
        private set

    private var lastRestart = 0L

    private fun restartProcess() {
        lastRestart = android.os.SystemClock.elapsedRealtime()
        controller.enableExtensionProcessSpawning()
    }

    /** Back in the app: make sure extensions may run again. */
    fun wake() {
        if (Browser.started) controller.enableExtensionProcessSpawning()
    }

    private val registered = HashSet<String>()
    private val actions = HashMap<String, WebExtension.Action>()
    private val defaults = HashMap<String, WebExtension.Action>()

    // Page actions (an older kind of button some extensions use instead).
    private val pageActions = HashMap<String, WebExtension.Action>()
    private val pageDefaults = HashMap<String, WebExtension.Action>()

    private val controller get() = Browser.runtime.webExtensionController

    fun setup() {
        // Android stops the extensions' process now and then (in the background, low memory).
        // After a few times the engine stops restarting it, and every extension (uBlock Origin
        // too) quietly stops working. Restart it: on its own once, and ask if it keeps happening.
        controller.setExtensionProcessDelegate(object : WebExtensionController.ExtensionProcessDelegate {
            override fun onDisabledProcessSpawning() {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRestart > 60_000) {
                    restartProcess()
                } else {
                    Browser.host?.toast("Extensions stopped working", "Restart") { restartProcess() }
                }
            }
        })
        controller.enableExtensionProcessSpawning()
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                val r = GeckoResult<WebExtension.PermissionPromptResponse>()
                val all = (permissions.toList() + origins.toList()).filter { it.isNotBlank() }
                Browser.main.post { pending = InstallRequest(extension, all, r) }
                return r
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.allow()

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.allow()
        })
        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                refresh()
                Browser.main.post { started(extension) }
            }
            override fun onUninstalled(extension: WebExtension) = refresh()
            override fun onEnabled(extension: WebExtension) = refresh()
            override fun onDisabled(extension: WebExtension) = refresh()
            override fun onOptionalPermissionsChanged(extension: WebExtension) = refresh()
            override fun onInstallationFailed(extension: WebExtension?, installException: WebExtension.InstallException) {
                Browser.main.post { Browser.host?.toast("Couldn't add the extension") }
            }
        })
        refresh()
    }

    /**
     * A newly added extension, ready to work straight away: it gets the sites it asked for (newer
     * extensions only ask for them "optionally" and otherwise sit idle until granted), and the
     * page you're on reloads so it runs there too.
     */
    private fun started(ext: WebExtension, reload: Boolean = true) {
        if (ext.id == Ublock.ID || ext.id == Look.ID || ext.id == AddonsSite.ID) return
        if (ext.id in Browser.prefs.extensionsSetUp && !reload) return
        Browser.prefs.extensionsSetUp = Browser.prefs.extensionsSetUp + ext.id
        val m = ext.metaData
        val origins = (m.optionalOrigins.toList() - m.grantedOptionalOrigins.toSet()).toTypedArray()
        val done = {
            if (origins.isNotEmpty()) refresh()
            if (reload) Browser.current?.let { t -> if (!t.isBlank && t.ready) t.session.reload() }
        }
        if (origins.isEmpty()) {
            done()
        } else {
            controller.addOptionalPermissions(ext.id, emptyArray(), origins, emptyArray())
                .accept({ Browser.main.post { done() } }, { _ -> Browser.main.post { done() } })
        }
    }

    /** Your answer to an install request. */
    fun answer(allow: Boolean, inPrivate: Boolean) {
        val req = pending ?: return
        pending = null
        req.result.complete(WebExtension.PermissionPromptResponse(allow, inPrivate, false))
    }

    fun refresh() {
        controller.list().accept({ all ->
            Browser.main.post {
                // The browser's own little extensions aren't yours: they stay out of the list.
                val exts = (all ?: emptyList()).filter { !it.metaData.temporary && it.id != Look.ID && it.id != AddonsSite.ID }
                list.clear()
                // uBlock Origin first, then by name.
                list.addAll(exts.sortedWith(compareBy({ it.id != Ublock.ID }, { it.metaData.name ?: it.id })))
                exts.forEach { register(it) }
                // Ones added before this version: give them their sites once, too.
                exts.filter { it.id !in Browser.prefs.extensionsSetUp }.forEach { started(it, reload = false) }
            }
        }, { _ -> })
    }

    private fun register(ext: WebExtension) {
        if (!registered.add(ext.id)) return
        ext.setActionDelegate(actionDelegate(ext, null))
        ext.setTabDelegate(tabDelegate)
        // browser.downloads: without this, an extension's downloads would wait forever.
        ext.setDownloadDelegate(downloadDelegate)
        Browser.tabs.forEach { attach(ext, it) }
        ext.metaData.icon.getBitmap(96).accept({ b -> if (b != null) Browser.main.post { icons[ext.id] = b } }, { _ -> })
    }

    /** Hooks a new tab up to every extension (their buttons, and tabs they close). */
    fun attach(tab: Tab) {
        list.forEach { attach(it, tab) }
    }

    private fun attach(ext: WebExtension, tab: Tab) {
        val c = tab.session.webExtensionController
        c.setActionDelegate(ext, actionDelegate(ext, tab))
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

    private fun actionDelegate(ext: WebExtension, tab: Tab?) = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (tab == null) {
                defaults[ext.id] = action
            } else {
                val full = defaults[ext.id]?.let { action.withDefault(it) } ?: action
                actions[ext.id + "|" + tab.id] = full
                if (ext.id == Ublock.ID) tab.blockedBadge = full.badgeText ?: ""
            }
        }

        override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (tab == null) {
                pageDefaults[ext.id] = action
            } else {
                pageActions[ext.id + "|" + tab.id] = pageDefaults[ext.id]?.let { action.withDefault(it) } ?: action
            }
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
            // Pressed again while its panel is open: close it.
            if (popup != null) {
                closePopup()
                return null
            }
            return openPopupSession()
        }

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopupSession()
    }

    private fun openPopupSession(): GeckoResult<GeckoSession> {
        popup?.let { if (it.isOpen) it.close() }
        val s = GeckoSession()
        // Its dialogs and dropdowns show like a page's, and links it opens go to a new tab.
        Browser.current?.let { s.setPromptDelegate(Prompts.delegate(it)) }
        s.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                Browser.main.post { closePopup() }
                val opener = Browser.current
                val t = Browser.newTab(uri, isPrivate = opener?.isPrivate ?: false, select = true, opener = opener, open = false)
                t.url = uri
                return GeckoResult.fromValue(t.session)
            }
        })
        s.open(Browser.runtime)
        popup = s
        return GeckoResult.fromValue(s)
    }

    /** Presses an extension's toolbar button for this tab (opens its panel, if it has one). */
    fun showPopup(ext: WebExtension, tab: Tab): Boolean {
        val key = ext.id + "|" + tab.id
        val action = actions[key] ?: defaults[ext.id] ?: pageActions[key] ?: pageDefaults[ext.id] ?: return false
        action.click()
        return true
    }

    fun hasButton(ext: WebExtension): Boolean = defaults.containsKey(ext.id) || pageDefaults.containsKey(ext.id)

    fun closePopup() {
        val s = popup ?: return
        popup = null
        if (s.isOpen) s.close()
    }

    fun openOptions(ext: WebExtension) {
        val url = ext.metaData.optionsPageUrl
            ?: if (ext.id == Ublock.ID) ext.metaData.baseUrl + "dashboard.html" else return
        Browser.newTab(url, isPrivate = false)
    }

    fun setEnabled(ext: WebExtension, on: Boolean) {
        val r = if (on) {
            controller.enable(ext, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(ext, WebExtensionController.EnableSource.USER)
        }
        r.accept({ refresh() }, { _ -> refresh() })
    }

    fun setPrivate(ext: WebExtension, on: Boolean) {
        controller.setAllowedInPrivateBrowsing(ext, on).accept({ refresh() }, { _ -> refresh() })
    }

    fun remove(ext: WebExtension) {
        controller.uninstall(ext).accept({ refresh() }, { _ -> refresh() })
    }

    /** An .xpi link the engine handed over as a download: install it instead. */
    fun installFrom(url: String) {
        controller.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            .accept({ refresh() }, { _ -> Browser.main.post { Browser.host?.toast("Couldn't add the extension") } })
    }

    private var nextDownloadId = 1

    /** browser.downloads.download: saved like any other download. */
    private val downloadDelegate = object : WebExtension.DownloadDelegate {
        override fun onDownload(
            extension: WebExtension,
            request: WebExtension.DownloadRequest,
        ): GeckoResult<WebExtension.DownloadInitData>? {
            val result = GeckoResult<WebExtension.DownloadInitData>()
            Browser.main.post {
                val name = request.filename?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: UrlText.fileName(null, request.request.uri, null)
                val download = controller.createDownload(nextDownloadId++)
                if (download == null) {
                    result.completeExceptionally(IllegalStateException("Download not available"))
                    return@post
                }
                result.complete(WebExtension.DownloadInitData(download, downloadInfo(name, WebExtension.Download.STATE_IN_PROGRESS)))
                Downloads.fetch(request.request, isPrivate = false) { ok ->
                    val state = if (ok) WebExtension.Download.STATE_COMPLETE else WebExtension.Download.STATE_INTERRUPTED
                    download.update(downloadInfo(name, state))
                }
            }
            return result
        }
    }

    private fun downloadInfo(name: String, state: Int) = object : WebExtension.Download.Info {
        override fun filename(): String = name
        override fun state(): Int = state
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
            openOptions(source)
        }
    }
}

/**
 * A small extension inside the app that restyles DuckDuckGo to match the browser
 * (Settings › Search engine › "Clean DuckDuckGo look").
 */
object Look {
    const val ID = "look@sonderweb"
    private const val LOCATION = "resource://android/assets/extensions/style/"
    private var extension: WebExtension? = null

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept({ ext ->
            Browser.main.post {
                extension = ext ?: return@post
                runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                apply(Browser.prefs.cleanDdg)
            }
        }, { _ -> })
    }

    fun apply(on: Boolean) {
        val ext = extension ?: return
        val c = Browser.runtime.webExtensionController
        val enabled = ext.metaData.enabled
        if (on && !enabled) {
            c.enable(ext, WebExtensionController.EnableSource.USER).accept({ e -> if (e != null) extension = e }, { _ -> })
        } else if (!on && enabled) {
            c.disable(ext, WebExtensionController.EnableSource.USER).accept({ e -> if (e != null) extension = e }, { _ -> })
        }
    }
}

/**
 * A small extension inside the app: on addons.mozilla.org the install buttons say
 * "Add to SonderWeb" instead of "Add to Firefox". Always on.
 */
object AddonsSite {
    const val ID = "amo@sonderweb"
    private const val LOCATION = "resource://android/assets/extensions/amo/"

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept({ ext ->
            if (ext != null) {
                Browser.main.post { runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true) }
            }
        }, { _ -> })
    }
}
