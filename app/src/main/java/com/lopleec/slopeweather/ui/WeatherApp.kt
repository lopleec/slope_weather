package com.lopleec.slopeweather.ui


import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.WeatherViewModel
import com.lopleec.slopeweather.data.*
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WeatherApp(vm: WeatherViewModel=viewModel()) {
    val places by vm.places.collectAsState();val place by vm.selected.collectAsState();val provider by vm.provider.collectAsState()
    val weather by vm.weather.collectAsState();val loading by vm.loading.collectAsState();val error by vm.error.collectAsState()
    val context=LocalContext.current
    val localeTag=androidx.compose.ui.platform.LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(localeTag) {vm.localizePlaces(localeTag)}
    val currentLocation by vm.currentLocation.collectAsState()
    val locationScope=rememberCoroutineScope()
    var locating by remember {mutableStateOf(false)}
    var openLocationWhenReady by remember {mutableStateOf(false)}
    var locationMessage by remember {mutableStateOf<String?>(null)}
    val locationFailed=s(R.string.location_failed);val locationDenied=s(R.string.location_denied);val pendingLocation=s(R.string.location_name_pending)
    fun locate(open: Boolean) {if(locating) return;openLocationWhenReady=open;locationScope.launch {
        locating=true
        try {
            val location=locateDevice(context,vm.prefs.getString("locationMode","balanced")=="gps")
            if(location==null) locationMessage=locationFailed else {
                val previous=vm.currentLocation.value
                // Keep an existing location stable until naming resolves, avoiding two
                // different forecast requests for a temporary GPS point and its station.
                if(previous==null) vm.setCurrentLocation(Place(pendingLocation,location.latitude,location.longitude,"",java.time.ZoneId.systemDefault().id),open)
                vm.setCurrentLocation(resolveLocationPlace(context,location,vm.repository,previous),open=open && previous!=null)
            }
        } catch(e: kotlinx.coroutines.CancellationException) {throw e}
        catch(_: Exception) {openLocationWhenReady=false;locationMessage=locationFailed}
        finally {locating=false}
    }}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {grants->if(grants.values.any {it}) locate(true) else locationMessage=locationDenied}
    fun requestLocation() {
        val needed=if(vm.prefs.getString("locationMode","balanced")=="gps") Manifest.permission.ACCESS_FINE_LOCATION else Manifest.permission.ACCESS_COARSE_LOCATION
        if(ContextCompat.checkSelfPermission(context,needed)==PackageManager.PERMISSION_GRANTED) locate(true)
        else {vm.prefs.edit().putBoolean("locationPermissionRequested",true).apply();permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))}
    }
    val weatherScroll=rememberScrollState()
    LaunchedEffect(place?.id) {weatherScroll.scrollTo(0)}
    var tab by rememberSaveable {mutableIntStateOf(if(currentLocation!=null) 0 else 2)}
    var startupLocation by remember {mutableStateOf(currentLocation==null)}
    val refreshDeviceLocation by rememberUpdatedState<()->Unit>({
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate(false)
        else if(!vm.prefs.getBoolean("locationPermissionRequested",false)) requestLocation()
    })
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_START) refreshDeviceLocation()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(currentLocation,locating) {if(currentLocation!=null && (startupLocation || (openLocationWhenReady && !locating))) {startupLocation=false;openLocationWhenReady=false;tab=0}}
    LaunchedEffect(place,tab) {if(place==null && tab in 0..1) tab=2}
    var sourcePicker by remember {mutableStateOf(false)}
    var search by remember {mutableStateOf(false)}
    var customize by remember {mutableStateOf(false)}
    var mode by remember {
        if(!vm.prefs.getBoolean("systemTheme013",false)) vm.prefs.edit().putString("theme","system").putBoolean("systemTheme013",true).apply()
        mutableStateOf(vm.prefs.getString("theme","system")!!)
    }
    var dynamic by remember {mutableStateOf(vm.prefs.getBoolean("dynamic",true))}
    var fahrenheit by remember {mutableStateOf(vm.prefs.getBoolean("fahrenheit",false))}
    var windUnit by remember {mutableStateOf(vm.prefs.getString("windUnit","km/h")!!)}
    var aqiStandard by remember {mutableStateOf(runCatching {AqiStandard.valueOf(vm.prefs.getString("aqiStandard","US")!!)}.getOrDefault(AqiStandard.US))}
    DisposableEffect(vm.prefs) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {prefs,key->if(key=="aqiStandard") aqiStandard=runCatching {AqiStandard.valueOf(prefs.getString(key,"US")!!)}.getOrDefault(AqiStandard.US)}
        vm.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {vm.prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val details=remember {mutableStateListOf<DetailTarget>()}
    var order by remember {mutableStateOf(normalizeCardOrder(vm.prefs.getString("homeCardOrder",null)?.split(',') ?: defaultHomeCards.take(6)+vm.prefs.getString("cardOrder",null).orEmpty().split(',').filter {it.isNotBlank()}+defaultHomeCards.drop(6)))}
    var hidden by remember {mutableStateOf(vm.prefs.getStringSet("hidden",setOf("dew_point_2m","wind_gusts_10m","surface_pressure","snowfall","shortwave_radiation","cape","cloud_cover"))!!.toSet())}
    fun persistCards() {vm.prefs.edit().putString("homeCardOrder",order.joinToString(",")).putStringSet("hidden",hidden).apply()}
    fun move(key: String,direction: Int) {order=moveCard(order,key,direction);persistCards()}
    fun open(target: DetailTarget) {details.add(target)}
    BackHandler(details.isNotEmpty()) {details.removeAt(details.lastIndex)}
    BackHandler(tab!=2 && details.isEmpty()) {tab=if(tab==1) 0 else 2}
    val sky=rememberSkyPalette(place,weather?.current?.code)
    SlopeTheme(mode,dynamic,weatherBackdrop=sky.takeIf {tab==0 && details.isEmpty() && !search}) {
        CompositionLocalProvider(LocalAqiStandard provides aqiStandard) {
        val snackbar=remember {SnackbarHostState()};val scope=rememberCoroutineScope()
        LaunchedEffect(locationMessage) {locationMessage?.let {snackbar.showSnackbar(it);locationMessage=null}}
        if(search) SearchScreen(vm,{search=false},{vm.add(it);vm.localizePlaces(localeTag);search=false;tab=0})
        else if(details.isNotEmpty() && weather!=null) WeatherDetailScreen(details.last(),weather!!,fahrenheit,windUnit,{details.removeAt(details.lastIndex)},::open)
        else Scaffold(contentWindowInsets=WindowInsets.systemBars.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal),containerColor=when(tab) {0->sky.colors.first();else->MaterialTheme.colorScheme.background},contentColor=MaterialTheme.colorScheme.onBackground,snackbarHost={SnackbarHost(snackbar)}) {padding->
            Box(Modifier.padding(padding).fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                when(tab) {
                    0->place?.let {selectedPlace->HomeScreen(selectedPlace,provider,weather,loading,error,fahrenheit,windUnit,order.filterNot {it in hidden},onSource={sourcePicker=true},onPlaces={tab=2},onRefresh=vm::refresh,onMap={tab=1},onOpen=::open,onMoveTo={a,b->order=moveCard(order,a,order.indexOf(b)-order.indexOf(a));persistCards()},scroll=weatherScroll,effectsActive=!sourcePicker && !customize && !search,sky=sky)}
                    1->place?.let {MapScreen(it.copy(zone=weather?.zone ?: it.zone),vm.prefs,onBack={tab=0})}
                    2->PlacesScreen(vm,places,place,fahrenheit,{search=true},{vm.select(it);tab=0},{message->scope.launch {snackbar.showSnackbar(message)}},{tab=3},locating=locating,onLocate=::requestLocation)
                    3->SettingsScreen(vm,provider,mode,dynamic,fahrenheit,windUnit,{sourcePicker=true},{tab=2},{customize=true},{mode=it;vm.prefs.edit().putString("theme",it).apply()},{dynamic=it;vm.prefs.edit().putBoolean("dynamic",it).apply()},{fahrenheit=it;vm.prefs.edit().putBoolean("fahrenheit",it).apply()},{windUnit=it;vm.prefs.edit().putString("windUnit",it).apply()})
                }
            }
        }
        if(sourcePicker) ModalBottomSheet(onDismissRequest={sourcePicker=false}) {
            Column(Modifier.fillMaxWidth().heightIn(max=660.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(s(R.string.source),style=MaterialTheme.typography.headlineSmall)
                Provider.entries.forEach {p->
                    ListItem(headlineContent={Text(p.title)},supportingContent={Text(s(providerRegion(p)))},leadingContent={RadioButton(p==provider,null)},modifier=Modifier.clip(MaterialTheme.shapes.large).clickableCompat {vm.setProvider(p);sourcePicker=false},colors=ListItemDefaults.colors(containerColor=if(p==provider) MaterialTheme.colorScheme.primaryContainer else Color.Transparent))
                }
            }
        }
        if(customize) ModalBottomSheet(onDismissRequest={customize=false}) {
            Column(Modifier.fillMaxWidth().heightIn(max=680.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(s(R.string.customize),style=MaterialTheme.typography.headlineSmall)
                order.forEachIndexed {index,key->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(key !in hidden,{checked->hidden=if(checked) hidden-key else hidden+key;persistCards()})
                        Text(cardTitle(key,weather?.daily?.size ?: 14),Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                        IconButton(enabled=index>0,onClick={move(key,-1)}) {Icon(Icons.Outlined.KeyboardArrowUp,s(R.string.move_up))}
                        IconButton(enabled=index<order.lastIndex,onClick={move(key,1)}) {Icon(Icons.Outlined.KeyboardArrowDown,s(R.string.move_down))}
                    }
                }
            }
        }
    }
    }
}

private fun Modifier.clickableCompat(enabled: Boolean=true,onClick: ()->Unit)=this.then(Modifier.clickable(enabled=enabled,onClick=onClick))

@Composable internal fun cardTitle(key: String,days: Int): String=metrics.find {it.key==key}?.let {s(it.title)} ?: s(when(key) {
    "alerts"->R.string.alerts;"overview"->R.string.local_summary;"minutely"->R.string.short_term;"hourly"->R.string.hourly;"radar"->R.string.radar;"sun"->R.string.sun;"moon"->R.string.moon;"air"->R.string.air_quality;"pollen"->R.string.pollen;else->R.string.daily
},*if(key=="daily") arrayOf<Any>(days) else emptyArray())

@Composable private fun CardFrame(key: String,drag: CardDragState,modifier: Modifier=Modifier,content: @Composable ()->Unit) {
    DraggableCard(key,drag,modifier=modifier) {content()}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeScreen(p: Place,provider: Provider,w: WeatherBundle?,loading: Boolean,error: String?,f: Boolean,windUnit: String,order: List<String>,onSource: ()->Unit,onPlaces: ()->Unit,onRefresh: ()->Unit,onMap: ()->Unit,onOpen: (DetailTarget)->Unit,onMoveTo: (String,String)->Unit,scroll: androidx.compose.foundation.ScrollState,effectsActive: Boolean=true,sky: SkyPalette) {
    fun moveTo(key: String,target: String) {if(key!=target) onMoveTo(key,target)}
    val density=androidx.compose.ui.platform.LocalDensity.current.density
    val drag=rememberCardDrag(scroll,::moveTo)
    val scene=rememberWeatherScene(p.id,w?.current?.code,scroll.isScrollInProgress || drag.key!=null,effectsActive)
    CompositionLocalProvider(LocalWeatherScene provides scene,LocalContentColor provides sky.foreground) {
        Box(Modifier.fillMaxSize().dragWeatherCards(drag,::moveTo)) {
            WeatherBackground(sky)
            WeatherClouds(scene,w?.current?.code,sky)
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing=loading,onRefresh=onRefresh,modifier=Modifier.fillMaxSize().padding(top=64.dp)) {
                Column(Modifier.align(Alignment.TopCenter).widthIn(max=680.dp).fillMaxSize().verticalScroll(scroll).padding(horizontal=16.dp).padding(bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {WeatherGlyph(w?.current?.code,sky.day,Modifier.size(30.dp),onDarkBackground=!sky.darkForeground);Text(w?.let {condition(it.current.code)} ?: s(R.string.weather),fontSize=22.sp)}
                        Text(temperature(w?.current?.get("temperature_2m"),f),fontSize=128.sp,lineHeight=154.sp,letterSpacing=(-5).sp,fontWeight=FontWeight.Normal)
                        w?.current?.get("apparent_temperature")?.let {Text(s(R.string.feels_like,temperature(it,f)),fontSize=20.sp,color=sky.secondary)}
                        todayForecast(w)?.let {Text(s(R.string.high_low,temperature(it.high,f),temperature(it.low,f)),fontSize=18.sp,color=sky.secondary,modifier=Modifier.padding(top=7.dp))}
                    }
                    if(error!=null) WeatherCard(color=MaterialTheme.colorScheme.errorContainer) {
                        Text(s(if(error=="OFFLINE") R.string.offline else R.string.error_title),style=MaterialTheme.typography.titleMedium)
                        Text(s(when(error) {"OFFLINE"->R.string.offline_note;"REGION_UNSUPPORTED"->R.string.region_error;"MET_CONTACT_REQUIRED"->R.string.met_contact;"NMC_STATION_REQUIRED"->R.string.nmc_station_required;else->R.string.network_error}),style=MaterialTheme.typography.bodyMedium)
                        w?.let {Text(s(R.string.cached_at,localTime(it.fetched,it.zone,"yyyy-MM-dd HH:mm")),style=MaterialTheme.typography.bodyMedium)}
                        Row {TextButton(onClick=onRefresh) {Text(s(R.string.retry))};TextButton(onClick=onSource) {Text(s(R.string.source))}}
                    }
                    if(w!=null) {
                        val visible=order.filter {cardAvailable(it,w)}
                        SideEffect {drag.allowed=visible.toSet()}
                        val rows=cardRows(visible)
                        rows.forEach {row->key(row.joinToString()) {
                            Row(Modifier.fillMaxWidth().zIndex(if(drag.key in row) 10f else 0f),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                row.forEach {key->
                                    val size=defaultCardSize(key)
                                    val modifier=if(size.columns==2) Modifier.fillMaxWidth() else Modifier.weight(1f)
                                    CompositionLocalProvider(LocalCardMinimumHeight provides if(size.rows==2) 372.dp else 0.dp) {CardFrame(key,drag,modifier) {
                                        val ratio=size.columns.toFloat()/size.rows
                                        val open={onOpen(DetailTarget(detailKindFor(key)))}
                                        val metric=metrics.firstOrNull {it.key==key}
                                        if(metric!=null) PixelMetricCard(metric,w,f,windUnit,Modifier.fillMaxWidth(),ratio) {onOpen(DetailTarget(DetailKind.METRIC,key))}
                                        else when(key) {
                                            "alerts"->AlertSummaryCard(w,open)
                                            "overview"->OverviewCard(w,f,open)
                                            "minutely"->RainCard(w,open)
                                            "hourly"->HourlyCard(w,f,{onOpen(DetailTarget(DetailKind.HOURLY,time=it.time))},open)
                                            "daily"->DailyCard(w,f,{onOpen(DetailTarget(DetailKind.DAILY,time=it.time))},open)
                                            "radar"->WeatherCard(onClick=onMap) {CardHeading(s(R.string.weather_map),Icons.Outlined.Map);RadarPreview(w,Modifier.fillMaxWidth().height(if(size.rows==2) 300.dp else 150.dp))}
                                            "sun"->PixelSunCard(w,ratio,open)
                                            "moon"->MoonCard(w,ratio,open)
                                            "air"->AirCard(w,open)
                                            "pollen"->PollenCard(w,open)
                                        }
                                    }}
                                }
                                if(row.size==1 && defaultCardSize(row.first()).columns==1) Spacer(Modifier.weight(1f))
                            }
                        }}
                        Text((provider.title.substringBefore(" · ")+if(w.notes.any {it=="Supplement: Open-Meteo"}) " + Open-Meteo" else "")+"  ·  "+localTime(w.current.time,w.zone,"HH:mm")+" · "+w.zone,style=MaterialTheme.typography.labelMedium,color=sky.secondary,modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=18.dp))
                    }
                }
            }
            TopAppBar(title={Column {Text(p.name.substringBefore(" / ").substringBefore(" · "),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text(listOf(p.name.substringAfter(" · ",""),w?.let {s(R.string.updated,localTime(it.current.time,it.zone))}.orEmpty()).filter {it.isNotBlank()}.joinToString(" · "),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=sky.secondary)}},navigationIcon={IconButton(onClick=onPlaces) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,s(R.string.places))}},modifier=Modifier.align(Alignment.TopCenter).widthIn(max=680.dp).fillMaxWidth(),colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent,titleContentColor=sky.foreground,navigationIconContentColor=sky.foreground,actionIconContentColor=sky.foreground),windowInsets=WindowInsets(0,0,0,0))
            WeatherPrecipitation(scene,w?.current?.code,w?.current?.get("precipitation"),darkSky=!sky.darkForeground)
        }
    }
}

