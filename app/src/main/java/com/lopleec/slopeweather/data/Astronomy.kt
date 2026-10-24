package com.lopleec.slopeweather.data

import org.shredzone.commons.suncalc.*
import java.time.*
import kotlin.math.ln

fun derivedWeatherValues(values: Map<String,Double>): Map<String,Double> {
    if(values["dew_point_2m"]!=null) return values
    val t=values["temperature_2m"] ?: return values
    val rh=values["relative_humidity_2m"]?.takeIf {it>0 && it<=100} ?: return values
    val gamma=ln(rh/100)+17.625*t/(243.04+t)
    return values+("dew_point_2m" to 243.04*gamma/(17.625-gamma))
}
data class Astronomy(val fraction: Double,val phase: Double,val rise: Long?,val set: Long?,val dawn: Long?,val dusk: Long?,val sunRise: Long?,val sunSet: Long?)
fun astronomy(p: Place,time: Long): Astronomy {
    val at=Instant.ofEpochSecond(time).atZone(ZoneId.of(p.zone))
    val start=at.toLocalDate().atStartOfDay(at.zone)
    val moon=MoonIllumination.compute().on(at).at(p.latitude,p.longitude).execute()
    val times=MoonTimes.compute().on(start).at(p.latitude,p.longitude).oneDay().execute()
    val civil=SunTimes.compute().on(start).at(p.latitude,p.longitude).twilight(SunTimes.Twilight.CIVIL).oneDay().execute()
    val sun=SunTimes.compute().on(start).at(p.latitude,p.longitude).oneDay().execute()
    return Astronomy(moon.fraction,(moon.phase+180)/360,times.rise?.toEpochSecond(),times.set?.toEpochSecond(),civil.rise?.toEpochSecond(),civil.set?.toEpochSecond(),sun.rise?.toEpochSecond(),sun.set?.toEpochSecond())
}
