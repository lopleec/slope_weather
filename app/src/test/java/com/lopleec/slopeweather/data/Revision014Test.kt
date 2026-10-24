package com.lopleec.slopeweather.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

class Revision014Test {
    private val place=Place("Hong Kong",22.2818,114.1583,"HK","Asia/Hong_Kong")
    @Test fun searchAcceptsDistrictsAndEnglishNamesWithoutReturningShops() {
        val result=photonPlaces(JSONObject("""{"features":[{"properties":{"osm_key":"place","type":"district","name":"Central and Western District","city":"Hong Kong","country":"China","countrycode":"CN"},"geometry":{"coordinates":[114.15,22.28]}},{"properties":{"osm_key":"shop","name":"Hongkong Shop"},"geometry":{"coordinates":[114.1,22.2]}}]}"""),"en")
        assertEquals(1,result.size);assertTrue(result[0].name.startsWith("Central and Western District"));assertTrue(result[0].name.contains("Hong Kong"));assertEquals("en",result[0].nameLanguage)
        assertEquals(result[0],Place.from(result[0].json()))
        // Actual Photon shape: two Hong Kong entries have different centre coordinates
        // but the same boundary. Hong Kong Island has its own, smaller boundary.
        val duplicated=JSONObject("""{"features":[{"properties":{"osm_key":"boundary","type":"state","name":"香港 Hong Kong","countrycode":"CN","extent":[113.8171111,22.5683333,114.5024444,22.1367222]},"geometry":{"coordinates":[114.1857778,22.3492361]}},{"properties":{"osm_key":"place","type":"city","name":"香港 Hong Kong","countrycode":"CN","extent":[113.8171111,22.5683333,114.5024444,22.1367222]},"geometry":{"coordinates":[114.1582831,22.2818333]}},{"properties":{"osm_key":"boundary","type":"district","name":"香港島 Hong Kong Island","countrycode":"CN","extent":[114.1098795,22.3002834,114.3090495,22.1926504]},"geometry":{"coordinates":[114.190975,22.2534634]}}]}""")
        val merged=photonPlaces(duplicated,"")
        assertEquals(2,merged.size);assertEquals(22.2818333,merged[0].latitude,0.0)
        assertTrue(merged[1].name.contains("Island"))
    }
    @Test fun standardsAreSelectedWithoutNumericalConversion() {
        val w=WeatherBundle(place,Provider.AUTO,Reading(1,emptyMap()),emptyList(),emptyList(),air=Reading(1,mapOf("us_aqi" to 66.0,"european_aqi" to 28.0)))
        assertEquals(66.0,selectedAqi(w,AqiStandard.US)!!,0.0)
        assertEquals(28.0,selectedAqi(w,AqiStandard.EU)!!,0.0)
        assertNull(selectedAqi(w,AqiStandard.CN))
        assertEquals(1,aqiBand(28.0,AqiStandard.EU));assertEquals(0,aqiBand(28.0,AqiStandard.US))
    }
    @Test fun cacheRoundTripPreservesMissingValuesAndActualTimestamps() {
        val w=WeatherBundle(place,Provider.AUTO,Reading(100,mapOf("precipitation" to 0.0),null),emptyList(),emptyList(),fetched=110,alertStatus="unavailable",alertsCheckedAt=null)
        val cached=WeatherBundle.from(w.json())
        assertEquals(100,cached.current.time);assertEquals(110,cached.fetched)
        assertNull(cached.current["temperature_2m"]);assertNull(cached.current.code)
        assertEquals(0.0,cached.current["precipitation"]!!,0.0);assertEquals("unavailable",cached.alertStatus)
    }
    @Test fun translatedHierarchyDoesNotAppendAnUntranslatedDuplicate() {
        assertEquals("巴黎 · 法兰西岛 · 法国",localizedHierarchy("巴黎",listOf("Paris","法兰西岛","法国"),"zh-CN"))
        assertEquals("Beijing · China",localizedHierarchy("Beijing",listOf("Beijing","China"),"en"))
    }
    @Test fun duplicateGazetteerEntriesDoNotRemoveDistantNamesakes() {
        val hk=Place("Hong Kong · China",22.28,114.16,"CN")
        val nearby=Place("Hong Kong",22.278,114.175,"HK")
        val distant=Place("Hong Kong · Mexico",22.95,-109.93,"MX")
        assertEquals(listOf(hk,distant),distinctPlaces(listOf(hk,nearby,distant)))
    }
    @Test fun partialHourlyCoverageNeverBecomesACompleteDailyRainTotal() {
        val base=Instant.parse("2026-10-21T00:00:00Z").epochSecond
        val complete=(0..23).map {Reading(base+it*3600,mapOf("precipitation" to 1.0))}
        assertEquals(24.0,aggregateDays(complete,"UTC").single().rain!!,0.0)
        assertNull(aggregateDays(complete.drop(1),"UTC").single().rain)
        assertNull(aggregateDays(complete.mapIndexed {i,r->if(i==5) r.copy(values=emptyMap()) else r},"UTC").single().rain)
    }
    @Test fun aCurrentModelValueDoesNotMasqueradeAsAnOldObservation() {
        val native=WeatherBundle(place,Provider.NMC,Reading(100, mapOf("temperature_2m" to 5.0)),emptyList(),emptyList())
        val model=native.copy(provider=Provider.AUTO,current=Reading(20000,mapOf("temperature_2m" to 25.0,"uv_index" to 5.0)))
        assertNull(supplementWeather(native,model).current["uv_index"])
        assertEquals(5.0,supplementWeather(native,model).current["temperature_2m"]!!,0.0)
    }
    private fun cap(status: String="Actual",type: String="Alert",expires: String="2026-10-23T00:00:00Z")="""<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2"><identifier>official-1</identifier><sender>agency</sender><sent>2026-10-22T00:00:00Z</sent><status>$status</status><msgType>$type</msgType><scope>Public</scope><info><language>fr-FR</language><event>Vent</event><senderName>METEO-FRANCE</senderName><description>Texte original</description><expires>$expires</expires></info><info><language>en-US</language><event>Wind</event><senderName>METEO-FRANCE</senderName><description>Official English text</description><expires>$expires</expires></info></alert>"""
    @Test fun globalWarningsKeepIssuerPreferSourceTranslationAndRejectTestCancelExpired() {
        val now=Instant.parse("2026-10-22T12:00:00Z").epochSecond
        val a=parseCap(cap(),"en","https://severeweather.wmo.int/",now)!!
        assertEquals("Wind",a.title);assertTrue(a.source.startsWith("METEO-FRANCE"));assertEquals("Official English text",a.text)
        assertNull(parseCap(cap(status="Test"),"en","",now));assertNull(parseCap(cap(type="Cancel"),"en","",now));assertNull(parseCap(cap(expires="2026-10-21T00:00:00Z"),"en","",now))
        assertTrue(wmoPointUrl(place).startsWith("https://severeweather.wmo.int/"))
    }
    @Test fun satelliteFramesComeOnlyFromPublishedTimesAndPreserveGaps() {
        val xml="""<Capabilities xmlns:ows="http://www.opengis.net/ows/1.1"><Contents><Layer><ows:Identifier>IMERG_Precipitation_Rate_30min</ows:Identifier><Dimension><ows:Identifier>Time</ows:Identifier><Default>2026-10-22T04:30:00Z</Default><Value>2026-10-22T02:00:00Z/2026-10-22T02:30:00Z/PT30M</Value><Value>2026-10-22T04:00:00Z/2026-10-22T04:30:00Z/PT30M</Value></Dimension></Layer></Contents></Capabilities>"""
        val f=nasaPrecipitationFrames(xml).getJSONArray("frames").objects()
        assertEquals(4,f.size);assertEquals(5400,f[2].getLong("time")-f[1].getLong("time"));assertTrue(f.all {it.getString("url").contains("IMERG_Precipitation_Rate_30min")})
    }
    @Test fun moonMatchesIndependentUsnoReferenceAtLocalNoon() {
        // Official USNO single-day API: 2026-10-21, 39.93 N 116.49 E, UTC+8.
        // fracillum refers to LOCAL NOON, not the weather observation time or late evening.
        val p=Place("Reference coordinate",39.93,116.49,"CN","Asia/Shanghai")
        val at=LocalDateTime.of(2026,10,21,12,0).atZone(ZoneId.of(p.zone)).toEpochSecond()
        val a=astronomy(p,at)
        assertEquals(.73,a.fraction,.015);assertTrue(a.phase in .25.. .5)
        val rise=LocalDateTime.of(2026,10,21,14,56).atZone(ZoneId.of(p.zone)).toEpochSecond()
        val set=LocalDateTime.of(2026,10,21,0,53).atZone(ZoneId.of(p.zone)).toEpochSecond()
        assertTrue(abs(a.rise!!-rise)<120);assertTrue(abs(a.set!!-set)<120)
    }
}