internal fun todayForecast(w: WeatherBundle?)=w?.daily?.firstOrNull {localTime(it.time,w.zone,"yyyy-MM-dd")==localTime(Instant.now().epochSecond,w.zone,"yyyy-MM-dd")}
internal fun cardAvailable(key: String,w: WeatherBundle): Boolean=when(key) {
    "alerts"->true;"overview"->w.overview.isNotBlank() || todayForecast(w)!=null;"minutely"->w.minutely.isNotEmpty();"hourly"->w.hourly.isNotEmpty();"daily"->w.daily.isNotEmpty();"radar"->true;"moon"->true;"sun"->w.daily.any {it.sunrise!=null && it.sunset!=null};"air"->w.air!=null || w.current["cn_aqi"]!=null;"pollen"->pollenKeys.any {w.air?.get(it.first)!=null};else->metrics.any {it.key==key} && metricValue(w,key)!=null && !(key=="us_aqi" && w.current["cn_aqi"]!=null)
}
@Composable internal fun OverviewCard(w: WeatherBundle,f: Boolean,onClick: (() -> Unit)?=null) {
    WeatherCard(onClick=onClick) {CardHeading(s(R.string.local_summary),Icons.AutoMirrored.Outlined.Article);val day=todayForecast(w);if(w.overview.isNotBlank()) Text(w.overview,style=MaterialTheme.typography.bodyLarge) else if(day!=null) Text(if(day.probability!=null) s(R.string.summary_format,condition(day.code),temperature(day.low,f),temperature(day.high,f),"${day.probability.roundToInt()}%") else s(R.string.summary_temperature,condition(day.code),temperature(day.low,f),temperature(day.high,f)),style=MaterialTheme.typography.bodyLarge)}
}
internal val pollutantKeys=listOf("pm2_5" to "PM2.5","pm10" to "PM10","ozone" to "O₃","nitrogen_dioxide" to "NO₂","sulphur_dioxide" to "SO₂","carbon_monoxide" to "CO")
internal val pollenKeys=listOf("grass_pollen" to R.string.grass,"birch_pollen" to R.string.birch,"alder_pollen" to R.string.alder,"ragweed_pollen" to R.string.ragweed,"mugwort_pollen" to R.string.mugwort,"olive_pollen" to R.string.olive)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun AirCard(w: WeatherBundle,onClick: (() -> Unit)?=null) {
    val standard=LocalAqiStandard.current;val score=selectedAqi(w,standard)
    val shape=RoundedCornerShape(28.dp);val longPress=LocalCardLongPress.current
    Surface(modifier=Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).combinedClickable(onClick=onClick ?: {},onLongClick=longPress).weatherSurface(shape),shape=shape,color=MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Waves,null,Modifier.size(20.dp))
                Text(s(R.string.air_quality),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(score?.roundToInt()?.toString() ?: "—",fontSize=46.sp,lineHeight=50.sp)
            Spectrum(score,standard.key)
            Text(if(score==null) s(R.string.aqi_unavailable) else qualityLabel(score,standard.key),style=MaterialTheme.typography.labelLarge,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
@Composable internal fun AirDetailCard(w: WeatherBundle) {
    val standard=LocalAqiStandard.current;val score=selectedAqi(w,standard)
    WeatherCard {
        CardHeading(s(R.string.air_quality),Icons.Outlined.Waves)
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(score?.roundToInt()?.toString() ?: "—",fontSize=46.sp)
            Text(qualityLabel(score,standard.key),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f).padding(bottom=7.dp))
        }
        Spectrum(score,standard.key)
        Text(aqiStandardName(standard),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(score==null) Text(s(R.string.aqi_unavailable),style=MaterialTheme.typography.bodyMedium)
        w.air?.let {air->
            Text(s(R.string.air_model_time,localTime(air.time,w.zone,"MM-dd HH:mm")),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            pollutantKeys.filter {air[it.first]!=null}.chunked(3).forEach {row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {row.forEach {(key,label)->Column(Modifier.weight(1f)) {Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("%.1f".format(air[key]),fontSize=20.sp);Text("μg/m³",style=MaterialTheme.typography.labelSmall)}}}}
        }
    }
}
@Composable fun PollenCard(w: WeatherBundle,onClick: (() -> Unit)?=null) {if(pollenKeys.any {w.air?.get(it.first)!=null}) PollenRings(w,onClick)}
@Composable fun ValueRow(label: String,value: String) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontWeight=FontWeight.Medium)}}
fun providerNote(p: Provider)=when(p) {Provider.NMC->R.string.nmc_note;Provider.JMA_DIRECT->R.string.jma_note;Provider.BRIGHTSKY->R.string.brightsky_note;Provider.CMA->R.string.cma_note;Provider.HKO->R.string.hko_note;Provider.NWS->R.string.nws_note;Provider.MET->R.string.met_note;else->R.string.via_openmeteo}
fun providerRegion(p: Provider)=when(p) {Provider.NMC->R.string.region_mainland;Provider.HKO->R.string.region_hk;Provider.JMA_DIRECT->R.string.region_japan;Provider.BRIGHTSKY->R.string.region_germany;Provider.NWS->R.string.region_us;Provider.KNMI->R.string.region_europe;else->R.string.region_global}
