package com.lopleec.slopeweather.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class WeatherThemeTest {
    @Test fun wallpaperChangesControlsWithoutTintingPageSurfaces() {
        for(dark in listOf(false,true)) {
            val base=if(dark) darkColorScheme() else lightColorScheme()
            val first=appColorScheme(dark,base.copy(primary=Color.Green,primaryContainer=Color.Red,background=Color.Green,surfaceContainerHigh=Color.Green))
            val second=appColorScheme(dark,base.copy(primary=Color.Magenta,primaryContainer=Color.Blue,background=Color.Magenta,surfaceContainerHigh=Color.Magenta))
            assertNotEquals(first.primary,second.primary)
            assertNotEquals(first.primaryContainer,second.primaryContainer)
            val surfaces=listOf(first.background,first.surface,first.surfaceContainerLowest,first.surfaceContainerHigh,first.surfaceContainerHighest,first.onSurface,first.onSurfaceVariant)
            surfaces.forEach {assertEquals(it.red,it.green,0f);assertEquals(it.green,it.blue,0f)}
            assertEquals(first.background,second.background)
            assertEquals(first.surfaceContainerHigh,second.surfaceContainerHigh)
            assertEquals(if(dark) Color(0xFF0F0F0F) else Color(0xFFF8F8F8),first.background)
            assertEquals(if(dark) Color.Black else Color.White,first.surfaceContainerLowest)
        }
    }

    @Test fun skyRemainsBlueAcrossWeatherAndDayNight() {
        for(code in listOf(0,1,2,3,45,48,51,61,65,71,75,95,99)) {
            for(day in listOf(false,true)) {
                val sky=weatherSky(code,SolarLight(if(day) 1f else 0f))
                assertEquals(3,sky.size)
                sky.forEach {assertTrue(it.blue>it.red);assertTrue(it.blue>it.green)}
                val top=sky.first()
                assertEquals(day,top.luminance()>.32f)
            }
        }
        assertNotEquals(weatherSky(0,SolarLight(1f)),weatherSky(0,SolarLight(0f)))
        assertNotEquals(weatherSky(0,SolarLight(1f)),weatherSky(95,SolarLight(1f)))
    }

    @Test fun theSameInstantHasDifferentLocalDaylightRegardlessOfPhoneTimeZone() {
        val now=Instant.parse("2026-10-22T12:00:00Z").epochSecond
        val previous=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val paris=solarLight(48.86,2.35,now)
            val hongKong=solarLight(22.28,114.16,now)
            assertEquals(1f,paris.daylight,0f);assertEquals(0f,hongKong.daylight,0f)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(paris,solarLight(48.86,2.35,now))
            assertEquals(hongKong,solarLight(22.28,114.16,now))
        } finally {TimeZone.setDefault(previous)}
    }

    @Test fun twilightAndPolarDaylightUseTheSunInsteadOfFixedClockHours() {
        val morning=solarLight(0.0,0.0,Instant.parse("2026-03-20T06:00:00Z").epochSecond)
        val evening=solarLight(0.0,0.0,Instant.parse("2026-03-20T18:00:00Z").epochSecond)
        assertTrue(morning.dawn>0);assertEquals(0f,morning.dusk,0f)
        assertTrue(evening.dusk>0);assertEquals(0f,evening.dawn,0f)
        assertNotEquals(weatherSky(0,morning),weatherSky(0,evening))
        assertEquals(1f,solarLight(78.22,15.65,Instant.parse("2026-06-21T00:00:00Z").epochSecond).daylight,0f)
        assertEquals(0f,solarLight(78.22,15.65,Instant.parse("2026-12-21T12:00:00Z").epochSecond).daylight,0f)
    }
}
