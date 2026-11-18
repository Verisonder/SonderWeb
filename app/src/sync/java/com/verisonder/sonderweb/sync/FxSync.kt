package com.verisonder.sonderweb.sync

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import mozilla.appservices.RustComponentsInitializer
import mozilla.appservices.db_crypto.KeyManager
import mozilla.appservices.fxaclient.DeviceConfig
import mozilla.appservices.fxaclient.FxaClient
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaEvent
import mozilla.appservices.fxaclient.FxaServer
import mozilla.appservices.fxaclient.FxaState
import mozilla.appservices.logins.DatabaseLoginsStorage
import mozilla.appservices.sync15.DeviceType
import mozilla.appservices.syncmanager.DeviceSettings
import mozilla.appservices.syncmanager.ServiceStatus
import mozilla.appservices.syncmanager.SyncAuthInfo
import mozilla.appservices.syncmanager.SyncEngineSelection
import mozilla.appservices.syncmanager.SyncManager
import mozilla.appservices.syncmanager.SyncParams
import mozilla.appservices.syncmanager.SyncReason
import java.util.concurrent.Executors

/**
 * Firefox Sync, an option (off until you turn it on): a Mozilla account, and the passwords
 * SonderWeb saves, synced both ways with Firefox on your other devices.
 *
 * Everything that talks to Mozilla's libraries runs on one background thread, in order.
 * The account's sign-in state and the passwords database's key are kept encrypted
 * ([Secrets]); the passwords themselves are encrypted in their database, and end to end
 * when synced (Mozilla can't read them).
 */
object FxSync {
    // Firefox for Android's registered Mozilla account app: SonderWeb signs in as it.
    private const val CLIENT_ID = "a2270f727f45f648"
    private const val REDIRECT = "urn:ietf:wg:oauth:2.0:oob:oauth-redirect-webchannel"
    private const val SCOPE_PROFILE = "profile"
    const val SCOPE_SYNC = "https://identity.mozilla.com/apps/oldsync"

    /** The passwords' name in Firefox Sync. */
    const val PASSWORDS = "passwords"

    /** The address the sign-in pages are on (the only site the sign-in channel talks to). */
    const val ACCOUNTS = "https://accounts.firefox.com"

    private lateinit var app: Context
    private lateinit var secrets: Secrets
    private val prefs by lazy { app.getSharedPreferences("fxsync", Context.MODE_PRIVATE) }
    private val main = Handler(Looper.getMainLooper())

    /** One background thread for every call into Mozilla's libraries. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "fxsync").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + worker.asCoroutineDispatcher())

    // ---- What the screens show (main thread) ----

    /** The option itself. */
    var enabled by mutableStateOf(false)
        private set

    var signedIn by mutableStateOf(false)
        private set

    /** Signed in, but the account needs you to sign in again (password changed, …). */
    var needsSignIn by mutableStateOf(false)
        private set

    var email by mutableStateOf<String?>(null)
        private set

    var syncing by mutableStateOf(false)
        private set

    var lastSynced by mutableLongStateOf(0L)
        private set

    /** What went wrong last, in plain words (null: nothing). */
    var problem by mutableStateOf<String?>(null)
        private set

    /** How many passwords are saved. */
    var passwordCount by mutableIntStateOf(0)
        private set

    // ---- Worker-thread state ----

    private var rustReady = false

    /** Signed in, as the sync thread knows it (the screens' copy follows a moment later). */
    private var connected = false
    private var account: FxaClient? = null
    private var manager: SyncManager? = null
    private var logins: DatabaseLoginsStorage? = null
    private var loginsKey: String? = null

    /** Called once at startup. Nothing of Mozilla's is loaded unless the option is on. */
    fun init(context: Context) {
        app = context.applicationContext
        secrets = Secrets(app)
        enabled = prefs.getBoolean("enabled", false)
        lastSynced = prefs.getLong("last_synced", 0L)
        email = prefs.getString("email", null)
        if (enabled) start(syncSoon = true)
    }

    /** Turns the option on or off. Off: signed out; the saved passwords stay on the phone. */
    fun turn(on: Boolean) {
        if (on == enabled) return
        enabled = on
        prefs.edit().putBoolean("enabled", on).apply()
        com.verisonder.sonderweb.browser.Passwords.applyOption()
        if (on) start(syncSoon = false) else signOut()
    }

