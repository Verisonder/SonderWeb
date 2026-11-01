package com.verisonder.sonderweb.data

import android.content.Context
import com.verisonder.sonderweb.core.SearchEngine
import com.verisonder.sonderweb.core.SearchEngines
import org.json.JSONArray

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
}
