package com.verisonder.sonderweb.browser

import com.verisonder.sonderweb.data.Profiles
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Profiles and Google accounts. A small built-in extension reads which Google account is signed
 * in on Google's pages (picture, email, name) and tells the tab's profile:
 * - the profile's first account gives it its picture;
 * - a different account signing in asks whether it should have a profile of its own (or switch
 *   to the profile that already has it).
 */
object GoogleAccount {
    const val ID = "account@sonderweb"
    private const val LOCATION = "resource://android/assets/extensions/account/"
    private const val APP = "account"
    private var extension: WebExtension? = null

    /** Asked already this session (profile|email): not asked twice. */
    private val asked = HashSet<String>()

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
                    val m = message as? JSONObject ?: return null
                    val email = m.optString("email").trim().lowercase()
                    val name = m.optString("name").trim()
                    val picture = m.optString("picture")
                    Browser.main.post { seen(tab, email, name, picture) }
                    return null
                }
            },
            APP,
        )
    }

    private fun seen(tab: Tab, email: String, name: String, picture: String) {
        if (!Profiles.enabled || tab !in Browser.tabs) return
        val p = Profiles.list.firstOrNull { it.id == tab.profile } ?: return
        when {
            // Only a picture (no email found): it's the profile's own account unless it keeps others.
            email.isEmpty() -> if (picture.isNotEmpty() && p.kept.isEmpty()) Profiles.googlePicture(p.id, picture)
            p.account.isEmpty() || p.account == email -> {
                Profiles.setAccount(p.id, email)
                if (picture.isNotEmpty()) Profiles.googlePicture(p.id, picture)
            }
            email in p.kept -> Unit
            asked.add(p.id + "|" + email) -> askAbout(tab, p.id, email, name)
        }
    }

    private fun askAbout(tab: Tab, here: String, email: String, name: String) {
        val mine = Profiles.get(here)
        val other = Profiles.withAccount(email)
        if (other != null && other.id != here) {
            Prompts.ask(
                tab,
                "$email is in your ${other.name} profile",
                "Switch to ${other.name}? You stay signed in to it here too, until you sign out on this page.",
                "Switch",
                "Keep here",
            ) { yes -> if (yes) Browser.switchProfile(other.id) else Profiles.keep(here, email) }
            return
        }
        Prompts.ask(
            tab,
            "Give $email its own profile?",
            "This profile is for ${mine.account}. A new profile keeps $email's sign-in, tabs and history apart. " +
                "You sign in to it once more there.",
            "New profile",
            "Keep here",
        ) { yes -> if (yes) moveTo(email, name) else Profiles.keep(here, email) }
    }

    /** A new profile for this Google account, and its sign-in page there. */
    private fun moveTo(email: String, name: String) {
        val made = Profiles.add(name.ifBlank { email.substringBefore('@') }, Profiles.nextColor())
        Profiles.setAccount(made.id, email)
        Browser.switchProfile(made.id)
        // Google's sign-in, with the email filled in, in the new profile.
        val url = "https://accounts.google.com/ServiceLogin?Email=" + java.net.URLEncoder.encode(email, "UTF-8") +
            "&continue=" + java.net.URLEncoder.encode("https://www.google.com/", "UTF-8")
        val cur = Browser.current
        if (cur != null && cur.isBlank && !cur.isPrivate && cur.profile == made.id) Browser.load(cur, url) else Browser.newTab(url, isPrivate = false)
    }
}
