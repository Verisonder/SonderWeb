package com.verisonder.sonderweb.browser

import com.verisonder.sonderweb.sync.FxSync
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * The sign-in channel for Firefox Sync: Mozilla's sign-in page (accounts.firefox.com) asks what
 * the browser supports, and hands over the result once you've signed in. A small built-in
 * extension passes the page's messages here and the answers back. Only that site is heard, and
 * only with the Firefox Sync option on.
 */
object FxaChannel {
    const val ID = "fxa@sonderweb"
    private const val LOCATION = "resource://android/assets/extensions/fxa/"
    private const val APP = "fxa"
    private const val CHANNEL = "account_updates"

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
        tab.session.webExtensionController.setMessageDelegate(
            ext,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    if (!port.sender.url.startsWith(FxSync.ACCOUNTS + "/")) {
                        port.disconnect()
                        return
                    }
                    port.setDelegate(
                        object : WebExtension.PortDelegate {
                            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                if (!FxSync.enabled) return
                                if (!port.sender.url.startsWith(FxSync.ACCOUNTS + "/")) return
                                val json = message as? JSONObject ?: return
                                runCatching { handle(json, port) }
                            }
                        },
                    )
                }
            },
            APP,
        )
    }

    private fun handle(json: JSONObject, port: WebExtension.Port) {
        val payload = json.optJSONObject("message") ?: return
        val command = payload.optString("command")
        val id = payload.optString("messageId", "")
        val data = payload.optJSONObject("data")
        when (command) {
            // What this browser can sync, and whether someone is signed in already.
            "fxaccounts:fxa_status" -> reply(
                port,
                id,
                command,
                JSONObject()
                    .put(
                        "capabilities",
                        JSONObject()
                            .put("engines", JSONArray().put(FxSync.PASSWORDS))
                            .put("choose_what_to_sync", true)
                            .put("can_link_account_uid", true),
                    )
                    .put("signedInUser", JSONObject.NULL),
            )
            // Signing in with another account than the one already here isn't allowed.
            "fxaccounts:can_link_account" -> {
                val signedIn = FxSync.signedInEmail()
                val ok = signedIn == null || signedIn.equals(data?.optString("email"), ignoreCase = true)
                reply(port, id, command, JSONObject().put("ok", ok))
            }
            // Signed in: the code to finish with.
            "fxaccounts:oauth_login" -> {
                val code = data?.optString("code").orEmpty()
                val state = data?.optString("state").orEmpty()
                if (code.isNotEmpty() && state.isNotEmpty()) FxSync.finishSignIn(code, state)
            }
            "fxaccounts:logout", "fxaccounts:delete" -> FxSync.signOut()
        }
    }

    private fun reply(port: WebExtension.Port, messageId: String, command: String, data: JSONObject) {
        port.postMessage(
            JSONObject()
                .put("id", CHANNEL)
                .put(
                    "message",
                    JSONObject().put("messageId", messageId).put("command", command).put("data", data),
                ),
        )
    }
}
