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

    private var filesResult: ((List<Uri>) -> Unit)? = null
    private var permsResult: ((Boolean) -> Unit)? = null

    /** True while we've sent you to a screen of our own (file picker, share sheet, screen lock):
     *  that isn't leaving the app, so private tabs stay unlocked. */
    private var ownScreen = false

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
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        ownScreen = false
        permsResult?.invoke(r.isNotEmpty() && r.values.all { it })
        permsResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Browser.start(this)
        Browser.host = this
        geckoView = com.verisonder.sonderweb.browser.PullGeckoView(this)

        setContent { BrowserScreen(this) }
        applySystemBars()
        preferHighRefreshRate()
        // No grey scrim behind the gesture bar: the page shows through.
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Browser.host = this
        if (unlockOnResume && Browser.privateLocked) unlockPrivate()
    }

    override fun onStart() {
        super.onStart()
        ownScreen = false
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
        if (::geckoView.isInitialized) {
            geckoView.requestLayout()
            geckoView.invalidate()
        }
    }

    override fun onDestroy() {
        if (Browser.host === this) Browser.host = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        Browser.saveTabs()
        // Paused while you're away (saves battery); woken again in onStart.
        Browser.current?.let { t -> if (t.ready && t.session.isOpen && !isChangingConfigurations) t.session.setActive(false) }
        if (!ownScreen && !isChangingConfigurations) Browser.leftApp()
    }

    /** Links from other apps open in a new tab; shared text and searches too. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
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

    override fun setFullscreen(on: Boolean) {
        inFullscreen = on
        applySystemBars()
    }

    /** Fullscreen video hides everything; otherwise the status bar follows Settings › Appearance. */
    override fun applySystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (inFullscreen) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            return
        }
        c.show(WindowInsetsCompat.Type.navigationBars())
        if (Browser.prefs.showStatusBar) {
            c.show(WindowInsetsCompat.Type.statusBars())
        } else {
            c.hide(WindowInsetsCompat.Type.statusBars())
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
