package com.lopleec.slopeweather.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable fun MapScreen(place: Place,prefs: SharedPreferences,onBack: ()->Unit) {
    val context=LocalContext.current
    var style by remember {mutableStateOf(prefs.getString("mapStyle","dark")!!.let {if(it=="carto") "dark" else it})}
    var radarSource by remember {mutableStateOf(if(place.country=="CN") "imerg" else if(place.country=="JP") "jma" else "rainviewer")}
    var frames by remember {mutableStateOf<JSONObject?>(null)}
    var error by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(true)}
    var retry by remember {mutableIntStateOf(0)}
    var radarRequest by remember {mutableIntStateOf(0)}
    var frame by remember {mutableIntStateOf(0)}
    var playing by remember {mutableStateOf(false)}
    var web by remember {mutableStateOf<WebView?>(null)}
    var ready by remember {mutableStateOf(false)}
    var shownFrame by remember {mutableIntStateOf(-1)}
    var maps by remember {mutableStateOf(false)}
    var layers by remember {mutableStateOf(false)}
    val times=frames?.optJSONArray("frames")
    val count=times?.length() ?: 0
    LaunchedEffect(retry,radarSource) {
        val request=++radarRequest
        loading=true;error=false;playing=false;frames=null;shownFrame=-1
        try {
            val http=WeatherHttp(java.io.File(context.cacheDir,"http"))
            val data=if(radarSource=="imerg") {
                nasaPrecipitationFrames(http.text("https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/wmts.cgi?SERVICE=WMTS&REQUEST=GetCapabilities",900_000,timeoutMillis=12000))
            } else if(radarSource=="jma") {
                val rows=http.json("https://www.jma.go.jp/bosai/jmatile/data/nowc/targetTimes_N1.json",300_000).getJSONArray("items")
                val f=JSONArray(rows.objects().sortedBy {it.getString("validtime")}.map { row ->
                    val time=java.time.LocalDateTime.parse(row.getString("validtime"),java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toEpochSecond(java.time.ZoneOffset.UTC)
                    JSONObject().put("time",time).put("url","https://www.jma.go.jp/bosai/jmatile/data/nowc/${row.getString("basetime")}/none/${row.getString("validtime")}/surf/hrpns/{z}/{x}/{y}.png")
                }); JSONObject().put("frames",f).put("attribution","Japan Meteorological Agency").put("maxZoom",10)
            } else {
                rainViewerFrames(http.json("https://api.rainviewer.com/public/weather-maps.json",300_000))
            }
            require(data.getJSONArray("frames").length()>0)
            frames=data;frame=data.getJSONArray("frames").length()-1
        } catch(e: kotlinx.coroutines.CancellationException) {throw e}
        catch(_: Exception) {if(request==radarRequest) error=true}
        finally {if(request==radarRequest) loading=false}
    }
    LaunchedEffect(playing,count,shownFrame) { if(playing && count>0 && shownFrame>=0) { delay(if(shownFrame==count-1) 1400 else 700);frame=(shownFrame+1)%count } }
    val mapError=s(R.string.radar_error)
    val baseError=s(R.string.base_map_error)
    val radarLoading=s(R.string.loading)
    val partialMap=s(R.string.partial_image)
    val mapConfig=remember(place,style,frames,mapError,baseError,radarLoading,partialMap) { JSONObject().put("lat",place.latitude).put("lon",place.longitude).put("name",place.name).put("style",style).put("date",LocalDate.now(java.time.ZoneOffset.UTC).minusDays(2).toString()).put("radar",frames).put("error",mapError).put("baseError",baseError).put("loading",radarLoading).put("partial",partialMap).toString() }
    LaunchedEffect(ready,mapConfig) { if(ready) web?.evaluateJavascript("window.configure($mapConfig)",null) }
    LaunchedEffect(ready,frame) { if(ready) web?.evaluateJavascript("window.setFrame($frame)",null) }
    DisposableEffect(Unit) { onDispose {web?.stopLoading();web?.destroy();web=null} }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            when(event) {
                Lifecycle.Event.ON_PAUSE->{playing=false;web?.onPause()}
                Lifecycle.Event.ON_RESUME->web?.onResume()
                else->Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory={ c -> WebView(c).apply {
                layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                if(com.lopleec.slopeweather.BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                webChromeClient=object: android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        if(com.lopleec.slopeweather.BuildConfig.DEBUG) android.util.Log.d("SlopeMap", "${message.lineNumber()}: ${message.message()}")
                        return true
                    }
                }
                setBackgroundColor(0xff19313a.toInt())
                settings.userAgentString=settings.userAgentString+" Slope Weather/${com.lopleec.slopeweather.BuildConfig.VERSION_NAME} (Android; com.lopleec.slopeweather)"
                settings.javaScriptEnabled=true
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface fun frameShown(index: Int) {android.os.Handler(android.os.Looper.getMainLooper()).post {shownFrame=index}}
                    @android.webkit.JavascriptInterface fun frameFailed() {android.os.Handler(android.os.Looper.getMainLooper()).post {playing=false;error=true}}
                },"SlopeMap")
                settings.domStorageEnabled=false
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient=object: WebViewClient() {
                    override fun onPageFinished(view: WebView,url: String) { ready=true;view.evaluateJavascript("window.configure($mapConfig);window.setFrame($frame)",null) }
                    override fun shouldOverrideUrlLoading(view: WebView,request: android.webkit.WebResourceRequest): Boolean { if(request.isForMainFrame && request.hasGesture() && request.url.scheme=="https") runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,request.url))};return true }
                }
                val html=c.assets.open("map.html").bufferedReader().use {it.readText()}.replace("<!--NASA_LEGEND-->",c.assets.open("gpm-legend.svg").bufferedReader().use {it.readText()})
                // Load bundled assets without granting filesystem access to WebView.
                val js=c.assets.open("leaflet.js").bufferedReader().use {it.readText()}
                val css=c.assets.open("leaflet.css").bufferedReader().use {it.readText()}
                loadDataWithBaseURL("https://slope.invalid/",html.replace("/*LEAFLET_CSS*/",css).replace("/*LEAFLET_JS*/",js),"text/html","UTF-8",null)
                web=this
            }},modifier=Modifier.fillMaxSize())

            TopAppBar(title={Text(s(R.string.radar))},navigationIcon={IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,s(R.string.weather))}},modifier=Modifier.align(Alignment.TopCenter),colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Black.copy(alpha=.28f),titleContentColor=Color.White,navigationIconContentColor=Color.White,actionIconContentColor=Color.White),windowInsets=WindowInsets(0,0,0,0))
            SmallFloatingActionButton(onClick={layers=true},modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp),containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer) {Icon(Icons.Outlined.Layers,s(R.string.map_style))}
            if(loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp),color=MaterialTheme.colorScheme.secondary)
            RadarLegend(radarSource,Modifier.align(Alignment.BottomStart).padding(start=16.dp,end=84.dp,bottom=16.dp).fillMaxWidth())
            if(error) Surface(Modifier.align(Alignment.Center).padding(24.dp),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.errorContainer) {Column(Modifier.padding(20.dp)) {Text(s(R.string.radar_error));TextButton(onClick={retry++}) {Text(s(R.string.retry))}}}
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal=28.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(count>0) localTime(times!!.getJSONObject((if(shownFrame>=0) shownFrame else frame).coerceIn(0,count-1)).getLong("time"),place.zone,"MM-dd HH:mm") else "—",fontSize=28.sp,modifier=Modifier.weight(1f))
                if(frame!=shownFrame && count>0) CircularProgressIndicator(Modifier.size(20.dp).padding(end=4.dp),strokeWidth=2.dp)
                val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
                FilledIconButton(onClick={playing=!playing;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)},enabled=count>1,modifier=Modifier.size(56.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer)) {Icon(if(playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,s(if(playing) R.string.pause else R.string.play),Modifier.size(28.dp))}
            }
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                val accent=MaterialTheme.colorScheme.secondary;val text=MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(Modifier.fillMaxSize().padding(horizontal=8.dp)) {
                    if(count>0) for(i in 0 until count) {val x=i.toFloat()/(count-1).coerceAtLeast(1)*size.width;drawLine(if(i==frame) accent else text,Offset(x,if(i==frame) 0f else size.height*.35f),Offset(x,size.height),if(i==frame) 4.dp.toPx() else 2.dp.toPx(),StrokeCap.Round)}
                }
                Slider(frame.toFloat(),onValueChange={playing=false;frame=it.roundToInt()},valueRange=0f..(count-1).coerceAtLeast(1).toFloat(),steps=(count-2).coerceAtLeast(0),enabled=count>1,colors=SliderDefaults.colors(thumbColor=Color.Transparent,activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent,activeTickColor=Color.Transparent,inactiveTickColor=Color.Transparent,disabledThumbColor=Color.Transparent,disabledActiveTrackColor=Color.Transparent,disabledInactiveTrackColor=Color.Transparent),modifier=Modifier.fillMaxWidth())
            }
            Text(s(if(radarSource=="imerg") R.string.imerg_note else R.string.radar_actual_note),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(if(count>0) localTime(times!!.getJSONObject(0).getLong("time"),place.zone,"MM-dd HH:mm") else "",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(when(radarSource) {"imerg"->"NASA IMERG";"jma"->"JMA";else->"RainViewer"},style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(count>0) localTime(times!!.getJSONObject(count-1).getLong("time"),place.zone,"MM-dd HH:mm") else "",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
    if(layers) ModalBottomSheet(onDismissRequest={layers=false}) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text(s(R.string.map_style),style=MaterialTheme.typography.titleLarge)
            ChoiceMenu(style,listOf("osm" to "OpenStreetMap","dark" to s(R.string.map_dark),"satellite" to "NASA · MODIS")) {style=it;prefs.edit().putString("mapStyle",it).apply()}
            Text(s(R.string.radar),style=MaterialTheme.typography.titleMedium)
            ChoiceMenu(radarSource,listOf("rainviewer" to "RainViewer","jma" to "JMA · 日本","imerg" to s(R.string.imerg_source))) {radarSource=it;layers=false}
            TextButton(onClick={layers=false;maps=true}) {Text(s(R.string.open_maps))}
            Text(s(when(radarSource) {"imerg"->R.string.imerg_note;"jma"->R.string.jma_radar_note;else->R.string.radar_note}),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(maps) AlertDialog(onDismissRequest={maps=false},title={Text(s(R.string.open_maps))},text={Column {
        val lat=place.latitude;val lon=place.longitude
        val gcj=wgsToGcj(lat,lon)
        val bd=gcjToBaidu(gcj.first,gcj.second)
        listOf("Google Maps" to "https://www.google.com/maps/search/?api=1&query=$lat,$lon","高德地图 · AMap" to "https://uri.amap.com/marker?position=${gcj.second},${gcj.first}&name=${Uri.encode(place.name)}&coordinate=gaode&callnative=1","百度地图 · Baidu" to "https://api.map.baidu.com/marker?location=${bd.first},${bd.second}&title=${Uri.encode(place.name)}&content=${Uri.encode(place.name)}&output=html&coord_type=bd09ll","OpenStreetMap" to "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=12/$lat/$lon","Windy" to "https://www.windy.com/?$lat,$lon,8").forEach { (label,url) -> TextButton(onClick={runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))};maps=false},Modifier.fillMaxWidth()) {Text(label)} }
    }},confirmButton={TextButton(onClick={maps=false}) {Text(s(R.string.close))}})
}
// Mainland web maps use GCJ-02 / BD-09. Do not pass unconverted WGS84 GPS coordinates.
internal fun wgsToGcj(lat: Double,lon: Double)=com.lopleec.slopeweather.data.wgs84ToGcj02(lat,lon)
internal fun gcjToBaidu(lat: Double,lon: Double): Pair<Double,Double> { val z=sqrt(lon*lon+lat*lat)+.00002*sin(lat*PI*3000/180);val theta=atan2(lat,lon)+.000003*cos(lon*PI*3000/180);return z*sin(theta)+.006 to z*cos(theta)+.0065 }
