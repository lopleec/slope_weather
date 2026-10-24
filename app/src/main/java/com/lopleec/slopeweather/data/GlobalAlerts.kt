package com.lopleec.slopeweather.data

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.time.Instant

data class AlertResult(val alerts: List<WeatherAlert>,val status: String,val checked: Long=Instant.now().epochSecond)

/** A broad forecast bounding box cannot establish national warning coverage for bare coordinates. */
fun regionalAlertProvider(p: Place): Provider? {
    if(p.country.isBlank()) return null
    return when {
        Provider.JMA_DIRECT.supports(p)->Provider.JMA_DIRECT
        Provider.NMC.supports(p) && !Provider.HKO.supports(p)->Provider.NMC
        p.country=="DE"->Provider.BRIGHTSKY
        Provider.HKO.supports(p)->Provider.HKO
        Provider.NWS.supports(p)->Provider.NWS
        else->null
    }
}

/** Same point-intersection query used by WMO SWIC. No country whitelist. */
fun wmoPointUrl(p: Place): String {
    val filter="""<Filter xmlns="http://www.opengis.net/ogc" xmlns:gml="http://www.opengis.net/gml"><And><Intersects><PropertyName>wkb_geometry</PropertyName><gml:Point><gml:coordinates>${p.longitude},${p.latitude}</gml:coordinates></gml:Point></Intersects><PropertyIsNotEqualTo><PropertyName>mem</PropertyName><Literal>0</Literal></PropertyIsNotEqualTo></And></Filter>"""
    val params=mapOf("service" to "WFS","version" to "1.0.0","request" to "GetFeature","typeName" to "local_postgis:postgis_geojsons","outputFormat" to "application/json","propertyName" to "event,areadesc,sent,mem,capurl,s,rlink","filter" to filter,"sortBy" to "s,sent")
    return "https://severeweather.wmo.int/f/ows/slope.json?"+params.entries.joinToString("&") {URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}
}
fun parseCap(xml: String,language: String,url: String,now: Long=Instant.now().epochSecond): WeatherAlert? {
    val doc=Jsoup.parse(xml,"",Parser.xmlParser())
    fun org.jsoup.nodes.Element.localName()=tagName().substringAfter(':')
    val alert=doc.getAllElements().firstOrNull {it.localName()=="alert"} ?: return null
    fun field(key: String)=alert.children().firstOrNull {it.localName()==key}?.text().orEmpty()
    if(field("status")!="Actual" || field("scope")!="Public" || field("msgType") in setOf("Cancel","Ack","Error")) return null
    val infos=alert.children().filter {it.localName()=="info"}
    fun org.jsoup.nodes.Element.textOf(k: String)=children().firstOrNull {it.localName()==k}?.text().orEmpty()
    val info=infos.firstOrNull {it.textOf("language").startsWith(language.substringBefore('-'),true)} ?: infos.firstOrNull {it.textOf("language").startsWith("en",true)} ?: infos.firstOrNull() ?: return null
    val expires=info.textOf("expires").takeIf {it.isNotBlank()}?.let {runCatching {timestamp(it)}.getOrNull()}
    if(expires!=null && expires<=now) return null
    val title=info.textOf("headline").ifBlank {info.textOf("event")}
    if(title.isBlank()) return null
    val text=listOf(info.textOf("description"),info.textOf("instruction")).filter {it.isNotBlank()}.distinct().joinToString("\n\n")
    return WeatherAlert(field("identifier"),title,text,info.textOf("senderName").ifBlank {field("sender")}+" · WMO SWIC",expires,info.textOf("web").takeIf {it.startsWith("https://")} ?: url,field("sent").takeIf {it.isNotBlank()}?.let {runCatching {timestamp(it)}.getOrNull()},info.textOf("severity"))
}
suspend fun globalAlerts(http: WeatherHttp,p: Place,language: String): AlertResult {
    val rows=http.json(wmoPointUrl(p),300_000,timeoutMillis=6000).getJSONArray("features").objects()
    var complete=true
    val alerts=rows.mapNotNull {row->
        val path=row.getJSONObject("properties").optString("capurl")
        if(path.contains("..") || !path.matches(Regex("[A-Za-z0-9_./-]+\\.xml"))) {complete=false;return@mapNotNull null}
        val url="https://severeweather.wmo.int/v2/cap-alerts/$path"
        try {parseCap(http.text(url,300_000,timeoutMillis=4000),language,url)} catch(e: kotlinx.coroutines.CancellationException) {throw e} catch(_: Exception) {complete=false;null}
    }.distinctBy {it.id}
    // A successful empty WMO query does not prove that every national feed is working or covered.
    return AlertResult(alerts,if(complete) "wmo" else "partial")
}
