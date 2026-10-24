package com.lopleec.slopeweather.data

import org.json.JSONObject

object OpenMeteo {
    val hourlyFields = listOf("temperature_2m","relative_humidity_2m","dew_point_2m","apparent_temperature","precipitation_probability","precipitation","rain","snowfall","weather_code","pressure_msl","surface_pressure","cloud_cover","visibility","wind_speed_10m","wind_direction_10m","wind_gusts_10m","uv_index","is_day","shortwave_radiation","cape")
    val basicFields = hourlyFields.filterNot { it in listOf("precipitation_probability","uv_index","shortwave_radiation","cape") }
    fun url(place: Place, provider: Provider,extended: Boolean=true): String {
        val fields = if (extended || provider==Provider.AUTO) hourlyFields else basicFields
        val daily="weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,wind_speed_10m_max" + if(extended || provider==Provider.AUTO) ",uv_index_max,precipitation_probability_max" else ""
        return "https://api.open-meteo.com/v1/${provider.endpoint}?latitude=${place.latitude}&longitude=${place.longitude}&hourly=${fields.joinToString(",")}&daily=$daily&timezone=auto&timeformat=unixtime&forecast_days=${provider.days}" + when(provider) { Provider.UKMO -> "&models=ukmo_seamless"; Provider.KNMI -> "&models=knmi_seamless"; else -> "" } + if(provider==Provider.AUTO) "&current=${fields.joinToString(",")}&minutely_15=precipitation&forecast_minutely_15=24" else ""
    }
    fun parse(j: JSONObject, place: Place, provider: Provider, now: Long = java.time.Instant.now().epochSecond): WeatherBundle {
        if(j.optBoolean("error")) error(j.optString("reason"))
        val zone = j.optString("timezone", place.zone)
        val hourly = series(j.getJSONObject("hourly"))
        val upcoming=hourly.filter { it.time >= now-1800 }
        val current=j.optJSONObject("current")?.let { c -> Reading(c.getLong("time"),hourlyFields.mapNotNull { key -> c.number(key)?.let { key to it } }.toMap(),c.number("weather_code")?.toInt(),c.optInt("is_day",1)==1) } ?: hourly.minByOrNull { kotlin.math.abs(it.time-now) } ?: error("No forecast data")
        require(current["temperature_2m"]!=null && kotlin.math.abs(current.time-now) < 10800) { "Forecast is unavailable or outdated" }
        val daily=j.getJSONObject("daily")
        val days=(0 until daily.getJSONArray("time").length()).map { i ->
            fun n(k: String)=daily.optJSONArray(k)?.number(i)
            DayForecast(daily.getJSONArray("time").getLong(i),n("temperature_2m_max"),n("temperature_2m_min"),n("weather_code")?.toInt(),n("precipitation_sum"),n("precipitation_probability_max"),n("sunrise")?.toLong(),n("sunset")?.toLong(),n("uv_index_max"),n("wind_speed_10m_max"))
        }
        return WeatherBundle(place,provider,current,upcoming,days,j.optJSONObject("minutely_15")?.let(::series).orEmpty(),zone=zone)
    }
    fun series(j: JSONObject): List<Reading> {
        val times=j.optJSONArray("time") ?: return emptyList()
        return (0 until times.length()).map { i ->
            val values=j.keys().asSequence().filter { it!="time" }.mapNotNull { key -> j.optJSONArray(key)?.number(i)?.let { key to it } }.toMap()
            Reading(times.getLong(i),values,values["weather_code"]?.toInt(),values["is_day"]!=0.0)
        }
    }
}
