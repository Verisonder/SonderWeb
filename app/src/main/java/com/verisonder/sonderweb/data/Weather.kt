package com.verisonder.sonderweb.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Current weather from Open-Meteo (free, no account, no key). Only the chosen place is sent. */
object Weather {
    data class Now(val temp: Int, val code: Int, val day: Boolean, val unit: String, val place: String, val at: Long)

    var now by mutableStateOf<Now?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var busy = false
    private var saved: Prefs? = null

    /** Refreshes when the last reading is older than 20 minutes (or the place/unit changed). */
    fun refresh(prefs: Prefs, force: Boolean = false) {
        if (!prefs.weatherOn) {
            now = null
            return
        }
        val place = prefs.place ?: return
        val unit = if (prefs.fahrenheit) "F" else "C"
        saved = prefs
        // Coming back after the app was closed: show the last reading at once (no jump when it arrives).
        if (now == null) now = load(prefs.lastWeather)?.takeIf { it.place == place.name && it.unit == unit }
        val cur = now
        val fresh = cur != null && cur.place == place.name && cur.unit == unit &&
            System.currentTimeMillis() - cur.at < 20 * 60_000L
        if ((fresh && !force) || busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetch(place, unit) }
            busy = false
            if (result != null) {
                now = result
                prefs.lastWeather = save(result)
            }
        }
    }

    private fun fetch(place: Place, unit: String): Now? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,weather_code,is_day&timezone=auto" +
            (if (unit == "F") "&temperature_unit=fahrenheit" else "")
        val body = Net.text(url) ?: return null
        return runCatching {
            val c = JSONObject(body).getJSONObject("current")
            Now(
                temp = c.getDouble("temperature_2m").roundToInt(),
                code = c.getInt("weather_code"),
                day = c.optInt("is_day", 1) == 1,
                unit = unit,
                place = place.name,
                at = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    /** Place search for Settings: "Tangier" → Tangier, Tanger-Tetouan-Al Hoceima, Morocco. */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val body = Net.text(
            "https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name=" +
                URLEncoder.encode(q, "UTF-8"),
        ) ?: return@withContext emptyList()
        runCatching {
            val arr = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList<Place>()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val label = listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                    .filter { it.isNotBlank() }.distinct().joinToString(", ")
                Place(label, o.getDouble("latitude"), o.getDouble("longitude"))
            }
        }.getOrDefault(emptyList())
    }

    fun forget() {
        now = null
        saved?.lastWeather = ""
    }

    private fun save(n: Now) = listOf(n.temp, n.code, n.day, n.unit, n.at, n.place).joinToString("\u0001")

    private fun load(s: String): Now? = runCatching {
        val f = s.split('\u0001')
        Now(f[0].toInt(), f[1].toInt(), f[2].toBooleanStrict(), f[3], f[5], f[4].toLong())
    }.getOrNull()
}
