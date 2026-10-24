package com.lopleec.slopeweather.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.toShape
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.*
import kotlin.math.*

@Composable fun condition(code: Int?): String = s(when(code) {
    48->R.string.condition_freezing_fog
    61->R.string.condition_light_rain;65->R.string.condition_heavy_rain
    80,81,82->R.string.condition_showers
    71->R.string.condition_light_snow;75->R.string.condition_heavy_snow;85,86->R.string.condition_snow_showers
    else->when(WeatherKind.from(code)) {
        WeatherKind.CLEAR->R.string.condition_clear;WeatherKind.FAIR->R.string.condition_mostly_clear
        WeatherKind.PARTLY_CLOUDY->R.string.condition_partly_cloudy;WeatherKind.OVERCAST->R.string.condition_cloudy
        WeatherKind.FOG->R.string.condition_fog;WeatherKind.HAZE->R.string.condition_haze
        WeatherKind.DUST->R.string.condition_dust;WeatherKind.SMOKE->R.string.condition_smoke
        WeatherKind.DRIZZLE->R.string.condition_drizzle;WeatherKind.RAIN->R.string.condition_rain
        WeatherKind.FREEZING_RAIN->R.string.condition_freezing_rain;WeatherKind.SNOW->R.string.condition_snow
        WeatherKind.SLEET->R.string.condition_sleet;WeatherKind.HAIL->R.string.condition_hail
        WeatherKind.THUNDER->R.string.condition_thunder;WeatherKind.THUNDER_HAIL->R.string.condition_thunder_hail
        WeatherKind.THUNDER_SNOW->R.string.condition_thunder_snow;WeatherKind.THUNDER_SLEET->R.string.condition_thunder_sleet
        WeatherKind.WIND->R.string.condition_wind;WeatherKind.HOT->R.string.condition_hot
        WeatherKind.COLD->R.string.condition_cold;WeatherKind.DRY->R.string.condition_dry
        WeatherKind.HUMID->R.string.condition_humid;WeatherKind.WARM->R.string.condition_warm
        WeatherKind.COOL->R.string.condition_cool;WeatherKind.UNKNOWN->R.string.condition_unknown
    }
})

