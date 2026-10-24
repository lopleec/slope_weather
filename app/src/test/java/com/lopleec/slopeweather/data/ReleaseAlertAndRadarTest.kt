package com.lopleec.slopeweather.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseAlertAndRadarTest {
    @Test fun aReusedWarningIdCanNotifyANewBulletinWithoutRepeatingTheSameOne() {
        val alert=WeatherAlert("WRAIN","Rain warning","Official text","HKO",issued=100,expires=200)
        val receipt=alert.notificationFingerprint()
        val remembered=rememberAlertReceipt(emptySet(),receipt)
        assertTrue(alert.copy().notificationFingerprint() in remembered)
        assertFalse(alert.copy(issued=300,expires=400).notificationFingerprint() in remembered)
        assertFalse(alert.copy(text="Updated official text").notificationFingerprint() in remembered)
        assertFalse(alert.copy(source="Another issuer").notificationFingerprint() in remembered)
    }

    @Test fun notificationReceiptsRemainBoundedAndPreserveRecentEntriesAcrossStorage() {
        var remembered=emptySet<String>()
        repeat(300) {remembered=rememberAlertReceipt(remembered,"receipt-$it")}
        val saved=JSONArray(remembered.toList())
        val restored=(0 until saved.length()).map {saved.getString(it)}.toCollection(LinkedHashSet())
        val updated=rememberAlertReceipt(restored,"receipt-300")
        assertEquals(256,updated.size);assertFalse("receipt-0" in updated)
        assertFalse("receipt-44" in updated);assertTrue("receipt-299" in updated)
        assertEquals("receipt-300",updated.last())
    }

    private fun radar(host: String,path: String="/v2/radar/200")=JSONObject()
        .put("host",host).put("radar",JSONObject().put("past",JSONArray()
            .put(JSONObject().put("time",200).put("path",path))
            .put(JSONObject().put("time",100).put("path","/v2/radar/100"))))

    @Test fun radarFramesUseSortedPublishedTimesAndNativeZoomSeven() {
        val parsed=rainViewerFrames(radar("https://tilecache.rainviewer.com"))
        assertEquals(7,parsed.getInt("maxZoom"))
        assertEquals(listOf(100L,200L),parsed.getJSONArray("frames").objects().map {it.getLong("time")})
        assertTrue(parsed.getJSONArray("frames").getJSONObject(0).getString("url").startsWith("https://tilecache.rainviewer.com/v2/radar/100/"))
    }

    @Test fun lookalikeHostsAndEmbeddedCredentialsCannotSupplyRadarTiles() {
        for(host in listOf("https://evilrainviewer.com","https://rainviewer.com.evil.test",
            "https://attacker@tilecache.rainviewer.com","http://tilecache.rainviewer.com",
            "https://tilecache.rainviewer.com:8443","https://tilecache.rainviewer.com/?redirect=evil")) {
            assertThrows(host,IllegalArgumentException::class.java) {rainViewerFrames(radar(host))}
        }
    }

    @Test fun radarMetadataCannotInjectAPathOrQueryOutsideItsRadarNamespace() {
        for(path in listOf("//evil.test/tiles","/v2/radar/../other","/v2/radar/1?redirect=evil","/v2/radar/1#fragment")) {
            assertThrows(path,IllegalArgumentException::class.java) {rainViewerFrames(radar("https://tilecache.rainviewer.com",path))}
        }
    }
}
