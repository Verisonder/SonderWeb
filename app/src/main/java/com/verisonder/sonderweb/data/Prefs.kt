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

    var restoreTabs: Boolean
        get() = sp.getBoolean("restore_tabs", true)
        set(v) = sp.edit().putBoolean("restore_tabs", v).apply()

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
