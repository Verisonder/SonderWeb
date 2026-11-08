package com.verisonder.sonderweb.data

import android.content.Context
import com.verisonder.sonderweb.core.SearchEngine
import com.verisonder.sonderweb.core.SearchEngines
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sonderweb", Context.MODE_PRIVATE)

    var searchEngineId: String
        get() = sp.getString("engine", SearchEngines.DEFAULT_ID) ?: SearchEngines.DEFAULT_ID
        set(v) = sp.edit().putString("engine", v).apply()

    var customSearch: String
        get() = sp.getString("engine_custom", "") ?: ""
        set(v) = sp.edit().putString("engine_custom", v).apply()

    fun engine(): SearchEngine = SearchEngines.resolve(searchEngineId, customSearch)

    /** Firefox tracking protection on top of uBlock Origin: false = standard, true = strict. */
    var strictTracking: Boolean
        get() = sp.getBoolean("strict", true)
        set(v) = sp.edit().putBoolean("strict", v).apply()

    /** Extensions already given the sites they asked for (done once, so your own later choice stands). */
    var extensionsSetUp: Set<String>
        get() = sp.getStringSet("ext_setup", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("ext_setup", v).apply()

    /** DuckDuckGo restyled to look like the browser (a small built-in extension). */
    var cleanDdg: Boolean
        get() = sp.getBoolean("clean_ddg", true)
        set(v) = sp.edit().putBoolean("clean_ddg", v).apply()

    var httpsOnly: Boolean
        get() = sp.getBoolean("https_only", true)
        set(v) = sp.edit().putBoolean("https_only", v).apply()

    /** Sites get slightly altered device details (screen, fonts, canvas...) so they can't recognise you. */
    var blockFingerprinting: Boolean
        get() = sp.getBoolean("fingerprinting", true)
        set(v) = sp.edit().putBoolean("fingerprinting", v).apply()

    /** Encrypted DNS: 0 off (the phone's own DNS, e.g. a VPN's blocker), 1 Quad9, 2 Mullvad, 3 Cloudflare. */
    var secureDns: Int
        get() = sp.getInt("secure_dns", 0)
        set(v) = sp.edit().putInt("secure_dns", v).apply()

    /** Private tabs: ask for your fingerprint / screen lock after you've left the app. */
    var privateLock: Boolean
        get() = sp.getBoolean("private_lock", true)
        set(v) = sp.edit().putBoolean("private_lock", v).apply()

    /** Private tabs: close them all as soon as you leave the app. */
    var privateCloseOnLeave: Boolean
        get() = sp.getBoolean("private_close", false)
        set(v) = sp.edit().putBoolean("private_close", v).apply()

    /** Private tabs: allow screenshots and the preview in recent apps. Off: blocked. */
    var privateScreenshots: Boolean
        get() = sp.getBoolean("private_screens", false)
        set(v) = sp.edit().putBoolean("private_screens", v).apply()

    var saveHistory: Boolean
        get() = sp.getBoolean("history", true)
        set(v) = sp.edit().putBoolean("history", v).apply()

    /** Shields: local IP addresses kept out of WebRTC, real pages instead of AMP copies, tracking redirects skipped. */
    var shieldWebrtc: Boolean
        get() = sp.getBoolean("shield_webrtc", true)
        set(v) = sp.edit().putBoolean("shield_webrtc", v).apply()

    var shieldAmp: Boolean
        get() = sp.getBoolean("shield_amp", true)
        set(v) = sp.edit().putBoolean("shield_amp", v).apply()

    var shieldDebounce: Boolean
        get() = sp.getBoolean("shield_debounce", true)
        set(v) = sp.edit().putBoolean("shield_debounce", v).apply()

    /** The language pages were last translated into (empty: the phone's). */
    var translateTo: String
        get() = sp.getString("translate_to", "") ?: ""
        set(v) = sp.edit().putString("translate_to", v).apply()

    /** Leave the app while a video plays: it keeps playing in a small window. */
    var pip: Boolean
        get() = sp.getBoolean("pip", true)
        set(v) = sp.edit().putBoolean("pip", v).apply()

    var restoreTabs: Boolean
        get() = sp.getBoolean("restore_tabs", true)
        set(v) = sp.edit().putBoolean("restore_tabs", v).apply()

    /** What opening the app shows: 0 = the last site, 1 = the start page, 2 = the start page
     *  once you've been away longer than [startPageAfter]. */
    var openOnLaunch: Int
        get() = sp.getInt("open_on_launch", 0)
        set(v) = sp.edit().putInt("open_on_launch", v).apply()

    /** Minutes away before the start page shows (with [openOnLaunch] = 2). */
    var startPageAfter: Int
        get() = sp.getInt("start_page_after", 60)
        set(v) = sp.edit().putInt("start_page_after", v).apply()

    /** Android 13+ asked once (on the first download) whether notifications may show. */
    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notifications", false)
        set(v) = sp.edit().putBoolean("asked_notifications", v).apply()

    /** When you last left the app (milliseconds since 1970). */
    var leftAt: Long
        get() = sp.getLong("left_at", 0L)
        set(v) = sp.edit().putLong("left_at", v).apply()

    /** Address bar at the top (like Chromium) or at the bottom. */
    var barOnTop: Boolean
        get() = sp.getBoolean("bar_top", true)
        set(v) = sp.edit().putBoolean("bar_top", v).apply()

    /** Floating rounded bar (true) or docked to the screen edge (false). */
    var barFloating: Boolean
        get() = sp.getBoolean("bar_floating", true)
        set(v) = sp.edit().putBoolean("bar_floating", v).apply()

    /** How the address bar gets out of the way: 0 = always shown, 1 = slides away while
     *  scrolling, 2 = shrinks into a small button you tap to bring it back. */
    var barHide: Int
        get() = sp.getInt("bar_hide", 1)
        set(v) = sp.edit().putInt("bar_hide", v).apply()

    /** Where the small bar button sits: 0 top left, 1 top right, 2 bottom left, 3 bottom right.
     *  -1 = the right corner on the bar's side (until you drag it somewhere). */
    var buttonCorner: Int
        get() = sp.getInt("button_corner", -1)
        set(v) = sp.edit().putInt("button_corner", v).apply()

    /** Advanced: the small button sits at a free spot ([buttonX], [buttonY]) instead of a corner. */
    var buttonFree: Boolean
        get() = sp.getBoolean("button_free", false)
        set(v) = sp.edit().putBoolean("button_free", v).apply()

    /** Free spot of the small button, 0 = left / top edge, 1 = right / bottom edge. */
    var buttonX: Float
        get() = sp.getFloat("button_x", 1f)
        set(v) = sp.edit().putFloat("button_x", v.coerceIn(0f, 1f)).apply()
    var buttonY: Float
        get() = sp.getFloat("button_y", 1f)
        set(v) = sp.edit().putFloat("button_y", v.coerceIn(0f, 1f)).apply()

    /** Size of the address bar, in percent of normal (everything in it grows or shrinks together). */
    var barSize: Int
        get() = sp.getInt("bar_size", 100)
        set(v) = sp.edit().putInt("bar_size", v).apply()

    /** Diameter of the small bar button, in dp. */
    var buttonSize: Int
        get() = sp.getInt("button_size", 46)
        set(v) = sp.edit().putInt("button_size", v).apply()

    /** The phone's status bar (time, battery) at the top. Hidden by default: pages use the whole screen. */
    var showStatusBar: Boolean
        get() = sp.getBoolean("status_bar", false)
        set(v) = sp.edit().putBoolean("status_bar", v).apply()

    /** Start page background colour (ARGB); 0 = the theme's own. */
    var startBgColor: Int
        get() = sp.getInt("start_bg_color", 0)
        set(v) = sp.edit().putInt("start_bg_color", v).apply()

    /** A wallpaper you chose is shown on the start page. */
    var startWallpaper: Boolean
        get() = sp.getBoolean("start_wallpaper", false)
        set(v) = sp.edit().putBoolean("start_wallpaper", v).apply()

    /** How much the wallpaper is darkened (percent), so the text on it stays readable. */
    var startDim: Int
        get() = sp.getInt("start_dim", 30)
        set(v) = sp.edit().putInt("start_dim", v).apply()

    /** How far above their usual place the greeting, weather, search box and shortcuts sit (dp; negative = lower). */
    var restLift: Int
        get() = sp.getInt("rest_lift", 50)
        set(v) = sp.edit().putInt("rest_lift", v).apply()

    /** How far above its usual place the time and date sit on the start page (dp; negative = lower). */
    var clockLift: Int
        get() = sp.getInt("clock_lift", 45)
        set(v) = sp.edit().putInt("clock_lift", v).apply()

    var showClock: Boolean
        get() = sp.getBoolean("show_clock", true)
        set(v) = sp.edit().putBoolean("show_clock", v).apply()

    /** Names under the start page shortcuts. */
    var showShortcutNames: Boolean
        get() = sp.getBoolean("shortcut_names", true)
        set(v) = sp.edit().putBoolean("shortcut_names", v).apply()

    var showGreeting: Boolean
        get() = sp.getBoolean("show_greeting", true)
        set(v) = sp.edit().putBoolean("show_greeting", v).apply()

    /** 0 = follow the system, 1 = light, 2 = dark. */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** Addresses of the normal (not private) tabs, to reopen next launch. */
    var savedTabs: List<String>
        get() {
            val raw = sp.getString("tabs", null) ?: return emptyList()
            return runCatching {
                val a = JSONArray(raw)
                List(a.length()) { a.getString(it) }
            }.getOrDefault(emptyList())
        }
        set(v) = sp.edit().putString("tabs", JSONArray(v).toString()).apply()

    /** Each saved tab's page title, in the order of [savedTabs]. */
    var savedTabTitles: List<String>
        get() {
            val raw = sp.getString("tab_titles", null) ?: return emptyList()
            return runCatching {
                val a = JSONArray(raw)
                List(a.length()) { a.optString(it) }
            }.getOrDefault(emptyList())
        }
        set(v) = sp.edit().putString("tab_titles", JSONArray(v).toString()).apply()

    /** Each saved tab's page colours (top, bottom; null = unknown), in the order of [savedTabs]. */
    var savedTabColors: List<Pair<Int?, Int?>>
        get() {
            val raw = sp.getString("tab_colors", null) ?: return emptyList()
            return runCatching {
                val a = JSONArray(raw)
                List(a.length()) { i ->
                    val c = a.getJSONArray(i)
                    val top: Int? = if (c.isNull(0)) null else c.getInt(0)
                    val bottom: Int? = if (c.isNull(1)) null else c.getInt(1)
                    top to bottom
                }
            }.getOrDefault(emptyList())
        }
        set(v) {
            val a = JSONArray()
            v.forEach { (top, bottom) ->
                a.put(JSONArray().put(top ?: JSONObject.NULL).put(bottom ?: JSONObject.NULL))
            }
            sp.edit().putString("tab_colors", a.toString()).apply()
        }

    /** When each saved tab was last used (ms), in the order of [savedTabs]. */
    var savedTabUsed: List<Long>
        get() {
            val raw = sp.getString("tab_used", null) ?: return emptyList()
            return runCatching {
                val a = JSONArray(raw)
                List(a.length()) { a.optLong(it) }
            }.getOrDefault(emptyList())
        }
        set(v) = sp.edit().putString("tab_used", JSONArray(v).toString()).apply()

    var savedTabIndex: Int
        get() = sp.getInt("tab_index", 0)
        set(v) = sp.edit().putInt("tab_index", v).apply()

    // ---- Start page ----

    /** Shown in the greeting ("Good afternoon, <name>"). Empty = no name. */
    var userName: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v.trim()).apply()

    var weatherOn: Boolean
        get() = sp.getBoolean("weather", true)
        set(v) = sp.edit().putBoolean("weather", v).apply()

    /** The last weather reading, shown straight away on the next start until a new one comes in. */
    var lastWeather: String
        get() = sp.getString("last_weather", "") ?: ""
        set(v) = sp.edit().putString("last_weather", v).apply()

    var fahrenheit: Boolean
        get() = sp.getBoolean("fahrenheit", false)
        set(v) = sp.edit().putBoolean("fahrenheit", v).apply()

    /** The weather's place, picked in Settings. Null until one is chosen. */
    var place: Place?
        get() {
            val name = sp.getString("place_name", null) ?: return null
            return Place(name, sp.getFloat("place_lat", 0f).toDouble(), sp.getFloat("place_lon", 0f).toDouble())
        }
        set(v) {
            val e = sp.edit()
            if (v == null) {
                e.remove("place_name").remove("place_lat").remove("place_lon")
            } else {
                e.putString("place_name", v.name).putFloat("place_lat", v.lat.toFloat()).putFloat("place_lon", v.lon.toFloat())
            }
            e.apply()
        }

    /** Start page shortcuts. None until you add some. */
    var shortcuts: List<Shortcut>
        get() {
            val raw = sp.getString("shortcuts", null) ?: return defaultShortcuts
            return runCatching {
                val a = JSONArray(raw)
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    Shortcut(o.getString("u"), o.optString("t"))
                }
            }.getOrDefault(defaultShortcuts)
        }
        set(v) {
            val a = JSONArray()
            v.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title)) }
            sp.edit().putString("shortcuts", a.toString()).apply()
        }

    companion object {
        val defaultShortcuts = emptyList<Shortcut>()
    }
}

data class Place(val name: String, val lat: Double, val lon: Double)

data class Shortcut(val url: String, val title: String)
