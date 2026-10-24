package com.lopleec.slopeweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.*

@Composable internal fun rememberAstronomy(w: WeatherBundle,time: Long=Instant.now().epochSecond/60*60): Astronomy? {
    val value by produceState<Astronomy?>(null,w.place.id,time) {value=withContext(Dispatchers.Default) {runCatching {astronomy(w.place.copy(zone=w.zone),time)}.getOrNull()}}
    return value
}
@Composable internal fun moonLabel(phase: Double)=s(listOf(R.string.moon_new,R.string.moon_waxing_crescent,R.string.moon_first_quarter,R.string.moon_waxing_gibbous,R.string.moon_full,R.string.moon_waning_gibbous,R.string.moon_last_quarter,R.string.moon_waning_crescent)[((phase*8+.5).toInt()%8).coerceIn(0,7)])
@Composable internal fun MoonDisc(fraction: Double,waxing: Boolean,modifier: Modifier=Modifier) {
    val dark=Color(0xFF30374B);val light=Color(0xFFE8EDFF)
    Canvas(modifier) {
        val r=size.minDimension/2;val x=size.width/2;val y=size.height/2
        val lit=fraction.coerceIn(0.0,1.0)
        drawCircle(if(lit<=0.0) dark else light,r)
        if(lit>0.0 && lit<1.0) {
            val side=if(waxing) 1f else -1f
            val terminator=(1-2*lit).toFloat()*side
            val path=Path().apply {
                // Share a true circular boundary with the disk. The previous
                // inscribed polygon exposed a hairline along the illuminated rim.
                arcTo(androidx.compose.ui.geometry.Rect(x-r,y-r,x+r,y+r),-90f,-180f*side,true)
                if(abs(terminator)<.0001f) lineTo(x,y-r)
                else arcTo(androidx.compose.ui.geometry.Rect(x-abs(terminator)*r,y-r,x+abs(terminator)*r,y+r),90f,if(terminator>0) -180f else 180f,false)
                close()
            }
            drawPath(path,dark)
        }
    }
}
@Composable fun MoonCard(w: WeatherBundle,ratio: Float=1f,onClick: (() -> Unit)?=null) {
    val a=rememberAstronomy(w)
    val shape=RoundedCornerShape(28.dp)
    Surface(onClick={onClick?.invoke()},modifier=Modifier.fillMaxWidth().aspectRatio(ratio).weatherSurface(shape),shape=shape,color=MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Icon(Icons.Outlined.DarkMode,null,Modifier.size(19.dp));Text(s(R.string.moon),style=MaterialTheme.typography.titleSmall)}
            if(a!=null) {MoonDisc(a.fraction,a.phase<.5,Modifier.size(if(ratio>1) 78.dp else 64.dp));Text(moonLabel(a.phase),style=MaterialTheme.typography.labelLarge);Text("${(a.fraction*100).roundToInt()}%",style=MaterialTheme.typography.bodyMedium)}
        }
    }
}
@Composable fun PixelSunCard(w: WeatherBundle,ratio: Float=1f,onClick: (() -> Unit)?=null) {
    val d=todayForecast(w)?.takeIf {it.sunrise!=null && it.sunset!=null} ?: w.daily.singleOrNull()?.takeIf {it.sunrise!=null && it.sunset!=null} ?: return
    val shape=RoundedCornerShape(28.dp)
    Surface(onClick={onClick?.invoke()},modifier=Modifier.fillMaxWidth().aspectRatio(ratio).weatherSurface(shape),shape=shape,color=MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column {
            Row(Modifier.padding(start=16.dp,end=12.dp,top=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {Icon(Icons.Outlined.WbTwilight,null,Modifier.size(20.dp));Text(s(R.string.sun),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
            Box(Modifier.weight(1f).fillMaxWidth()) {
                DaylightPlot(d,Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {Icon(Icons.Outlined.WbSunny,null,Modifier.size(16.dp));Text(localTime(d.sunrise!!,w.zone),style=MaterialTheme.typography.labelLarge)}
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {Icon(Icons.Outlined.WbTwilight,null,Modifier.size(16.dp));Text(localTime(d.sunset!!,w.zone),style=MaterialTheme.typography.labelLarge)}
                }
            }
        }
    }
}
@Composable internal fun MoonDetailContent(w: WeatherBundle) {
    val a=rememberAstronomy(w) ?: return
    WeatherCard {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {MoonDisc(a.fraction,a.phase<.5,Modifier.size(110.dp));Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {Text(moonLabel(a.phase),style=MaterialTheme.typography.headlineSmall);Text("${(a.fraction*100).roundToInt()}%",fontSize=42.sp);Text(s(R.string.illumination),style=MaterialTheme.typography.bodyMedium)}}
        ValueRow(s(R.string.moonrise),a.rise?.let {localTime(it,w.zone)} ?: "—")
        ValueRow(s(R.string.moonset),a.set?.let {localTime(it,w.zone)} ?: "—")
    }
    Text(s(R.string.moon_help),style=MaterialTheme.typography.bodyLarge)
    Text(s(R.string.astronomical),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun AlertSummaryCard(w: WeatherBundle,onClick: ()->Unit) {
    val alert=w.alerts.firstOrNull {it.critical} ?: w.alerts.firstOrNull()
    if(alert==null) {
        WeatherCard(onClick=onClick) {CardHeading(s(R.string.alerts),Icons.Outlined.NotificationsNone);Text(s(if(w.alertStatus=="regional") R.string.alerts_none else if(w.alertStatus in setOf("wmo","partial")) R.string.alerts_received_none else R.string.alerts_unavailable),style=MaterialTheme.typography.bodyMedium)}
        return
    }
    WeatherCard(color=if(alert.critical) Color(0xFFAA101A) else MaterialTheme.colorScheme.surfaceContainerLowest,onClick=onClick) {
        CompositionLocalProvider(LocalContentColor provides if(alert.critical) Color(0xFFFFE5E4) else MaterialTheme.colorScheme.onSurface) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Icon(Icons.Outlined.WarningAmber,null,Modifier.size(22.dp));Text(alert.title,style=MaterialTheme.typography.titleLarge)}
            if(alert.text.isNotBlank()) Text(alert.text,style=MaterialTheme.typography.bodyLarge,maxLines=5,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(alert.source,style=MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable internal fun OfficialAlertDetail(a: WeatherAlert,zone: String) {
    val context=androidx.compose.ui.platform.LocalContext.current
    WeatherCard(color=if(a.critical) Color(0xFFAA101A) else MaterialTheme.colorScheme.surfaceContainerLowest) {
        CompositionLocalProvider(LocalContentColor provides if(a.critical) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.onSurface) {
            Icon(Icons.Outlined.WarningAmber,null,Modifier.size(24.dp))
            Text(a.title,style=MaterialTheme.typography.headlineSmall)
            if(a.text.isNotBlank()) Text(a.text,style=MaterialTheme.typography.bodyLarge)
            Text(a.source,style=MaterialTheme.typography.labelLarge)
            a.issued?.let {Text(s(R.string.updated,localTime(it,zone,"EEE HH:mm")),style=MaterialTheme.typography.bodyMedium)}
            a.expires?.let {Text(s(R.string.alert_until,localTime(it,zone,"EEE HH:mm")),style=MaterialTheme.typography.bodyMedium)}
            if(a.url.startsWith("https://")) OutlinedButton(onClick={runCatching {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(a.url))) }},colors=ButtonDefaults.outlinedButtonColors(contentColor=if(a.critical) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.primary)) {Text(s(R.string.official_alert))}
        }
    }
}
