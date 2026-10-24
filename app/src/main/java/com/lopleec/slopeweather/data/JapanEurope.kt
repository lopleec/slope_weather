package com.lopleec.slopeweather.data

import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

class JmaProvider(private val stations: JSONObject,private val regions: JSONObject) {
    data class Region(val office: String,val area: String,val week: String,val station: String)
    fun resolve(p: Place): Region = regions.keys().asSequence().flatMap { office -> regions.getJSONArray(office).objects().asSequence().map { d -> Region(office,d.getString("srf"),d.getString("week"),d.getString("amedas")) } }.filter {stations.has(it.station)}.minBy { r ->
        val s=stations.getJSONObject(r.station)
        val lat=s.getJSONArray("lat");val lon=s.getJSONArray("lon")
        distance(p.latitude,p.longitude,lat.getDouble(0)+lat.getDouble(1)/60,lon.getDouble(0)+lon.getDouble(1)/60)
    }
    suspend fun load(http: WeatherHttp,p: Place): WeatherBundle {
        val r=resolve(p)
        val forecasts=http.json("https://www.jma.go.jp/bosai/forecast/data/forecast/${r.office}.json").getJSONArray("items").objects()
        val latest=timestamp(http.text("https://www.jma.go.jp/bosai/amedas/data/latest_time.txt",300_000).trim())
        val stamp=DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(latest))
        val observed=http.json("https://www.jma.go.jp/bosai/amedas/data/map/$stamp.json",300_000).getJSONObject(r.station)
        return parse(forecasts,observed,latest,p,r)
    }
    fun parse(forecasts: List<JSONObject>, observed: JSONObject, latest: Long, p: Place,r: Region): WeatherBundle {
        val v=mutableMapOf<String,Double>()
        mapOf("temp" to "temperature_2m","humidity" to "relative_humidity_2m","pressure" to "surface_pressure","normalPressure" to "pressure_msl","precipitation1h" to "precipitation","wind" to "wind_speed_10m","windDirection" to "wind_direction_10m").forEach { (source,target) ->
            val arr=observed.optJSONArray(source)
            // JMA quality flag 0 indicates a valid observation. Do not use invalid or suspect readings.
            if(arr!=null && arr.optInt(1,-1)==0) arr.number(0)?.let { value -> v[target]=when(source) { "wind" -> value*3.6;"windDirection" -> value*22.5;else -> value } }
        }
        require(v["temperature_2m"]!=null) {"No recent AMeDAS observation"}
        val days=linkedMapOf<String,DayForecast>()
        for(report in forecasts.reversed()) {
            val series=report.getJSONArray("timeSeries").objects()
            val conditions=series.firstOrNull {s -> s.getJSONArray("areas").objects().any {it.has("weatherCodes")} } ?: continue
            val area=conditions.getJSONArray("areas").objects().firstOrNull {it.getJSONObject("area").getString("code") in listOf(r.area,r.week)} ?: continue
            val dates=conditions.getJSONArray("timeDefines")
            val tempSeries=series.firstOrNull {s -> s.getJSONArray("areas").objects().any {it.getJSONObject("area").optString("code")==r.station && (it.has("tempsMin") || it.has("temps"))} }
            val temps=tempSeries?.getJSONArray("areas")?.objects()?.firstOrNull {it.getJSONObject("area").optString("code")==r.station}
            val pops=series.firstOrNull {s ->s.getJSONArray("areas").objects().any {it.has("pops")} }
            val popArea=pops?.getJSONArray("areas")?.objects()?.firstOrNull {it.getJSONObject("area").optString("code") in listOf(r.area,r.week)}
            for(i in 0 until dates.length()) {
                val time=timestamp(dates.getString(i));val date=localTime(time,"Asia/Tokyo","yyyy-MM-dd")
                fun temp(k: String): Double? { val index=tempSeries?.getJSONArray("timeDefines")?.let {a -> (0 until a.length()).firstOrNull {localTime(timestamp(a.getString(it)),"Asia/Tokyo","yyyy-MM-dd")==date} };return index?.let {temps?.optJSONArray(k)?.number(it)} }
                val shortTemps=tempSeries?.getJSONArray("timeDefines")?.let {a -> (0 until a.length()).filter {localTime(timestamp(a.getString(it)),"Asia/Tokyo","yyyy-MM-dd")==date}.mapNotNull {temps?.optJSONArray("temps")?.number(it)} }.orEmpty()
                val probability=pops?.getJSONArray("timeDefines")?.let {a -> (0 until a.length()).filter {localTime(timestamp(a.getString(it)),"Asia/Tokyo","yyyy-MM-dd")==date}.mapNotNull {popArea?.optJSONArray("pops")?.number(it)}.maxOrNull() }
                val existing=days[date]
                val jmaCode=area.getJSONArray("weatherCodes").optString(i).toIntOrNull()
                days[date]=DayForecast(time,temp("tempsMax") ?: shortTemps.maxOrNull() ?: existing?.high,temp("tempsMin") ?: shortTemps.minOrNull() ?: existing?.low,jmaCode?.let(::code) ?: existing?.code,probability=probability ?: existing?.probability,description=area.optJSONArray("weathers")?.optString(i).orEmpty())
            }
        }
        val h=Instant.ofEpochSecond(latest).atZone(ZoneId.of("Asia/Tokyo")).hour
        val station=stations.getJSONObject(r.station)
        return WeatherBundle(p,Provider.JMA_DIRECT,Reading(latest,v,days[localTime(latest,"Asia/Tokyo","yyyy-MM-dd")]?.code,h in 6..17),emptyList(),days.values.sortedBy {it.time},zone="Asia/Tokyo",notes=listOf("JMA AMeDAS · ${station.optString("kjName")} / ${station.optString("enName")}"))
    }
    fun code(value: Int): Int? = when(value/100) {1 -> if(value==100) 0 else 2;2 -> if(value==200) 3 else 2;3 -> 63;4 -> 73;else -> null}
}
object BrightSkyProvider {
    fun parse(j: JSONObject,p: Place,now: Long=Instant.now().epochSecond): WeatherBundle {
        val fields=mapOf("temperature" to "temperature_2m","relative_humidity" to "relative_humidity_2m","dew_point" to "dew_point_2m","pressure_msl" to "pressure_msl","wind_speed" to "wind_speed_10m","wind_gust_speed" to "wind_gusts_10m","wind_direction" to "wind_direction_10m","precipitation" to "precipitation","precipitation_probability" to "precipitation_probability","visibility" to "visibility","cloud_cover" to "cloud_cover")
        val all=j.getJSONArray("weather").objects().map { h ->
            val v=fields.mapNotNull { (source,target) -> h.number(source)?.let {target to it} }.toMap()
            val icon=h.optString("icon")
            val code=when {"thunder" in icon -> 95;"sleet" in icon -> ExtraWeatherCode.SLEET;"snow" in icon -> 73;"rain" in icon -> 63;"fog" in icon -> 45;"wind" in icon -> ExtraWeatherCode.WIND;"partly" in icon -> 2;"cloud" in icon -> 3;"clear" in icon -> 0;else -> null}
            Reading(timestamp(h.getString("timestamp")),v,code,!icon.endsWith("night"))
        }
        val hours=all.filter {it.time>=now-1800}
        require(hours.isNotEmpty()) {"No forecast data"}
        val current=all.minBy {kotlin.math.abs(it.time-now)}
        return WeatherBundle(p,Provider.BRIGHTSKY,current,hours,aggregateDays(all,p.zone),zone=p.zone,notes=j.optJSONArray("sources")?.objects()?.mapNotNull {it.optString("station_name").takeIf {n ->n.isNotBlank()}}?.distinct()?.take(3)?.map {"DWD · $it"}.orEmpty())
    }
}
