package com.verisonder.sonderweb.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.verisonder.sonderweb.sync.FxSync
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.IPProtectionController
import org.mozilla.geckoview.IPProtectionController.IPProxyException
import org.mozilla.geckoview.IPProtectionController.ProxyState

/**
 * SonderWeb Sync: Firefox's free built-in VPN. The engine has it; SonderWeb gives it a Mozilla
 * account token with the VPN permission and the on/off and location choices. It covers all of
 * SonderWeb's tabs (private ones too), nothing else on the phone. Mozilla's servers decide where
 * it's offered and how much data a month it allows.
 */
object Vpn {
    private lateinit var prefs: android.content.SharedPreferences
    private var controller: IPProtectionController? = null
    private var started = false
    private var announced: Boolean? = null

    /** You turned it on (it comes back on when the app opens again). */
    var wanted by mutableStateOf(false)
        private set

    /** Where to come out: an ISO country code, or null for the recommended place. */
    var country by mutableStateOf<String?>(null)
        private set

    /** Shows a small VPN mark in the address bar while it's on. */
    var showInBar by mutableStateOf(true)
        private set

    /** One of ProxyState's codes. */
    var state by mutableStateOf(ProxyState.NOT_READY)
        private set

    /** Turning on or enrolling right now. */
    var busy by mutableStateOf(false)
        private set

    /** What went wrong last, in plain words; null when all is well. */
    var problem by mutableStateOf<String?>(null)
        private set

    var countries by mutableStateOf<List<IPProtectionController.Country>>(emptyList())
        private set

    /** Data left and the allowance, in bytes; both 0 means no limit right now. */
    var remaining by mutableStateOf(0L)
        private set
    var allowance by mutableStateOf(0L)
        private set
    var resetsAt by mutableStateOf<String?>(null)
        private set

    val on: Boolean get() = state == ProxyState.ACTIVE

    /** Called once the engine is up (SonderWeb Sync only). */
    fun install(app: Context, runtime: GeckoRuntime) {
        if (started) return
        started = true
        prefs = app.getSharedPreferences("vpn", Context.MODE_PRIVATE)
        wanted = prefs.getBoolean("on", false)
        country = prefs.getString("country", null)
        showInBar = prefs.getBoolean("bar", true)
        runCatching { runtime.settings.setIpProtectionAuthProvider(GeckoRuntimeSettings.IP_PROTECTION_AUTH_PROVIDER_FXA) }
        val c = runCatching { runtime.ipProtectionController }.getOrNull() ?: return
        controller = c
        c.setDelegate(object : IPProtectionController.Delegate {
            override fun onProxyStateChanged(state: ProxyState) {
                this@Vpn.state = state.state
                if (state.state == ProxyState.ERROR) problem = message(state.errorType)
                if (state.state == ProxyState.PAUSED) problem = message("quota-exhausted")
                if (state.state == ProxyState.ACTIVE) problem = null
            }

            override fun onUsageChanged(info: IPProtectionController.UsageInfo) {
                remaining = info.remaining
                allowance = info.max
                resetsAt = info.resetTime
            }

            override fun onCountryListChanged(countries: List<IPProtectionController.Country>) {
                this@Vpn.countries = countries.sortedBy { name(it.code) }
            }
        })
        c.init().accept({
            // Tokens come from the Mozilla account, asked for whenever the engine needs one.
            c.setAuthProvider(object : IPProtectionController.AuthProvider {
                override fun onTokenRequest(): GeckoResult<String> {
                    val r = GeckoResult<String>()
                    FxSync.vpnToken { token ->
                        if (token != null) r.complete(token) else r.completeExceptionally(IllegalStateException("no token"))
                    }
                    return r
                }
            })
            // Follows the account: signing in (with the VPN permission) or out.
            MainScope().launch {
                snapshotFlow { FxSync.vpnAllowed }.collect { allowed -> signedIn(allowed) }
            }
        }, { _ -> })
    }

