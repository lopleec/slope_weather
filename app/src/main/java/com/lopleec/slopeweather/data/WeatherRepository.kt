package com.lopleec.slopeweather.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

class WeatherRepository(private val context: Context) {
    private val assets=context.assets
    private val root=File(context.filesDir,"weather-cache").apply { mkdirs() }
    private val http=WeatherHttp(File(context.cacheDir,"http"))
    private val prefs=context.getSharedPreferences("weather",Context.MODE_PRIVATE)
    suspend fun mainlandLocation(latitude: Double,longitude: Double): Place? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if(latitude !in 18.0..54.0 || longitude !in 73.0..135.0) null else ChinaGeocoder(JSONObject(assets.open("china-provinces.json").bufferedReader().use {it.readText()}),http).resolve(latitude,longitude)
    }
    private fun file(p: Place,provider: Provider)=File(root,"${p.id}-${provider.name}.json")
    fun cached(p: Place,provider: Provider): WeatherBundle? = runCatching { WeatherBundle.from(JSONObject(file(p,provider).readText())).let {w->w.copy(place=p,alertStatus="cached",alerts=w.alerts.filter {it.expires==null || it.expires>java.time.Instant.now().epochSecond})} }.getOrNull()
    suspend fun load(p: Place,provider: Provider): WeatherBundle = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {coroutineScope {
        if(provider in setOf(Provider.NMC,Provider.JMA_DIRECT,Provider.HKO,Provider.BRIGHTSKY,Provider.NWS)) require(provider.supports(p)) {"REGION_UNSUPPORTED"}
        val aq=async { optional { airQuality(p) } }
        val warnings=async { optional { officialAlerts(p) } }
        val supplement=async {if(provider!=Provider.AUTO && prefs.getBoolean("supplementMissing",true)) optional {OpenMeteo.parse(http.json(OpenMeteo.url(p,Provider.AUTO),timeoutMillis=4500),p,Provider.AUTO)} else null}
        val weather=when {
            provider==Provider.NMC -> NmcProvider.load(http,p)
            provider==Provider.JMA_DIRECT -> JmaProvider(JSONObject(assets.open("jma-stations.json").bufferedReader().use {it.readText()}),JSONObject(assets.open("jma-week.json").bufferedReader().use {it.readText()})).load(http,p)
            provider==Provider.BRIGHTSKY -> {
                val start=java.time.LocalDate.now(java.time.ZoneId.of(p.zone))
                BrightSkyProvider.parse(http.json("https://api.brightsky.dev/weather?lat=${p.latitude}&lon=${p.longitude}&date=$start&last_date=${start.plusDays(9)}"),p)
            }
            provider.viaOpenMeteo -> {
                val response=try {http.json(OpenMeteo.url(p,provider))} catch(e: java.io.IOException) {if(provider!=Provider.AUTO && e.message.orEmpty().startsWith("HTTP 400")) http.json(OpenMeteo.url(p,provider,extended=false)) else throw e}
                OpenMeteo.parse(response,p,provider)
            }
            provider==Provider.MET -> {
                require(prefs.getString("contact","").orEmpty().contains('@') || prefs.getString("contact","").orEmpty().startsWith("https://")) { "MET_CONTACT_REQUIRED" }
                // MET timestamps are UTC and contain no IANA zone. Resolve the coordinate's
                // zone from geographic metadata, independently of optional weather supplementation.
                val zone=optional {http.json("https://api.open-meteo.com/v1/forecast?latitude=${p.latitude}&longitude=${p.longitude}&timezone=auto&forecast_days=1",2_592_000_000,timeoutMillis=3000).getString("timezone")} ?: p.zone
                RegionalProviders.met(http.json("https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=${String.format(Locale.US,"%.4f",p.latitude)}&lon=${String.format(Locale.US,"%.4f",p.longitude)}",userAgentContact=prefs.getString("contact","").orEmpty()),p.copy(zone=zone)).copy(place=p)
            }
            provider==Provider.HKO -> {
                val language=when(sourceLanguage()) { "zh-CN","zh-Hans" -> "sc"; "zh-TW","zh-HK","zh-Hant" -> "tc"; else -> "en" }
                val daily=async { http.json("https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=fnd&lang=$language") }
                RegionalProviders.hko(http.json("https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=rhrread&lang=$language"),daily.await(),p)
            }
            else -> {
                val points=http.json("https://api.weather.gov/points/${String.format(Locale.US,"%.4f,%.4f",p.latitude,p.longitude)}").getJSONObject("properties")
                val zone=points.optString("timeZone",p.zone)
                val base=RegionalProviders.nwsHourly(http.json(points.getString("forecastHourly")))
                val grid=optional {http.json(points.getString("forecastGridData"),timeoutMillis=4500)}
                val hours=if(grid!=null) RegionalProviders.nwsGrid(grid,base) else base
                require(hours.isNotEmpty()) { "No forecast data" }
                WeatherBundle(p,provider,hours.first(),hours,aggregateDays(hours,zone),zone=zone)
            }
        }
        val w=warnings.await()
        val model=supplement.await()
        val enriched=if(model!=null) supplementWeather(weather,model) else weather
        val air=aq.await()
        val result=enriched.copy(air=air?.first,airHourly=air?.second.orEmpty(),daily=enriched.daily.map {d->
            if(d.sunrise!=null && d.sunset!=null) d else runCatching {val a=astronomy(p.copy(zone=enriched.zone),d.time);d.copy(sunrise=d.sunrise ?: a.sunRise,sunset=d.sunset ?: a.sunSet)}.getOrDefault(d)
        },alerts=(weather.alerts+w?.alerts.orEmpty()).filter {it.expires==null || it.expires>java.time.Instant.now().epochSecond}.distinctBy {it.id},alertsChecked=weather.alertsChecked || w!=null,alertStatus=w?.status ?: if(weather.alertsChecked) "regional" else "unavailable",alertsCheckedAt=w?.checked)
        currentCoroutineContext().ensureActive()
        writeCacheAtomically(file(p,provider),result.json().toString())
        result
    }}
    private suspend fun airQuality(p: Place): Pair<Reading,List<Reading>>? {
        val fields="us_aqi,european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,uv_index,grass_pollen,birch_pollen,alder_pollen,ragweed_pollen,mugwort_pollen,olive_pollen"
        val j=http.json("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${p.latitude}&longitude=${p.longitude}&current=$fields&hourly=$fields&forecast_days=5&timeformat=unixtime",timeoutMillis=4500)
        val c=j.getJSONObject("current")
        val v=fields.split(',').mapNotNull { k -> c.number(k)?.let { k to it } }.toMap()
        return v.takeIf { it.isNotEmpty() }?.let { Reading(c.getLong("time"),it) to j.optJSONObject("hourly")?.let(OpenMeteo::series).orEmpty() }
    }
    suspend fun officialAlerts(p: Place): AlertResult = kotlinx.coroutines.withTimeoutOrNull(15_000) {
        val regional=kotlinx.coroutines.withTimeoutOrNull(6500) {optional {regionalAlerts(p)}}
        if(regional!=null) AlertResult(regional,"regional")
        else globalAlerts(http,p,sourceLanguage())
    } ?: AlertResult(emptyList(),"unavailable")

    private fun sourceLanguage(): String =
        if(android.os.Build.VERSION.SDK_INT>=33) context.resources.configuration.locales[0].toLanguageTag()
        else prefs.getString("language","").orEmpty().ifBlank {Locale.getDefault().toLanguageTag()}
    private suspend fun regionalAlerts(p: Place): List<WeatherAlert>? = when(regionalAlertProvider(p)) {
        Provider.JMA_DIRECT -> {
            val source=JmaProvider(JSONObject(assets.open("jma-stations.json").bufferedReader().use {it.readText()}),JSONObject(assets.open("jma-week.json").bufferedReader().use {it.readText()}))
            val r=source.resolve(p)
            jmaAlerts(http.json("https://www.jma.go.jp/bosai/warning/data/r8/${r.office}.json",300_000,timeoutMillis=4500).optJSONArray("items")?.objects().orEmpty(),r.area,r.office)
        }
        Provider.NMC -> NmcProvider.load(http,p,withForecast=false).alerts
        Provider.BRIGHTSKY -> http.json("https://api.brightsky.dev/alerts?lat=${p.latitude}&lon=${p.longitude}",300_000).optJSONArray("alerts")?.objects().orEmpty().filter {a ->
            a.optString("status")=="actual" && a.optString("response_type")!="allclear" && (a.optString("expires").takeUnless {it.isBlank() || it=="null"}?.let {timestamp(it)>java.time.Instant.now().epochSecond} ?: true)
        }.map {a -> WeatherAlert(a.optString("alert_id",a.optString("id")),a.optString("headline_en"),listOf(a.optString("description_en"),a.optString("instruction_en")).filter {it.isNotBlank() && it!="null"}.joinToString("\n\n"),"DWD / Bright Sky",a.optString("expires").takeUnless {it.isBlank() || it=="null"}?.let(::timestamp),severity=a.optString("severity")) }

        Provider.HKO -> {
            val j=http.json("https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=warningInfo&lang=en",300_000)
            j.optJSONArray("details")?.objects().orEmpty().map { a -> WeatherAlert(a.optString("warningStatementCode"),a.optString("warningStatementCode"),a.optJSONArray("contents")?.let { arr -> (0 until arr.length()).joinToString("\n") { arr.optString(it) } }.orEmpty(),"Hong Kong Observatory", a.optString("expireTime").takeIf { it.isNotBlank() }?.let { runCatching { timestamp(it) }.getOrNull() },issued=a.optString("updateTime").ifBlank {a.optString("issueTime")}.takeIf {it.isNotBlank()}?.let {runCatching {timestamp(it)}.getOrNull()},severity=if(a.optString("subtype") in setOf("TC8NE","TC8SE","TC8NW","TC8SW","TC9","TC10","WRAINB")) "Severe" else "") }
        }
        Provider.NWS -> RegionalProviders.nwsAlerts(http.json("https://api.weather.gov/alerts/active?point=${p.latitude},${p.longitude}",300_000))
        else -> null
    }
    suspend fun search(query: String,language: String): List<Place> = coroutineScope {
        val coords=query.split(',').mapNotNull { it.trim().toDoubleOrNull() }
        if(coords.size==2 && coords[0] in -90.0..90.0 && coords[1] in -180.0..180.0) return@coroutineScope listOf(Place(query,coords[0],coords[1],zone="UTC"))
        val q=URLEncoder.encode(query,"UTF-8")
        val lang=language.substringBefore('-').ifBlank {"en"}
        val photonLang=if(lang in setOf("en","de","fr")) "&lang=$lang" else ""
        // Search geography independently of the selected forecast provider. Both requests are bounded.
        val osm=async {optional {photonPlaces(http.json("https://photon.komoot.io/api/?q=$q&limit=15$photonLang&layer=city&layer=district&layer=locality&layer=county&layer=state",86_400_000,timeoutMillis=5000),if(photonLang.isBlank()) "" else language)}}
        val meteo=async {optional {openMeteoPlaces(http.json("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=15&language=$lang&format=json",86_400_000,timeoutMillis=5000),language)}}
        val a=osm.await();val b=meteo.await()
        if(a==null && b==null) throw java.io.IOException("SEARCH_UNAVAILABLE")
        distinctPlaces(if(lang in setOf("zh","ja")) b.orEmpty()+a.orEmpty() else a.orEmpty()+b.orEmpty()).take(25)
    }
    suspend fun localizedPlace(p: Place,language: String): Place {
        if(p.nameVersion>=4 && p.nameLanguage==language) return p
        if(p.nameVersion>=4) p.names[language]?.let {return p.copy(name=it,nameLanguage=language)}
        val query=p.names.entries.firstOrNull {it.key.substringBefore('-')=="en"}?.value?.substringBefore(" · ") ?: p.name.substringBefore(" · ")
        val translated=if(language.startsWith("zh") || language.startsWith("ja")) {
            optional {openMeteoPlaces(http.json("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query,"UTF-8")}&count=10&language=${language.substringBefore('-')}&format=json",2_592_000_000,timeoutMillis=4000),language)}?.minByOrNull {distance(p.latitude,p.longitude,it.latitude,it.longitude)}?.takeIf {distance(p.latitude,p.longitude,it.latitude,it.longitude)<8}
                ?: optional {administrativeLocation(p.latitude,p.longitude,language,p.zone)}
        } else optional {administrativeLocation(p.latitude,p.longitude,language,p.zone)}
        if(translated==null) return p
        val name=placeNameScript(translated.name,language)
        return p.copy(name=name,country=p.country.ifBlank {translated.country},names=p.names+(p.nameLanguage.ifBlank {"local"} to p.name)+(language to name),nameLanguage=language,nameVersion=4)
    }

    private val reverseMutex=kotlinx.coroutines.sync.Mutex()
    private var lastReverse=0L
    suspend fun administrativeLocation(latitude: Double,longitude: Double,language: String,zone: String): Place? {
        reverseMutex.lock()
        try {
            val elapsed=(System.nanoTime()-lastReverse)/1_000_000
            if(lastReverse!=0L && elapsed<1100) kotlinx.coroutines.delay(1100-elapsed)
            lastReverse=System.nanoTime()
            val lang=language.substringBefore('-')
            val lat=String.format(Locale.US,"%.4f",latitude);val lon=String.format(Locale.US,"%.4f",longitude)
            val custom=prefs.getString("reverseGeocoderUrl","").orEmpty()
            if(custom.isNotBlank()) {
                require(custom.startsWith("https://"))
                return administrativePlace(http.json("$custom?format=geocodejson&lat=$lat&lon=$lon&zoom=14&addressdetails=1&accept-language=${URLEncoder.encode(language,"UTF-8")}",2_592_000_000,timeoutMillis=5000),latitude,longitude,zone)
            }
            val param=if(lang in setOf("en","de","fr")) "&lang=$lang" else ""
            val j=http.json("https://photon.komoot.io/reverse?lat=$lat&lon=$lon&limit=5$param",2_592_000_000,timeoutMillis=5000)
            val features=j.optJSONArray("features")?.objects().orEmpty()
            val properties=features.firstOrNull()?.optJSONObject("properties") ?: return null
            val hierarchy=listOf("district","city","county","state","country").map {properties.optString(it)}
            val named=features.firstOrNull {it.optJSONObject("properties")?.optString("osm_key") in setOf("place","boundary")}?.optJSONObject("properties")?.optString("name")
            var name=administrativeName(listOf(named)+hierarchy)
            if(name.isBlank()) return null
            // Photon public demo has en/de/fr plus local names. For other languages use the
            // platform geocoder, then genuine GeoNames translations, never romanize in the app.
            if(lang !in setOf("en","de","fr")) {
                val system=platformPlaceName(latitude,longitude,language)
                if(system!=null) name=system
                else {
                    val city=properties.optString("city").ifBlank {named.orEmpty()}
                    if(city.isNotBlank()) optional {openMeteoPlaces(http.json("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city,"UTF-8")}&count=5&language=$lang&format=json",2_592_000_000,timeoutMillis=4000),language)}?.minByOrNull {distance(latitude,longitude,it.latitude,it.longitude)}?.takeIf {distance(latitude,longitude,it.latitude,it.longitude)<40}?.let {name=it.name}
                }
            }
            return Place(name,latitude,longitude,properties.optString("countrycode").uppercase(Locale.ROOT),zone,names=mapOf(language to name),nameLanguage=language)
        } finally {reverseMutex.unlock()}
    }
    private suspend fun platformPlaceName(lat: Double,lon: Double,language: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if(!android.location.Geocoder.isPresent()) return@withContext null
        optional {
            @Suppress("DEPRECATION")
            val address=android.location.Geocoder(context,Locale.forLanguageTag(language)).getFromLocation(lat,lon,1)?.firstOrNull() ?: return@optional null
            administrativeName(listOf(address.subLocality,address.locality,address.subAdminArea,address.adminArea,address.countryName)).takeIf {it.isNotBlank()}
        }
    }
    private suspend fun <T> optional(block: suspend ()->T): T? = try { block() } catch(e: CancellationException) { throw e } catch(_: Exception) { null }
}
