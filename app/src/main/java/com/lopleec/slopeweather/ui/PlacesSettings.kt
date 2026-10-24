package com.lopleec.slopeweather.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.location.Location
import android.location.LocationListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.core.content.ContextCompat
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.WeatherViewModel
import com.lopleec.slopeweather.data.*
import com.lopleec.slopeweather.scheduleAlerts
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,ExperimentalMaterial3Api::class)
@Composable fun PlacesScreen(vm: WeatherViewModel,places: List<Place>,selected: Place?,f: Boolean,onAdd: ()->Unit,onSelect: (Place)->Unit,onMessage: (String)->Unit,onSettings: ()->Unit,locating: Boolean=false,onLocate: ()->Unit) {
    val previews by vm.previews.collectAsState()
    val current by vm.currentLocation.collectAsState()
    val provider by vm.provider.collectAsState()
    val snackbar=remember {SnackbarHostState()};val scope=rememberCoroutineScope();val haptic=LocalHapticFeedback.current
    val removed=s(R.string.city_removed);val undo=s(R.string.undo)
    val swipeRevisions=remember {mutableStateMapOf<String,Int>()}
    fun remove(p: Place) {val index=places.indexOf(p);vm.remove(p);haptic.performHapticFeedback(HapticFeedbackType.LongPress);scope.launch {if(snackbar.showSnackbar(removed,undo)==SnackbarResult.ActionPerformed) {swipeRevisions[p.id]=(swipeRevisions[p.id] ?: 0)+1;vm.restorePlace(p,index)}}}
    LaunchedEffect(places,current,provider) {vm.loadPreviews()}
    DisposableEffect(vm) {onDispose {vm.cancelPreviews()}}
    Box(Modifier.widthIn(max=680.dp).fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title={Text(s(R.string.app_name))},actions={FilledTonalIconButton(onClick=onSettings,shape=androidx.compose.foundation.shape.CircleShape) {Icon(Icons.Outlined.Settings,s(R.string.settings))}},windowInsets=WindowInsets(0,0,0,0),colors=TopAppBarDefaults.topAppBarColors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=116.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item("current-heading") {PlaceSection(s(R.string.current_location_title),Icons.Outlined.MyLocation)}
                current?.let {p->item("current-card") {PlaceCard(p,previews[p.id],f,{onSelect(p)})}}
                item("locate") {
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                        TextButton(onClick={onLocate()},enabled=!locating) {
                            if(locating) {CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                            Text(s(if(current==null) R.string.current_location else R.string.update_location))
                        }
                    }
                }
                item("saved-heading") {PlaceSection(s(R.string.saved_places),Icons.Outlined.LocationOn)}
                items(places,key={"saved-"+it.id}) {p ->
                    var menu by remember(p.id) {mutableStateOf(false)}
                    val index=places.indexOf(p)
                    Box(Modifier.animateItem()) {
                        key(swipeRevisions[p.id] ?: 0) {
                        val dismiss=rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismiss.settledValue) {if(dismiss.settledValue==SwipeToDismissBoxValue.EndToStart) remove(p)}
                        SwipeToDismissBox(state=dismiss,enableDismissFromStartToEnd=false,backgroundContent={Surface(Modifier.fillMaxSize(),shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.errorContainer) {Box(Modifier.fillMaxSize().padding(end=28.dp),contentAlignment=Alignment.CenterEnd) {Icon(Icons.Outlined.Delete,s(R.string.remove_city),tint=MaterialTheme.colorScheme.onErrorContainer)}}}) {
                            PlaceCard(p,previews[p.id],f,{onSelect(p)},{haptic.performHapticFeedback(HapticFeedbackType.LongPress);menu=true})
                        }
                        }
                        DropdownMenu(menu,{menu=false}) {
                            if(index>0) DropdownMenuItem(text={Text(s(R.string.move_up))},trailingIcon={Icon(Icons.Outlined.ArrowUpward,null)},onClick={vm.movePlace(p,-1);menu=false})
                            if(index<places.lastIndex) DropdownMenuItem(text={Text(s(R.string.move_down))},trailingIcon={Icon(Icons.Outlined.ArrowDownward,null)},onClick={vm.movePlace(p,1);menu=false})
                            DropdownMenuItem(text={Text(s(R.string.remove_city),color=MaterialTheme.colorScheme.error)},trailingIcon={Icon(Icons.Outlined.RemoveCircleOutline,null,tint=MaterialTheme.colorScheme.error)},onClick={remove(p);menu=false})
                        }
                    }
                }
                if(places.isEmpty()) item("empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical=48.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        Icon(Icons.Outlined.AddLocationAlt,null,Modifier.size(88.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s(R.string.search_save_location),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        FloatingActionButton(onClick=onAdd,modifier=Modifier.align(Alignment.BottomEnd).padding(24.dp),containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,shape=RoundedCornerShape(24.dp)) {Icon(Icons.Outlined.Search,s(R.string.add_place),Modifier.size(28.dp))}
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=104.dp))
    }
}
@Composable private fun PlaceSection(title: String,icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.padding(top=20.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Text(title,style=MaterialTheme.typography.titleMedium)}
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun PlaceCard(p: Place,w: WeatherBundle?,f: Boolean,onClick: ()->Unit,onLongClick: (() -> Unit)?=null) {
    val shape=RoundedCornerShape(50)
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val administrativeParts=p.name.substringBefore(" / ").split(" · ")
    Surface(modifier=Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick=onClick,onLongClick=onLongClick),shape=shape,color=if(w?.alerts?.any {it.critical}==true) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().heightIn(min=108.dp).padding(horizontal=18.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(60.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest),contentAlignment=Alignment.Center) {WeatherGlyph(w?.current?.code,w?.current?.day ?: true,Modifier.size(42.dp))}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(administrativeParts.first(),style=MaterialTheme.typography.titleLarge,maxLines=if(administrativeParts.size>1) 1 else 2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if(administrativeParts.size>1) Text(administrativeParts.drop(1).joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if(w!=null) {Text(condition(w.current.code),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);w.daily.firstOrNull()?.let {Text("${temperature(it.high,f)} ${temperature(it.low,f)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                else Text("%.3f, %.3f".format(locale,p.latitude,p.longitude),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(temperature(w?.current?.get("temperature_2m"),f),fontSize=32.sp)
        }
    }
}
internal suspend fun resolveLocationPlace(context: android.content.Context,location: Location,repository: WeatherRepository,previous: Place?): Place {
    val mainland=try {repository.mainlandLocation(location.latitude,location.longitude)}
        catch(e: CancellationException) {throw e} catch(_: Exception) {null}
    if(mainland!=null) return repository.localizedPlace(mainland,context.resources.configuration.locales[0].toLanguageTag())
    val address=try {
        if(!android.location.Geocoder.isPresent()) null
        else if(Build.VERSION.SDK_INT>=33) withTimeoutOrNull(6000) {suspendCancellableCoroutine<android.location.Address?> {continuation->
            android.location.Geocoder(context,context.resources.configuration.locales[0]).getFromLocation(location.latitude,location.longitude,1,object: android.location.Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) {if(continuation.isActive) continuation.resume(addresses.firstOrNull())}
                override fun onError(errorMessage: String?) {if(continuation.isActive) continuation.resume(null)}
            })
        }} else withContext(Dispatchers.IO) {@Suppress("DEPRECATION") android.location.Geocoder(context,context.resources.configuration.locales[0]).getFromLocation(location.latitude,location.longitude,1)?.firstOrNull()}
    } catch(e: CancellationException) {throw e} catch(_: Exception) {null}
    val country=address?.countryCode.orEmpty().uppercase(java.util.Locale.ROOT)
    val administrativeSuffix=Regex(".*(区|區|县|縣|旗|郡|District|County|Ward|Qu|Xian)$",RegexOption.IGNORE_CASE)
    val systemDistrict=if(country in setOf("CN","TW","HK","MO","JP")) listOfNotNull(address?.subLocality,address?.subAdminArea,address?.locality).firstOrNull {administrativeSuffix.matches(it)}
        else address?.subLocality?.takeIf {it.isNotBlank()} ?: address?.subAdminArea?.takeIf {it.isNotBlank() && it!=address.locality && it!=address.adminArea}
    val systemName=administrativeName(listOf(systemDistrict,address?.locality,address?.subAdminArea,address?.adminArea,address?.countryName))
    val zone=java.time.ZoneId.systemDefault().id
    if(systemDistrict!=null && systemName.isNotBlank()) return Place(systemName,location.latitude,location.longitude,country,zone)
    val supplemental=try {repository.administrativeLocation(location.latitude,location.longitude,context.resources.configuration.locales[0].toLanguageTag(),zone)} catch(e: CancellationException) {throw e} catch(_: Exception) {null}
    if(supplemental!=null) return supplemental
    if(systemName.isNotBlank()) return Place(systemName,location.latitude,location.longitude,address?.countryCode.orEmpty(),zone)
    // A failed lookup never labels a newly visited district with an unrelated cached city.
    val oldName=previous?.takeIf {distance(it.latitude,it.longitude,location.latitude,location.longitude)<.25 && !it.name.matches(Regex("[0-9., \\-]+"))}?.name
    return Place(oldName ?: context.getString(R.string.location_name_pending),location.latitude,location.longitude,address?.countryCode.orEmpty(),zone)

}
@Suppress("MissingPermission")
internal suspend fun locateDevice(context: android.content.Context,gpsOnly: Boolean): Location? = suspendCancellableCoroutine { continuation ->
    val manager=context.getSystemService(LocationManager::class.java)
    val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val providers=runCatching {manager.getProviders(true).filter {(it==LocationManager.GPS_PROVIDER && fine) || (!gpsOnly && it in setOf("fused",LocationManager.NETWORK_PROVIDER,LocationManager.PASSIVE_PROVIDER))}}.getOrDefault(emptyList())
    if(providers.isEmpty()) {continuation.resume(null);return@suspendCancellableCoroutine}
    var best=providers.mapNotNull {runCatching {manager.getLastKnownLocation(it)}.getOrNull()}.filter {android.os.SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos<120_000_000_000L}.minByOrNull {it.accuracy}
    if(best!=null && best!!.accuracy<=100f && android.os.SystemClock.elapsedRealtimeNanos()-best!!.elapsedRealtimeNanos<10_000_000_000L) {continuation.resume(best);return@suspendCancellableCoroutine}
    val handler=android.os.Handler(Looper.getMainLooper())
    lateinit var listener: LocationListener
    lateinit var timeout: Runnable
    lateinit var settle: Runnable
    fun finish() {
        try {manager.removeUpdates(listener)} catch(_: SecurityException) {}
        handler.removeCallbacks(timeout);handler.removeCallbacks(settle)
        if(continuation.isActive) continuation.resume(best)
    }
    var settling=false
    timeout=Runnable {finish()};settle=Runnable {finish()}
    listener=object: LocationListener {override fun onLocationChanged(location: Location) {
        if(best==null || location.accuracy<=100f || location.accuracy<best!!.accuracy || location.elapsedRealtimeNanos-best!!.elapsedRealtimeNanos>30_000_000_000L) best=location
        if(location.accuracy<=100f) finish() else if(!settling) {settling=true;handler.postDelayed(settle,4000)}
    }}
    var started=false
    providers.forEach {provider->runCatching {manager.requestLocationUpdates(provider,0L,0f,listener,Looper.getMainLooper())}.onSuccess {started=true}}
    if(started) {handler.postDelayed(timeout,45_000);if(best!=null && !settling) {settling=true;handler.postDelayed(settle,4000)}} else finish()
    continuation.invokeOnCancellation {
        try {manager.removeUpdates(listener)} catch(_: SecurityException) {}
        handler.removeCallbacks(timeout);handler.removeCallbacks(settle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchScreen(vm: WeatherViewModel,onDismiss: ()->Unit,onAdd: (Place)->Unit) {
    androidx.activity.compose.BackHandler(onBack=onDismiss)
    var query by remember {mutableStateOf("")};var results by remember {mutableStateOf<List<Place>>(emptyList())};var loading by remember {mutableStateOf(false)};var failed by remember {mutableStateOf(false)}
    var searchRequest by remember {mutableIntStateOf(0)}
    val current by vm.currentLocation.collectAsState();val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0].toLanguageTag()
    val focus=remember {FocusRequester()};val haptic=LocalHapticFeedback.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {focus.requestFocus();delay(100);keyboard?.show()}
    LaunchedEffect(query,locale) {
        val request=++searchRequest
        if(query.trim().isEmpty()) {results=emptyList();loading=false;failed=false;return@LaunchedEffect}
        delay(350);loading=true
        try {results=vm.repository.search(query.trim(),locale);failed=false}
        catch(e: CancellationException) {throw e}
        catch(_: Exception) {if(request==searchRequest) {failed=true;results=emptyList()}}
        finally {if(request==searchRequest) loading=false}
    }
    Scaffold(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,topBar={
        TopAppBar(title={TextField(query,{query=it},modifier=Modifier.fillMaxWidth().focusRequester(focus),placeholder={Text(s(R.string.search))},singleLine=true,colors=TextFieldDefaults.colors(focusedContainerColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedContainerColor=androidx.compose.ui.graphics.Color.Transparent,focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent))},navigationIcon={IconButton(onClick=onDismiss) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,s(R.string.close))}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh))
    }) {padding->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            HorizontalDivider()
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed) Text(s(R.string.search_failed),Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
            if(!loading && !failed && query.isNotBlank() && results.isEmpty()) Text(s(R.string.no_results),Modifier.padding(24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(contentPadding=PaddingValues(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(query.isBlank() && current!=null) item {
                    Text(s(R.string.current_location_title),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=6.dp))
                    ListItem(headlineContent={Text(current!!.name)},trailingContent={FilledTonalIconButton(onClick={haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);onAdd(current!!)}) {Icon(Icons.Outlined.Add,s(R.string.add_place))}},colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
                }
                items(results,key={it.id}) {p->ListItem(headlineContent={Text(p.name.substringBefore(" · "))},supportingContent={Text(p.name.substringAfter(" · ",p.country))},trailingContent={Icon(Icons.Outlined.Add,s(R.string.add_place))},modifier=Modifier.clickable {haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);onAdd(p)},colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))}
            }
        }
    }
}
@Composable fun SettingsScreen(vm: WeatherViewModel,provider: Provider,mode: String,dynamic: Boolean,f: Boolean,windUnit: String,onSource: ()->Unit,onBack: ()->Unit,onCustomize: ()->Unit,onTheme: (String)->Unit,onDynamic: (Boolean)->Unit,onFahrenheit: (Boolean)->Unit,onWindUnit: (String)->Unit) {
    val context=LocalContext.current
    var language by remember {mutableStateOf(if(Build.VERSION.SDK_INT>=33) context.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags() else vm.prefs.getString("language","")!!)}
    var alerts by remember {mutableStateOf(vm.prefs.getBoolean("alerts",false))}
    var supplement by remember {mutableStateOf(vm.prefs.getBoolean("supplementMissing",true))}
    var contact by remember {mutableStateOf(vm.prefs.getString("contact","")!!)}
    var locationMode by remember {mutableStateOf(vm.prefs.getString("locationMode","balanced")!!)}
    val chosenAqi=LocalAqiStandard.current
    var aqi by remember {mutableStateOf(chosenAqi)}
    var dialog by remember {mutableStateOf<String?>(null)}
    if(dialog=="about") {AboutScreen {dialog=null};return}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->alerts=granted;vm.prefs.edit().putBoolean("alerts",granted).apply();scheduleAlerts(context)}
    val languages=listOf("" to s(R.string.system),"zh-CN" to "简体中文","zh-TW" to "繁體中文","en" to "English","ja" to "日本語")
    val themes=listOf("system" to s(R.string.system),"dark" to s(R.string.dark),"light" to s(R.string.light))
    Column(Modifier.widthIn(max=680.dp).fillMaxSize()) {
        TopAppBar(title={Text(s(R.string.settings))},navigationIcon={IconButton(onClick=onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,s(R.string.places))}},windowInsets=WindowInsets(0,0,0,0))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            SettingsGroup(s(R.string.weather)) {
                SettingsRow(s(R.string.source),provider.title,Icons.Outlined.Storage,onSource)
                if(provider!=Provider.AUTO) ListItem(headlineContent={Text(s(R.string.supplement_missing))},supportingContent={Text("Open-Meteo")},trailingContent={Switch(supplement,{supplement=it;vm.prefs.edit().putBoolean("supplementMissing",it).apply();vm.refresh()})},colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
                if(provider==Provider.MET) SettingsRow(s(R.string.contact),contact.ifBlank {s(R.string.contact_hint)},Icons.Outlined.AlternateEmail) {dialog="contact"}
                SettingsRow(s(R.string.units),s(if(f) R.string.fahrenheit else R.string.celsius)+" · $windUnit",Icons.Outlined.Thermostat) {dialog="units"}
                SettingsRow(s(R.string.customize),null,Icons.Outlined.DashboardCustomize,onCustomize)
            }
            SettingsGroup(s(R.string.theme)) {
                SettingsRow(s(R.string.language),languages.find {it.first==language}?.second,Icons.Outlined.Language) {dialog="language"}
                SettingsRow(s(R.string.theme),themes.find {it.first==mode}?.second,Icons.Outlined.Palette) {dialog="theme"}
                ListItem(headlineContent={Text(s(R.string.dynamic_color))},leadingContent={Icon(Icons.Outlined.Wallpaper,null)},trailingContent={Switch(dynamic,onDynamic,enabled=Build.VERSION.SDK_INT>=31)},colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
            }
            SettingsGroup(s(R.string.general)) {
                SettingsRow(s(R.string.current_location),if(locationMode=="gps") "GPS" else "GPS + Network",Icons.Outlined.MyLocation) {dialog="location"}
                ListItem(headlineContent={Text(s(R.string.alerts_on))},leadingContent={Icon(Icons.Outlined.NotificationsNone,null)},trailingContent={Switch(alerts,{enabled->if(enabled && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else {alerts=enabled;vm.prefs.edit().putBoolean("alerts",enabled).apply();scheduleAlerts(context)}})},modifier=Modifier.clickable {dialog="alerts"},colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
                SettingsRow(s(R.string.about),null,Icons.Outlined.Info) {dialog="about"}
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    when(dialog) {
        "language" -> SelectionDialog(s(R.string.language),language,languages,{dialog=null}) {tag->language=tag;vm.prefs.edit().putString("language",tag).apply();if(Build.VERSION.SDK_INT>=33) context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags(tag) else (context as? Activity)?.recreate()}
        "theme" -> SelectionDialog(s(R.string.theme),mode,themes,{dialog=null},onTheme)
        "location" -> SelectionDialog(s(R.string.current_location),locationMode,listOf("balanced" to "GPS + Network","gps" to "GPS"),{dialog=null}) {locationMode=it;vm.prefs.edit().putString("locationMode",it).apply()}
        "units" -> AlertDialog(onDismissRequest={dialog=null},title={Text(s(R.string.units))},text={Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {ChoiceMenu(if(f) "f" else "c",listOf("c" to s(R.string.celsius),"f" to s(R.string.fahrenheit))) {onFahrenheit(it=="f")};ChoiceMenu(windUnit,listOf("km/h" to "km/h","m/s" to "m/s","mph" to "mph"),onWindUnit);Text(s(R.string.aqi_standard));ChoiceMenu(aqi.name,AqiStandard.entries.map {it.name to aqiStandardName(it)}) {aqi=AqiStandard.valueOf(it);vm.prefs.edit().putString("aqiStandard",it).apply()};Text(s(R.string.aqi_unavailable),style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={dialog=null}) {Text(s(R.string.close))}})
        "contact" -> AlertDialog(onDismissRequest={dialog=null},title={Text(s(R.string.contact))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {OutlinedTextField(contact,{contact=it},label={Text(s(R.string.contact_hint))},singleLine=true);Text(s(R.string.contact_note))}},confirmButton={TextButton(onClick={vm.prefs.edit().putString("contact",contact.trim()).apply();vm.refresh();dialog=null}) {Text(s(R.string.save))}},dismissButton={TextButton(onClick={dialog=null}) {Text(s(R.string.cancel))}})
        "alerts" -> AlertDialog(onDismissRequest={dialog=null},title={Text(s(if(dialog=="about") R.string.app_name else R.string.alerts))},text={Text(s(if(dialog=="about") R.string.about_text else R.string.alerts_note),Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()))},confirmButton={TextButton(onClick={dialog=null}) {Text(s(R.string.close))}})
    }

}
@Composable private fun SettingsGroup(title: String,content: @Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(start=16.dp));Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {Column(content=content)}}
}
@Composable private fun SettingsRow(title: String,subtitle: String?,icon: androidx.compose.ui.graphics.vector.ImageVector,onClick: ()->Unit) {
    ListItem(headlineContent={Text(title)},supportingContent=subtitle?.let {{Text(it)}},leadingContent={Icon(icon,null)},modifier=Modifier.clickable(onClick=onClick),colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
}
@Composable private fun SelectionDialog(title: String,selected: String,choices: List<Pair<String,String>>,onDismiss: ()->Unit,onSelect: (String)->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column {choices.forEach {(key,label)->Row(Modifier.fillMaxWidth().clickable {onSelect(key);onDismiss()}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(selected==key,{onSelect(key);onDismiss()});Text(label)}}}},confirmButton={TextButton(onClick=onDismiss) {Text(s(R.string.cancel))}})
}
@Composable fun ChoiceMenu(selected: String,choices: List<Pair<String,String>>,onChoose: (String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box { OutlinedButton(onClick={expanded=true},Modifier.fillMaxWidth()) {Text(choices.find {it.first==selected}?.second ?: selected,Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(expanded,{expanded=false}) {choices.forEach { (key,label) -> DropdownMenuItem(text={Text(label)},onClick={expanded=false;onChoose(key)},trailingIcon={if(selected==key) Icon(Icons.Outlined.Check,null)})}} }
}
