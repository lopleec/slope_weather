package com.lopleec.slopeweather.data

import org.json.JSONObject
import java.util.Locale

/** Keep administrative areas from the most specific available level upward. Never use POIs or roads. */
fun administrativeName(parts: List<String?>): String {
    val names=ArrayList<String>()
    for(raw in parts) {
        val value=raw?.trim()?.takeUnless {it.isBlank() || it.equals("null",true)} ?: continue
        if(names.any {it.equals(value,true)}) continue
        // A bare duplicate such as 北京 / 北京市 is one area. 吉林市 / 吉林省 remain distinct.
        val suffixes=listOf("市","省","都","特别行政区","特別行政區")
        if(names.any {existing->suffixes.any {suffix->existing.removeSuffix(suffix)==value || value.removeSuffix(suffix)==existing}}) continue
        names.add(value)
    }
    return names.joinToString(" · ")
}
fun administrativePlace(j: JSONObject,latitude: Double,longitude: Double,zone: String): Place? {
    val geocoding=j.optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties")?.optJSONObject("geocoding")
    if(geocoding!=null) {
        val country=geocoding.optString("country_code").uppercase(Locale.ROOT)
        val maximumLevel=when(country) {"CN"->6;"JP","TW","HK","MO"->8;else->9}
        val admin=geocoding.optJSONObject("admin")
        val levels=admin?.keys()?.asSequence()?.mapNotNull {key->key.removePrefix("level").toIntOrNull()?.takeIf {it<=maximumLevel}?.let {it to admin.optString(key)}}?.sortedByDescending {it.first}?.map {it.second}?.toList().orEmpty()
        val hierarchy=if(levels.isNotEmpty()) levels else listOf("district","county","city","state").map {geocoding.optString(it)}
        val name=administrativeName(hierarchy+geocoding.optString("country"))
        return name.takeIf {it.isNotBlank()}?.let {Place(it,latitude,longitude,country,zone)}
    }
    val a=j.optJSONObject("address") ?: return null
    fun text(key: String)=a.optString(key).takeUnless {it.isBlank() || it=="null"}
    val country=text("country_code").orEmpty().uppercase(Locale.ROOT)
    val county=text("county")
    val district=text("city_district") ?: text("borough") ?: county.takeIf {country=="CN"}
    val city=text("city") ?: text("town") ?: text("municipality")
    val name=administrativeName(listOf(district,city,county.takeUnless {country=="CN"},text("state_district"),text("state"),text("country")))
    return name.takeIf {it.isNotBlank()}?.let {Place(it,latitude,longitude,country,zone)}
}
