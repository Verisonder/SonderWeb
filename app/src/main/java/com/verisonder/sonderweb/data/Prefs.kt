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

    var httpsOnly: Boolean
        get() = sp.getBoolean("https_only", false)
        set(v) = sp.edit().putBoolean("https_only", v).apply()

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

    /** How the address bar gets out of the way: 0 = always shown, 1 = slides away while
     *  scrolling, 2 = shrinks into a small button you tap to bring it back. */
    var barHide: Int
        get() = sp.getInt("bar_hide", 2)
        set(v) = sp.edit().putInt("bar_hide", v).apply()

    /** The phone's status bar (time, battery) at the top. Hidden by default: pages use the whole screen. */
    var showStatusBar: Boolean
        get() = sp.getBoolean("status_bar", false)
        set(v) = sp.edit().putBoolean("status_bar", v).apply()

    var showClock: Boolean
        get() = sp.getBoolean("show_clock", true)
        set(v) = sp.edit().putBoolean("show_clock", v).apply()

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
