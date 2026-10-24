package com.lopleec.slopeweather.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lopleec.slopeweather.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CardLayoutAndDetailsTest {
    private val icon=ImageVector.Builder(defaultWidth=1.dp,defaultHeight=1.dp,viewportWidth=1f,viewportHeight=1f).build()
    private fun metric(key: String)=Metric(key,0,"",icon)
    private val time=Instant.parse("2026-10-20T00:00:00Z").epochSecond
    private val place=Place("Test",0.0,0.0,zone="UTC")
    private fun bundle(hours: List<Reading>,days: List<DayForecast>)=WeatherBundle(place,Provider.AUTO,Reading(time,mapOf("relative_humidity_2m" to 90.0)),hours,days,zone="UTC")

    @Test fun persistedOrderRemovesUnknownsAndDuplicatesAndIncludesEveryNewCard() {
        val actual=normalizeCardOrder(listOf("sun","hourly","unknown","sun","precipitation"))
        assertEquals(listOf("sun","hourly","precipitation"),actual.take(3))
        assertEquals(defaultHomeCards.toSet(),actual.toSet())
        assertEquals(actual.size,actual.distinct().size)
        assertTrue(actual.containsAll(listOf("overview","minutely","radar","air","pollen")))
    }
    @Test fun movingAcrossUnavailableCardsReachesTheVisibleNeighbor() {
        val order=listOf("hourly","hidden","daily","radar")
        assertEquals(listOf("hidden","daily","hourly","radar"),moveCard(order,"hourly",2))
        assertSame(order,moveCard(order,"hourly",-1))
        assertSame(order,moveCard(order,"unknown",1))
    }
    @Test fun selectedDayNeverInheritsCurrentAirQualityOrMissingReadings() {
        val next=DayForecast(time+86400,20.0,10.0,3,rain=0.0,uv=0.0)
        val weather=bundle(listOf(Reading(time,mapOf("relative_humidity_2m" to 55.0))),listOf(next)).copy(air=Reading(time,mapOf("us_aqi" to 25.0)))
        val day=weatherOnDay(weather,next)
        assertNull(day.current["relative_humidity_2m"])
        assertNull(day.air)
        assertTrue(day.hourly.isEmpty())
        assertEquals(0.0,day.current["precipitation"]!!,0.0)
        assertEquals(0.0,day.current["uv_index"]!!,0.0)
    }
    @Test fun dailyTotalsOverridePartialHourlyAccumulationAndWindUsesPeakDirection() {
        val d=DayForecast(time,20.0,10.0,3,rain=4.0,uv=7.0,wind=28.0)
        val h=listOf(Reading(time,mapOf("precipitation" to .2,"wind_speed_10m" to 10.0,"wind_direction_10m" to 350.0,"relative_humidity_2m" to 40.0)),Reading(time+3600,mapOf("precipitation" to .1,"wind_speed_10m" to 20.0,"wind_direction_10m" to 10.0,"relative_humidity_2m" to 60.0)))
        val day=weatherOnDay(bundle(h,listOf(d)),d)
        assertEquals(4.0,day.current["precipitation"]!!,0.0)
        assertEquals(28.0,day.current["wind_speed_10m"]!!,0.0)
        assertEquals(10.0,day.current["wind_direction_10m"]!!,0.0)
        assertEquals(50.0,day.current["relative_humidity_2m"]!!,0.0)
    }
    @Test fun midnightUsesLocationTimezoneAndExcludesOtherDates() {
        val d=DayForecast(time,20.0,10.0,3)
        val hours=listOf(Reading(time-9*3600,emptyMap()),Reading(time-7*3600,emptyMap()),Reading(time+15*3600,emptyMap()),Reading(time+17*3600,emptyMap()))
        val weather=bundle(hours,listOf(d)).copy(zone="Asia/Shanghai")
        assertEquals(listOf(time-7*3600,time+15*3600),hoursOnDay(weather,d.time).map {it.time})
    }
    @Test fun humiditySummaryUsesAvailableForecastAverageAndKeepsMissingDataMissing() {
        val d=DayForecast(time,20.0,10.0,3)
        val weather=bundle(listOf(Reading(time,mapOf("relative_humidity_2m" to 40.0)),Reading(time+3600,mapOf("relative_humidity_2m" to 60.0)),Reading(time+7200,emptyMap())),listOf(d))
        assertEquals(50.0,metricSummary(metric("relative_humidity_2m"),weather).value!!,0.0)
        assertNull(metricSummary(metric("visibility"),weather).value)
    }
    @Test fun dailyDetailsUseTheSameCompleteLocalDayRuleAsProviderAggregation() {
        val zone=ZoneId.of("America/New_York")
        for(date in listOf("2026-03-08","2026-11-01")) {
            val local=LocalDate.parse(date)
            val start=local.atStartOfDay(zone).toEpochSecond()
            val end=local.plusDays(1).atStartOfDay(zone).toEpochSecond()
            val hours=(start until end step 3600).map {Reading(it,mapOf("precipitation" to 1.0,"snowfall" to .5))}
            val forecast=DayForecast(start,20.0,10.0,3)
            val weather=bundle(hours,listOf(forecast)).copy(zone=zone.id)
            val complete=weatherOnDay(weather,forecast)
            assertEquals(hours.size.toDouble(),complete.current["precipitation"]!!,0.0)
            assertEquals(hours.size*.5,complete.current["snowfall"]!!,0.0)
            assertNull(weatherOnDay(weather.copy(hourly=hours.dropLast(1)),forecast).current["precipitation"])
        }
    }
}
