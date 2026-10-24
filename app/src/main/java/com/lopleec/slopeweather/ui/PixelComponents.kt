package com.lopleec.slopeweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable internal fun WeatherBackground(sky: SkyPalette) {
    val painter=remember {createSkyPainter()}
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas {painter.draw(it.nativeCanvas,size.width,size.height,sky.colors)}
    }
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun PixelMetricCard(m: Metric,w: WeatherBundle,f: Boolean,windUnit: String,modifier: Modifier=Modifier,ratio: Float=1f,dailyTotal: Boolean=true,onClick: ()->Unit) {
    val dayRain=if(dailyTotal && m.key=="precipitation") (todayForecast(w) ?: w.daily.singleOrNull())?.rain else null
    val value=dayRain ?: metricValue(w,m.key)
    val scheme=MaterialTheme.colorScheme
    val fill=scheme.surfaceContainerHighest
    val foreground=scheme.onSurface
    val card=scheme.surfaceContainerLowest
    val cookie=MaterialShapes.Cookie12Sided.toShape()
    val shape=when(m.key) {"uv_index","uv_index_clear_sky"->cookie;"pressure_msl","surface_pressure","visibility","wind_speed_10m","wind_gusts_10m"->CircleShape;else->RoundedCornerShape(28.dp)}
    val wind=m.key.startsWith("wind_")
    val longPress=LocalCardLongPress.current
    Surface(modifier=modifier.aspectRatio(ratio).clip(shape).combinedClickable(onClick=onClick,onLongClick=longPress).weatherSurface(shape),shape=shape,color=card) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            if(m.key=="relative_humidity_2m" || m.key=="cloud_cover") Canvas(Modifier.fillMaxSize()) {
                val level=normalizedPercent(value)
                val y=size.height*(1-level)
                val wave=Path().apply {moveTo(0f,size.height);lineTo(0f,y);for(x in 0..size.width.toInt() step 3) lineTo(x.toFloat(),y+sin(x/size.width*PI.toFloat()*8f)*(if(level>0f && level<1f) 3.dp.toPx() else 0f));lineTo(size.width,size.height);close()}
                drawPath(wave,fill)
            }
            if(m.key=="visibility") {
                val scale=visibilityScale(value)
                repeat(3) {i->Box(Modifier.fillMaxSize(scale+i*.035f).graphicsLayer {rotationZ=i*4f}.background(fill.copy(alpha=1f-i*.18f),cookie))}
            }
            if(m.key.startsWith("wind_")) {
                val direction=w.current["wind_direction_10m"]
                if(direction!=null) RoundedWindArrow(direction,fill,Modifier.fillMaxSize(.78f))
            }
            if(m.key=="pressure_msl" || m.key=="surface_pressure") Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                drawArc(fill,135f,270f,false,style=Stroke(7.dp.toPx(),cap=StrokeCap.Round))
                value?.let {drawArc(foreground,135f,pressureProgress(it)*270f,false,style=Stroke(7.dp.toPx(),cap=StrokeCap.Round))}
            }
            if(wind) Text(s(R.string.north),fontSize=10.sp,color=scheme.onSurfaceVariant,modifier=Modifier.align(Alignment.TopCenter).padding(top=8.dp))
            val circular=shape==CircleShape
            Column(Modifier.fillMaxSize().padding(if(circular) 24.dp else 15.dp),horizontalAlignment=if(m.key in listOf("precipitation","relative_humidity_2m","us_aqi","cn_aqi")) Alignment.Start else Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(if(wind) 6.dp else 10.dp,Alignment.CenterVertically)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {Icon(m.icon,null,Modifier.size(20.dp));Text(s(m.title),modifier=Modifier.weight(1f,fill=false),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(if(m.key in listOf("pressure_msl","surface_pressure") && value!=null) java.text.NumberFormat.getIntegerInstance().format(value.roundToInt()) else metricNumber(value,m.key,f,windUnit),fontSize=if(ratio>1) 52.sp else if(m.key in listOf("pressure_msl","surface_pressure")) 34.sp else 42.sp,lineHeight=50.sp,letterSpacing=(-1.8).sp,maxLines=1)
                    if(wind) Text(windUnit,fontSize=12.sp,modifier=Modifier.padding(bottom=7.dp))
                    else if(m.unit=="%") Text("%",fontSize=28.sp,modifier=Modifier.padding(bottom=3.dp))
                    else if(m.key in listOf("visibility","precipitation")) Text(m.unit,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=5.dp))
                }
                when(m.key) {
                    "uv_index","uv_index_clear_sky" -> {
                        Text(uvCategory(value),style=MaterialTheme.typography.labelLarge)
                        Row(Modifier.padding(top=3.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {uvColors.forEachIndexed {i,color->val active=i==uvCategoryIndex(value);Box(Modifier.size(if(active) 12.dp else 8.dp).background(color.copy(alpha=if(active) 1f else .2f),CircleShape))}}
                    }
                    "us_aqi","cn_aqi" -> {Spectrum(value,m.key);Text(qualityLabel(value,m.key),style=MaterialTheme.typography.labelLarge,maxLines=2)}
                    "relative_humidity_2m" -> w.current["dew_point_2m"]?.let {dew->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {Surface(shape=CircleShape,color=foreground,contentColor=card) {Text(temperature(dew,f),Modifier.padding(7.dp),style=MaterialTheme.typography.labelLarge)};Text(s(R.string.dew_point),style=MaterialTheme.typography.labelMedium,maxLines=1)}}
                    "wind_speed_10m","wind_gusts_10m" -> Text(w.current["wind_direction_10m"]?.let {s(R.string.wind_from,windDirection(it))} ?: "—",style=MaterialTheme.typography.labelLarge)
                    "precipitation" -> Text(s(if(dayRain!=null) R.string.day_total else R.string.current_condition),style=MaterialTheme.typography.labelMedium,maxLines=2)
                    else -> if(m.unit.isNotBlank() && m.unit !in listOf("%","°","km")) Text(m.unit,style=MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
@Composable fun RadarPreview(w: WeatherBundle,modifier: Modifier=Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val preview by produceState<android.graphics.Bitmap?>(null,w.place.id) {
        value=withContext(Dispatchers.IO) {
            runCatching {
                val p=w.place;val zoom=6;val n=1 shl zoom
                val worldX=(p.longitude+180)/360*n*256
                val rad=Math.toRadians(p.latitude.coerceIn(-85.0,85.0))
                val worldY=(1-ln(tan(rad)+1/cos(rad))/PI)/2*n*256
                val width=512;val height=224
                val left=worldX-width/2;val top=(worldY-height/2).coerceIn(0.0,n*256.0-height)
                val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(bitmap)
                for(tileY in floor(top/256).toInt()..floor((top+height-1)/256).toInt()) {
                    for(tileX in floor(left/256).toInt()..floor((left+width-1)/256).toInt()) {
                        val x=Math.floorMod(tileX,n)
                        val file=java.io.File(context.cacheDir,"preview-osm-$zoom-$x-$tileY.png")
                        val tile=if(file.exists()) android.graphics.BitmapFactory.decodeFile(file.path) else {
                            val connection=java.net.URL("https://tile.openstreetmap.org/$zoom/$x/$tileY.png").openConnection().apply {connectTimeout=8000;readTimeout=8000;setRequestProperty("User-Agent","Slope Weather/${com.lopleec.slopeweather.BuildConfig.VERSION_NAME} (Android; com.lopleec.slopeweather)")}
                            val bytes=connection.getInputStream().use {it.readBytes()}
                            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.also {file.writeBytes(bytes)}
                        }
                        checkNotNull(tile)
                        canvas.drawBitmap(tile,(tileX*256-left).toFloat(),(tileY*256-top).toFloat(),null)
                    }
                }
                bitmap
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF152442)),contentAlignment=Alignment.Center) {
        preview?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Surface(Modifier.size(12.dp),shape=CircleShape,color=Color(0xFF80B4FF),border=androidx.compose.foundation.BorderStroke(2.dp,Color.White)) {}} ?: Icon(Icons.Outlined.Map,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.secondary)
        if(preview!=null) Text("© OpenStreetMap",fontSize=8.sp,color=Color(0xFF1A2340),modifier=Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha=.8f)).padding(horizontal=4.dp))
    }
}
