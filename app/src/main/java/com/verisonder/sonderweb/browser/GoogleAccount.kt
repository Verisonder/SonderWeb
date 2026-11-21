package com.verisonder.sonderweb.browser

import com.verisonder.sonderweb.data.Profiles
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Profiles: a profile's picture can be the one of the Google account signed in on it. A small
 * built-in extension reads the account picture's address on Google's pages and tells the tab's
 * profile; the picture is then downloaded once (again only when it changes).
 */
object GoogleAccount {
    const val ID = "account@sonderweb"
    private const val LOCATION = "resource://android/assets/extensions/account/"
    private const val APP = "account"
    private var extension: WebExtension? = null

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept({ ext ->
            Browser.main.post {
                extension = ext ?: return@post
                Browser.tabs.forEach { attach(it) }
            }
        }, { _ -> })
    }

    fun attach(tab: Tab) {
        val ext = extension ?: return
        if (tab.isPrivate) return
        tab.session.webExtensionController.setMessageDelegate(
            ext,
            object : WebExtension.MessageDelegate {
                override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                    val url = (message as? JSONObject)?.optString("picture").orEmpty()
                    if (url.isNotEmpty()) Browser.main.post { Profiles.googlePicture(tab.profile, url) }
                    return null
                }
            },
            APP,
        )
    }
}
