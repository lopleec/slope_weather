package com.lopleec.slopeweather.ui

import com.lopleec.slopeweather.data.*
import org.junit.Assert.*
import org.junit.Test

class WidgetBehaviorTest {
    @Test fun metricShapesReflectTheDataAndClampInvalidRanges() {
        assertEquals(0f,normalizedPercent(-10.0),0f);assertEquals(1f,normalizedPercent(120.0),0f)
        assertTrue(normalizedPercent(34.0)<normalizedPercent(90.0))
        assertTrue(visibilityScale(16000.0)>visibilityScale(1000.0))
        assertTrue(pressureProgress(1025.0)>pressureProgress(1000.0))
        assertEquals(0,uvCategoryIndex(0.0));assertEquals(1,uvCategoryIndex(3.0));assertEquals(4,uvCategoryIndex(12.0))
    }
    @Test fun rainStrengthTracksAmountWithoutUnboundedParticles() {
        val values=listOf(.1,1.0,4.0,15.0,50.0).map {rainIntensity(63,it)}
        assertTrue(values.zipWithNext().all {(a,b)->b>=a});assertTrue(values.all {it in 0f..1f})
    }
    @Test fun currentTimeIsExplicitEvenWhenForecastStartsNextHour() {
        val now=200000L;val w=WeatherBundle(Place("test",0.0,0.0,zone="UTC"),Provider.AUTO,Reading(now-300,mapOf("relative_humidity_2m" to 60.0)),(1..48).map {Reading(now+it*3600,mapOf("relative_humidity_2m" to it.toDouble()))},emptyList())
        val timeline=metricTimeline(w,"relative_humidity_2m",now)
        val threeHourly=w.copy(hourly=(1..32).map {Reading(now+it*10800,mapOf("relative_humidity_2m" to 60.0))})
        assertEquals(9,metricTimeline(threeHourly,"relative_humidity_2m",now).size)
        assertEquals(24,timeline.size);assertTrue(timeline.first().current);assertEquals(1,timeline.count {it.current})
    }
    @Test fun nativeObservationHistoryDoesNotDuplicateNowOrInventForecasts() {
        val now=200000L;val current=Reading(now-300,mapOf("relative_humidity_2m" to 50.0));val w=WeatherBundle(Place("test",0.0,0.0,zone="UTC"),Provider.NMC,current,emptyList(),emptyList(),history=listOf(current,Reading(now-3900,mapOf("relative_humidity_2m" to 40.0))))
        val points=metricTimeline(w,"relative_humidity_2m",now)
        assertEquals(2,points.size);assertTrue(points.last().current);assertTrue(points.all {it.reading.time<=now})
    }
    @Test fun gridRespectsWidthsAndDoesNotLoseCards() {
        val keys=listOf("hourly","uv_index","sun","daily","moon")
        val rows=cardRows(keys)
        assertEquals(keys,rows.flatten());assertEquals(listOf("uv_index","sun"),rows[1]);assertEquals(2,defaultCardSize("daily").columns);assertEquals(1,defaultCardSize("moon").columns)
    }
    @Test fun airQualityMarkerUsesAllSixNonLinearRanges() {
        assertEquals(1f/6,aqiPosition(50.0)!!,.0001f);assertEquals(5f/6,aqiPosition(300.0)!!,.0001f);assertEquals(1f,aqiPosition(500.0)!!,0f)
        assertNull(aqiPosition(null))
    }
}
