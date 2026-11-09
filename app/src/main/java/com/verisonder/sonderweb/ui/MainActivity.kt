package com.verisonder.sonderweb.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.KeyguardManager
import android.app.SearchManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.core.AddressInput
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt
import org.mozilla.geckoview.GeckoView
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity(), Browser.Host {

    private companion object {
        const val PIP_TOGGLE = "com.verisonder.sonderweb.PIP_TOGGLE"
        val NO_BIOMETRICS = setOf(
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
        )
    }

    lateinit var geckoView: GeckoView
        private set
    val snackbar = SnackbarHostState()

    /** True while a page shows a video (or anything) in fullscreen. */
    var inFullscreen by mutableStateOf(false)
        private set

    /** In the small picture-in-picture window: only the page (its video) shows. */
    var inPip by mutableStateOf(false)

    /** The page takes the whole window: a fullscreen video, or picture-in-picture. */
    val fullView: Boolean get() = inFullscreen || inPip

    private val pipSupported by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    /** The play/pause button in the picture-in-picture window. */
    private val pipButton = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            com.verisonder.sonderweb.browser.Pip.togglePlay()
        }
    }

    private var filesResult: ((List<Uri>) -> Unit)? = null
    private var permsResult: ((Boolean) -> Unit)? = null

    /** True while we've sent you to a screen of our own (file picker, share sheet, screen lock):
     *  that isn't leaving the app, so private tabs stay unlocked. */
    private var ownScreen = false

    /** Opening the app (or coming back to it): check whether the start page should show. */
    private var launchCheck = true

    /** This screen was just opened from scratch (the app had been closed), not brought back. */
    private var freshStart = false

    private val confirmLock = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        authenticating = false
        if (r.resultCode == Activity.RESULT_OK) Browser.privateLocked = false
    }

    private val pickOne = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        filesResult?.invoke(listOfNotNull(uri))
        filesResult = null
    }
    private val pickMany = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        filesResult?.invoke(uris)
        filesResult = null
    }
    /** When the "default browser" question was shown, to tell a real answer from one the phone
     *  refused to show (some phones, Xiaomi's among them, close it at once). */
    private var askedDefaultAt = 0L

    private val askDefault = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ownScreen = false
        if (isDefaultBrowser()) {
            toast("SonderWeb is now your default browser")
        } else if (android.os.SystemClock.elapsedRealtime() - askedDefaultAt < 700) {
            // Closed straight away without asking: the phone's own setting instead.
            openDefaultAppsSettings()
        }
    }

    private fun isDefaultBrowser(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                return rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
            }
        }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val info = packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun openDefaultAppsSettings() {
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            ownScreen = true
        }.onFailure {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }
    }

    override fun askDefaultBrowser() {
        if (isDefaultBrowser()) {
            // Already the default: the phone's setting, in case you want to change it.
            openDefaultAppsSettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                // Android's own "Set SonderWeb as default?" question. It only works when started
                // for a result (started any other way it closes without showing anything).
                askedDefaultAt = android.os.SystemClock.elapsedRealtime()
                ownScreen = true
                runCatching { askDefault.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)) }
                    .onFailure { openDefaultAppsSettings() }
                return
            }
        }
        openDefaultAppsSettings()
    }

    /** Whether SonderWeb opens links from other apps (for Settings). */
    fun defaultBrowser(): Boolean = isDefaultBrowser()

    // ---- The search widget ----

    /** The widget asked for the search to open (BrowserScreen opens it on a fresh tab). */
    var openSearch by mutableStateOf(false)

    /** The widget's picture button: "Take a photo / Choose a photo" is showing. */
    var askImage by mutableStateOf(false)

    /** Where the camera saves the photo for an image search. */
    private var photoUri: Uri? = null

    private val voiceSearch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        ownScreen = false
        val heard = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) Browser.go(heard, freshTab())
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        ownScreen = false
        val uri = photoUri
        if (ok && uri != null) searchImage(uri)
    }

    private val choosePhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        ownScreen = false
        if (uri != null) searchImage(uri)
    }

    /** A new tab to search in: the empty tab you're on, or one you have, or a new one. */
    private fun freshTab(): Tab {
        val cur = Browser.current
        if (cur != null && cur.isBlank && !cur.isPrivate) return cur
        Browser.tabs.lastOrNull { !it.isPrivate && it.isBlank }?.let {
            Browser.select(it)
            return it
        }
        return Browser.newTab(null, isPrivate = false)
    }

    private fun searchImage(uri: Uri) {
        com.verisonder.sonderweb.browser.ImageSearch.search(this, uri) { page -> Browser.load(freshTab(), page) }
    }

    private fun startVoiceSearch() {
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search")
        try {
            ownScreen = true
            voiceSearch.launch(intent)
        } catch (_: ActivityNotFoundException) {
            ownScreen = false
            toast("No voice input on this phone")
        }
    }

    fun takePhotoForSearch() {
        requestAndroidPermissions(arrayOf(android.Manifest.permission.CAMERA)) { granted ->
            if (!granted) {
                toast("SonderWeb needs the camera for this")
                return@requestAndroidPermissions
            }
            val dir = java.io.File(cacheDir, "images").apply { mkdirs() }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", java.io.File(dir, "search.jpg"))
            photoUri = uri
            try {
                ownScreen = true
                takePhoto.launch(uri)
            } catch (_: ActivityNotFoundException) {
                ownScreen = false
                toast("No camera app on this phone")
            }
        }
    }

    fun choosePhotoForSearch() {
        try {
            ownScreen = true
            choosePhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            ownScreen = false
        }
    }

    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        ownScreen = false
        permsResult?.invoke(r.isNotEmpty() && r.values.all { it })
        permsResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Rebuilt (turned, theme changed): that isn't opening the app.
        launchCheck = savedInstanceState == null
        freshStart = savedInstanceState == null
        Browser.start(this)
        Browser.host = this
        geckoView = com.verisonder.sonderweb.browser.PullGeckoView(this)

        setContent { BrowserScreen(this) }
        applySystemBars()
        preferHighRefreshRate()
        // No grey scrim behind the gesture bar: the page shows through.
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) handleIntent(intent)
        ContextCompat.registerReceiver(this, pipButton, android.content.IntentFilter(PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch { com.verisonder.sonderweb.data.AppUpdate.autoCheck(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkPipEnded()
        // Back in front at full size: the page may pause its video again (its own pause button).
        if (!isInPictureInPictureMode) Browser.current?.let { com.verisonder.sonderweb.browser.Pip.hold(it, false) }
        Browser.host = this
        if (launchCheck) {
            launchCheck = false
            Browser.openStartPageIfDue(freshStart)
        }
        freshStart = false
        if (unlockOnResume && Browser.privateLocked) unlockPrivate()
    }

    override fun onStart() {
        super.onStart()
        ownScreen = false
        com.verisonder.sonderweb.browser.Extensions.wake()
        wake()
    }

    /**
     * Back in the app: the system threw away the page's drawing surface while you were away.
     * Re-activating the tab makes the engine paint it again, instead of leaving it black.
     */
    private fun wake() {
        val t = Browser.current ?: return
        if (!t.ready || !t.session.isOpen) return
        t.session.setActive(true)
        t.session.setFocused(true)
        // Extensions (uBlock's panel) see this tab as the one in front again.
        runCatching { Browser.runtime.webExtensionController.setTabActive(t.session, true) }
        if (::geckoView.isInitialized) {
            // The page's own colour until the engine has drawn it again (no black flash on return).
            (t.edgeBottom ?: t.edgeTop)?.let { geckoView.coverUntilFirstPaint(it) }
            geckoView.requestLayout()
            geckoView.invalidate()
            // If it still comes back empty, the page is reloaded (see Delegates.checkPainted).
            geckoView.postDelayed({ if (Browser.current === t) com.verisonder.sonderweb.browser.Delegates.checkPainted(t) }, 800)
            // Once more a bit later, for a page that's slow to come back.
            geckoView.postDelayed({ if (Browser.current === t) com.verisonder.sonderweb.browser.Delegates.checkPainted(t) }, 3500)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipButton) }
        if (Browser.host === this) Browser.host = null
        super.onDestroy()
    }

    override fun onPause() {
        // A fresh picture of the page you're leaving, for the tab switcher next time.
        Browser.current?.let { com.verisonder.sonderweb.browser.Delegates.capture(it) }
        // Leaving (or going small) with a video playing: from now on the page may not pause it.
        Browser.current?.let { if (com.verisonder.sonderweb.browser.Pip.playingNow(it)) com.verisonder.sonderweb.browser.Pip.hold(it, true) }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        Browser.current?.let { it.flatWhenLeft = it.lastFlat }
        Browser.saveTabs()
        // Still on screen in the small picture-in-picture window: the page keeps playing, and
        // private tabs don't lock under it. That happens when the window is closed.
        if (isInPictureInPictureMode) return
        // Something is playing (music, a video): it keeps playing while you're away or the screen
        // is off, with the media notification. Otherwise the page is paused to save battery.
        if (com.verisonder.sonderweb.browser.MediaPlayback.keepAwake) {
            Browser.current?.let { com.verisonder.sonderweb.browser.Pip.hold(it, true) }
            if (!ownScreen && !isChangingConfigurations) {
                Browser.leftApp()
                launchCheck = true
            }
            return
        }
        // Paused while you're away (saves battery); woken again in onStart.
        Browser.current?.let { t -> if (t.ready && t.session.isOpen && !isChangingConfigurations) t.session.setActive(false) }
        if (!ownScreen && !isChangingConfigurations) {
            Browser.leftApp()
            launchCheck = true
        }
    }

    /** Links from other apps open in a new tab; shared text and searches too. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            SearchWidget.SEARCH, SearchWidget.VOICE, SearchWidget.IMAGE -> {
                // From the home screen widget: that is what you see, not the start page.
                launchCheck = false
                when (intent.action) {
                    SearchWidget.SEARCH -> {
                        freshTab()
                        openSearch = true
                    }
                    SearchWidget.VOICE -> startVoiceSearch()
                    else -> askImage = true
                }
                return
            }
        }
        if (intent.getBooleanExtra(com.verisonder.sonderweb.browser.DownloadNotifications.EXTRA_SHOW_DOWNLOADS, false)) {
            launchCheck = false
            com.verisonder.sonderweb.browser.Downloads.showPage = true
            return
        }
        val target: String? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("""https?://\S+""").find(text)?.value ?: text
            }
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (target.isNullOrBlank()) return
        // Opened for a link: that link is what you see, not the start page.
        launchCheck = false
        val url = if (intent.action == Intent.ACTION_VIEW) target else {
            AddressInput.resolve(target, Browser.prefs.engine())?.url ?: return
        }
        val cur = Browser.current
        if (cur != null && cur.isBlank && !cur.isPrivate) Browser.load(cur, url) else Browser.newTab(url, isPrivate = false)
    }

    // ---- Browser.Host ----

    override fun geckoViewFor(tab: Tab): GeckoView? =
        if (::geckoView.isInitialized && geckoView.session === tab.session) geckoView else null

    override fun launch(intent: Intent): Boolean = try {
        startActivity(intent)
        ownScreen = true
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    override fun share(text: String, title: String?) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (!title.isNullOrBlank()) send.putExtra(Intent.EXTRA_SUBJECT, title)
        runCatching {
            startActivity(Intent.createChooser(send, null))
            ownScreen = true
        }
    }

    /**
     * The bar is swiped down out of sight: the gesture bar and the status bar go too (swipe from
     * the edge to see them).
     */
    var hideNavigation = false
        set(v) {
            if (field == v) return
            field = v
            applySystemBars()
        }

    override fun setFullscreen(on: Boolean) {
        inFullscreen = on
        applySystemBars()
    }

    /** Fullscreen video hides everything; otherwise the status bar follows Settings › Appearance. */
    override fun applySystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (inFullscreen || inPip) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            return
        }
        if (hideNavigation) {
            c.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            c.show(WindowInsetsCompat.Type.navigationBars())
        }
        val prefs = Browser.prefs
        // Dynamic: only while the page is at its top, or on the start page.
        val cur = Browser.current
        val atTop = cur == null || cur.isBlank || cur.atTop
        if (prefs.showStatusBar && !hideNavigation && (!prefs.statusBarDynamic || atTop)) {
            c.show(WindowInsetsCompat.Type.statusBars())
        } else {
            c.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // ---- Picture-in-picture ----

    /** The small window: the video's shape and a play/pause button. Opens by itself when you leave (Android 12+). */
    private fun pipParams(): android.app.PictureInPictureParams {
        val ready = com.verisonder.sonderweb.browser.Pip.ready
        // Android only allows shapes between 1:2.39 and 2.39:1.
        val ratio = (Browser.current?.videoRatio ?: (16f / 9f)).coerceIn(0.42f, 2.38f)
        val icon = android.graphics.drawable.Icon.createWithResource(
            this,
            if (ready) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        val tap = android.app.PendingIntent.getBroadcast(
            this,
            0,
            Intent(PIP_TOGGLE).setPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = if (ready) "Pause" else "Play"
        val b = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational((ratio * 1000).toInt(), 1000))
            .setActions(listOf(android.app.RemoteAction(icon, label, label, tap)))
        if (Build.VERSION.SDK_INT >= 31) {
            // Swiping home while a video plays glides it into the small window.
            b.setAutoEnterEnabled(ready)
            b.setSeamlessResizeEnabled(false)
        }
        return b.build()
    }

    override fun updatePip() {
        if (!pipSupported) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Leaving with a video playing: into the small window. Android 12+ may already be doing
        // it by itself; asking again is harmless, and some phones (HyperOS) only do it this way.
        if (com.verisonder.sonderweb.browser.Pip.ready) {
            Browser.current?.let { com.verisonder.sonderweb.browser.Pip.hold(it, true) }
            enterPip()
        }
    }

    /** Opens the small window now (also from the ⋮ menu). False when the phone won't. */
    override fun enterPip(): Boolean {
        if (!pipSupported || isInPictureInPictureMode) return false
        return runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipChanged(isInPictureInPictureMode)
    }

    /**
     * Some phones (HyperOS) don't always say the small window has closed when you come back to
     * the app. Without this the app stayed in "small window" mode: no bar, no back, and the page
     * still laid out for the small window. So it's checked again whenever the app is in front.
     */
    private fun checkPipEnded() {
        if (inPip && !isInPictureInPictureMode) pipChanged(false)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        checkPipEnded()
    }

    private fun pipChanged(on: Boolean) {
        if (inPip == on) return
        inPip = on
        com.verisonder.sonderweb.browser.Pip.shown(on)
        applySystemBars()
        if (!on) {
            // The small window closed. Tapped to open the app: it comes back to the front. Closed
            // with its ✕: the app isn't on screen any more, so the video stops and you've left.
            window.decorView.postDelayed({
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    com.verisonder.sonderweb.browser.Pip.pause()
                    Browser.current?.let { t -> if (t.ready && t.session.isOpen) t.session.setActive(false) }
                    if (!ownScreen) {
                        Browser.leftApp()
                        launchCheck = true
                    }
                }
            }, 400)
        }
    }

    /** No screenshots, screen recording or recent-apps preview while private tabs are on screen. */
    fun setSecure(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Asked while the app wasn't on screen: ask once it is. */
    private var unlockOnResume = false

    /** A fingerprint / screen lock prompt is already up: don't stack a second one. */
    private var authenticating = false

    override fun unlockPrivate() {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            unlockOnResume = true
            return
        }
        unlockOnResume = false
        if (authenticating) return
        val km = getSystemService(KeyguardManager::class.java)
        // No screen lock on the phone: nothing to check against.
        if (km == null || !km.isDeviceSecure) {
            Browser.privateLocked = false
            return
        }
        if (Build.VERSION.SDK_INT < 28) {
            confirmScreenLock(km)
            return
        }
        val b = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle("Unlock private tabs")
        when {
            Build.VERSION.SDK_INT >= 30 -> b.setAllowedAuthenticators(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            Build.VERSION.SDK_INT == 29 -> @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
            else -> b.setNegativeButton("Use screen lock", mainExecutor) { _, _ -> confirmScreenLock(km) }
        }
        ownScreen = true
        authenticating = true
        b.build().authenticate(
            CancellationSignal(),
            mainExecutor,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                    ownScreen = false
                    authenticating = false
                    Browser.privateLocked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    ownScreen = false
                    authenticating = false
                    // Android 9 without a fingerprint: fall back to the PIN / pattern.
                    if (Build.VERSION.SDK_INT == 28 && errorCode in NO_BIOMETRICS) confirmScreenLock(km)
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun confirmScreenLock(km: KeyguardManager) {
        val i = km.createConfirmDeviceCredentialIntent("Unlock private tabs", null)
        if (i == null) {
            Browser.privateLocked = false
            return
        }
        ownScreen = true
        authenticating = true
        runCatching { confirmLock.launch(i) }.onFailure { authenticating = false }
    }

    /** Ask for the screen's fastest refresh rate (120 Hz on the 12T); some phones keep apps at 60 Hz otherwise. */
    private fun preferHighRefreshRate() {
        runCatching {
            @Suppress("DEPRECATION")
            val d = if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
            val cur = d?.mode ?: return
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun requestAndroidPermissions(perms: Array<String>, result: (Boolean) -> Unit) {
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            result(true)
            return
        }
        permsResult?.invoke(false)
        permsResult = result
        ownScreen = true
        askPerms.launch(missing.toTypedArray())
    }

    override fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, result: (List<Uri>) -> Unit) {
        filesResult?.invoke(emptyList())
        filesResult = result
        val types = mimeTypes.ifEmpty { arrayOf("*/*") }
        try {
            ownScreen = true
            if (multiple) pickMany.launch(types) else pickOne.launch(types)
        } catch (_: ActivityNotFoundException) {
            filesResult = null
            result(emptyList())
        }
    }

    override fun pickDateTime(type: Int, value: String?, min: String?, max: String?, result: (String?) -> Unit) {
        val cal = Calendar.getInstance()
        parseInto(cal, type, value)
        var answered = false
        fun answer(v: String?) {
            if (!answered) {
                answered = true
                result(v)
            }
        }
        val pickTime = { date: String? ->
            val d = TimePickerDialog(
                this,
                { _, h, m ->
                    val t = String.format(Locale.US, "%02d:%02d", h, m)
                    answer(if (date == null) t else "${date}T$t")
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            )
            d.setOnCancelListener { answer(null) }
            d.show()
        }
        if (type == DateTimePrompt.Type.TIME) {
            pickTime(null)
            return
        }
        val d = DatePickerDialog(
            this,
            { _, y, mo, day ->
                val c = Calendar.getInstance().apply { set(y, mo, day) }
                when (type) {
                    DateTimePrompt.Type.MONTH -> answer(String.format(Locale.US, "%04d-%02d", y, mo + 1))
                    DateTimePrompt.Type.WEEK -> {
                        c.firstDayOfWeek = Calendar.MONDAY
                        c.minimalDaysInFirstWeek = 4
                        val wy = c.getWeekYear()
                        answer(String.format(Locale.US, "%04d-W%02d", wy, c.get(Calendar.WEEK_OF_YEAR)))
                    }
                    DateTimePrompt.Type.DATETIME_LOCAL ->
                        pickTime(String.format(Locale.US, "%04d-%02d-%02d", y, mo + 1, day))
                    else -> answer(String.format(Locale.US, "%04d-%02d-%02d", y, mo + 1, day))
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        )
        dateMillis(min)?.let { d.datePicker.minDate = it }
        dateMillis(max)?.let { d.datePicker.maxDate = it }
        d.setOnCancelListener { answer(null) }
        d.show()
    }

    private fun parseInto(cal: Calendar, type: Int, value: String?) {
        if (value.isNullOrBlank()) return
        runCatching {
            if (type == DateTimePrompt.Type.TIME) {
                val (h, m) = value.split(':')
                cal.set(Calendar.HOUR_OF_DAY, h.toInt())
                cal.set(Calendar.MINUTE, m.take(2).toInt())
                return
            }
            val datePart = value.substringBefore('T')
            val p = datePart.split('-')
            if (p.size >= 2 && !p[1].startsWith("W")) {
                cal.set(Calendar.YEAR, p[0].toInt())
                cal.set(Calendar.MONTH, p[1].toInt() - 1)
                if (p.size >= 3) cal.set(Calendar.DAY_OF_MONTH, p[2].toInt())
            }
            if (value.contains('T')) {
                val (h, m) = value.substringAfter('T').split(':')
                cal.set(Calendar.HOUR_OF_DAY, h.toInt())
                cal.set(Calendar.MINUTE, m.take(2).toInt())
            }
        }
    }

    private fun dateMillis(v: String?): Long? {
        if (v.isNullOrBlank()) return null
        return runCatching {
            val p = v.substringBefore('T').split('-')
            if (p.size < 3) return null
            Calendar.getInstance().apply { set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0) }.timeInMillis
        }.getOrNull()
    }

    override fun toast(text: String, action: String?, onAction: (() -> Unit)?) {
        lifecycleScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                text,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}
