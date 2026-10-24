package com.lopleec.slopeweather.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

object RegionalProviders {
    fun met(j: JSONObject,p: Place,now: Long=java.time.Instant.now().epochSecond): WeatherBundle {
        val hours=j.getJSONObject("properties").getJSONArray("timeseries").objects().map { row ->
            val d=row.getJSONObject("data")
            val instant=d.getJSONObject("instant").getJSONObject("details")
            val next=d.optJSONObject("next_1_hours") ?: d.optJSONObject("next_6_hours") ?: d.optJSONObject("next_12_hours")
            val mapped=mapOf("air_temperature" to "temperature_2m","relative_humidity" to "relative_humidity_2m","air_pressure_at_sea_level" to "pressure_msl","wind_speed" to "wind_speed_10m","wind_from_direction" to "wind_direction_10m","wind_speed_of_gust" to "wind_gusts_10m","cloud_area_fraction" to "cloud_cover","dew_point_temperature" to "dew_point_2m","ultraviolet_index_clear_sky" to "uv_index_clear_sky")
            val values=mapped.mapNotNull { (source,target) -> instant.number(source)?.let { target to if(source=="wind_speed" || source=="wind_speed_of_gust") it*3.6 else it } }.toMap().toMutableMap()
            // A 6h accumulation must never be presented as a 1h accumulation.
            d.optJSONObject("next_1_hours")?.optJSONObject("details")?.number("precipitation_amount")?.let { values["precipitation"]=it }
            next?.optJSONObject("details")?.number("probability_of_precipitation")?.let { values["precipitation_probability"]=it }
            val symbol=next?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
            Reading(timestamp(row.getString("time")),derivedWeatherValues(values),metCode(symbol),!symbol.endsWith("night"))
        }.filter { it.time >= now-3600 }
        require(hours.isNotEmpty()) { "No forecast data" }
        return WeatherBundle(p,Provider.MET,hours.first(),hours,aggregateDays(hours,p.zone),zone=p.zone)
    }
    fun metCode(symbol: String): Int? = when {
        "thunder" in symbol && "sleet" in symbol -> ExtraWeatherCode.THUNDER_SLEET
        "thunder" in symbol && "snow" in symbol -> ExtraWeatherCode.THUNDER_SNOW
        "thunder" in symbol -> 95
        "sleet" in symbol -> ExtraWeatherCode.SLEET
        "heavysnow" in symbol -> 75
        "lightsnow" in symbol -> 71
        "snow" in symbol -> 73
        "heavyrain" in symbol -> 65
        "rain" in symbol -> 61
        "fog" in symbol -> 45
        "partlycloudy" in symbol -> 2
        "fair" in symbol -> 1
        "cloudy" in symbol -> 3
        "clearsky" in symbol -> 0
        else -> null
    }
    fun hko(current: JSONObject,forecast: JSONObject,p: Place): WeatherBundle {
        val temperatures=current.getJSONObject("temperature").getJSONArray("data").objects()
        val station=temperatures.firstOrNull { it.optString("place") in setOf("Hong Kong Observatory","香港天文台") } ?: temperatures.first()
        val v=mutableMapOf<String,Double>()
        station.number("value")?.let { v["temperature_2m"]=it }
        current.optJSONObject("humidity")?.optJSONArray("data")?.optJSONObject(0)?.number("value")?.let { v["relative_humidity_2m"]=it }
        current.optJSONObject("uvindex")?.optJSONArray("data")?.optJSONObject(0)?.number("value")?.let { v["uv_index"]=it }
        val days=forecast.getJSONArray("weatherForecast").objects().map { d ->
            val date=LocalDate.parse(d.getString("forecastDate"),java.time.format.DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(ZoneId.of("Asia/Hong_Kong")).toEpochSecond()
            DayForecast(date,d.optJSONObject("forecastMaxtemp")?.number("value"),d.optJSONObject("forecastMintemp")?.number("value"),hkoCode(d.optInt("ForecastIcon")),description=listOf(d.optString("forecastWeather"),d.optString("forecastWind")).filter { it.isNotBlank() }.joinToString("\n"))
        }
        return WeatherBundle(p,Provider.HKO,Reading(timestamp(current.getString("updateTime")),derivedWeatherValues(v),hkoCode(current.getJSONArray("icon").optInt(0)),java.time.Instant.ofEpochSecond(timestamp(current.getString("updateTime"))).atZone(ZoneId.of("Asia/Hong_Kong")).hour in 6..17),emptyList(),days,zone="Asia/Hong_Kong",overview=forecast.optString("generalSituation"))
    }
    fun hkoCode(code: Int): Int? = when(code) {
        50,70,71,72,73,74,75->0;77->1;51,52,76->2;53,54->80
        60,61->3;62->61;63->63;64->65;65->95;83,84->45
        85->ExtraWeatherCode.HAZE;80->ExtraWeatherCode.WIND
        81->ExtraWeatherCode.DRY;82->ExtraWeatherCode.HUMID
        90->ExtraWeatherCode.HOT;91->ExtraWeatherCode.WARM
        92->ExtraWeatherCode.COOL;93->ExtraWeatherCode.COLD;else->null
    }
    fun nwsHourly(j: JSONObject): List<Reading> = j.getJSONObject("properties").getJSONArray("periods").objects().map { h ->
        val v=mutableMapOf<String,Double>()
        h.number("temperature")?.let { v["temperature_2m"]=if(h.optString("temperatureUnit")=="F") (it-32)*5/9 else it }
        h.optJSONObject("relativeHumidity")?.number("value")?.let { v["relative_humidity_2m"]=it }
        h.optJSONObject("dewpoint")?.number("value")?.let { v["dew_point_2m"]=it }
        h.optJSONObject("probabilityOfPrecipitation")?.number("value")?.let { v["precipitation_probability"]=it }
        val wind=Regex("[0-9.]+").findAll(h.optString("windSpeed")).mapNotNull { it.value.toDoubleOrNull() }.maxOrNull()
        wind?.let { v["wind_speed_10m"]=it * if("mph" in h.optString("windSpeed")) 1.609344 else 1.0 }
        val directions=listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        directions.indexOf(h.optString("windDirection")).takeIf { it>=0 }?.let { v["wind_direction_10m"]=it*22.5 }
        Reading(timestamp(h.getString("startTime")),v,nwsCode(h.optString("shortForecast")),h.optBoolean("isDaytime",true))
    }
    fun nwsGrid(j: JSONObject,hours: List<Reading>): List<Reading> {
        val properties=j.getJSONObject("properties")
        val mapping=mapOf("apparentTemperature" to "apparent_temperature","relativeHumidity" to "relative_humidity_2m","dewpoint" to "dew_point_2m","skyCover" to "cloud_cover","visibility" to "visibility","windGust" to "wind_gusts_10m","quantitativePrecipitation" to "precipitation")
        return hours.map {h->
            val extra=buildMap<String,Double> {
                mapping.forEach {(field,key)->
                    val series=properties.optJSONObject(field) ?: return@forEach
                    val row=series.optJSONArray("values")?.objects()?.firstOrNull {r->
                        val interval=r.optString("validTime").split('/')
                        if(interval.size!=2) false else runCatching {val start=timestamp(interval[0]);val duration=java.time.Duration.parse(interval[1]).seconds;h.time>=start && h.time<start+duration && (field!="quantitativePrecipitation" || duration==3600L)}.getOrDefault(false)
                    }
                    row?.number("value")?.let {raw->val unit=series.optString("uom");put(key,when {unit.endsWith(":km_h-1")->raw;unit.endsWith(":m_s-1")->raw*3.6;unit.endsWith(":degF")->(raw-32)*5/9;unit.endsWith(":km") && key=="visibility"->raw*1000;else->raw})}
                }
            }
            h.copy(values=derivedWeatherValues(extra+h.values))
        }
    }
    fun nwsCode(description: String): Int? { val d=description.lowercase(); return when {
        "thunder" in d->when {"hail" in d->96;"sleet" in d || ("snow" in d && "rain" in d)->ExtraWeatherCode.THUNDER_SLEET;"snow" in d->ExtraWeatherCode.THUNDER_SNOW;else->95}
        "freezing" in d && ("rain" in d || "drizzle" in d)->67
        "sleet" in d || "wintry" in d || ("rain" in d && "snow" in d)->ExtraWeatherCode.SLEET
        "hail" in d || "ice pellet" in d->ExtraWeatherCode.HAIL
        "snow" in d || "flurr" in d->73;"drizzle" in d->51
        "rain" in d || "shower" in d->63;"fog" in d || "mist" in d->45
        "dust" in d || "sand" in d->ExtraWeatherCode.DUST
        "smoke" in d->ExtraWeatherCode.SMOKE;"haze" in d->ExtraWeatherCode.HAZE
        "partly" in d->2;"mostly sunny" in d || "mostly clear" in d->1
        "cloud" in d || "overcast" in d->3;"sun" in d || "clear" in d->0
        "wind" in d || "breez" in d->ExtraWeatherCode.WIND;else->null
    } }
    fun nwsAlerts(j: JSONObject) = j.optJSONArray("features")?.objects().orEmpty().map { f ->
        val a=f.getJSONObject("properties")
        WeatherAlert(f.optString("id"),a.optString("headline").ifBlank { a.optString("event") },listOf(a.optString("description"),a.optString("instruction")).filter { it.isNotBlank() && it!="null" }.joinToString("\n\n"),"NWS · weather.gov",a.optString("expires").takeIf { it.isNotBlank() && it!="null" }?.let { runCatching { timestamp(it) }.getOrNull() },url=f.optString("id").takeIf {it.startsWith("https://")}.orEmpty(),issued=a.optString("sent").takeIf {it.isNotBlank() && it!="null"}?.let {runCatching {timestamp(it)}.getOrNull()},severity=a.optString("severity"))
    }
}
