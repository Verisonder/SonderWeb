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
import mozilla.appservices.autofill.Store as AutofillStore
import mozilla.appservices.autofill.UpdatableAddressFields
import mozilla.appservices.autofill.UpdatableCreditCardFields
import mozilla.appservices.fxaclient.DeviceCapability
import mozilla.appservices.fxaclient.DeviceConfig
import mozilla.appservices.fxaclient.IncomingDeviceCommand
import mozilla.appservices.fxaclient.FxaClient
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaEvent
import mozilla.appservices.fxaclient.FxaServer
import mozilla.appservices.fxaclient.FxaState
import mozilla.appservices.logins.DatabaseLoginsStorage
import mozilla.appservices.remotetabs.RemoteTabRecord
import mozilla.appservices.remotetabs.TabsStore
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
 * Firefox Sync, an option (off until you turn it on): a Mozilla account, and what SonderWeb
 * keeps (bookmarks, history, open tabs, passwords, addresses, payment cards) synced both ways
 * with Firefox on your other devices. Tabs can be sent between devices too.
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
    private const val SCOPE_VPN = "https://identity.mozilla.com/apps/vpn"

    /** The kinds' names in Firefox Sync. */
    const val PASSWORDS = "passwords"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val TABS = "tabs"
    const val ADDRESSES = "addresses"
    const val CARDS = "creditcards"

    /** How often to sync while SonderWeb is open. */
    private const val EVERY = 15L * 60 * 1000

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

    var syncTabs by mutableStateOf(true)
        private set

    var syncAddresses by mutableStateOf(true)
        private set

    var syncCards by mutableStateOf(true)
        private set

    /** SonderWeb offers to save addresses and payment cards, and fills them in. */
    var fillAddresses by mutableStateOf(true)
        private set

    var fillCards by mutableStateOf(true)
        private set

    var addressCount by mutableIntStateOf(0)
        private set

    var cardCount by mutableIntStateOf(0)
        private set

    /** Changes when bookmarks came in from your other devices (the bookmarks screen reloads). */
    var bookmarksVersion by mutableIntStateOf(0)
        private set

    /** Your other devices and their open tabs, as of the last sync. */
    var remoteDevices by mutableStateOf<List<RemoteDevice>>(emptyList())
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

    /** Signed in, and the account gave SonderWeb the VPN permission (accounts signed in before
     *  the VPN existed here don't have it: they sign in again once). */
    var vpnAllowed by mutableStateOf(false)
        private set

    // ---- Worker-thread state ----

    private var rustReady = false

    /** Signed in, as the sync thread knows it (the screens' copy follows a moment later). */
    private var connected = false
    private var account: FxaClient? = null
    private var manager: SyncManager? = null
    private var logins: DatabaseLoginsStorage? = null
    private var tabsStore: TabsStore? = null
    private var autofill: AutofillStore? = null
    private var autofillKey: String? = null

    /** A sync is waiting to run (another isn't queued behind it). */
    private val queued = java.util.concurrent.atomic.AtomicBoolean(false)
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
        syncTabs = prefs.getBoolean("sync_tabs", true)
        syncAddresses = prefs.getBoolean("sync_addresses", true)
        syncCards = prefs.getBoolean("sync_cards", true)
        fillAddresses = prefs.getBoolean("fill_addresses", true)
        fillCards = prefs.getBoolean("fill_cards", true)
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
            countAutofill()
            places()
            showRemoteTabs()
            if (syncSoon && connected) {
                main.postDelayed({
                    sync(user = false)
                    // Opened before the account was ready: the every-15-minutes syncs start now.
                    if (visibleNow) {
                        main.removeCallbacks(every)
                        main.postDelayed(every, EVERY)
                    }
                }, 5000)
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

    /** A change made in SonderWeb that Firefox Sync must hear about right away (only while the option is on). */
    private fun mirror(block: PlacesMirror.() -> Unit) {
        if (!enabled) return
        scope.launch { runCatching { places().block() } }
    }

    fun noteHistoryRemoved(url: String) {
        mirror { historyRemoved(url) }
        syncSoon()
    }

    fun noteHistoryCleared() {
        mirror { historyCleared() }
        syncSoon()
    }

    /** Bookmarks changed in SonderWeb: they go out at a sync a few seconds from now. */
    fun noteBookmarksChanged() = syncSoon()

    // ---- When to sync ----

    /** SonderWeb is on screen (main thread). */
    private var visibleNow = false

    private val soon = Runnable { sync(user = false) }
    private val every = object : Runnable {
        override fun run() {
            sync(user = false)
            main.postDelayed(this, EVERY)
        }
    }

    /** A sync shortly (changes made one after another go out together). */
    fun syncSoon() {
        if (!enabled || !signedIn) return
        main.removeCallbacks(soon)
        main.postDelayed(soon, 4000)
    }

    /**
     * SonderWeb came to the front or left it. In front: tabs sent to this phone come in, it
     * syncs if it's been a while, then every 15 minutes. Leaving: a sync, so your other devices
     * get this phone's latest history and open tabs.
     */
    fun appVisible(visible: Boolean) {
        visibleNow = visible
        main.removeCallbacks(every)
        if (!enabled || !signedIn) return
        if (visible) {
            if (System.currentTimeMillis() - lastSynced > 60_000L) sync(user = false) else pollCommands()
            main.postDelayed(every, EVERY)
        } else {
            sync(user = false)
        }
    }

    private fun deviceConfig() = DeviceConfig(
        name = deviceName(),
        deviceType = DeviceType.MOBILE,
        capabilities = listOf(DeviceCapability.SEND_TAB, DeviceCapability.CLOSE_TABS),
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
        val vpn = now && runCatching { account?.hasScope(SCOPE_VPN) }.getOrNull() == true
        if (now && !prefs.getBoolean("caps_v1", false)) {
            // Accounts signed in before tabs could be sent: this phone now takes them.
            runCatching {
                account?.ensureCapabilities(setOf(DeviceCapability.SEND_TAB, DeviceCapability.CLOSE_TABS))
                prefs.edit().putBoolean("caps_v1", true).apply()
            }
        }
        persist()
        if (profile != null) {
            prefs.edit().putString("email", profile.email).putString("name", profile.displayName).apply()
        }
        val picture = profile?.let { loadAvatar(it.avatar, it.isDefaultAvatar) }
        ui {
            signedIn = now || issues
            needsSignIn = issues
            vpnAllowed = vpn
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

    fun changeSyncTabs(on: Boolean) {
        syncTabs = on
        prefs.edit().putBoolean("sync_tabs", on).apply()
        if (on) {
            sync(user = false)
        } else {
            remoteDevices = emptyList()
        }
    }

    fun changeSyncAddresses(on: Boolean) {
        syncAddresses = on
        prefs.edit().putBoolean("sync_addresses", on).apply()
        if (on) sync(user = false)
    }

    fun changeSyncCards(on: Boolean) {
        syncCards = on
        prefs.edit().putBoolean("sync_cards", on).apply()
        if (on) sync(user = false)
    }

    /** Saving and filling addresses on or off. */
    fun changeFillAddresses(on: Boolean) {
        if (on && !enabled) turn(true)
        fillAddresses = on
        prefs.edit().putBoolean("fill_addresses", on).apply()
        com.verisonder.sonderweb.browser.Passwords.applyOption()
    }

    /** Saving and filling payment cards on or off. */
    fun changeFillCards(on: Boolean) {
        if (on && !enabled) turn(true)
        fillCards = on
        prefs.edit().putBoolean("fill_cards", on).apply()
        com.verisonder.sonderweb.browser.Passwords.applyOption()
    }

    /** The kinds chosen to sync. */
    private fun chosen(): List<String> = buildList {
        if (prefs.getBoolean("sync_bookmarks", true)) add(BOOKMARKS)
        if (prefs.getBoolean("sync_history", true)) add(HISTORY)
        if (prefs.getBoolean("sync_tabs", true)) add(TABS)
        if (prefs.getBoolean("sync_passwords", true)) add(PASSWORDS)
        if (prefs.getBoolean("sync_addresses", true)) add(ADDRESSES)
        if (prefs.getBoolean("sync_cards", true)) add(CARDS)
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
                        scopes = listOf(SCOPE_PROFILE, SCOPE_SYNC, SCOPE_VPN),
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
                ui {
                    done(mail)
                    if (connected) sync(user = true)
                }
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
                    vpnAllowed = false
                    email = null
                    displayName = null
                    avatar = null
                    lastSynced = 0L
                    syncing = false
                    remoteDevices = emptyList()
                }
            }
        }
    }

    /** Its own thread, so the VPN never waits behind a long sync. */
    private val vpnWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "fxvpn").apply { isDaemon = true } }

    /**
     * A Mozilla account token for Firefox's built-in VPN, or null (signed out, no permission, no
     * network). [done] runs on the main thread. The account keeps it cached until it expires.
     */
    fun vpnToken(done: (String?) -> Unit) {
        val client = account
        if (client == null || !connected) {
            done(null)
            return
        }
        vpnWorker.execute {
            val token = runCatching { client.getAccessToken(SCOPE_VPN).token }.getOrNull()
            scope.launch { persist() }
            ui { done(token?.takeIf { it.isNotEmpty() }) }
        }
    }

    /** Sync now (from Settings, after a change, at startup, every so often). Call on the main thread. */
    fun sync(user: Boolean = true) {
        if (!enabled || !signedIn) return
        main.removeCallbacks(soon)
        val tabs = localTabs()
        if (!queued.compareAndSet(false, true)) return
        scope.launch {
            queued.set(false)
            syncNow(if (user) SyncReason.USER else SyncReason.SCHEDULED, tabs)
        }
    }

    /** This phone's open tabs (not private ones), for your other devices to see. */
    private fun localTabs(): List<RemoteTabRecord> = runCatching {
        // Firefox Sync follows the first profile: in another one, its saved tabs are sent.
        if (com.verisonder.sonderweb.data.Profiles.current.isNotEmpty()) {
            val p = com.verisonder.sonderweb.data.Prefs(app)
            val titles = p.savedTabTitles
            val used = p.savedTabUsed
            return@runCatching p.savedTabs.mapIndexedNotNull { i, url ->
                if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapIndexedNotNull null
                RemoteTabRecord(
                    title = titles.getOrNull(i)?.ifBlank { null } ?: url,
                    urlHistory = listOf(url),
                    icon = null,
                    lastUsed = used.getOrNull(i) ?: 0L,
                )
            }.sortedByDescending { it.lastUsed }
        }
        com.verisonder.sonderweb.browser.Browser.tabs
            .filter { !it.isPrivate }
            .mapNotNull { t ->
                val url = t.url.ifEmpty { t.pendingUrl ?: "" }
                if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
                RemoteTabRecord(
                    title = t.title.ifBlank { url },
                    urlHistory = listOf(url),
                    icon = null,
                    lastUsed = t.lastUsed,
                )
            }
            .sortedByDescending { it.lastUsed }
    }.getOrDefault(emptyList())

    private fun syncNow(reason: SyncReason, tabs: List<RemoteTabRecord>) {
        val client = account ?: return
        val engines = chosen()
        if (engines.isEmpty()) {
            pollCommands()
            return
        }
        ui {
            syncing = true
            problem = null
        }
        try {
            store()
            places()
            val keys = HashMap<String, String>()
            if (ADDRESSES in engines || CARDS in engines) {
                autofillStore()
                autofillKey?.let { keys[CARDS] = it }
            }
            tabStore().setLocalTabs(tabs)
            // What changed here goes in first.
            if (HISTORY in engines) runCatching { PlacesMirror.pushHistory() }
            if (BOOKMARKS in engines) runCatching { PlacesMirror.reconcileBookmarks() }
            val token = client.getAccessToken(SCOPE_SYNC)
            persist()
            val key = token.key ?: error("The account gave no sync key")
            val params = SyncParams(
                reason = reason,
                engines = SyncEngineSelection.Some(engines),
                enabledChanges = emptyMap(),
                // Only payment cards take a key here (their numbers are encrypted with it on this
                // phone); the passwords database has its own, handed to it when it opens.
                localEncryptionKeys = keys.filterKeys { it in engines },
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
            countAutofill()
            // What came from your other devices, into SonderWeb's own lists.
            if (BOOKMARKS in engines && BOOKMARKS !in result.failures) {
                val came = runCatching { PlacesMirror.reconcileBookmarks() }.getOrDefault(false)
                if (came) ui { bookmarksVersion++ }
            }
            if (HISTORY in engines && HISTORY !in result.failures) {
                runCatching { PlacesMirror.pullHistory(app) }
            }
            if (TABS in engines) showRemoteTabs()
            pollCommands()
            when (result.status) {
                ServiceStatus.OK -> {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("last_synced", now).apply()
                    val failed = result.failures.entries.firstOrNull()?.let { (kind, why) ->
                        "${kindName(kind)} didn't sync: $why"
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

    private fun kindName(kind: String) = when (kind) {
        PASSWORDS -> "Passwords"
        BOOKMARKS -> "Bookmarks"
        HISTORY -> "History"
        TABS -> "Open tabs"
        ADDRESSES -> "Addresses"
        CARDS -> "Payment cards"
        else -> kind.replaceFirstChar { it.uppercase() }
    }

    // ---- Open tabs and sending tabs ----

    private fun tabStore(): TabsStore {
        tabsStore?.let { return it }
        ready()
        val path = app.getDatabasePath("tabs.sqlite")
        path.parentFile?.mkdirs()
        return TabsStore(path.absolutePath).also {
            it.registerWithSyncManager()
            tabsStore = it
        }
    }

    /** Your other devices' open tabs, as the last sync brought them, onto the screens. */
    private fun showRemoteTabs() {
        if (!prefs.getBoolean("sync_tabs", true) || !connected) return
        val all = runCatching { tabStore().getAll() }.getOrDefault(emptyList())
        val devices = all
            .map { c ->
                RemoteDevice(
                    c.clientId,
                    c.clientName,
                    c.deviceType.name.lowercase(),
                    c.lastModified,
                    c.remoteTabs
                        .mapNotNull { t ->
                            val url = t.urlHistory.firstOrNull() ?: return@mapNotNull null
                            RemoteTabInfo(t.title.ifBlank { url }, url, t.lastUsed)
                        }
                        .sortedByDescending { it.lastUsed },
                )
            }
            .filter { it.tabs.isNotEmpty() }
            .sortedByDescending { it.lastModified }
        ui { remoteDevices = devices }
    }

    /** The devices a tab can be sent to (handed to [done] on the main thread; empty if none or offline). */
    fun loadSendTargets(done: (List<SendTarget>) -> Unit) {
        if (!enabled || !signedIn) return done(emptyList())
        scope.launch {
            val list = runCatching {
                account?.getDevices(true).orEmpty()
                    .filter { !it.isCurrentDevice && DeviceCapability.SEND_TAB in it.capabilities }
                    .map { SendTarget(it.id, it.displayName, it.deviceType.name.lowercase()) }
            }.getOrDefault(emptyList())
            ui { done(list) }
        }
    }

    /** Sends a page to one of your devices; [done] gets whether it went. */
    fun sendTab(target: SendTarget, title: String, url: String, done: (Boolean) -> Unit) {
        scope.launch {
            val ok = runCatching { account!!.sendSingleTab(target.id, title.ifBlank { url }, url) }.isSuccess
            persist()
            ui { done(ok) }
        }
    }

    /** Tabs sent to this phone open; tabs your other devices closed here, close. */
    private fun pollCommands() {
        if (!connected) return
        scope.launch {
            val commands = runCatching { account?.pollDeviceCommands().orEmpty() }.getOrDefault(emptyArray())
            persist()
            for (c in commands) {
                when (c) {
                    is IncomingDeviceCommand.TabReceived -> {
                        val entry = c.payload.entries.lastOrNull() ?: continue
                        if (!entry.url.startsWith("http://") && !entry.url.startsWith("https://")) continue
                        val from = c.sender?.displayName
                        ui {
                            com.verisonder.sonderweb.browser.Browser.newTab(entry.url, isPrivate = false)
                            com.verisonder.sonderweb.browser.Browser.host?.toast(
                                if (from.isNullOrBlank()) "Tab received" else "Tab from $from",
                            )
                        }
                    }
                    is IncomingDeviceCommand.TabsClosed -> {
                        val urls = c.payload.urls.toSet()
                        ui {
                            val b = com.verisonder.sonderweb.browser.Browser
                            b.tabs.filter { !it.isPrivate && it.url in urls }.forEach { b.close(it) }
                        }
                    }
                }
            }
        }
    }

    // ---- Addresses and payment cards ----

    private fun autofillStore(): AutofillStore {
        autofill?.let { return it }
        ready()
        autofillKey = secrets.get("autofill_key") ?: mozilla.appservices.autofill.createAutofillKey().also {
            secrets.put("autofill_key", it)
        }
        val path = app.getDatabasePath("autofill.sqlite")
        path.parentFile?.mkdirs()
        return AutofillStore(path.absolutePath).also {
            it.registerWithSyncManager()
            autofill = it
        }
    }

    /** Runs [block] with the addresses and cards database and its key, on the sync thread. */
    internal fun withAutofill(block: (AutofillStore, String) -> Unit) {
        if (!enabled) return
        scope.launch {
            try {
                val store = autofillStore()
                block(store, autofillKey!!)
            } catch (e: Throwable) {
                fail("Addresses and cards", e)
            }
        }
    }

    private fun countAutofill() {
        val a = runCatching { autofillStore().countAllAddresses().toInt() }.getOrDefault(0)
        val c = runCatching { autofillStore().countAllCreditCards().toInt() }.getOrDefault(0)
        ui {
            addressCount = a
            cardCount = c
        }
    }

    internal fun autofillChanged() {
        scope.launch { countAutofill() }
        main.post { syncSoon() }
    }

    internal fun addressOf(a: mozilla.appservices.autofill.Address) = SavedAddress(
        a.guid, a.name, a.organization, a.streetAddress, a.addressLevel3, a.addressLevel2, a.addressLevel1,
        a.postalCode, a.country, a.tel, a.email,
    )

    internal fun fieldsOf(a: SavedAddress) = UpdatableAddressFields(
        name = a.name,
        organization = a.organization,
        streetAddress = a.street,
        addressLevel3 = a.suburb,
        addressLevel2 = a.city,
        addressLevel1 = a.region,
        postalCode = a.postalCode,
        country = a.country,
        tel = a.tel,
        email = a.email,
    )

    internal fun cardOf(c: mozilla.appservices.autofill.CreditCard) = SavedCard(
        c.guid, c.ccName, c.ccNumberLast4, c.ccExpMonth.toInt(), c.ccExpYear.toInt(), c.ccType,
    )

    /** The card's fields, its number encrypted with [key] (only the last four digits kept as they are). */
    internal fun cardFields(name: String, number: String, month: Int, year: Int, key: String): UpdatableCreditCardFields {
        val digits = number.filter { it.isDigit() }
        return UpdatableCreditCardFields(
            ccName = name,
            ccNumberEnc = mozilla.appservices.autofill.encryptString(key, digits),
            ccNumberLast4 = digits.takeLast(4),
            ccExpMonth = month.toLong(),
            ccExpYear = (if (year in 0..99) 2000 + year else year).toLong(),
            ccType = cardType(digits),
        )
    }

    private fun cardType(n: String): String = when {
        n.startsWith("4") -> "visa"
        n.matches(Regex("^(5[1-5]|2[2-7]).*")) -> "mastercard"
        n.startsWith("34") || n.startsWith("37") -> "amex"
        n.startsWith("6011") || n.startsWith("65") -> "discover"
        n.startsWith("35") -> "jcb"
        n.startsWith("36") || n.startsWith("38") || n.startsWith("30") -> "diners"
        n.startsWith("62") -> "unionpay"
        n.startsWith("220") -> "mir"
        else -> ""
    }

    fun listAddresses(done: (List<SavedAddress>) -> Unit) {
        withAutofill { store, _ ->
            val all = runCatching { store.getAllAddresses() }.getOrDefault(emptyList()).map { addressOf(it) }
            ui { done(all.sortedBy { it.summary.lowercase() }) }
        }
    }

    fun saveAddress(a: SavedAddress, done: () -> Unit = {}) {
        withAutofill { store, _ ->
            if (a.guid != null) store.updateAddress(a.guid, fieldsOf(a)) else store.addAddress(fieldsOf(a))
            autofillChanged()
            ui { done() }
        }
    }

    fun deleteAddress(guid: String, done: () -> Unit = {}) {
        withAutofill { store, _ ->
            runCatching { store.deleteAddress(guid) }
            autofillChanged()
            ui { done() }
        }
    }

    fun listCards(done: (List<SavedCard>) -> Unit) {
        withAutofill { store, _ ->
            val all = runCatching { store.getAllCreditCards() }.getOrDefault(emptyList()).map { cardOf(it) }
            ui { done(all) }
        }
    }

    /** Saves a card. [number] empty when editing: the saved number stays. */
    fun saveCard(card: SavedCard, number: String, done: () -> Unit = {}) {
        withAutofill { store, key ->
            if (card.guid != null) {
                val old = store.getCreditCard(card.guid)
                val fields = if (number.isBlank()) {
                    UpdatableCreditCardFields(
                        ccName = card.name,
                        ccNumberEnc = old.ccNumberEnc,
                        ccNumberLast4 = old.ccNumberLast4,
                        ccExpMonth = card.month.toLong(),
                        ccExpYear = (if (card.year in 0..99) 2000 + card.year else card.year).toLong(),
                        ccType = old.ccType,
                    )
                } else {
                    cardFields(card.name, number, card.month, card.year, key)
                }
                store.updateCreditCard(card.guid, fields)
            } else {
                store.addCreditCard(cardFields(card.name, number, card.month, card.year, key))
            }
            autofillChanged()
            ui { done() }
        }
    }

    fun deleteCard(guid: String, done: () -> Unit = {}) {
        withAutofill { store, _ ->
            runCatching { store.deleteCreditCard(guid) }
            autofillChanged()
            ui { done() }
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