    private fun ui(block: () -> Unit) = main.post(block)

    private fun fail(what: String, e: Throwable) {
        val msg = what + (e.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        ui {
            problem = msg
            syncing = false
        }
    }

    /** Loads Mozilla's libraries (once), opens the passwords and the account. */
    private fun start(syncSoon: Boolean) = scope.launch {
        try {
            ready()
            openAccount()
            countPasswords()
            if (syncSoon && connected) {
                kotlinx.coroutines.delay(5000)
                syncNow(SyncReason.STARTUP)
            }
        } catch (e: Throwable) {
            fail("Couldn't start Firefox Sync", e)
        }
    }

    private fun ready() {
        if (rustReady) return
        RustComponentsInitializer.init()
        mozilla.appservices.viaduct.initBackend(HttpBackend())
        manager = SyncManager()
        rustReady = true
    }

    /** The passwords database, opened with its key (made the first time). */
    internal fun store(): DatabaseLoginsStorage {
        logins?.let { return it }
        ready()
        val key = secrets.get("logins_key") ?: mozilla.appservices.db_crypto.createKey().also {
            secrets.put("logins_key", it)
        }
        loginsKey = key
        val path = app.getDatabasePath("logins.sqlite")
        path.parentFile?.mkdirs()
        val store = DatabaseLoginsStorage(
            path.absolutePath,
            object : KeyManager {
                override fun getKey(): ByteArray = key.toByteArray()
            },
        )
        store.registerWithSyncManager()
        logins = store
        return store
    }

    /** Runs [block] with the passwords database, on the sync thread. */
    internal fun withStore(block: (DatabaseLoginsStorage) -> Unit) {
        if (!enabled) return
        scope.launch {
            try {
                block(store())
            } catch (e: Throwable) {
                fail("Passwords", e)
            }
        }
    }

    private fun deviceConfig() = DeviceConfig(
        name = deviceName(),
        deviceType = DeviceType.MOBILE,
        capabilities = emptyList(),
    )

    private fun deviceName() = "SonderWeb on " + Build.MODEL

    private fun openAccount() {
        if (account != null) return
        val saved = secrets.get("account")
        val client = if (saved != null) {
            runCatching { FxaClient.fromJSONString(saved) }.getOrNull()
        } else {
            null
        } ?: FxaClient(FxaConfig(FxaServer.Release, CLIENT_ID, REDIRECT))
        account = client
        settle(client.processEvent(FxaEvent.Initialize(deviceConfig())))
        persist()
    }

    private fun persist() {
        val json = runCatching { account?.toJSONString() }.getOrNull() ?: return
        secrets.put("account", json)
    }

    /** The account's new state, onto the screens. */
    private fun settle(state: FxaState) {
        val now = state is FxaState.Connected
        val issues = state is FxaState.AuthIssues
        connected = now
        val mail = if (now) runCatching { account?.getProfile(false)?.email }.getOrNull() else null
        persist()
        if (mail != null) prefs.edit().putString("email", mail).apply()
        ui {
            signedIn = now || issues
            needsSignIn = issues
            if (mail != null) email = mail
            if (!connected && !issues) email = null
        }
    }

    /**
     * Sign in: [open] gets the address of Mozilla's sign-in page, to load in a tab. The page then
     * hands SonderWeb the result through the sign-in channel ([finishSignIn]).
     */
    fun signIn(open: (String) -> Unit) {
        if (!enabled) return
        ui { problem = null }
        scope.launch {
            try {
                ready()
                openAccount()
                val state = account!!.processEvent(
                    FxaEvent.BeginOAuthFlow(
                        service = "sync",
                        scopes = listOf(SCOPE_PROFILE, SCOPE_SYNC),
                        entrypoint = "preferences",
                    ),
                )
                persist()
                if (state is FxaState.Authenticating) {
                    val url = state.oauthUrl
                    ui { open(url) }
                }
            } catch (e: Throwable) {
                fail("Couldn't start signing in", e)
            }
        }
    }

    /** The sign-in page finished: the code to finish signing in with. */
    fun finishSignIn(code: String, state: String) {
        scope.launch {
            try {
                ready()
                openAccount()
                settle(account!!.processEvent(FxaEvent.CompleteOAuthFlow(code, state)))
                if (connected) syncNow(SyncReason.USER)
            } catch (e: Throwable) {
                fail("Couldn't finish signing in", e)
            }
        }
    }

    /** Whether the sign-in page may link this account (the same one, or none yet). */
    fun signedInEmail(): String? = if (signedIn) email else null

    fun signOut() {
        scope.launch {
            try {
                account?.let { a ->
                    runCatching { a.processEvent(FxaEvent.Disconnect) }
                }
                runCatching { manager?.disconnect() }
            } finally {
                account = null
                connected = false
                secrets.put("account", null)
                secrets.put("sync_state", null)
                prefs.edit().remove("email").remove("last_synced").apply()
                ui {
                    signedIn = false
                    needsSignIn = false
                    email = null
                    lastSynced = 0L
                    syncing = false
                }
            }
        }
    }

    /** Sync now (from Settings, after saving a password, at startup). */
    fun sync(user: Boolean = true) {
        if (!enabled || !signedIn) return
        scope.launch { syncNow(if (user) SyncReason.USER else SyncReason.SCHEDULED) }
    }

    private fun syncNow(reason: SyncReason) {
        val client = account ?: return
        ui {
            syncing = true
            problem = null
        }
        try {
            store()
            val token = client.getAccessToken(SCOPE_SYNC)
            persist()
            val key = token.key ?: error("The account gave no sync key")
            val params = SyncParams(
                reason = reason,
                engines = SyncEngineSelection.Some(listOf(PASSWORDS)),
                enabledChanges = emptyMap(),
                localEncryptionKeys = mapOf(PASSWORDS to (loginsKey ?: error("No passwords key"))),
                authInfo = SyncAuthInfo(
                    kid = key.kid,
                    fxaAccessToken = token.token,
                    syncKey = key.k,
                    tokenserverUrl = client.getTokenServerEndpointURL(),
                ),
                persistedState = secrets.get("sync_state"),
                deviceSettings = DeviceSettings(
                    fxaDeviceId = runCatching { client.getCurrentDeviceId() }.getOrDefault(""),
                    name = deviceName(),
                    kind = DeviceType.MOBILE,
                ),
            )
            val result = manager!!.sync(params)
            secrets.put("sync_state", result.persistedState)
            countPasswords()
            when (result.status) {
                ServiceStatus.OK -> {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("last_synced", now).apply()
                    val failed = result.failures[PASSWORDS]
                    ui {
                        lastSynced = now
                        syncing = false
                        problem = failed?.let { "Passwords didn't sync: $it" }
                    }
                }
                ServiceStatus.AUTH_ERROR -> {
                    settle(client.processEvent(FxaEvent.CheckAuthorizationStatus))
                    ui {
                        syncing = false
                        problem = "Sign in again to keep syncing"
                    }
                }
                ServiceStatus.NETWORK_ERROR -> ui {
                    syncing = false
                    problem = "No connection to Firefox Sync"
                }
                ServiceStatus.BACKED_OFF -> ui {
                    syncing = false
                    problem = "Firefox Sync is busy, it will try again later"
                }
                else -> ui {
                    syncing = false
                    problem = "Firefox Sync had a problem, it will try again later"
                }
            }
        } catch (e: Throwable) {
            fail("Couldn't sync", e)
        }
    }

    private fun countPasswords() {
        val n = runCatching { store().count().toInt() }.getOrDefault(0)
        ui { passwordCount = n }
    }

    /** Refresh the count after a change (the passwords list, a saved password). */
    internal fun changed() {
        scope.launch { countPasswords() }
    }

    /** The saved passwords, by site, handed to [done] on the main thread. */
    fun listPasswords(done: (List<SavedPassword>) -> Unit) {
        withStore { store ->
            val all = runCatching { store.list() }.getOrDefault(emptyList())
                .map {
                    SavedPassword(
                        it.id,
                        com.verisonder.sonderweb.core.UrlText.host(it.origin).ifEmpty { it.origin },
                        it.username,
                        it.password,
                    )
                }
                .sortedBy { it.site }
            ui { done(all) }
        }
    }

    /** Deletes a saved password (on your other devices too, at the next sync). */
    fun deletePassword(id: String, done: () -> Unit) {
        withStore { store ->
            runCatching { store.delete(id) }
            countPasswords()
            ui {
                done()
                sync(user = false)
            }
        }
    }
}