    private fun signedIn(allowed: Boolean) {
        val c = controller ?: return
        if (announced == allowed) return
        announced = allowed
        c.notifySignInStateChanged(allowed).accept({
            if (allowed) {
                refresh()
                if (wanted) start(userAction = false)
            }
        }, { _ -> })
        if (!allowed) {
            if (state == ProxyState.ACTIVE || state == ProxyState.ACTIVATING) c.deactivate()
            state = ProxyState.NOT_READY
            busy = false
        }
    }

    /** The places you can come out from, and the data used (for the settings page). */
    fun refresh() {
        val c = controller ?: return
        if (!FxSync.vpnAllowed) return
        c.getCountryList().accept({}, { _ -> })
        c.getProxyState().accept({ s -> if (s != null) state = s.state }, { _ -> })
        if (state == ProxyState.ACTIVE) c.refreshUsage().accept({}, { _ -> })
    }

    fun turn(on: Boolean) {
        wanted = on
        prefs.edit().putBoolean("on", on).apply()
        problem = null
        if (on) start(userAction = true) else stop()
    }

    fun changeCountry(code: String?) {
        country = code
        prefs.edit().putString("country", code).apply()
        // Already on: the engine moves the connection over without turning off.
        if (state == ProxyState.ACTIVE) activate(userAction = true)
    }

    fun changeShowInBar(on: Boolean) {
        showInBar = on
        prefs.edit().putBoolean("bar", on).apply()
    }

    private fun start(userAction: Boolean) {
        val c = controller ?: return
        if (!FxSync.vpnAllowed) {
            problem = "Sign in again to use the VPN"
            return
        }
        busy = true
        c.getServiceState().accept({ s ->
            if (s == IPProtectionController.SERVICE_STATE_READY) {
                activate(userAction)
            } else {
                // First time on this account: Mozilla's VPN service signs it up.
                c.enroll().accept({ e ->
                    if (e != null && e.isEnrolledAndEntitled) {
                        activate(userAction)
                    } else {
                        fail(e?.error)
                    }
                }, { e -> fail(errorOf(e)) })
            }
        }, { e -> fail(errorOf(e)) })
    }

    private fun activate(userAction: Boolean) {
        val c = controller ?: return
        busy = true
        val go = {
            c.activate(userAction, false, country).accept({
                busy = false
                problem = null
                refresh()
            }, { e -> fail(errorOf(e)) })
        }
        // An earlier failure has to be cleared before trying again.
        if (state == ProxyState.ERROR || (state == ProxyState.PAUSED && userAction)) {
            c.deactivate().accept({ go() }, { _ -> go() })
        } else {
            go()
        }
    }

    private fun stop() {
        val c = controller ?: return
        busy = false
        c.deactivate().accept({}, { _ -> })
    }

    /** It couldn't turn on: the switch goes back off (and stays off next time) with the reason. */
    private fun fail(error: String?) {
        busy = false
        // Stopped because you turned it off: nothing to tell.
        if (!(error == "activation-canceled" && !wanted)) problem = message(error)
        wanted = false
        prefs.edit().putBoolean("on", false).apply()
        // The engine passes on only a label: ask Mozilla's service ourselves for the real answer.
        if (error !in KNOWN) explain(problem)
    }

    /** Failures that already say what happened; anything else gets Mozilla's own answer. */
    private val KNOWN = setOf(
        "vpn-unavailable", "region_unavailable", "quota-exhausted", "quota_exceeded", "login_needed",
        "unauthorized", "not_entitled", "network-error", "network_error", "timeout-error", "activation-canceled",
    )

    private const val GUARDIAN = "https://vpn.mozilla.org/api/v1/fpn/activate"

