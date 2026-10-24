package com.lopleec.slopeweather.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.lopleec.slopeweather.data.*
import org.junit.Assert.*
import org.junit.Test

class WeatherEffectsTest {
    @Test fun everySupportedWmoConditionHasAnAtmosphere() {
        val codes=listOf(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99)
        assertTrue(codes.all {WeatherKind.from(it)!=WeatherKind.UNKNOWN})
        assertEquals(WeatherKind.UNKNOWN,WeatherKind.from(null))
        assertEquals(WeatherKind.UNKNOWN,WeatherKind.from(9999))
        assertTrue(precipitationStrength(65)>precipitationStrength(61))
    }
    @Test fun regionalConditionsKeepTheirDistinctMeaning() {
        assertEquals(WeatherKind.SLEET,WeatherKind.from(NmcProvider.code(6)))
        assertEquals(WeatherKind.DUST,WeatherKind.from(NmcProvider.code(29)))
        assertEquals(WeatherKind.HAZE,WeatherKind.from(NmcProvider.code(53)))
        assertEquals(WeatherKind.THUNDER_HAIL,WeatherKind.from(NmcProvider.code(5)))
        assertEquals(WeatherKind.OVERCAST,WeatherKind.from(RegionalProviders.hkoCode(61)))
        assertEquals(WeatherKind.PARTLY_CLOUDY,WeatherKind.from(RegionalProviders.hkoCode(76)))
        assertEquals(WeatherKind.WIND,WeatherKind.from(RegionalProviders.hkoCode(80)))
        assertEquals(WeatherKind.HUMID,WeatherKind.from(RegionalProviders.hkoCode(82)))
        assertEquals(WeatherKind.FREEZING_RAIN,WeatherKind.from(RegionalProviders.nwsCode("Freezing Rain")))
        assertEquals(WeatherKind.SLEET,WeatherKind.from(RegionalProviders.nwsCode("Rain And Snow")))
        assertEquals(WeatherKind.SMOKE,WeatherKind.from(RegionalProviders.nwsCode("Areas Of Smoke")))
        assertEquals(WeatherKind.SLEET,WeatherKind.from(RegionalProviders.metCode("sleetshowers_day")))
        assertEquals(WeatherKind.THUNDER_SNOW,WeatherKind.from(RegionalProviders.metCode("heavysnowandthunder_night")))
        assertEquals(WeatherKind.THUNDER_SLEET,WeatherKind.from(RegionalProviders.nwsCode("Rain And Snow With Thunderstorms")))
    }
    @Test fun dailyAggregationDoesNotLetWindHideAThunderstorm() {
        val hours=listOf(Reading(1000,emptyMap(),95),Reading(2000,emptyMap(),ExtraWeatherCode.WIND))
        assertEquals(95,aggregateDays(hours,"UTC").single().code)
    }
    @Test fun rainCollisionTracksShapeRimAndScrolling() {
        val rim=WeatherRim(Offset(20f,100f),Size(200f,150f),floatArrayOf(25f,0f,25f))
        assertNull(rim.topAt(19f))
        assertEquals(100f,rim.topAt(120f)!!,.01f)
        assertEquals(125f,rim.topAt(20f)!!,.01f)
        val scrolled=rim.relativeTo(Offset(0f,60f))
        assertEquals(40f,scrolled.topAt(120f)!!,.01f)
    }
}
