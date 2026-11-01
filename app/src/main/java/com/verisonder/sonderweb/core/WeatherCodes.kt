package com.verisonder.sonderweb.core

/** Which little picture to draw for the weather. */
enum class Sky { SUN, MOON, PARTLY_DAY, PARTLY_NIGHT, CLOUD, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** WMO weather codes (as sent by Open-Meteo) to words and a picture. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81 -> "Rain showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Weather"
    }

    fun sky(code: Int, day: Boolean): Sky = when (code) {
        0, 1 -> if (day) Sky.SUN else Sky.MOON
        2 -> if (day) Sky.PARTLY_DAY else Sky.PARTLY_NIGHT
        3 -> Sky.CLOUD
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.STORM
        else -> Sky.CLOUD
    }

    /** "Mostly clear. It is currently 22°C." */
    fun sentence(code: Int, temp: Int, unit: String): String =
        describe(code) + ". It is currently " + temp + "°" + unit + "."

    /** "Good afternoon, Nidal" — or without a name when none is set. */
    fun greeting(hour: Int, name: String): String {
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (name.isBlank()) part else "$part, ${name.trim()}"
    }
}