    /**
     * Sends the same sign-up request the engine sent, with the account's token, and shows the
     * status and Mozilla's message instead of the engine's label (if nothing newer replaced it).
     */
    private fun explain(shown: String?) {
        FxSync.vpnToken { token ->
            if (token == null) return@vpnToken
            Thread {
                val answer = runCatching {
                    val c = java.net.URL(GUARDIAN).openConnection() as java.net.HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 15_000
                    c.readTimeout = 15_000
                    c.setRequestProperty("Authorization", "Bearer $token")
                    c.setRequestProperty("Content-Type", "application/json")
                    c.doOutput = true
                    c.outputStream.use { it.write(ByteArray(0)) }
                    val code = c.responseCode
                    val body = (if (code >= 400) c.errorStream else c.inputStream)
                        ?.use { it.readBytes().decodeToString().take(4000) }.orEmpty()
                    c.disconnect()
                    code to said(body)
                }
                Browser.main.post {
                    if (problem != shown) return@post
                    answer.onSuccess { (code, text) ->
                        problem = when {
                            code in 200..299 -> "Mozilla's VPN service accepted the account just now: try turning it on again"
                            text.isEmpty() -> "Mozilla's VPN service refused (error $code)"
                            else -> "Mozilla's VPN service refused (error $code): $text"
                        }
                    }
                    answer.onFailure { e ->
                        problem = "Couldn't reach Mozilla's VPN service" + (e.message?.let { " ($it)" } ?: "")
                    }
                }
            }.start()
        }
    }

    /** The readable part of an answer: a JSON message if there is one, else the text without tags. */
    private fun said(body: String): String {
        val json = runCatching { org.json.JSONObject(body) }.getOrNull()
        val fromJson = json?.let { j ->
            listOf("message", "error_description", "detail", "error", "code")
                .firstNotNullOfOrNull { k -> j.optString(k).takeIf { it.isNotBlank() && it != "null" } }
        }
        val text = fromJson ?: body.replace(Regex("<[^>]*>"), " ")
        return text.replace(Regex("\\s+"), " ").trim().take(200)
    }

    private fun errorOf(e: Throwable?): String? = when (e) {
        is IPProxyException -> when (e.code) {
            IPProxyException.ERROR_VPN_UNAVAILABLE -> "vpn-unavailable"
            IPProxyException.ERROR_QUOTA_EXHAUSTED -> "quota-exhausted"
            IPProxyException.ERROR_AUTH_REQUIRED -> "login_needed"
            IPProxyException.ERROR_NOT_ENTITLED -> "not_entitled"
            IPProxyException.ERROR_NETWORK -> "network-error"
            IPProxyException.ERROR_TIMEOUT -> "timeout-error"
            IPProxyException.ERROR_SERVER_NOT_FOUND -> "server-not-found"
            IPProxyException.ERROR_SERVERLIST_UNAVAILABLE -> "serverlist-unavailable"
            IPProxyException.ERROR_CONNECTION_FAILED -> "connection-failed"
            IPProxyException.ERROR_ACTIVATION_CANCELED -> "activation-canceled"
            else -> null
        }
        else -> e?.message
    }

    private fun message(error: String?): String = when (error) {
        "vpn-unavailable", "region_unavailable" -> "The VPN isn't available in your region yet"
        "quota-exhausted", "quota_exceeded" -> "This month's VPN data is used up" + (resetsAt?.let { " until " + day(it) } ?: "")
        "login_needed", "unauthorized" -> "Sign in again to use the VPN"
        "not_entitled" -> "Your account can't use the VPN yet"
        "network-error", "network_error" -> "No connection to Mozilla's VPN service"
        "timeout-error" -> "Mozilla's VPN service took too long to answer"
        "server-not-found" -> "No VPN server in that location right now"
        "serverlist-unavailable" -> "Couldn't get the list of VPN locations"
        "connection-failed" -> "Couldn't connect to the VPN server"
        "activation-canceled" -> "Turning on was stopped"
        "server_error", "catastrophic-error" -> "Mozilla's VPN service had a problem"
        else -> "The VPN couldn't turn on" + (error?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }

    /** A country code as its name in the phone's language. */
    fun name(code: String): String =
        runCatching { java.util.Locale.Builder().setRegion(code).build().displayCountry }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != code } ?: code

    /** The reset date of an ISO 8601 time ("2026-12-01T00:00:00Z" → "1 Dec"). */
    fun day(iso: String): String = runCatching {
        val t = java.time.OffsetDateTime.parse(iso)
        t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }.getOrDefault(iso.take(10))
}