@Composable fun WeatherGlyph(code: Int?,day: Boolean=true,modifier: Modifier=Modifier,onDarkBackground: Boolean?=null) {
    val kind=WeatherKind.from(code)
    val dark=onDarkBackground ?: (MaterialTheme.colorScheme.background.luminance()<.5f)
    val moonTint=if(dark) Color(0xFF8DB8FF) else Color(0xFF3071EB)
    val cloudTint=if(dark) Color(0xFFB6B8C1) else Color(0xFF777A80)
    val sunShape=MaterialShapes.Cookie12Sided.toShape()
    Box(modifier,contentAlignment=Alignment.Center) {
        when(kind) {
            WeatherKind.THUNDER,WeatherKind.THUNDER_HAIL,WeatherKind.THUNDER_SNOW,WeatherKind.THUNDER_SLEET->Icon(Icons.Outlined.Thunderstorm,null,Modifier.fillMaxSize(.90f),tint=Color(0xFFA7B9FF))
            WeatherKind.SNOW,WeatherKind.SLEET,WeatherKind.HAIL->{
                Icon(Icons.Outlined.Cloud,null,Modifier.fillMaxSize(.78f).align(Alignment.TopCenter),tint=cloudTint)
                Icon(if(kind==WeatherKind.HAIL) Icons.Outlined.Grain else Icons.Outlined.AcUnit,null,Modifier.fillMaxSize(.34f).align(Alignment.BottomCenter),tint=Color(0xFF9FBCFF))
            }
            WeatherKind.FOG,WeatherKind.HAZE,WeatherKind.SMOKE->Icon(Icons.Outlined.Dehaze,null,Modifier.fillMaxSize(.85f),tint=cloudTint)
            WeatherKind.WIND,WeatherKind.DUST->Icon(Icons.Outlined.Air,null,Modifier.fillMaxSize(.85f),tint=if(kind==WeatherKind.DUST) Color(0xFFD3BD96) else Color(0xFFA3B1CA))
            WeatherKind.HOT,WeatherKind.WARM->Icon(Icons.Outlined.Thermostat,null,Modifier.fillMaxSize(.82f),tint=Color(0xFFFFBA76))
            WeatherKind.COLD,WeatherKind.COOL->Icon(Icons.Outlined.AcUnit,null,Modifier.fillMaxSize(.78f),tint=Color(0xFFA5C7FF))
            WeatherKind.HUMID->Icon(Icons.Outlined.WaterDrop,null,Modifier.fillMaxSize(.78f),tint=Color(0xFFA5C7FF))
            WeatherKind.CLEAR,WeatherKind.FAIR,WeatherKind.DRY->if(day) Surface(Modifier.fillMaxSize(.78f),shape=sunShape,color=Color(0xFFFFCC00),border=if(dark) null else androidx.compose.foundation.BorderStroke(1.2.dp,Color(0xFFE46B00))) {} else Icon(Icons.Filled.DarkMode,null,Modifier.fillMaxSize(.78f),tint=moonTint)
            else->{
                if(kind==WeatherKind.PARTLY_CLOUDY) {if(day) Surface(Modifier.fillMaxSize(.64f).align(Alignment.TopEnd),shape=sunShape,color=Color(0xFFFFCC00),border=if(dark) null else androidx.compose.foundation.BorderStroke(1.2.dp,Color(0xFFE46B00))) {} else Icon(Icons.Filled.DarkMode,null,Modifier.fillMaxSize(.64f).align(Alignment.TopEnd),tint=moonTint)}
                Icon(Icons.Outlined.Cloud,null,Modifier.fillMaxSize(.82f).align(if(kind.wet) Alignment.TopCenter else Alignment.Center),tint=cloudTint)
                if(kind.wet) Row(Modifier.fillMaxWidth(.62f).fillMaxHeight(.30f).align(Alignment.BottomCenter),horizontalArrangement=Arrangement.SpaceEvenly) {repeat(if(precipitationStrength(code)>.7f) 3 else 1) {Icon(Icons.Filled.WaterDrop,null,Modifier.fillMaxHeight().width(12.dp),tint=Color(0xFF69A0FF))}}
            }
        }
    }
}

internal val LocalCardLongPress=staticCompositionLocalOf<(() -> Unit)?> {null}
internal val LocalCardMinimumHeight=staticCompositionLocalOf {0.dp}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun WeatherCard(modifier: Modifier=Modifier,color: Color=MaterialTheme.colorScheme.surfaceContainerLowest,onClick: (() -> Unit)?=null,content: @Composable ColumnScope.()->Unit) {
    val longPress=LocalCardLongPress.current
    val shape=MaterialTheme.shapes.extraLarge
    val interaction=if(onClick!=null || longPress!=null) Modifier.clip(shape).combinedClickable(onClick=onClick ?: {},onLongClick=longPress) else Modifier
    Card(modifier=modifier.heightIn(min=LocalCardMinimumHeight.current).then(interaction).weatherSurface(shape),shape=shape,colors=CardDefaults.cardColors(containerColor=color),elevation=CardDefaults.cardElevation(defaultElevation=0.dp)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}
@Composable fun CardHeading(title: String,icon: ImageVector,action: (@Composable ()->Unit)?=null) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) { Icon(icon,null,Modifier.size(19.dp),tint=MaterialTheme.colorScheme.onSurface); Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f)); action?.invoke() }
}

