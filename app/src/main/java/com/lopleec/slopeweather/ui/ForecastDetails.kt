package com.lopleec.slopeweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.*
import java.time.Instant
import kotlin.math.*

enum class DetailKind {OVERVIEW,MINUTELY,HOURLY,DAILY,METRIC,SUN,AIR,POLLEN,MOON,ALERTS}
data class DetailTarget(val kind: DetailKind,val key: String="",val time: Long?=null,val day: Long?=null)
internal fun detailKindFor(key: String)=when(key) {"overview"->DetailKind.OVERVIEW;"minutely"->DetailKind.MINUTELY;"hourly"->DetailKind.HOURLY;"daily"->DetailKind.DAILY;"sun"->DetailKind.SUN;"moon"->DetailKind.MOON;"air"->DetailKind.AIR;"pollen"->DetailKind.POLLEN;else->DetailKind.ALERTS}

internal fun hoursOnDay(w: WeatherBundle,time: Long)=w.hourly.filter {localTime(it.time,w.zone,"yyyy-MM-dd")==localTime(time,w.zone,"yyyy-MM-dd")}
internal fun weatherOnDay(w: WeatherBundle,d: DayForecast): WeatherBundle {
    val hours=hoursOnDay(w,d.time)
    val values=buildMap<String,Double> {
        for(key in metrics.map {it.key}+"temperature_2m"+"wind_direction_10m") {
            val data=hours.mapNotNull {it[key]}
            if(data.isNotEmpty()) put(key,when(key) {"precipitation","snowfall"->dailyAccumulation(hours,key,w.zone) ?: continue;"uv_index","wind_speed_10m","wind_gusts_10m"->data.max();"wind_direction_10m"->hours.maxByOrNull {it["wind_speed_10m"] ?: -1.0}?.get(key) ?: data.first();else->data.average()})
        }
        d.rain?.let {put("precipitation",it)};d.uv?.let {put("uv_index",it)};d.wind?.let {put("wind_speed_10m",it)}
    }
    return w.copy(current=Reading(d.time,values,d.code),hourly=hours,daily=listOf(d),minutely=emptyList(),air=null,alerts=emptyList(),history=emptyList(),airHourly=emptyList())
}
internal data class MetricSummary(val value: Double?,val label: Int)
internal fun metricSummary(m: Metric,w: WeatherBundle): MetricSummary {
    val d=todayForecast(w) ?: w.daily.singleOrNull()
    val hours=if(d!=null) hoursOnDay(w,d.time) else w.hourly.take(24)
    val values=hours.mapNotNull {it[m.key]}
    return when(m.key) {
        "uv_index","uv_index_clear_sky"->MetricSummary((if(m.key=="uv_index") d?.uv else null) ?: values.maxOrNull() ?: metricValue(w,m.key),R.string.forecast_high)
        "wind_speed_10m"->MetricSummary(d?.wind ?: values.maxOrNull() ?: metricValue(w,m.key),R.string.forecast_high)
        "wind_gusts_10m"->MetricSummary(values.maxOrNull() ?: metricValue(w,m.key),R.string.forecast_high)
        "relative_humidity_2m"->if(values.isNotEmpty()) MetricSummary(values.average(),R.string.forecast_average) else MetricSummary(metricValue(w,m.key),R.string.current_condition)
        "precipitation"->if(d?.rain!=null) MetricSummary(d.rain,R.string.day_total) else MetricSummary(metricValue(w,m.key),R.string.current_condition)
        else->MetricSummary(metricValue(w,m.key),R.string.current_condition)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WeatherDetailScreen(target: DetailTarget,w: WeatherBundle,f: Boolean,windUnit: String,onBack: ()->Unit,onOpen: (DetailTarget)->Unit) {
    val selectedDay=target.day?.let {t->w.daily.firstOrNull {it.time==t}}
    val data=selectedDay?.let {weatherOnDay(w,it)} ?: w
    val title=when(target.kind) {
        DetailKind.METRIC->metrics.find {it.key==target.key}?.let {s(it.title)} ?: s(R.string.details)
        DetailKind.OVERVIEW->s(R.string.local_summary);DetailKind.MINUTELY->s(R.string.precipitation);DetailKind.HOURLY->s(R.string.hourly);DetailKind.DAILY->s(R.string.daily,w.daily.size);DetailKind.SUN->s(R.string.sun);DetailKind.MOON->s(R.string.moon);DetailKind.AIR->s(R.string.air_quality);DetailKind.POLLEN->s(R.string.pollen);DetailKind.ALERTS->s(R.string.alerts)
    }
    Scaffold(contentWindowInsets=WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),containerColor=MaterialTheme.colorScheme.background,topBar={
        TopAppBar(title={Column {Text(title,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);if(target.kind!=DetailKind.SUN) Text(if(target.kind==DetailKind.MOON) s(R.string.calculated_at,localTime(Instant.now().epochSecond,w.zone,"MM-dd HH:mm")) else s(R.string.updated,localTime(w.current.time,w.zone)),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}},navigationIcon={IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,s(R.string.close))}})
    }) {padding->
        key(target.kind,target.key,target.day) {
            Column(Modifier.padding(padding).fillMaxSize().widthIn(max=680.dp).verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(top=20.dp,bottom=32.dp+WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                when(target.kind) {
                    DetailKind.METRIC->metrics.find {it.key==target.key}?.let {MetricDetailContent(it,data,f,windUnit,target.time)}
                    DetailKind.DAILY->DailyDetailContent(w,f,windUnit,target.time ?: w.daily.firstOrNull()?.time,onOpen)
                    DetailKind.HOURLY->HourlyDetailContent(data,f,windUnit,target.time) {onOpen(it.copy(day=target.day))}
                    DetailKind.SUN->SunDetailContent(data)
                    DetailKind.MOON->MoonDetailContent(data)
                    DetailKind.MINUTELY->{
                        RainCard(data)
                        metrics.first {it.key=="precipitation"}.let {MetricDetailContent(it,data,f,windUnit)}
                    }
                    DetailKind.AIR->AirDetailContent(data)
                    DetailKind.POLLEN->PollenDetailContent(data)
                    DetailKind.ALERTS->{
                        if(data.alerts.isEmpty()) Text(s(if(data.alertStatus=="regional") R.string.alerts_none else if(data.alertStatus in setOf("wmo","partial")) R.string.alerts_received_none else R.string.alerts_unavailable))
                        Text(s(R.string.alerts_global_coverage),style=MaterialTheme.typography.bodyMedium)
                        data.alertsCheckedAt?.let {Text(s(R.string.updated,localTime(it,data.zone,"MM-dd HH:mm")),style=MaterialTheme.typography.labelMedium)}
                        val context=androidx.compose.ui.platform.LocalContext.current
                        TextButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://severeweather.wmo.int/")))}) {Text("WMO · SWIC") }
                        data.alerts.forEach {a->OfficialAlertDetail(a,data.zone)}
                    }
                    DetailKind.OVERVIEW->{
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text(temperature(data.current["temperature_2m"],f),fontSize=80.sp,letterSpacing=(-4).sp);WeatherGlyph(data.current.code,data.current.day,Modifier.size(80.dp))}
                        Text(condition(data.current.code),style=MaterialTheme.typography.headlineMedium)
                        OverviewCard(data,f)
                        if(data.hourly.isNotEmpty()) HourlyCard(data,f,{onOpen(DetailTarget(DetailKind.HOURLY,time=it.time))},{onOpen(DetailTarget(DetailKind.HOURLY))})
                        if(data.daily.isNotEmpty()) DailyCard(data,f,{onOpen(DetailTarget(DetailKind.DAILY,time=it.time))},{onOpen(DetailTarget(DetailKind.DAILY))})
                        ForecastMetricGrid(data,f,windUnit,onOpen)
                    }
                }
            }
        }
    }
}

