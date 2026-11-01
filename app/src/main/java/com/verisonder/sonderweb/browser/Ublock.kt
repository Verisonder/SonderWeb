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

    private val registered = HashSet<String>()
    private val actions = HashMap<String, WebExtension.Action>()
    private val defaults = HashMap<String, WebExtension.Action>()

    private val controller get() = Browser.runtime.webExtensionController

    fun setup() {
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
            override fun onInstalled(extension: WebExtension) = refresh()
            override fun onUninstalled(extension: WebExtension) = refresh()
            override fun onEnabled(extension: WebExtension) = refresh()
            override fun onDisabled(extension: WebExtension) = refresh()
            override fun onInstallationFailed(extension: WebExtension?, installException: WebExtension.InstallException) {
                Browser.main.post { Browser.host?.toast("Couldn't add the extension") }
            }
        })
        refresh()
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
                val exts = (all ?: emptyList()).filter { !it.metaData.temporary }
                list.clear()
                // uBlock Origin first, then by name.
                list.addAll(exts.sortedWith(compareBy({ it.id != Ublock.ID }, { it.metaData.name ?: it.id })))
                exts.forEach { register(it) }
            }
        }, { _ -> })
    }

    private fun register(ext: WebExtension) {
        if (!registered.add(ext.id)) return
        ext.setActionDelegate(actionDelegate(ext, null))
        ext.setTabDelegate(tabDelegate)
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

    /** Presses an extension's toolbar button for this tab (opens its panel, if it has one). */
    fun showPopup(ext: WebExtension, tab: Tab): Boolean {
        val action = actions[ext.id + "|" + tab.id] ?: defaults[ext.id] ?: return false
        action.click()
        return true
    }

    fun hasButton(ext: WebExtension): Boolean = defaults.containsKey(ext.id)

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