@Composable fun HourlyCard(w: WeatherBundle,f: Boolean,onHour: (Reading)->Unit,onOpen: (() -> Unit)?=null) {
    WeatherCard(onClick=onOpen) {
        CardHeading(s(R.string.hourly),Icons.Outlined.Schedule)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            w.hourly.take(72).forEach {h ->
                Column(Modifier.width(46.dp).clip(RoundedCornerShape(24.dp)).clickable {onHour(h)}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(temperature(h["temperature_2m"],f),fontSize=16.sp)
                    WeatherGlyph(h.code,h.day,Modifier.size(29.dp))
                    h["precipitation_probability"]?.let {Text("${it.roundToInt()}%",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    val now=java.time.Instant.now().epochSecond
                    Text(if(now in h.time until h.time+3600) s(R.string.now) else localTime(h.time,w.zone),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable fun DailyCard(w: WeatherBundle,f: Boolean,onDay: (DayForecast)->Unit,onOpen: (() -> Unit)?=null) {
    WeatherCard(onClick=onOpen) {
        CardHeading(s(R.string.daily,w.daily.size),Icons.Outlined.CalendarMonth)
        DailyForecastStrip(w,f,null,onDay)
    }
}
@Composable internal fun DailyForecastStrip(w: WeatherBundle,f: Boolean,selected: Long?,onDay: (DayForecast)->Unit) {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        w.daily.forEach {d->
            val chosen=selected==d.time
            Surface(onClick={haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);onDay(d)},modifier=Modifier.width(58.dp),shape=RoundedCornerShape(50),color=Color.Transparent,border=androidx.compose.foundation.BorderStroke(if(chosen) 2.dp else 1.dp,if(chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(temperature(d.high,f),fontSize=15.sp);Text(temperature(d.low,f),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    WeatherGlyph(d.code,modifier=Modifier.size(29.dp))
                    Box(Modifier.height(18.dp),contentAlignment=Alignment.Center) {d.probability?.let {Text("${it.roundToInt()}%",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    val today=localTime(d.time,w.zone,"yyyy-MM-dd")==localTime(java.time.Instant.now().epochSecond,w.zone,"yyyy-MM-dd")
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {Text(if(today) s(R.string.today) else localTime(d.time,w.zone,"EEE"),fontSize=12.sp);Text(localTime(d.time,w.zone,"M/d"),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }
    }
}

data class Metric(val key: String,val title: Int,val unit: String,val icon: ImageVector,val help: Int?=null)
val metrics=listOf(
    Metric("cn_aqi",R.string.cn_aqi,"CN AQI",Icons.Outlined.Waves,R.string.aqi_help),
    Metric("uv_index",R.string.uv,"",Icons.Outlined.WbSunny,R.string.uv_help),
    Metric("uv_index_clear_sky",R.string.clear_sky_uv,"",Icons.Outlined.WbSunny,R.string.clear_sky_uv_help),
    Metric("wind_speed_10m",R.string.wind,"km/h",Icons.Outlined.Air,R.string.wind_help),
    Metric("relative_humidity_2m",R.string.humidity,"%",Icons.Outlined.WaterDrop,R.string.humidity_help),
    Metric("visibility",R.string.visibility,"km",Icons.Outlined.Visibility,R.string.visibility_help),
    Metric("pressure_msl",R.string.pressure,"hPa",Icons.Outlined.Compress,R.string.pressure_help),
    Metric("us_aqi",R.string.air_quality,"US AQI",Icons.Outlined.Waves,R.string.aqi_help),
    Metric("precipitation",R.string.precipitation,"mm",Icons.Outlined.Umbrella,R.string.precip_help),
    Metric("cloud_cover",R.string.cloud,"%",Icons.Outlined.Cloud),
    Metric("dew_point_2m",R.string.dew_point,"°",Icons.Outlined.Thermostat,R.string.humidity_help),
    Metric("wind_gusts_10m",R.string.wind_gusts,"km/h",Icons.Outlined.Air,R.string.wind_help),
    Metric("surface_pressure",R.string.surface_pressure,"hPa",Icons.Outlined.Compress,R.string.pressure_help),
    Metric("snowfall",R.string.snowfall,"cm",Icons.Outlined.AcUnit),
    Metric("shortwave_radiation",R.string.radiation,"W/m²",Icons.Outlined.WbSunny),
    Metric("cape",R.string.cape,"J/kg",Icons.Outlined.Bolt)
)
fun metricValue(w: WeatherBundle,key: String): Double? = w.current[key] ?: if(key=="us_aqi") w.air?.get(key) else if(key=="uv_index") w.air?.get(key) else null
fun metricNumber(value: Double?,key: String,f: Boolean,windUnit: String): String {
    if(value==null) return "—"
    if(key=="dew_point_2m" || key=="temperature_2m") return temperature(value,f)
    val v=when { key=="visibility" -> value/1000; key.startsWith("wind_") && key!="wind_direction_10m" -> when(windUnit) { "m/s" -> value/3.6; "mph" -> value/1.609344; else -> value }; else -> value }
    return if(key in listOf("precipitation","snowfall","visibility") || key.startsWith("wind_")) "%.1f".format(v) else v.roundToInt().toString()
}
@Composable fun aqiLabel(v: Double) = s(when { v<=50 -> R.string.good; v<=100 -> R.string.moderate; v<=150 -> R.string.unhealthy_sensitive; v<=200 -> R.string.unhealthy; v<=300 -> R.string.very_unhealthy; else -> R.string.hazardous })
@Composable fun Spectrum(value: Double?,scale: String="us_aqi") {
    val position=aqiPosition(value,scale)
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val h=5.dp.toPx();val y=(size.height-h)/2
        clipPath(Path().apply {addRoundRect(RoundRect(0f,y,size.width,y+h,CornerRadius(h/2)))}) {aqiColors.forEachIndexed {i,color->drawRect(color,Offset(size.width*i/6,y),Size(size.width/6+1,h))}}
        position?.let {val x=it.coerceIn(.025f,.975f)*size.width;val color=aqiColors[(it*6).toInt().coerceIn(0,5)];drawCircle(Color.Black.copy(alpha=.6f),7.dp.toPx(),Offset(x,size.height/2));drawCircle(color,4.5.dp.toPx(),Offset(x,size.height/2))}
    }
}
@Composable fun RainCard(w: WeatherBundle,onClick: (() -> Unit)?=null) {
    val rain=w.minutely.mapNotNull { it["precipitation"] }
    WeatherCard(onClick=onClick) {
        CardHeading(s(R.string.short_term),Icons.Outlined.Grain)
        Text(s(if(rain.isEmpty()) R.string.missing_data else if(rain.max()<.1) R.string.rain_dry else R.string.rain_expected),fontSize=20.sp)
        val accent=MaterialTheme.colorScheme.onSurface; val track=MaterialTheme.colorScheme.surfaceContainerHigh
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val step=size.width/rain.size.coerceAtLeast(1); val max=(rain.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            rain.forEachIndexed { i,value -> val h=(value/max).toFloat()*size.height; drawRoundRect(if(value>0) accent else track,Offset(i*step,size.height-h.coerceAtLeast(3.dp.toPx())),Size((step-4.dp.toPx()).coerceAtLeast(1f),h.coerceAtLeast(3.dp.toPx())),CornerRadius(3.dp.toPx())) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { w.minutely.firstOrNull()?.let { Text(localTime(it.time,w.zone),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }; w.minutely.lastOrNull()?.let { Text(localTime(it.time,w.zone),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }

    }
}
@Composable fun SunCard(w: WeatherBundle,onClick: (() -> Unit)?=null) {
    val d=w.daily.firstOrNull { it.sunrise!=null && it.sunset!=null } ?: return
    WeatherCard(onClick=onClick) {
        CardHeading(s(R.string.sun),Icons.Outlined.WbTwilight)
        DaylightPlot(d,Modifier.fillMaxWidth().height(100.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("${s(R.string.sunrise)} ${localTime(d.sunrise!!,w.zone)}",fontSize=14.sp); Text("${s(R.string.sunset)} ${localTime(d.sunset!!,w.zone)}",fontSize=14.sp) }
    }
}
