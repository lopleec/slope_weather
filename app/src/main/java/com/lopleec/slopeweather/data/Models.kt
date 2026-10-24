package com.lopleec.slopeweather.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class Place(val name: String, val latitude: Double, val longitude: Double, val country: String = "", val zone: String = "Asia/Shanghai", val nmcCode: String = "",val names: Map<String,String> = emptyMap(),val nameLanguage: String = "",val nameVersion: Int = 4) {
    val id: String get() = String.format(Locale.US, "%.4f,%.4f", latitude, longitude)
    fun json() = JSONObject().put("name", name).put("lat", latitude).put("lon", longitude).put("country", country).put("zone", zone).put("nmcCode", nmcCode).put("names",JSONObject(names)).put("nameLanguage",nameLanguage).put("nameVersion",nameVersion)
    companion object {
        fun from(j: JSONObject) = Place(j.getString("name"), j.getDouble("lat"), j.getDouble("lon"), j.optString("country"), j.optString("zone", "UTC"),j.optString("nmcCode"),j.optJSONObject("names")?.let {n->n.keys().asSequence().associateWith {n.getString(it)}}.orEmpty(),j.optString("nameLanguage"),j.optInt("nameVersion",1))
    }
}

enum class Provider(val title: String, val region: String, val endpoint: String? = null, val days: Int = 7) {
    AUTO("Open-Meteo · Best match", "🌐", "forecast", 14),
    NMC("中央气象台 · NMC", "🇨🇳"),
    CMA("CMA · GRAPES", "🇨🇳", "cma", 7),
    JMA("JMA · GSM / MSM", "🇯🇵", "jma", 7),
    DWD("DWD · ICON", "🇩🇪", "dwd-icon", 7),
    ECMWF("ECMWF · IFS", "🇪🇺", "ecmwf", 10),
    GFS("NOAA · GFS", "🇺🇸", "gfs", 10),
    FRANCE("Météo-France · ARPEGE / AROME", "🇫🇷", "meteofrance", 4),
    UKMO("UK Met Office · UKMO", "🇬🇧", "forecast", 7),
    KNMI("KNMI · HARMONIE", "🇳🇱", "forecast", 2),
    BRIGHTSKY("Bright Sky · DWD stations", "🇩🇪"),
    JMA_DIRECT("気象庁 · AMeDAS + Forecast", "🇯🇵"),
    MET("MET Norway · Locationforecast", "🇳🇴"),
    HKO("香港天文台 · HKO", "🇭🇰"),
    NWS("NWS · weather.gov", "🇺🇸");
    val viaOpenMeteo get() = endpoint != null
    fun supports(p: Place) = when (this) {
        NMC -> p.country == "CN" || p.nmcCode.isNotBlank() || (p.latitude in 18.0..54.0 && p.longitude in 73.0..135.0 && p.country.isBlank())
        JMA_DIRECT -> p.country == "JP" || (p.latitude in 24.0..46.0 && p.longitude in 123.0..146.0 && p.country.isBlank())
        BRIGHTSKY -> p.country == "DE" || (p.country.isBlank() && p.latitude in 47.0..55.2 && p.longitude in 5.5..15.5)
        KNMI -> p.latitude in 48.0..56.0 && p.longitude in -3.0..11.0
        HKO -> p.latitude in 22.10..22.60 && p.longitude in 113.80..114.50
        NWS -> p.country == "US" || (p.country.isBlank() && p.latitude in 18.0..72.0 && p.longitude in -180.0..-66.0)
        else -> true
    }
}

