package com.lopleec.slopeweather.data

import org.json.JSONArray
import org.json.JSONObject

/** Region URLs contain only an administrative code, never the user's coordinates. */
class ChinaGeocoder(private val provinces: JSONObject,private val http: WeatherHttp) {
    suspend fun resolve(lat: Double,lon: Double): Place? {
        val boundary=wgs84ToGcj02(lat,lon)
        val province=featureAt(provinces,boundary.first,boundary.second) ?: return null
        val names=mutableListOf(province.getJSONObject("properties").getString("name"))
        var parent=province
        for(level in 0..1) {
            val properties=parent.getJSONObject("properties")
            if(properties.optInt("childrenNum")==0 || properties.optString("level")=="district") break
            val collection=try {http.json("https://geo.datav.aliyun.com/areas_v3/bound/${properties.getInt("adcode")}_full.json",2_592_000_000,timeoutMillis=3500)} catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) {break}
            val child=featureAt(collection,boundary.first,boundary.second) ?: break
            names.add(0,child.getJSONObject("properties").getString("name"));parent=child
        }
        val country=when(province.getJSONObject("properties").optInt("adcode")) {710000->"TW";810000->"HK";820000->"MO";else->"CN"}
        return Place(administrativeName(names+"中国"),lat,lon,country,when(country) {"HK"->"Asia/Hong_Kong";"MO"->"Asia/Macau";"TW"->"Asia/Taipei";else->"Asia/Shanghai"})
    }
    companion object {
        fun featureAt(collection: JSONObject,lat: Double,lon: Double): JSONObject? = collection.optJSONArray("features")?.objects()?.firstOrNull {contains(it.getJSONObject("geometry"),lat,lon)}
        fun contains(geometry: JSONObject,lat: Double,lon: Double): Boolean {
            val coordinates=geometry.optJSONArray("coordinates") ?: return false
            fun ring(r: JSONArray): Boolean {
                var inside=false;var j=r.length()-1
                for(i in 0 until r.length()) {
                    val a=r.getJSONArray(i);val b=r.getJSONArray(j)
                    val ax=a.getDouble(0);val ay=a.getDouble(1);val bx=b.getDouble(0);val by=b.getDouble(1)
                    if((ay>lat)!=(by>lat) && lon<(bx-ax)*(lat-ay)/(by-ay)+ax) inside=!inside
                    j=i
                };return inside
            }
            fun polygon(p: JSONArray)=p.length()>0 && ring(p.getJSONArray(0)) && (1 until p.length()).none {ring(p.getJSONArray(it))}
            return when(geometry.optString("type")) {"Polygon"->polygon(coordinates);"MultiPolygon"->(0 until coordinates.length()).any {polygon(coordinates.getJSONArray(it))};else->false}
        }
    }
}
