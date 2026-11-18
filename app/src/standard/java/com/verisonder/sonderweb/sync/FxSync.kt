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
    val fillPasswords = false

    fun changeFillPasswords(on: Boolean) = Unit

    fun manageAccount(open: (String) -> Unit) = Unit

    fun renameDevice(name: String) = Unit

    fun changeSyncPasswords(on: Boolean) = Unit

    fun changeSyncBookmarks(on: Boolean) = Unit

    fun changeSyncHistory(on: Boolean) = Unit

    fun noteVisit(url: String, title: String?) = Unit

    fun noteTitle(url: String, title: String) = Unit

    fun noteHistoryRemoved(url: String) = Unit

    fun noteHistoryCleared() = Unit

    fun noteBookmarked(url: String, title: String) = Unit

    fun noteUnbookmarked(url: String) = Unit

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