// All values are stored in SI / °C / km/h. Missing data remains null throughout the pipeline.
data class Reading(val time: Long, val values: Map<String,Double>, val code: Int? = null, val day: Boolean = true) {
    operator fun get(key: String): Double? = values[key]
    fun json() = JSONObject().put("time",time).put("values",JSONObject(values)).put("code",code).put("day",day)
    companion object {
        fun from(j: JSONObject): Reading {
            val v = j.getJSONObject("values")
            return Reading(j.getLong("time"),v.keys().asSequence().associateWith { v.getDouble(it) },j.number("code")?.toInt(),j.optBoolean("day",true))
        }
    }
}
data class DayForecast(val time: Long, val high: Double?, val low: Double?, val code: Int?, val rain: Double? = null, val probability: Double? = null, val sunrise: Long? = null, val sunset: Long? = null, val uv: Double? = null, val wind: Double? = null, val description: String = "") {
    fun json() = JSONObject().put("time",time).put("high",high).put("low",low).put("code",code).put("rain",rain).put("prob",probability).put("rise",sunrise).put("set",sunset).put("uv",uv).put("wind",wind).put("description",description)
    companion object { fun from(j: JSONObject) = DayForecast(j.getLong("time"),j.number("high"),j.number("low"),j.number("code")?.toInt(),j.number("rain"),j.number("prob"),j.number("rise")?.toLong(),j.number("set")?.toLong(),j.number("uv"),j.number("wind"),j.optString("description")) }
}
data class WeatherAlert(val id: String, val title: String, val text: String, val source: String, val expires: Long? = null,val url: String="",val issued: Long?=null,val severity: String="") {
    val critical get()=severity.equals("severe",true) || severity.equals("extreme",true)
    fun json() = JSONObject().put("id",id).put("title",title).put("text",text).put("source",source).put("expires",expires).put("url",url).put("issued",issued).put("severity",severity)
    companion object { fun from(j: JSONObject) = WeatherAlert(j.getString("id"),j.getString("title"),j.getString("text"),j.getString("source"),j.number("expires")?.toLong(),j.optString("url"),j.number("issued")?.toLong(),j.optString("severity")) }
}
data class WeatherBundle(val place: Place, val provider: Provider, val current: Reading, val hourly: List<Reading>, val daily: List<DayForecast>, val minutely: List<Reading> = emptyList(), val air: Reading? = null, val alerts: List<WeatherAlert> = emptyList(), val fetched: Long = Instant.now().epochSecond, val zone: String = place.zone, val notes: List<String> = emptyList(), val overview: String = "", val alertsChecked: Boolean = false, val radarImage: String? = null, val history: List<Reading> = emptyList(),val airHourly: List<Reading> = emptyList(),val supplemented: Set<String> = emptySet(),val alertStatus: String = "unknown",val alertsCheckedAt: Long? = null,val supplementTime: Long? = null) {
    fun json() = JSONObject().put("place",place.json()).put("provider",provider.name).put("current",current.json()).put("hourly",JSONArray(hourly.map { it.json() })).put("daily",JSONArray(daily.map { it.json() })).put("minutely",JSONArray(minutely.map { it.json() })).put("air",air?.json()).put("alerts",JSONArray(alerts.map { it.json() })).put("fetched",fetched).put("zone",zone).put("notes",JSONArray(notes)).put("overview",overview).put("alertsChecked",alertsChecked).put("radarImage",radarImage).put("history",JSONArray(history.map {it.json()})).put("airHourly",JSONArray(airHourly.map {it.json()})).put("supplemented",JSONArray(supplemented.toList())).put("alertStatus",alertStatus).put("alertsCheckedAt",alertsCheckedAt).put("supplementTime",supplementTime)
    companion object {
        fun from(j: JSONObject) = WeatherBundle(Place.from(j.getJSONObject("place")),Provider.valueOf(j.getString("provider")),Reading.from(j.getJSONObject("current")), j.getJSONArray("hourly").objects().map(Reading::from),j.getJSONArray("daily").objects().map(DayForecast::from),j.getJSONArray("minutely").objects().map(Reading::from),j.optJSONObject("air")?.let(Reading::from),j.getJSONArray("alerts").objects().map(WeatherAlert::from),j.getLong("fetched"),j.getString("zone"),j.getJSONArray("notes").let { a -> (0 until a.length()).map(a::getString) }, j.optString("overview"),j.optBoolean("alertsChecked"),j.optString("radarImage").takeIf {it.isNotBlank() && it!="null"},j.optJSONArray("history")?.objects()?.map(Reading::from).orEmpty(),j.optJSONArray("airHourly")?.objects()?.map(Reading::from).orEmpty(),j.optJSONArray("supplemented")?.let {a->(0 until a.length()).map {a.getString(it)}.toSet()}.orEmpty(),j.optString("alertStatus","unknown"),j.number("alertsCheckedAt")?.toLong(),j.number("supplementTime")?.toLong())
    }
}
fun JSONObject.number(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null
fun JSONArray.number(index: Int): Double? = if (index in 0 until length() && !isNull(index)) optDouble(index).takeIf { it.isFinite() } else null
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun timestamp(value: String): Long = runCatching { Instant.parse(value).epochSecond }.getOrElse { java.time.OffsetDateTime.parse(value).toEpochSecond() }
fun localTime(time: Long, zone: String, pattern: String = "HH:mm", locale: Locale = Locale.getDefault()) = DateTimeFormatter.ofPattern(pattern,locale).withZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))).format(Instant.ofEpochSecond(time))
fun temperature(value: Double?, fahrenheit: Boolean = false) = value?.let { "${(if (fahrenheit) it*9/5+32 else it).roundToInt()}°" } ?: "—"
/** An hourly accumulation covers the local day, including 23/25-hour DST days, or stays missing. */
fun dailyAccumulation(hours: List<Reading>,key: String,zone: String): Double? {
    if(hours.isEmpty()) return null
    val times=hours.map {it.time}.sorted()
    val timezone=runCatching {ZoneId.of(zone)}.getOrDefault(ZoneId.of("UTC"))
    val date=Instant.ofEpochSecond(times.first()).atZone(timezone).toLocalDate()
    val start=date.atStartOfDay(timezone).toEpochSecond()
    val end=date.plusDays(1).atStartOfDay(timezone).toEpochSecond()
    if((end-start)%3600!=0L || times.size.toLong()!=(end-start)/3600 ||
        times.withIndex().any {(index,time)->time!=start+index*3600L}) return null
    return hours.sumOf {it[key]?.takeIf {value->value.isFinite()} ?: return null}.takeIf {it.isFinite()}
}
fun aggregateDays(hourly: List<Reading>, zone: String): List<DayForecast> = hourly.groupBy { localTime(it.time,zone,"yyyy-MM-dd") }.values.map { hours ->
    DayForecast(hours.first().time,hours.mapNotNull { it["temperature_2m"] }.maxOrNull(),hours.mapNotNull { it["temperature_2m"] }.minOrNull(),hours.mapNotNull { it.code }.maxWithOrNull(compareBy<Int> {weatherSeverity(it)}.thenBy {it}),dailyAccumulation(hours,"precipitation",zone),hours.mapNotNull { it["precipitation_probability"] }.maxOrNull(),wind=hours.mapNotNull { it["wind_speed_10m"] }.maxOrNull())
}
