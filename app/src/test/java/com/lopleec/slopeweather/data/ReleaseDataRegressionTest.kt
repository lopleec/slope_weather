package com.lopleec.slopeweather.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReleaseDataRegressionTest {
    private fun day(date: String,zone: String): List<Reading> {
        val local=LocalDate.parse(date);val tz=ZoneId.of(zone)
        val start=local.atStartOfDay(tz).toEpochSecond()
        val end=local.plusDays(1).atStartOfDay(tz).toEpochSecond()
        return (start until end step 3600).map {Reading(it,mapOf("precipitation" to 1.0))}
    }

    @Test fun springClockChangeStillHasACompleteDailyAccumulation() {
        val hours=day("2026-03-08","America/New_York")
        assertEquals(23,hours.size)
        assertEquals(23.0,aggregateDays(hours,"America/New_York").single().rain!!,0.0)
    }

    @Test fun autumnClockChangeIncludesBothRepeatedHours() {
        val hours=day("2026-11-01","America/New_York")
        assertEquals(25,hours.size)
        assertEquals(25.0,aggregateDays(hours,"America/New_York").single().rain!!,0.0)
    }

    @Test fun twentyFourHoursOfATwentyFiveHourDayAreStillIncomplete() {
        assertNull(aggregateDays(day("2026-11-01","America/New_York").dropLast(1),"America/New_York").single().rain)
    }

    @Test fun knownForeignCountriesDoNotUseAnOverlappingRegionalBoundingBox() {
        assertFalse(Provider.NWS.supports(Place("Toronto",43.65,-79.38,"CA")))
        assertFalse(Provider.NWS.supports(Place("Mexico City",19.43,-99.13,"MX")))
        assertFalse(Provider.BRIGHTSKY.supports(Place("Austria",48.2,14.2,"AT")))
        assertTrue(Provider.NWS.supports(Place("New York",40.71,-74.01,"US")))
        assertTrue(Provider.NWS.supports(Place("Unknown country",40.71,-74.01)))
    }

    @Test fun twoGazetteersCannotCreateDuplicateSearchListKeys() {
        val first=Place("Paris · France",48.8566,2.3522,"FR")
        val second=first.copy(name="巴黎 · 法国")
        assertEquals(listOf(first),distinctPlaces(listOf(first,second)))
    }
    @Test fun coordinateOnlySearchesUseGlobalWarningsInsteadOfAssumingANationalCoverageArea() {
        val toronto=Place("Coordinate",43.65,-79.38)
        val tokyo=Place("Coordinate",35.68,139.65)
        assertTrue(Provider.NWS.supports(toronto))
        assertNull(regionalAlertProvider(toronto))
        assertNull(regionalAlertProvider(tokyo))
        assertNull(regionalAlertProvider(toronto.copy(country="CA")))
        assertEquals(Provider.JMA_DIRECT,regionalAlertProvider(tokyo.copy(country="JP")))
        assertEquals(Provider.NWS,regionalAlertProvider(Place("New York",40.71,-74.01,"US")))
        assertEquals(Provider.NMC,regionalAlertProvider(Place("Beijing",39.9,116.4,"CN")))
        assertEquals(Provider.BRIGHTSKY,regionalAlertProvider(Place("Berlin",52.52,13.4,"DE")))
    }

    @Test fun capPrefixesDoNotHideAnActualPublicWarning() {
        val xml="""<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2"><cap:identifier>warning-1</cap:identifier><cap:sender>agency</cap:sender><cap:status>Actual</cap:status><cap:msgType>Alert</cap:msgType><cap:scope>Public</cap:scope><cap:info><cap:language>en-US</cap:language><cap:event>Wind</cap:event><cap:description>Official warning</cap:description><cap:severity>Severe</cap:severity></cap:info></cap:alert>"""
        val alert=parseCap(xml,"en","https://example.org/warning")
        assertNotNull(alert)
        assertEquals("warning-1",alert!!.id)
        assertEquals("Official warning",alert.text)
        assertTrue(alert.critical)
    }
}
