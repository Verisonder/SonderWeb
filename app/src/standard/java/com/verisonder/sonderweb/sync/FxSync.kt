package com.verisonder.sonderweb.sync

import android.content.Context

/**
 * The normal SonderWeb has no Firefox Sync (that's SonderWeb Sync): this stand-in answers like
 * Firefox Sync switched off, so the shared code needs no checks of its own.
 */
object FxSync {
    const val PASSWORDS = "passwords"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val TABS = "tabs"
    const val ADDRESSES = "addresses"
    const val CARDS = "creditcards"
    const val ACCOUNTS = "https://accounts.firefox.com"

    val enabled = false
    val signedIn = false
    val needsSignIn = false
    val email: String? = null
    val syncing = false
    val lastSynced = 0L
    val problem: String? = null
    val passwordCount = 0
    val displayName: String? = null
    val avatar: android.graphics.Bitmap? = null
    val deviceName = ""
    val syncPasswords = false
    val syncBookmarks = false
    val syncHistory = false
    val syncTabs = false
    val syncAddresses = false
    val syncCards = false
    val fillAddresses = false
    val fillCards = false
    val addressCount = 0
    val cardCount = 0
    val bookmarksVersion = 0
    val remoteDevices: List<RemoteDevice> = emptyList()
    val fillPasswords = false

    fun changeFillPasswords(on: Boolean) = Unit

    fun manageAccount(open: (String) -> Unit) = Unit

    fun renameDevice(name: String) = Unit

    fun changeSyncPasswords(on: Boolean) = Unit

    fun changeSyncBookmarks(on: Boolean) = Unit

    fun changeSyncHistory(on: Boolean) = Unit

    fun changeSyncTabs(on: Boolean) = Unit

    fun changeSyncAddresses(on: Boolean) = Unit

    fun changeSyncCards(on: Boolean) = Unit

    fun changeFillAddresses(on: Boolean) = Unit

    fun changeFillCards(on: Boolean) = Unit

    fun noteHistoryRemoved(url: String) = Unit

    fun noteHistoryCleared() = Unit

    fun noteBookmarksChanged() = Unit

    fun syncSoon() = Unit

    fun appVisible(visible: Boolean) = Unit

    fun loadSendTargets(done: (List<SendTarget>) -> Unit) = done(emptyList())

    fun sendTab(target: SendTarget, title: String, url: String, done: (Boolean) -> Unit) = done(false)

    fun listAddresses(done: (List<SavedAddress>) -> Unit) = done(emptyList())

    fun saveAddress(a: SavedAddress, done: () -> Unit = {}) = done()

    fun deleteAddress(guid: String, done: () -> Unit = {}) = done()

    fun listCards(done: (List<SavedCard>) -> Unit) = done(emptyList())

    fun saveCard(card: SavedCard, number: String, done: () -> Unit = {}) = done()

    fun deleteCard(guid: String, done: () -> Unit = {}) = done()

    fun init(context: Context) = Unit

    fun shouldAskFirst() = false

    fun askedFirst() = Unit

    fun turn(on: Boolean) = Unit

    fun signIn(open: (String) -> Unit) = Unit

    fun finishSignIn(code: String, state: String, done: (String?) -> Unit) = done(null)

    fun signedInEmail(): String? = null

    fun signOut() = Unit

    fun sync(user: Boolean = true) = Unit

    fun listPasswords(done: (List<SavedPassword>) -> Unit) = done(emptyList())

    fun deletePassword(id: String, done: () -> Unit) = done()
}
