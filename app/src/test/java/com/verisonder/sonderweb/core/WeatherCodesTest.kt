package com.verisonder.sonderweb.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {
    @Test fun sentence() {
        assertEquals("Mostly clear. It is currently 22°C.", WeatherCodes.sentence(1, 22, "C"))
        assertEquals("Heavy rain. It is currently 50°F.", WeatherCodes.sentence(65, 50, "F"))
    }

    @Test fun sky() {
        assertEquals(Sky.SUN, WeatherCodes.sky(0, true))
        assertEquals(Sky.MOON, WeatherCodes.sky(1, false))
        assertEquals(Sky.PARTLY_NIGHT, WeatherCodes.sky(2, false))
        assertEquals(Sky.RAIN, WeatherCodes.sky(81, true))
        assertEquals(Sky.STORM, WeatherCodes.sky(99, true))
    }

    @Test fun greeting() {
        assertEquals("Good afternoon, SondeR", WeatherCodes.greeting(17, "SondeR "))
        assertEquals("Good morning", WeatherCodes.greeting(5, ""))
        assertEquals("Good evening", WeatherCodes.greeting(23, ""))
        assertEquals("Good evening", WeatherCodes.greeting(2, ""))
    }
}
