package com.verisonder.sonderweb.browser

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toBitmap
import com.verisonder.sonderweb.core.UrlText

/**
 * Links that another app could open. Nothing opens an app on its own: you're asked
 * "Open in <app>?" and can always stay in the browser instead.
 */
object AppLinks {

    class Ask(
        val tab: Tab,
        val app: String,
        val icon: Bitmap?,
        val intent: Intent,
        /** Where "Stay in browser" goes: the web address itself, or a page's fallback. Null: nowhere. */
        val stay: String?,
    )

    var ask by mutableStateOf<Ask?>(null)
        private set

    /** Sites you chose to keep in the browser: not asked again until the app restarts. */
    private val stayHosts = HashSet<String>()

    /**
     * A tapped web link that an installed app (not a browser) also handles. Returns true when
     * you're being asked, so the browser holds the page until you answer.
     */
    fun askForWebLink(tab: Tab, url: String): Boolean {
        val host = UrlText.host(url)
        if (host.isEmpty() || host in stayHosts || host == UrlText.host(tab.url)) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val app = handlerOf(intent, skipBrowsers = true) ?: return false
        intent.setPackage(app.first)
        show(Ask(tab, app.second, icon(app.first), intent, url))
        return true
    }

    /** tel:, mailto:, intent:, market: and other app links. Always asks; false when no app can open it. */
    fun askForAppLink(tab: Tab, url: String): Boolean {
        val intent = parse(url) ?: return false
        val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { UrlText.isWeb(it) }
        val app = handlerOf(intent, skipBrowsers = false)
        if (app == null && fallback != null) {
            // No app for it: the page's own web fallback opens in the browser.
            Browser.load(tab, fallback)
            return true
        }
        // Android may hide which app it is; it can still be opened, so ask anyway.
        show(Ask(tab, app?.second ?: "another app", app?.first?.let { icon(it) }, intent, fallback))
        return true
    }

    fun openApp() {
        val a = ask ?: return
        ask = null
        val ok = Browser.host?.launch(a.intent) ?: false
        if (!ok) {
            Browser.host?.toast("Couldn't open ${a.app}")
            a.stay?.let { stay(a, it) }
        }
    }

    fun stayInBrowser() {
        val a = ask ?: return
        ask = null
        a.stay?.let { stay(a, it) }
    }

    private fun stay(a: Ask, url: String) {
        stayHosts += UrlText.host(url)
        Browser.load(a.tab, url)
    }

    private fun show(a: Ask) {
        ask = a
    }

    /** Pages may only open apps, never pick a component or run a selector. */
    private fun parse(url: String): Intent? = try {
        if (url.startsWith("intent:", ignoreCase = true)) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                component = null
                selector = null
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
    } catch (_: Exception) {
        null
    }

    /** The app that would open [intent]: package and name. Browsers (and this app) don't count when [skipBrowsers]. */
    @Suppress("DEPRECATION")
    private fun handlerOf(intent: Intent, skipBrowsers: Boolean): Pair<String, String>? {
        val pm = Browser.app.packageManager
        val own = Browser.app.packageName
        val browsers = if (skipBrowsers) {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/")).addCategory(Intent.CATEGORY_BROWSABLE)
            runCatching { pm.queryIntentActivities(probe, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
                .map { it.activityInfo.packageName }.toSet()
        } else {
            emptySet()
        }
        val found = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrDefault(emptyList())
            .firstOrNull { it.activityInfo.packageName != own && it.activityInfo.packageName !in browsers }
            ?: return null
        val name = runCatching { found.loadLabel(pm).toString() }.getOrDefault(found.activityInfo.packageName)
        return found.activityInfo.packageName to name
    }

    private fun icon(pkg: String): Bitmap? = runCatching {
        Browser.app.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
    }.getOrNull()
}
