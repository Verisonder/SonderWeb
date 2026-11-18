package com.verisonder.sonderweb.browser

import com.verisonder.sonderweb.sync.FxSync
import mozilla.appservices.logins.Login
import mozilla.appservices.logins.LoginEntry
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * The passwords SonderWeb saves itself (with the Firefox Sync option on): the engine asks here
 * for a site's saved sign-ins to fill in, and hands over the ones you choose to save. They live
 * in Firefox Sync's encrypted passwords database ([FxSync]) and sync with your account.
 */
object Passwords {

    /** The option on: the engine fills and offers to save sign-ins; off: it leaves them alone. */
    fun applyOption() {
        val on = FxSync.enabled
        runCatching { Browser.runtime.settings.setLoginAutofillEnabled(on) }
        Browser.runtime.autocompleteStorageDelegate = if (on) storage else null
    }

    private fun Login.toGecko(): Autocomplete.LoginEntry = Autocomplete.LoginEntry.Builder()
        .guid(id)
        .origin(origin)
        .formActionOrigin(formActionOrigin)
        .httpRealm(httpRealm)
        .username(username)
        .password(password)
        .build()

    private fun Autocomplete.LoginEntry.toEntry() = LoginEntry(
        origin = origin,
        httpRealm = httpRealm,
        formActionOrigin = formActionOrigin,
        usernameField = "",
        passwordField = "",
        password = password,
        username = username,
    )

    private fun fetch(pick: (mozilla.appservices.logins.DatabaseLoginsStorage) -> List<Login>): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        // Answered once, on the main thread: with what was found, or with nothing if the
        // passwords can't be read in time (the option turned off, the database not opening).
        var done = false
        fun answer(found: Array<Autocomplete.LoginEntry>) = Browser.main.post {
            if (!done) {
                done = true
                result.complete(found)
            }
        }
        FxSync.withStore { store ->
            answer(runCatching { pick(store).map { it.toGecko() }.toTypedArray() }.getOrDefault(emptyArray()))
        }
        Browser.main.postDelayed({ answer(emptyArray()) }, 4000)
        return result
    }

    private val storage = object : Autocomplete.StorageDelegate {
        override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> =
            fetch { it.getByBaseDomain(domain) }

        override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> = fetch { it.list() }

        override fun onLoginSave(login: Autocomplete.LoginEntry) {
            val entry = login.toEntry()
            val guid = login.guid
            FxSync.withStore { store ->
                if (!guid.isNullOrEmpty() && store.get(guid) != null) store.update(guid, entry) else store.addOrUpdate(entry)
                FxSync.changed()
                Browser.main.post { FxSync.sync(user = false) }
            }
        }

        override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
            val guid = login.guid ?: return
            FxSync.withStore { store -> runCatching { store.touch(guid) } }
        }
    }
}
