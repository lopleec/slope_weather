package com.lopleec.slopeweather.data

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.net.URI
import java.util.Locale

/** A suffix such as evilrainviewer.com is not a RainViewer subdomain. */
fun rainViewerFrames(response: JSONObject): JSONObject {
    val origin=URI(response.getString("host"))
    val host=origin.host?.lowercase(Locale.ROOT).orEmpty()
    require(origin.scheme=="https" && (host=="rainviewer.com" || host.endsWith(".rainviewer.com")) &&
        origin.rawUserInfo==null && origin.rawQuery==null && origin.rawFragment==null &&
        origin.rawPath.orEmpty() in setOf("","/") && origin.port in setOf(-1,443)) {"Invalid radar origin"}
    val base=origin.toString().trimEnd('/')
    val frames=response.getJSONObject("radar").getJSONArray("past").objects().map {row->
        val path=row.getString("path")
        require(path.startsWith("/v2/radar/") && path.matches(Regex("/[A-Za-z0-9/_-]+"))) {"Invalid radar frame path"}
        JSONObject().put("time",row.getLong("time")).put("url",base+path+"/256/{z}/{x}/{y}/2/1_1.png")
    }.sortedBy {it.getLong("time")}.distinctBy {it.getLong("time")}
    require(frames.isNotEmpty()) {"No radar frames"}
    return JSONObject().put("frames",JSONArray(frames))
        .put("coverage",base+"/v2/coverage/0/256/{z}/{x}/{y}/0/0_0.png")
        .put("attribution","RainViewer").put("maxZoom",7)
}

/** Only timestamps advertised by the WMTS service are emitted; gaps are kept as gaps. */
fun nasaPrecipitationFrames(capabilities: String): JSONObject {
    val xml=Jsoup.parse(capabilities,"",Parser.xmlParser())
    val layer=xml.getAllElements().filter {it.tagName().substringAfter(':')=="Layer"}.firstOrNull {l->l.children().any {it.tagName().substringAfter(':')=="Identifier" && it.text()=="IMERG_Precipitation_Rate_30min"}} ?: error("IMERG layer unavailable")
    val dimension=layer.getAllElements().filter {it.tagName().substringAfter(':')=="Dimension"}.firstOrNull() ?: error("IMERG time metadata unavailable")
    val end=Instant.parse(dimension.getAllElements().first {it.tagName().substringAfter(':')=="Default"}.text()).epochSecond
    val start=end-86400
    val advertised=dimension.getAllElements().filter {it.tagName().substringAfter(':')=="Value"}.map {it.text().split('/')}
    val times=(start..end step 1800).filter {time->advertised.any {range->
        if(range.size!=3 || range[2]!="PT30M") false else {
            val first=Instant.parse(range[0]).epochSecond;val last=Instant.parse(range[1]).epochSecond
            time in first..last && (time-first)%1800==0L
        }
    }}
    require(times.isNotEmpty()) {"No satellite precipitation frames"}
    return JSONObject().put("frames",JSONArray(times.map {t->JSONObject().put("time",t).put("url","https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/${Instant.ofEpochSecond(t)}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png")})).put("attribution","NASA GIBS · GPM IMERG").put("maxZoom",6).put("satellite",true)
}
