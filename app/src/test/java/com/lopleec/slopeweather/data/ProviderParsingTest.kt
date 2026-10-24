package com.lopleec.slopeweather.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray

class ProviderParsingTest {
    private fun fixture(name: String)=javaClass.classLoader!!.getResource(name)!!.readText()
    private val shanghai=TestPlaces.all.first()
    @Test fun missingValuesStayMissingAndZeroRemainsReal() {
        val readings=OpenMeteo.series(JSONObject("""{"time":[100,200],"temperature_2m":[null,0],"relative_humidity_2m":[70,null]}"""))
        assertNull(readings[0]["temperature_2m"])
        assertEquals(0.0,readings[1]["temperature_2m"]!!,0.0)
        assertNull(readings[1]["relative_humidity_2m"])
    }
    @Test fun nmcSentinelNeverAppearsAsTemperatureOrWind() {
        val w=NmcProvider.parse(JSONObject(fixture("nmc.json")),shanghai)
        assertEquals(22.7,w.current["temperature_2m"]!!,.001)
        assertEquals(.36,w.current["wind_speed_10m"]!!,.001)
        assertNull(w.current["wind_direction_10m"])
        assertEquals(28.0,w.daily.first().high!!,.001)
        assertEquals(17.0,w.daily.first().low!!,.001)
        assertEquals(76.0,w.current["cn_aqi"]!!,.001)
        assertFalse(w.current.day)
        assertEquals("20:00",localTime(w.current.time,w.zone))
    }
    @Test fun cacheRoundTripPreservesProviderAndAbsence() {
        val source=NmcProvider.parse(JSONObject(fixture("nmc.json")),shanghai)
        val saved=WeatherBundle.from(JSONObject(source.json().toString()))
        assertEquals(source.current,saved.current)
        assertEquals(source.daily,saved.daily)
        assertEquals(source.provider,saved.provider)
        assertEquals(source.notes,saved.notes)
        assertEquals(source.radarImage,saved.radarImage)
        assertEquals(source.alertsChecked,saved.alertsChecked)
    }
    @Test fun hkoHasNoInventedHourlyOrRainProbabilities() {
        val w=RegionalProviders.hko(JSONObject(fixture("hko-current.json")),JSONObject(fixture("hko-daily.json")),TestPlaces.all.first {it.country=="HK"})
        assertEquals(29.0,w.current["temperature_2m"]!!,.001)
        assertEquals(9,w.daily.size)
        assertTrue(w.hourly.isEmpty())
        assertNull(w.daily.first().probability)
        assertNull(w.current["uv_index"])
        assertFalse(w.current.day)
    }
    @Test fun nwsConvertsFahrenheitMphAndKeepsMissingPrecipitation() {
        val j=JSONObject("""{"properties":{"periods":[{"startTime":"2026-10-20T08:00:00-04:00","temperature":68,"temperatureUnit":"F","windSpeed":"5 to 10 mph","windDirection":"NE","shortForecast":"Rain Showers","isDaytime":true,"probabilityOfPrecipitation":{"value":null}}]}}""")
        val h=RegionalProviders.nwsHourly(j).single()
        assertEquals(20.0,h["temperature_2m"]!!,.001)
        assertEquals(16.09344,h["wind_speed_10m"]!!,.001)
        assertEquals(45.0,h["wind_direction_10m"]!!,.001)
        assertNull(h["precipitation_probability"])
        assertEquals(63,h.code)
    }
    @Test fun metSixHourTotalsAreNotPresentedAsHourlyRain() {
        val j=JSONObject("""{"properties":{"timeseries":[{"time":"2026-10-20T12:00:00Z","data":{"instant":{"details":{"air_temperature":8,"wind_speed":3}},"next_6_hours":{"summary":{"symbol_code":"rain"},"details":{"precipitation_amount":6}}}}]}}""")
        val w=RegionalProviders.met(j,shanghai,timestamp("2026-10-20T12:00:00Z"))
        assertNull(w.current["precipitation"])
        assertEquals(10.8,w.current["wind_speed_10m"]!!,.001)
        assertEquals(61,w.current.code)
        assertNull(RegionalProviders.metCode("unknown"))
    }
    @Test fun jmaChoosesTokyoAndHonorsQualityFlags() {
        val p=TestPlaces.all.first {it.country=="JP"}
        val jma=JmaProvider(JSONObject(fixture("jma-stations.json")),JSONObject(fixture("jma-week.json")))
        val r=jma.resolve(p)
        assertEquals("130000",r.office)
        assertEquals("44132",r.station)
        val observation=JSONObject("""{"temp":[22.5,0],"humidity":[999,1],"wind":[2.5,0],"windDirection":[4,0],"precipitation1h":[0,0]}""")
        val w=jma.parse(JSONArray(fixture("jma.json")).objects(),observation,timestamp("2026-10-20T12:00:00Z"),p,r)
        assertEquals(22.5,w.current["temperature_2m"]!!,.001)
        assertNull(w.current["relative_humidity_2m"])
        assertEquals(9.0,w.current["wind_speed_10m"]!!,.001)
        assertEquals(90.0,w.current["wind_direction_10m"]!!,.001)
        assertEquals(29.0,w.daily.first {localTime(it.time,w.zone,"yyyy-MM-dd")=="2026-10-21"}.high!!,.001)
        assertTrue(w.daily.size>=7)
        assertEquals(3,w.current.code)
    }
    @Test fun brightSkyMissingHumidityStaysMissing() {
        val w=BrightSkyProvider.parse(JSONObject(fixture("brightsky.json")),Place("Berlin",52.52,13.4,"DE","Europe/Berlin"),timestamp("2026-10-20T12:00:00Z"))
        assertEquals(8.7,w.current["temperature_2m"]!!,.001)
        assertNull(w.current["relative_humidity_2m"])
        assertTrue(w.hourly.isNotEmpty())
    }
    @Test fun timezonesHandleDstAndNonLocalCities() {
        assertEquals("08:00",localTime(timestamp("2026-10-20T12:00:00Z"),"America/New_York"))
        assertEquals("01:00",localTime(timestamp("2026-11-01T05:00:00Z"),"America/New_York"))
        assertEquals("01:00",localTime(timestamp("2026-11-01T06:00:00Z"),"America/New_York"))
    }
    @Test fun regionalProvidersRejectWrongContinents() {
        assertFalse(Provider.HKO.supports(shanghai))
        assertFalse(Provider.NWS.supports(shanghai))
        assertFalse(Provider.JMA_DIRECT.supports(shanghai))
        assertTrue(Provider.NMC.supports(shanghai))
        assertTrue(Provider.JMA_DIRECT.supports(TestPlaces.all.first {it.country=="JP"}))
    }
    @Test fun allNineModelAdaptersParseRealResponses() {
        val cases=listOf("auto" to Provider.AUTO,"cma" to Provider.CMA,"jma-model" to Provider.JMA,"dwd" to Provider.DWD,"ecmwf" to Provider.ECMWF,"gfs" to Provider.GFS,"france" to Provider.FRANCE,"ukmo" to Provider.UKMO,"knmi" to Provider.KNMI)
        for((name,provider) in cases) {
            val j=JSONObject(fixture("models/$name.json"))
            val hours=j.getJSONObject("hourly")
            val now=j.optJSONObject("current")?.getLong("time") ?: hours.getJSONArray("time").getLong(12)
            val w=OpenMeteo.parse(j,shanghai,provider,now)
            assertNotNull("$name temperature",w.current["temperature_2m"])
            assertTrue("$name daily",w.daily.isNotEmpty())
            assertTrue("$name hourly",w.hourly.isNotEmpty())
            assertTrue(w.hourly.all {h->h.values.values.all(Double::isFinite)})
        }
    }
    @Test fun nwsLiveHourlyFixtureParsesWithoutInventedValues() {
        val hours=RegionalProviders.nwsHourly(JSONObject(fixture("nws-hourly.json")))
        assertTrue(hours.size>=24)
        assertNotNull(hours.first()["temperature_2m"])
        assertTrue(hours.all {it["precipitation"]==null})
    }

}
