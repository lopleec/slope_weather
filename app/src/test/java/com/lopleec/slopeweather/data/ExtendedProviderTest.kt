package com.lopleec.slopeweather.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ExtendedProviderTest {
    private fun text(name: String)=javaClass.classLoader!!.getResource(name)!!.readText()
    @Test fun nmcPublicForecastHasRealThreeHourFields() {
        val hours=NmcProvider.parseForecastPage(text("nmc-forecast.html"),NmcProvider.time("2026-10-21 20:00"))
        assertTrue(hours.size>=40)
        val first=hours.first()
        assertEquals(NmcProvider.time("2026-10-21 23:00"),first.time)
        assertEquals(3.6,first["temperature_2m"]!!,.001)
        assertEquals(3.1*3.6,first["wind_speed_10m"]!!,.001)
        assertEquals(70.1,first["relative_humidity_2m"]!!,.001)
        assertEquals(1021.9,first["surface_pressure"]!!,.001)
        assertNull(first["precipitation"])
        assertNull(first["precipitation_probability"])
        assertEquals(.2,first["cloud_cover"]!!,.001)
        assertFalse(first.day)
        assertEquals(225.0,first["wind_direction_10m"]!!,.001)
        assertTrue(hours.zipWithNext().all {(a,b)->b.time>a.time})
        assertEquals(NmcProvider.time("2026-10-22 02:00"),hours[1].time)
    }
    @Test fun nmcPastObservationsAreNotForecasts() {
        val w=NmcProvider.parse(JSONObject(text("nmc.json")),TestPlaces.all.first())
        assertTrue(w.hourly.isEmpty())
        assertTrue(w.history.isNotEmpty())
        assertEquals(2.3*3.6,w.history.last()["wind_speed_10m"]!!,.001)
        assertTrue(w.current["dew_point_2m"]!!<=w.current["temperature_2m"]!!)
    }
    @Test fun domesticPolygonResolvesDistrictWithoutNetworkGeocoding() {
        val districts=JSONObject(text("geocoding/beijing-districts.json"))
        val at=ChinaGeocoder.featureAt(districts,39.959,116.298)!!
        assertEquals("海淀区",at.getJSONObject("properties").getString("name"))
        assertNull(ChinaGeocoder.featureAt(districts,35.6762,139.6503))
    }
    @Test fun polygonHolesDoNotCountAsAdministrativeLand() {
        val j=JSONObject("""{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]],[[4,4],[6,4],[6,6],[4,6],[4,4]]]}""")
        assertTrue(ChinaGeocoder.contains(j,2.0,2.0));assertFalse(ChinaGeocoder.contains(j,5.0,5.0))
    }
    @Test fun supplementNeverOverwritesNativeObservationsOrCadence() {
        val p=TestPlaces.all.first();val now=Instant.now().epochSecond
        val native=WeatherBundle(p,Provider.NMC,Reading(now,mapOf("temperature_2m" to 12.0,"relative_humidity_2m" to 60.0),65),listOf(Reading(now+10800,mapOf("temperature_2m" to 14.0))),listOf(DayForecast(now,high=15.0,low=8.0,code=65)))
        val model=WeatherBundle(p,Provider.AUTO,Reading(now,mapOf("temperature_2m" to 18.0,"uv_index" to 2.0),0),listOf(Reading(now+10800,mapOf("temperature_2m" to 19.0,"visibility" to 16000.0))),listOf(DayForecast(now,high=20.0,low=10.0,code=0,uv=3.0)))
        val result=supplementWeather(native,model)
        assertEquals(12.0,result.current["temperature_2m"]!!,0.0);assertEquals(65,result.current.code)
        assertEquals(14.0,result.hourly.single()["temperature_2m"]!!,0.0)
        assertEquals(16000.0,result.hourly.single()["visibility"]!!,0.0)
        assertEquals(15.0,result.daily.single().high!!,0.0)
        assertEquals(setOf("uv_index"),result.supplemented)
        assertEquals(result,WeatherBundle.from(result.json()))
    }
    @Test fun nwsMultiHourRainIsNotPretendedToBeHourlyRain() {
        val at=Instant.parse("2026-10-21T12:00:00Z").epochSecond
        val grid=JSONObject("""{"properties":{"visibility":{"uom":"wmoUnit:m","values":[{"validTime":"2026-10-21T12:00:00+00:00/PT3H","value":16000}]},"quantitativePrecipitation":{"uom":"wmoUnit:mm","values":[{"validTime":"2026-10-21T12:00:00+00:00/PT6H","value":9}]},"windGust":{"uom":"wmoUnit:km_h-1","values":[{"validTime":"2026-10-21T12:00:00+00:00/PT1H","value":42}]}}}""")
        val h=RegionalProviders.nwsGrid(grid,listOf(Reading(at,mapOf("temperature_2m" to 15.0)))).single()
        assertEquals(16000.0,h["visibility"]!!,0.0);assertEquals(42.0,h["wind_gusts_10m"]!!,0.0);assertNull(h["precipitation"])
    }
    @Test fun jmaCancellationAndDifferentAreaCannotBecomeCurrentAlerts() {
        val rows=org.json.JSONArray(text("jma-warning-r8.json")).objects()
        assertEquals(1,jmaAlerts(rows,"130010","130000").size)
        assertTrue(jmaAlerts(rows.take(1),"130010","130000").isEmpty())
        val report=JSONObject("""{"reportDatetime":"2026-10-21T12:00:00+09:00","headlineText":"大雨に警戒","warning":{"class10Items":[{"areaCode":"130010","kinds":[{"code":"03","status":"発表"}]}]}}""")
        assertEquals(1,jmaAlerts(listOf(report),"130010","130000").size)
        assertTrue(jmaAlerts(listOf(report),"130020","130000").isEmpty())
        val cancelled=JSONObject(report.toString()).put("reportDatetime","2026-10-21T13:00:00+09:00");cancelled.getJSONObject("warning").getJSONArray("class10Items").getJSONObject(0).getJSONArray("kinds").getJSONObject(0).put("status","解除")
        assertTrue(jmaAlerts(listOf(report,cancelled),"130010","130000").isEmpty())
    }
    @Test fun dewPointUsesHumidityAndCannotBeDerivedFromMissingData() {
        assertFalse(derivedWeatherValues(mapOf("temperature_2m" to 20.0)).containsKey("dew_point_2m"))
        assertEquals(20.0,derivedWeatherValues(mapOf("temperature_2m" to 20.0,"relative_humidity_2m" to 100.0))["dew_point_2m"]!!,.01)
    }
    @Test fun astronomicalEventsUseTheSelectedLocalDate() {
        val p=Place("New York",40.7128,-74.006,"US","America/New_York")
        val at=ZonedDateTime.of(2024,6,21,12,0,0,0,ZoneId.of(p.zone)).toEpochSecond()
        val a=astronomy(p,at)
        assertEquals("2024-06-21",localTime(a.sunRise!!,p.zone,"yyyy-MM-dd"))
        val rise=Instant.ofEpochSecond(a.sunRise).atZone(ZoneId.of(p.zone))
        assertTrue(rise.hour==5 && rise.minute in 10..40)
        assertTrue(a.dawn!!<a.sunRise);assertTrue(a.dusk!!>a.sunSet!!)
        assertTrue(a.fraction in 0.0..1.0)
    }
}
