package com.verisonder.sonderweb.browser

import com.verisonder.sonderweb.sync.FxSync
import com.verisonder.sonderweb.sync.SavedAddress
import com.verisonder.sonderweb.sync.SavedCard
import mozilla.appservices.logins.Login
import mozilla.appservices.logins.LoginEntry
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * The passwords, addresses and payment cards SonderWeb saves itself (with the Firefox Sync option
 * on): the engine asks here for what to fill in, and hands over what you choose to save. They live
 * in Firefox Sync's encrypted databases ([FxSync]) and sync with your account.
 */
object Passwords {

    /** The option on: the engine fills and offers to save what's turned on; off: it leaves forms alone. */
    fun applyOption() {
        val logins = FxSync.enabled && FxSync.fillPasswords
        val any = FxSync.enabled && (FxSync.fillPasswords || FxSync.fillAddresses || FxSync.fillCards)
        runCatching { Browser.runtime.settings.setLoginAutofillEnabled(logins) }
        Browser.runtime.autocompleteStorageDelegate = if (any) storage else null
    }

    /** Answers the engine once, on the main thread: with what was found, or nothing after a few seconds. */
    private fun <T> answerOnce(result: GeckoResult<Array<T>>, empty: Array<T>): (Array<T>) -> Unit {
        var done = false
        val answer: (Array<T>) -> Unit = { found ->
            Browser.main.post {
                if (!done) {
                    done = true
                    result.complete(found)
                }
            }
        }
        Browser.main.postDelayed({ answer(empty) }, 4000)
        return answer
    }

    private fun fetchAddresses(): GeckoResult<Array<Autocomplete.Address>> {
        val result = GeckoResult<Array<Autocomplete.Address>>()
        if (!FxSync.fillAddresses) return GeckoResult.fromValue(emptyArray())
        val answer = answerOnce(result, emptyArray())
        FxSync.withAutofill { store, _ ->
            answer(
                runCatching {
                    store.getAllAddresses().map { a ->
                        Autocomplete.Address.Builder()
                            .guid(a.guid)
                            .name(a.name)
                            .organization(a.organization)
                            .streetAddress(a.streetAddress)
                            .addressLevel3(a.addressLevel3)
                            .addressLevel2(a.addressLevel2)
                            .addressLevel1(a.addressLevel1)
                            .postalCode(a.postalCode)
                            .country(a.country)
                            .tel(a.tel)
                            .email(a.email)
                            .build()
                    }.toTypedArray()
                }.getOrDefault(emptyArray()),
            )
        }
        return result
    }

    private fun fetchCards(): GeckoResult<Array<Autocomplete.CreditCard>> {
        val result = GeckoResult<Array<Autocomplete.CreditCard>>()
        if (!FxSync.fillCards) return GeckoResult.fromValue(emptyArray())
        val answer = answerOnce(result, emptyArray())
        FxSync.withAutofill { store, key ->
            answer(
                runCatching {
                    store.getAllCreditCards().mapNotNull { c ->
                        // A card synced from a device whose key this phone doesn't have can't be read: left out.
                        val number = runCatching { mozilla.appservices.autofill.decryptString(key, c.ccNumberEnc) }.getOrNull()
                            ?: return@mapNotNull null
                        Autocomplete.CreditCard.Builder()
                            .guid(c.guid)
                            .name(c.ccName)
                            .number(number)
                            .expirationMonth(c.ccExpMonth.toString())
                            .expirationYear(c.ccExpYear.toString())
                            .build()
                    }.toTypedArray()
                }.getOrDefault(emptyArray()),
            )
        }
        return result
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

        override fun onAddressFetch(): GeckoResult<Array<Autocomplete.Address>> = fetchAddresses()

        override fun onAddressSave(address: Autocomplete.Address) {
            if (!FxSync.fillAddresses) return
            val a = SavedAddress(
                guid = address.guid?.takeIf { it.isNotEmpty() },
                name = address.name.ifBlank {
                    listOf(address.givenName, address.additionalName, address.familyName).filter { it.isNotBlank() }.joinToString(" ")
                },
                organization = address.organization,
                street = address.streetAddress,
                suburb = address.addressLevel3,
                city = address.addressLevel2,
                region = address.addressLevel1,
                postalCode = address.postalCode,
                country = address.country,
                tel = address.tel,
                email = address.email,
            )
            FxSync.saveAddress(a)
        }

        override fun onCreditCardFetch(): GeckoResult<Array<Autocomplete.CreditCard>> = fetchCards()

        override fun onCreditCardSave(card: Autocomplete.CreditCard) {
            if (!FxSync.fillCards) return
            val c = SavedCard(
                guid = card.guid?.takeIf { it.isNotEmpty() },
                name = card.name,
                month = card.expirationMonth.toIntOrNull() ?: 0,
                year = card.expirationYear.toIntOrNull() ?: 0,
            )
            FxSync.saveCard(c, card.number)
        }
    }
}
