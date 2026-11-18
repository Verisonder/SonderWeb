package com.verisonder.sonderweb.sync

import android.content.Context

/**
 * The normal SonderWeb has no Firefox Sync (that's SonderWeb Sync): this stand-in answers like
 * Firefox Sync switched off, so the shared code needs no checks of its own.
 */
object FxSync {
    const val PASSWORDS = "passwords"
    const val ACCOUNTS = "https://accounts.firefox.com"

    val enabled = false
    val signedIn = false
    val needsSignIn = false
    val email: String? = null
    val syncing = false
    val lastSynced = 0L
    val problem: String? = null
    val passwordCount = 0

    fun init(context: Context) = Unit

    fun turn(on: Boolean) = Unit

    fun signIn(open: (String) -> Unit) = Unit

    fun finishSignIn(code: String, state: String) = Unit

    fun signedInEmail(): String? = null

    fun signOut() = Unit

    fun sync(user: Boolean = true) = Unit

    fun listPasswords(done: (List<SavedPassword>) -> Unit) = done(emptyList())

    fun deletePassword(id: String, done: () -> Unit) = done()
}
