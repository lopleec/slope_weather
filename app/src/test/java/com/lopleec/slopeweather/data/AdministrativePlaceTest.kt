package com.lopleec.slopeweather.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdministrativePlaceTest {
    @Test fun districtComesFirstAndDuplicateMunicipalityIsRemoved() {
        val name=administrativeName(listOf("海淀区","北京市","北京","北京市","中国"))
        assertEquals("海淀区 · 北京市 · 中国",name)
    }
    @Test fun sameNamedCityAndProvinceAreDifferentLevels() {
        assertEquals("船营区 · 吉林市 · 吉林省 · 中国",administrativeName(listOf("船营区","吉林市","吉林省","中国")))
    }
    @Test fun roadsPoisAndPostcodesAreNeverUsedAsAdministrativeNames() {
        val j=JSONObject("""{"address":{"road":"Test street","house_number":"42","postcode":"100000","city_district":"海淀区","city":"北京市","state":"北京市","country":"中国","country_code":"cn"}}""")
        val p=administrativePlace(j,39.959,116.298,"Asia/Shanghai")!!
        assertEquals("海淀区 · 北京市 · 中国",p.name)
        assertEquals("CN",p.country)
        assertEquals(39.959,p.latitude,0.0)
    }
    @Test fun countyIsRetainedWhenItIsTheOnlyDistrictFieldInChina() {
        val j=JSONObject("""{"address":{"county":"望城区","city":"长沙市","state":"湖南省","country":"中国","country_code":"cn"}}""")
        assertEquals("望城区 · 长沙市 · 湖南省 · 中国",administrativePlace(j,28.3,112.8,"Asia/Shanghai")!!.name)
    }
    @Test fun missingAdministrativeDataDoesNotReturnARoadOrInventACity() {
        assertNull(administrativePlace(JSONObject("""{"address":{"road":"Test street","postcode":"100000"}}"""),0.0,0.0,"UTC"))
        assertEquals("",administrativeName(listOf(null,"", "null")))
    }
    @Test fun japaneseWardAndPrefectureStayInTheirLocalHierarchy() {
        val j=JSONObject("""{"address":{"city":"杉並区","state":"東京都","country":"日本","country_code":"jp"}}""")
        assertEquals("杉並区 · 東京都 · 日本",administrativePlace(j,35.6762,139.6503,"Asia/Tokyo")!!.name)
    }
    @Test fun realGeocodeJsonIncludesDistrictAndBeijingButNotItsStreet() {
        val raw=javaClass.getResourceAsStream("/geocoding/haidian-geocodejson.json")!!.bufferedReader().use {it.readText()}
        val p=administrativePlace(JSONObject(raw),39.959,116.298,"Asia/Shanghai")!!
        assertEquals("海淀区 · 北京市 · 中国",p.name)
        assertFalse(p.name.contains("街道"))
    }

    @Test fun realTokyoGeocodeJsonStopsAtTheWardAndSkipsNeighbourhoodLevels() {
        val raw=javaClass.getResourceAsStream("/geocoding/tokyo-geocodejson.json")!!.bufferedReader().use {it.readText()}
        val p=administrativePlace(JSONObject(raw),35.6762,139.6503,"Asia/Tokyo")!!
        assertTrue(p.name.startsWith("杉並區"))
        assertTrue(p.name.endsWith("日本"))
        assertFalse(p.name.contains("二丁目"))
        assertFalse(p.name.contains("和泉"))
    }

}
