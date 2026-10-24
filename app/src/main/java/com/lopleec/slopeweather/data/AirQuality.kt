package com.lopleec.slopeweather.data

enum class AqiStandard(val key: String) { US("us_aqi"),EU("european_aqi"),CN("cn_aqi") }
fun selectedAqi(w: WeatherBundle,standard: AqiStandard): Double? = w.current[standard.key] ?: w.air?.get(standard.key)
/** These are independent indices. Never rescale a US/European value into a Chinese value. */
fun aqiBand(value: Double,standard: AqiStandard): Int {
    val edges=if(standard==AqiStandard.EU) listOf(20,40,60,80,100) else listOf(50,100,150,200,300)
    return edges.indexOfFirst {value<=it}.takeIf {it>=0} ?: 5
}
