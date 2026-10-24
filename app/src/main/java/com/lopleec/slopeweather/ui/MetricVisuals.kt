package com.lopleec.slopeweather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.*
import java.time.Instant
import kotlin.math.roundToInt

internal fun normalizedPercent(value: Double?)=((value ?: 0.0)/100).toFloat().coerceIn(0f,1f)
internal fun visibilityScale(meters: Double?)=(.46f+((meters ?: 0.0)/24000).toFloat().coerceIn(0f,1f)*.40f)
internal fun pressureProgress(hpa: Double)=((hpa-950)/100).toFloat().coerceIn(0f,1f)
internal val uvColors=listOf(Color(0xFF65D68E),Color(0xFFFFD32A),Color(0xFFFF8D2A),Color(0xFFF44C4C),Color(0xFFB064D9))
internal fun uvCategoryIndex(v: Double?): Int {val index=v?.roundToInt();return when {index==null || index<3->0;index<6->1;index<8->2;index<11->3;else->4}}
@Composable internal fun uvCategory(v: Double?)=s(listOf(R.string.low,R.string.moderate,R.string.high,R.string.very_high,R.string.extreme)[uvCategoryIndex(v)])
internal val aqiColors=listOf(Color(0xFF00D818),Color(0xFFFFE500),Color(0xFFFF8800),Color(0xFFFF2020),Color(0xFFAB42BB),Color(0xFF95003D))
internal fun aqiPosition(value: Double?,scale: String="us_aqi"): Float? {
    val v=value ?: return null
    val bounds=if(scale=="european_aqi") listOf(0,20,40,60,80,100,200) else listOf(0,50,100,150,200,300,500)
    val band=(0..5).firstOrNull {v<=bounds[it+1]} ?: 5
    return ((band+(v-bounds[band]).div(bounds[band+1]-bounds[band]).coerceIn(0.0,1.0))/6).toFloat()
}
@Composable internal fun qualityLabel(v: Double?,scale: String): String {
    if(v==null) return "—"
    if(scale=="european_aqi") return s(listOf(R.string.eu_quality_0,R.string.eu_quality_1,R.string.eu_quality_2,R.string.eu_quality_3,R.string.eu_quality_4,R.string.eu_quality_5)[aqiBand(v,AqiStandard.EU)])
    if(scale!="cn_aqi") return aqiLabel(v)
    return s(listOf(R.string.cn_quality_0,R.string.cn_quality_1,R.string.cn_quality_2,R.string.cn_quality_3,R.string.cn_quality_4,R.string.cn_quality_5)[when {v<=50->0;v<=100->1;v<=150->2;v<=200->3;v<=300->4;else->5}])
}
internal data class TimelinePoint(val reading: Reading,val current: Boolean=false)
internal fun metricTimeline(w: WeatherBundle,key: String,now: Long=Instant.now().epochSecond): List<TimelinePoint> {
    val singleDay=w.daily.singleOrNull()
    val today=localTime(now,w.zone,"yyyy-MM-dd")
    if(singleDay!=null && localTime(singleDay.time,w.zone,"yyyy-MM-dd")!=today) return w.hourly.filter {it[key]!=null}.take(24).map {TimelinePoint(it)}
    val current=w.current.takeIf {it[key]!=null && kotlin.math.abs(it.time-now)<10800}
    val future=w.hourly.filter {it.time>now && it.time<=now+86400 && it[key]!=null}.take(23)
    if(future.isNotEmpty()) return listOfNotNull(current?.let {TimelinePoint(it,true)})+future.map {TimelinePoint(it)}
    return w.history.filter {it[key]!=null && it.time>=now-86400 && it.time<(current?.time ?: now)}.takeLast(23).map {TimelinePoint(it)}+listOfNotNull(current?.let {TimelinePoint(it,true)})
}

internal fun rainIntensity(code: Int?,mm: Double?): Float = if(mm!=null && mm>0) maxOf(precipitationStrength(code)*.75f,(.12+kotlin.math.ln(1+mm)/kotlin.math.ln(16.0)*.88).toFloat().coerceIn(.12f,1f)) else precipitationStrength(code)