@Composable private fun DailyDetailContent(w: WeatherBundle,f: Boolean,windUnit: String,initial: Long?,onOpen: (DetailTarget)->Unit) {
    var selected by remember(initial) {mutableStateOf(initial)}
    DailyForecastStrip(w,f,selected) {selected=it.time}
    val d=w.daily.firstOrNull {it.time==selected} ?: w.daily.firstOrNull() ?: return
    val data=remember(w,d) {weatherOnDay(w,d)}
    Column(Modifier.padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(localTime(d.time,w.zone,"MMMM d"),style=MaterialTheme.typography.titleLarge)
        Text(w.place.name.substringBefore(" / "),style=MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(temperature(d.high,f),fontSize=52.sp,letterSpacing=(-2).sp)
            Text(temperature(d.low,f),fontSize=52.sp,letterSpacing=(-2).sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            WeatherGlyph(d.code,modifier=Modifier.size(52.dp))
        }
        Text(condition(d.code),style=MaterialTheme.typography.headlineSmall)
        if(d.description.isNotBlank()) Text(d.description,style=MaterialTheme.typography.bodyLarge)
    }
    if(data.hourly.isNotEmpty()) HourlyCard(data,f,{onOpen(DetailTarget(DetailKind.HOURLY,time=it.time,day=d.time))},{onOpen(DetailTarget(DetailKind.HOURLY,day=d.time))})
    ForecastMetricGrid(data,f,windUnit,{onOpen(it.copy(day=d.time))})
    if(d.sunrise!=null && d.sunset!=null) SunCard(data) {onOpen(DetailTarget(DetailKind.SUN,day=d.time))}
}

@Composable private fun HourlyDetailContent(w: WeatherBundle,f: Boolean,windUnit: String,initial: Long?,onOpen: (DetailTarget)->Unit) {
    var selected by remember(initial) {mutableStateOf(initial ?: w.hourly.firstOrNull()?.time)}
    val h=w.hourly.firstOrNull {it.time==selected} ?: w.hourly.firstOrNull() ?: return
    Text(localTime(h.time,w.zone,"EEE, MMM d · HH:mm"),style=MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text(temperature(h["temperature_2m"],f),fontSize=80.sp,letterSpacing=(-4).sp);WeatherGlyph(h.code,h.day,Modifier.size(80.dp))}
    Text(condition(h.code),style=MaterialTheme.typography.headlineSmall)
    WeatherCard {
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),state=rememberLazyListState(initialFirstVisibleItemIndex=w.hourly.indexOf(h).coerceAtLeast(0))) {
            items(w.hourly.take(72),key={it.time}) {hour->
                Surface(onClick={selected=hour.time},shape=RoundedCornerShape(50),color=if(hour.time==selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                    Column(Modifier.width(54.dp).padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {Text(temperature(hour["temperature_2m"],f));WeatherGlyph(hour.code,hour.day,Modifier.size(28.dp));Text(localTime(hour.time,w.zone),style=MaterialTheme.typography.labelMedium)}
                }
            }
        }
    }
    h["precipitation_probability"]?.let {WeatherCard {ValueRow(s(R.string.rain_chance),"${it.roundToInt()}%")}}
    ForecastMetricGrid(w.copy(current=h,air=null),f,windUnit,onOpen,selectedHour=h.time)
}

@Composable private fun ForecastMetricGrid(w: WeatherBundle,f: Boolean,windUnit: String,onOpen: (DetailTarget)->Unit,selectedHour: Long?=null) {
    var more by remember {mutableStateOf(false)}
    val extra=setOf("uv_index_clear_sky","dew_point_2m","wind_gusts_10m","surface_pressure","snowfall","shortwave_radiation","cape","cloud_cover")
    val visible=metrics.filter {it.key !in setOf("cn_aqi","us_aqi") && metricValue(w,it.key)!=null && (more || it.key !in extra || (it.key=="surface_pressure" && metricValue(w,"pressure_msl")==null))}
    if(metrics.any {it.key in extra && metricValue(w,it.key)!=null}) TextButton(onClick={more=!more}) {Text(s(if(more) R.string.fewer_metrics else R.string.more_metrics))}
    visible.chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach {m->PixelMetricCard(m,w,f,windUnit,Modifier.weight(1f),dailyTotal=selectedHour==null) {onOpen(DetailTarget(DetailKind.METRIC,m.key,time=selectedHour))}};if(row.size==1) Spacer(Modifier.weight(1f))}}
}

