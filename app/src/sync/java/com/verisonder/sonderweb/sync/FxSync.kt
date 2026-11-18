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
 * Firefox Sync, an option (off until you turn it on): a Mozilla account, and the bookmarks,
 * history and passwords SonderWeb keeps, synced both ways with Firefox on your other devices.
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

    /** The kinds' names in Firefox Sync. */
    const val PASSWORDS = "passwords"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"

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

    /** Your account's name and picture (null: none set). */
    var displayName by mutableStateOf<String?>(null)
        private set

    var avatar by mutableStateOf<android.graphics.Bitmap?>(null)
        private set

    /** This phone's name in your account's device list (and on Firefox's "Send tab" lists). */
    var deviceName by mutableStateOf("")
        private set

    /** SonderWeb offers to save passwords and fills them in (separate from syncing them). */
    var fillPasswords by mutableStateOf(true)
        private set

    /** Choose what to sync. */
    var syncPasswords by mutableStateOf(true)
        private set

    var syncBookmarks by mutableStateOf(true)
        private set

    var syncHistory by mutableStateOf(true)
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
        displayName = prefs.getString("name", null)
        deviceName = prefs.getString("device_name", null) ?: defaultDeviceName()
        syncPasswords = prefs.getBoolean("sync_passwords", true)
        fillPasswords = prefs.getBoolean("fill_passwords", true)
        syncBookmarks = prefs.getBoolean("sync_bookmarks", true)
        syncHistory = prefs.getBoolean("sync_history", true)
        avatar = runCatching { android.graphics.BitmapFactory.decodeFile(avatarFile().path) }.getOrNull()
        if (enabled) start(syncSoon = true)
    }

    /** SonderWeb Sync's first launch: whether to ask if you want to sign in (asked once). */
    fun shouldAskFirst(): Boolean = !prefs.getBoolean("asked", false) && !enabled

    fun askedFirst() {
        prefs.edit().putBoolean("asked", true).apply()
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
            places()
            if (prefs.getBoolean("sync_bookmarks", true)) PlacesMirror.reconcileBookmarks()
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

    internal fun appContext(): Context = app

    /** Bookmarks and history for syncing (opened once the option is on). */
    private fun places(): PlacesMirror {
        ready()
        PlacesMirror.open(app)
        return PlacesMirror
    }

    /** A change made in SonderWeb, copied for Firefox Sync (only while the option is on). */
    private fun mirror(block: PlacesMirror.() -> Unit) {
        if (!enabled) return
        scope.launch { runCatching { places().block() } }
    }

    fun noteVisit(url: String, title: String?) = mirror { visited(url, title) }

    fun noteTitle(url: String, title: String) = mirror { titled(url, title) }

    fun noteHistoryRemoved(url: String) = mirror { historyRemoved(url) }

    fun noteHistoryCleared() = mirror { historyCleared() }

    fun noteBookmarked(url: String, title: String) = mirror { bookmarked(url, title) }

    fun noteUnbookmarked(url: String) = mirror { unbookmarked(url) }

    private fun deviceConfig() = DeviceConfig(
        name = deviceName(),
        deviceType = DeviceType.MOBILE,
        capabilities = emptyList(),
    )

    private fun defaultDeviceName() = "SonderWeb on " + Build.MODEL

    private fun deviceName() = prefs.getString("device_name", null) ?: defaultDeviceName()

    private fun avatarFile() = java.io.File(app.filesDir, "fxa_avatar.png")

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

    /** The account's new state, onto the screens. Returns the email when signed in. */
    private fun settle(state: FxaState): String? {
        val now = state is FxaState.Connected
        val issues = state is FxaState.AuthIssues
        connected = now
        val profile = if (now) runCatching { account?.getProfile(false) }.getOrNull() else null
        val mail = profile?.email
        persist()
        if (profile != null) {
            prefs.edit().putString("email", profile.email).putString("name", profile.displayName).apply()
        }
        val picture = profile?.let { loadAvatar(it.avatar, it.isDefaultAvatar) }
        ui {
            signedIn = now || issues
            needsSignIn = issues
            if (profile != null) {
                email = profile.email
                displayName = profile.displayName
                avatar = picture
            }
            if (!now && !issues) {
                email = null
                displayName = null
                avatar = null
            }
        }
        return if (now) mail ?: prefs.getString("email", null) ?: "" else null
    }

    /** Your account's picture, downloaded once (and again when it changes); none for the default one. */
    private fun loadAvatar(url: String, isDefault: Boolean): android.graphics.Bitmap? {
        val file = avatarFile()
        if (isDefault || url.isBlank()) {
            file.delete()
            prefs.edit().remove("avatar_url").apply()
            return null
        }
        if (prefs.getString("avatar_url", null) == url && file.exists()) {
            return android.graphics.BitmapFactory.decodeFile(file.path)
        }
        return runCatching {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            val bytes = try {
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val side = minOf(bmp.width, bmp.height)
            val small = android.graphics.Bitmap.createScaledBitmap(
                android.graphics.Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side),
                192,
                192,
                true,
            )
            file.outputStream().use { small.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            prefs.edit().putString("avatar_url", url).apply()
            small
        }.getOrNull()
    }

    /** Mozilla's page to manage your account (password, data, deleting it), for [open] to load in a tab. */
    fun manageAccount(open: (String) -> Unit) {
        scope.launch {
            val url = runCatching { account?.getManageAccountURL("preferences") }.getOrNull()
                ?: (ACCOUNTS + "/settings")
            ui { open(url) }
        }
    }

    /** Renames this phone in your account. */
    fun renameDevice(name: String) {
        val clean = name.trim().ifEmpty { defaultDeviceName() }.take(64)
        deviceName = clean
        prefs.edit().putString("device_name", clean).apply()
        scope.launch {
            runCatching { account?.setDeviceDisplayName(clean) }
            persist()
        }
    }

    /** Saving and filling passwords on or off (signing in and syncing are left as they are). */
    fun changeFillPasswords(on: Boolean) {
        if (on && !enabled) turn(true)
        fillPasswords = on
        prefs.edit().putBoolean("fill_passwords", on).apply()
        com.verisonder.sonderweb.browser.Passwords.applyOption()
    }

    /** Choose what to sync: passwords on or off (on this phone only; your other devices keep theirs). */
    fun changeSyncPasswords(on: Boolean) {
        syncPasswords = on
        prefs.edit().putBoolean("sync_passwords", on).apply()
        if (on) sync(user = false)
    }

    fun changeSyncBookmarks(on: Boolean) {
        syncBookmarks = on
        prefs.edit().putBoolean("sync_bookmarks", on).apply()
        if (on) sync(user = false)
    }

    fun changeSyncHistory(on: Boolean) {
        syncHistory = on
        prefs.edit().putBoolean("sync_history", on).apply()
        if (on) sync(user = false)
    }

    /** The kinds chosen to sync. */
    private fun chosen(): List<String> = buildList {
        if (prefs.getBoolean("sync_passwords", true)) add(PASSWORDS)
        if (prefs.getBoolean("sync_bookmarks", true)) add(BOOKMARKS)
        if (prefs.getBoolean("sync_history", true)) add(HISTORY)
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

    /**
     * The sign-in page finished: the code to finish signing in with. [done] gets the account's
     * email once signed in (empty if it isn't known), or null if it didn't work.
     */
    fun finishSignIn(code: String, state: String, done: (String?) -> Unit) {
        scope.launch {
            try {
                ready()
                openAccount()
                val mail = settle(account!!.processEvent(FxaEvent.CompleteOAuthFlow(code, state)))
                ui { done(mail) }
                if (connected) syncNow(SyncReason.USER)
            } catch (e: Throwable) {
                fail("Couldn't finish signing in", e)
                ui { done(null) }
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
                prefs.edit().remove("email").remove("name").remove("avatar_url").remove("last_synced").apply()
                avatarFile().delete()
                ui {
                    signedIn = false
                    needsSignIn = false
                    email = null
                    displayName = null
                    avatar = null
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
        val engines = chosen()
        if (engines.isEmpty()) return
        ui {
            syncing = true
            problem = null
        }
        try {
            store()
            places()
            // Anything that couldn't be copied over earlier goes in first.
            if (BOOKMARKS in engines) PlacesMirror.reconcileBookmarks()
            val token = client.getAccessToken(SCOPE_SYNC)
            persist()
            val key = token.key ?: error("The account gave no sync key")
            val params = SyncParams(
                reason = reason,
                engines = SyncEngineSelection.Some(engines),
                enabledChanges = emptyMap(),
                // The passwords database encrypts with its own key (handed to it when it opens);
                // the sync engine takes none and refuses one (bookmarks and history need none).
                localEncryptionKeys = emptyMap(),
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
            // What came from your other devices, into SonderWeb's own lists.
            if (BOOKMARKS in engines && BOOKMARKS !in result.failures) {
                runCatching { PlacesMirror.reconcileBookmarks() }
            }
            if (HISTORY in engines && HISTORY !in result.failures) {
                runCatching { PlacesMirror.importHistory() }
            }
            when (result.status) {
                ServiceStatus.OK -> {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("last_synced", now).apply()
                    val failed = result.failures.entries.firstOrNull()?.let { (kind, why) ->
                        val name = when (kind) {
                            PASSWORDS -> "Passwords"
                            BOOKMARKS -> "Bookmarks"
                            HISTORY -> "History"
                            else -> kind.replaceFirstChar { it.uppercase() }
                        }
                        "$name didn't sync: $why"
                    }
                    ui {
                        lastSynced = now
                        syncing = false
                        problem = failed
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
