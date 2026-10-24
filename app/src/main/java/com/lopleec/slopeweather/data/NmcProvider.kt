package com.lopleec.slopeweather.data

import org.json.JSONObject
import java.net.URLEncoder
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

object NmcProvider {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    fun time(s: String)=LocalDateTime.parse(s,formatter).atZone(zone).toEpochSecond()
    fun value(j: JSONObject,key: String)=j.number(key)?.takeIf { abs(it)<9990 }
    suspend fun search(http: WeatherHttp,query: String): List<Place> {
        val j=http.json("https://www.nmc.cn/essearch/api/autocomplete?q=${URLEncoder.encode(query,"UTF-8")}",86_400_000)
        val a=j.optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val row=a.optString(i).split('|')
            if(row.size<6) null else { val lat=row[5].toDoubleOrNull();val lon=row[4].toDoubleOrNull(); if(lat==null || lon==null) null else Place("${row[1]}, ${row[2]}",lat,lon,"CN","Asia/Shanghai",row[0]) }
        }
    }
    suspend fun load(http: WeatherHttp,p: Place,withForecast: Boolean=true): WeatherBundle {
        val station=if(p.nmcCode.isNotBlank()) p else {
            val queries=(p.names.values+ p.name).joinToString(" · ").substringBefore(" / ").split(" · ",",").map {it.trim()}.filter {it.isNotBlank()}.distinct().take(3)
            var nearest: Place?=null
            for(query in queries) {
                val matches=search(http,query).ifEmpty {search(http,query.removeSuffix("区").removeSuffix("县"))}
                nearest=matches.minByOrNull {distance(p.latitude,p.longitude,it.latitude,it.longitude)}?.takeIf {distance(p.latitude,p.longitude,it.latitude,it.longitude)<80}
                if(nearest!=null) break
            }
            nearest ?: error("NMC_STATION_REQUIRED")
        }
        val response=http.json("https://www.nmc.cn/rest/weather?stationid=${URLEncoder.encode(station.nmcCode,"UTF-8")}")
        val basic=parse(response,p)
        val path=response.optJSONObject("data")?.optJSONObject("real")?.optJSONObject("station")?.optString("url").orEmpty()
        val hours=if(withForecast && path.startsWith("/publish/forecast/") && !path.contains("..")) try {parseForecastPage(http.text("https://www.nmc.cn$path"),basic.daily.firstOrNull()?.time ?: basic.current.time)} catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) {emptyList()} else emptyList()
        return basic.copy(hourly=hours,notes=basic.notes+if(hours.isNotEmpty()) listOf("NMC · 3 h forecast") else emptyList())
    }
    /** The public page includes native 3-hour forecast rows omitted by /rest/weather. No interpolation. */
    fun parseForecastPage(html: String,baseTime: Long): List<Reading> {
        val doc=org.jsoup.Jsoup.parse(html)
        val base=Instant.ofEpochSecond(baseTime).atZone(zone).toLocalDate()
        val dates=doc.select("#day7 > .weather .date").mapNotNull {el->Regex("(\\d{1,2})/(\\d{1,2})").find(el.text())?.let {m->runCatching {LocalDate.of(base.year,m.groupValues[1].toInt(),m.groupValues[2].toInt())}.getOrNull()?.let {if(it.isBefore(base.minusMonths(6))) it.plusYears(1) else it}}}
        val result=mutableListOf<Reading>()
        doc.select("#hourValues > div > div[id^=day]").forEachIndexed {index,group->
            var date=dates.getOrNull(index) ?: base.plusDays(index.toLong())
            var previousHour=-1
            group.select(".hour3").forEach {row->
                val cells=row.children();if(cells.size<9) return@forEach
                val label=cells[0].text();val clock=Regex("(\\d{1,2}):(\\d{2})").find(label) ?: return@forEach
                val hour=clock.groupValues[1].toInt();val minute=clock.groupValues[2].toInt()
                Regex("(\\d{1,2})日").find(label)?.let {day->
                    val candidate=runCatching {date.withDayOfMonth(day.groupValues[1].toInt())}.getOrNull()
                    if(candidate!=null) date=if(candidate.isBefore(date.minusDays(15))) candidate.plusMonths(1) else candidate
                } ?: run {if(previousHour>=0 && hour<previousHour) date=date.plusDays(1)}
                previousHour=hour
                fun n(i: Int)=Regex("-?\\d+(?:\\.\\d+)?").find(cells[i].text())?.value?.toDoubleOrNull()?.takeIf {abs(it)<9990}
                val values=buildMap {
                    n(2)?.let {put("precipitation",it)};n(3)?.let {put("temperature_2m",it)};n(4)?.let {put("wind_speed_10m",it*3.6)}
                    direction(cells[5].text())?.let {put("wind_direction_10m",it)};n(6)?.let {put("surface_pressure",it)}
                    n(7)?.let {put("relative_humidity_2m",it)};n(8)?.let {put("cloud_cover",it)}
                }
                val code=Regex("/(\\d+)\\.png").find(row.select("img").attr("src"))?.groupValues?.get(1)?.toIntOrNull()?.let(::code)
                if(values["temperature_2m"]!=null) result.add(Reading(date.atTime(hour,minute).atZone(zone).toEpochSecond(),derivedWeatherValues(values),code,hour in 6..17))
            }
        }
        return result.distinctBy {it.time}.sortedBy {it.time}
    }
    fun direction(text: String): Double? = listOf("北","东北","东","东南","南","西南","西","西北").indexOf(text.removeSuffix("风")).takeIf {it>=0}?.times(45.0)
    fun parse(j: JSONObject,p: Place): WeatherBundle {
        val data=j.getJSONObject("data");val real=data.getJSONObject("real");val weather=real.getJSONObject("weather");val wind=real.getJSONObject("wind")
        val values=mutableMapOf<String,Double>()
        mapOf("temperature" to "temperature_2m","humidity" to "relative_humidity_2m","feelst" to "apparent_temperature","airpressure" to "surface_pressure","rain" to "precipitation").forEach { (source,target) -> value(weather,source)?.let { values[target]=it } }
        value(wind,"speed")?.let {values["wind_speed_10m"]=it*3.6};value(wind,"degree")?.takeIf {it in 0.0..360.0}?.let {values["wind_direction_10m"]=it}
        data.optJSONObject("air")?.let {value(it,"aqi")}?.let {values["cn_aqi"]=it}
        require(values["temperature_2m"]!=null) {"No observation data"}
        val sun=real.optJSONObject("sunriseSunset")
        val days=data.getJSONObject("predict").getJSONArray("detail").objects().map { d ->
            val day=d.getJSONObject("day").getJSONObject("weather");val night=d.getJSONObject("night").getJSONObject("weather")
            val date=LocalDate.parse(d.getString("date")).atStartOfDay(zone).toEpochSecond()
            val today=localTime(date,"Asia/Shanghai","yyyy-MM-dd")==real.getString("publish_time").take(10)
            val code=value(day,"img") ?: value(night,"img")
            val chart=data.optJSONArray("tempchart")?.objects()?.firstOrNull {it.optString("time").replace('/','-')==d.getString("date")}
            DayForecast(date,value(day,"temperature") ?: chart?.let {value(it,"max_temp")},value(night,"temperature") ?: chart?.let {value(it,"min_temp")},code?.toInt()?.let(::code),value(d,"precipitation"),sunrise=if(today) sun?.optString("sunrise")?.takeIf {it.isNotBlank()}?.let(::time) else null,sunset=if(today) sun?.optString("sunset")?.takeIf {it.isNotBlank()}?.let(::time) else null,description=listOf(day.optString("info"),night.optString("info")).filter {it!="9999" && it.isNotBlank()}.distinct().joinToString(" / "))
        }
        val warn=real.optJSONObject("warn")
        val alerts=if(warn!=null && warn.optString("alert") !in listOf("","9999")) listOf(WeatherAlert(warn.optString("url"),warn.optString("alert"),warn.optString("issuecontent"),"中央气象台 NMC",issued=warn.optString("issuetime").takeIf {it.isNotBlank() && it!="9999"}?.let {runCatching {time(it)}.getOrNull()},severity=when(warn.optString("signallevel").lowercase()) {"红","红色","red"->"Extreme";"橙","橙色","orange"->"Severe";"黄","黄色","yellow"->"Moderate";"蓝","蓝色","blue"->"Minor";else->""})) else emptyList()
        val t=time(real.getString("publish_time"));val hour=Instant.ofEpochSecond(t).atZone(zone).hour
        val history=data.optJSONArray("passedchart")?.objects().orEmpty().mapNotNull {r->
            val at=runCatching {time(r.getString("time"))}.getOrNull() ?: return@mapNotNull null
            val v=buildMap {mapOf("temperature" to "temperature_2m","humidity" to "relative_humidity_2m","pressure" to "surface_pressure","rain1h" to "precipitation","windDirection" to "wind_direction_10m").forEach {(a,b)->value(r,a)?.let {put(b,it)}};value(r,"windSpeed")?.let {put("wind_speed_10m",it*3.6)}}
            Reading(at,derivedWeatherValues(v),day=Instant.ofEpochSecond(at).atZone(zone).hour in 6..17)
        }.sortedBy {it.time}
        return WeatherBundle(p,Provider.NMC,Reading(t,derivedWeatherValues(values),value(weather,"img")?.toInt()?.let(::code),hour in 6..17),emptyList(),days,alerts=alerts,zone="Asia/Shanghai",notes=listOf(real.getJSONObject("station").let {"NMC · ${it.optString("province")} ${it.optString("city")}"}),alertsChecked=true,radarImage=data.optJSONObject("radar")?.optString("image")?.takeIf {it.startsWith("/product/")}?.let {"https://image.nmc.cn$it"},history=history)
    }
    fun code(code: Int): Int? = when(code) { 0 -> 0;1 -> 2;2 -> 3;3 -> 80;4 -> 95;5 -> 96;6 -> ExtraWeatherCode.SLEET;13 -> 85;7,21 -> 61;8,22 -> 63;9,10,11,12,23,24,25 -> 65;14 -> 71;15,26 -> 73;16,17,27,28 -> 75;18 -> 45;20,29,30,31 -> ExtraWeatherCode.DUST;53 -> ExtraWeatherCode.HAZE;19 -> 67;else -> null }
}
fun distance(lat1: Double,lon1: Double,lat2: Double,lon2: Double): Double {
    val a=sin(Math.toRadians(lat2-lat1)/2).pow(2)+cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(Math.toRadians(lon2-lon1)/2).pow(2)
    return 6371*2*atan2(sqrt(a),sqrt((1-a).coerceAtLeast(0.0)))
}