@Composable private fun MetricDetailContent(m: Metric,w: WeatherBundle,f: Boolean,windUnit: String,atTime: Long?=null,showHelp: Boolean=true) {
    val summary=remember(m,w,atTime) {if(atTime!=null) MetricSummary(w.hourly.firstOrNull {it.time==atTime}?.get(m.key),R.string.current_condition) else metricSummary(m,w)}
    val points=remember(w,m.key) {metricTimeline(w,m.key)}
    val hours=points.map {it.reading}
    if(m.key=="visibility") Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest) {
        Box(Modifier.fillMaxWidth().height(136.dp)) {
            VisibilityBands(summary.value,Modifier.matchParentSize())
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(s(summary.label),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(metricDisplay(summary.value,m,f,windUnit),fontSize=44.sp,lineHeight=54.sp,letterSpacing=(-1.6).sp)
            }
        }
    } else {
    WeatherCard {
        Text(if(atTime!=null) localTime(atTime,w.zone,"EEE HH:mm") else s(summary.label),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(metricDisplay(summary.value,m,f,windUnit),fontSize=44.sp,lineHeight=54.sp,letterSpacing=(-1.6).sp)
        if(m.key.startsWith("uv_index") && summary.value!=null) Text(uvLabel(summary.value),style=MaterialTheme.typography.titleLarge)
        when(m.key) {
            "pressure_msl","surface_pressure"->{
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress={((summary.value ?: 950.0)-950.0).toFloat().div(100).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(8.dp),color=MaterialTheme.colorScheme.onSurface,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh,drawStopIndicator={})
            }
            "visibility"->VisibilityBands(summary.value)
            else->if(hours.any {it[m.key]!=null}) MetricTimeline(m,points,w.zone,f,windUnit)
        }
    }
    }
    if(m.key.startsWith("wind_")) Text(s(R.string.wind_direction_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(m.key in w.supplemented) Text(s(R.string.supplement_source)+(if(atTime==null && summary.label==R.string.current_condition) w.supplementTime?.let {" · "+localTime(it,w.zone,"MM-dd HH:mm")}.orEmpty() else ""),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(showHelp) Text(s(metricHelp(m)),style=MaterialTheme.typography.bodyLarge,lineHeight=28.sp)
    if(m.key=="relative_humidity_2m") w.current["dew_point_2m"]?.let {WeatherCard {ValueRow(s(R.string.dew_point),temperature(it,f))}}
}
internal fun metricDisplay(v: Double?,m: Metric,f: Boolean,windUnit: String): String {
    val unit=when {m.key.startsWith("wind_")->windUnit;m.unit=="°"->"";else->m.unit}
    return metricNumber(v,m.key,f,windUnit)+(if(unit.isBlank()) "" else if(unit=="%") "%" else " $unit")
}
@Composable private fun uvLabel(v: Double)=uvCategory(v)
private fun metricHelp(m: Metric)=m.help ?: when(m.key) {"cloud_cover"->R.string.cloud_help;"snowfall"->R.string.snow_help;"shortwave_radiation"->R.string.radiation_help;"cape"->R.string.cape_help;else->R.string.forecast_help}

@Composable private fun MetricTimeline(m: Metric,points: List<TimelinePoint>,zone: String,f: Boolean,windUnit: String) {
    val hours=points.map {it.reading}
    val maxValue=when(m.key) {"relative_humidity_2m","cloud_cover"->100.0;"uv_index","uv_index_clear_sky"->11.0;else->hours.mapNotNull {it[m.key]}.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0}
    Spacer(Modifier.height(32.dp))
    LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        items(points,key={it.reading.time}) {point->
            val h=point.reading;val value=h[m.key]
            Column(Modifier.width(38.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(m.key=="precipitation") WeatherGlyph(h.code,h.day,Modifier.size(25.dp))
                Box(Modifier.height(112.dp).fillMaxWidth(),contentAlignment=Alignment.BottomCenter) {
                    if(value!=null) {
                        val ratio=(value/maxValue).toFloat().coerceIn(0f,1f)
                        Box(Modifier.width(26.dp).height(if(value==0.0) 2.dp else (12+ratio*100).dp).clip(RoundedCornerShape(20.dp)).background(if(m.key.startsWith("uv_index")) uvColors[uvCategoryIndex(value)] else MaterialTheme.colorScheme.onSurface),contentAlignment=Alignment.TopCenter) {
                            if(m.key=="relative_humidity_2m" && value>15) Icon(Icons.Outlined.WaterDrop,null,Modifier.padding(top=6.dp).size(12.dp),tint=MaterialTheme.colorScheme.surface)
                            if(m.key.startsWith("wind_") && value>0) h["wind_direction_10m"]?.let {direction->RoundedWindArrow(direction,MaterialTheme.colorScheme.surface,Modifier.padding(top=5.dp).size(14.dp))}
                        }
                    }
                }
                if(m.key.startsWith("wind_")) Text(windDirection(h["wind_direction_10m"]),style=MaterialTheme.typography.labelMedium)
                Text(metricNumber(value,m.key,f,windUnit)+(if(m.unit=="%") "%" else ""),style=MaterialTheme.typography.labelMedium)
                if(m.key.startsWith("uv_index")) WeatherGlyph(h.code,h.day,Modifier.size(25.dp))
                if(m.key=="precipitation") Text(h["precipitation_probability"]?.let {"${it.roundToInt()}%"} ?: "—",style=MaterialTheme.typography.labelMedium)
                Text(if(point.current) s(R.string.now) else localTime(h.time,zone),style=MaterialTheme.typography.labelSmall,fontWeight=if(point.current) FontWeight.Bold else FontWeight.Normal,color=if(point.current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable internal fun windDirection(degrees: Double?): String {
    if(degrees==null) return "—"
    val labels=listOf(R.string.north,R.string.northeast,R.string.east,R.string.southeast,R.string.south,R.string.southwest,R.string.west,R.string.northwest)
    return s(labels[(((degrees%360+360)%360+22.5)/45).toInt()%8])
}
@Composable private fun VisibilityBands(value: Double?,modifier: Modifier=Modifier.fillMaxWidth().height(32.dp)) {
    val color=MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        repeat(3) {i->
            val path=Path();val edge=size.width*(visibilityScale(value)-.32f+i*.19f)
            path.moveTo(0f,0f);path.lineTo(edge,0f);path.cubicTo(edge+30f,size.height*.35f,edge-15f,size.height*.65f,edge+18f,size.height);path.lineTo(0f,size.height);path.close()
            drawPath(path,color.copy(alpha=.18f+i*.13f))
        }
    }
}
@Composable private fun SunDetailContent(w: WeatherBundle) {
    val d=w.daily.firstOrNull {it.sunrise!=null && it.sunset!=null}
    val astro=rememberAstronomy(w,d?.time ?: w.current.time)
    if(d==null) {Text(s(R.string.missing_data));return}
    Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            DaylightPlot(d,Modifier.fillMaxWidth().height(180.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=22.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf(s(R.string.dawn) to astro?.dawn,s(R.string.sunrise) to d.sunrise,s(R.string.sunset) to d.sunset,s(R.string.dusk) to astro?.dusk).forEach {(label,time)->
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(label,style=MaterialTheme.typography.labelLarge);Text(time?.let {localTime(it,w.zone)} ?: "—",fontSize=21.sp)}
                }
            }
        }
    }
    val minutes=((d.sunset!!-d.sunrise!!)/60).toInt()
    ValueRow(s(R.string.daylight),s(R.string.hours_minutes,minutes/60,minutes%60))
    Text(s(R.string.sun_help),style=MaterialTheme.typography.bodyLarge,lineHeight=28.sp)
}
@Composable internal fun DaylightPlot(d: DayForecast,modifier: Modifier=Modifier) {
    val fill=MaterialTheme.colorScheme.surfaceContainerHighest;val ground=MaterialTheme.colorScheme.surfaceContainerHigh;val horizon=MaterialTheme.colorScheme.outline;val sun=Color(0xFFFFD02F)
    Canvas(modifier.clip(RoundedCornerShape(20.dp))) {
        val base=size.height*.5f
        val hill=Path().apply {moveTo(0f,size.height);lineTo(0f,size.height*.78f);cubicTo(size.width*.25f,size.height*.78f,size.width*.28f,size.height*.15f,size.width*.5f,size.height*.15f);cubicTo(size.width*.72f,size.height*.15f,size.width*.75f,size.height*.78f,size.width,size.height*.78f);lineTo(size.width,size.height);close()}
        drawPath(hill,fill)
        drawRect(ground.copy(alpha=.72f),Offset(0f,base),Size(size.width,size.height-base))
        drawLine(horizon,Offset(0f,base),Offset(size.width,base),2.dp.toPx())
        val rise=d.sunrise;val set=d.sunset;val now=Instant.now().epochSecond
        if(rise!=null && set!=null && now in rise..set && set>rise) {val progress=((now-rise).toFloat()/(set-rise)).coerceIn(0f,1f);drawCircle(sun,7.dp.toPx(),Offset(size.width*(.22f+.56f*progress),base-sin(progress*PI).toFloat()*size.height*.35f))}
    }
}
