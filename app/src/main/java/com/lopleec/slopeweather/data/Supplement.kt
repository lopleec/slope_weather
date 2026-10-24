package com.lopleec.slopeweather.data

/** Native observations and forecast cadence win. Supplementary model values only fill missing fields. */
fun supplementWeather(native: WeatherBundle,model: WeatherBundle): WeatherBundle {
    val additions=if(kotlin.math.abs(model.current.time-native.current.time)<=3600) model.current.values.filterKeys {it !in native.current.values} else emptyMap()
    val hours=if(native.hourly.isEmpty()) model.hourly else native.hourly.map {n->
        val m=model.hourly.minByOrNull {kotlin.math.abs(it.time-n.time)}?.takeIf {kotlin.math.abs(it.time-n.time)<=1800}
        if(m==null) n else n.copy(values=m.values+n.values)
    }
    val days=native.daily.map {n->
        val m=model.daily.firstOrNull {localTime(it.time,model.zone,"yyyy-MM-dd")==localTime(n.time,native.zone,"yyyy-MM-dd")}
        n.copy(rain=n.rain ?: m?.rain,probability=n.probability ?: m?.probability,uv=n.uv ?: m?.uv,wind=n.wind ?: m?.wind,sunrise=n.sunrise ?: m?.sunrise,sunset=n.sunset ?: m?.sunset)
    }
    return native.copy(current=native.current.copy(values=additions+native.current.values),hourly=hours,daily=days,minutely=native.minutely.ifEmpty {model.minutely},notes=native.notes+"Supplement: Open-Meteo",supplemented=additions.keys,supplementTime=model.current.time)
}
