package com.lopleec.slopeweather.data

import org.json.JSONObject
import java.util.Locale

/** Convert the script of an existing Chinese name; this does not invent or romanize a name. */
fun placeNameScript(name: String,language: String): String {
    if(!language.startsWith("zh") || android.os.Build.VERSION.SDK_INT<29) return name
    val traditional=language.contains("TW",true) || language.contains("HK",true) || language.contains("Hant",true)
    return runCatching {android.icu.text.Transliterator.getInstance(if(traditional) "Simplified-Traditional" else "Traditional-Simplified").transliterate(name)}.getOrDefault(name)
}
fun localizedHierarchy(primary: String,parents: List<String>,language: String): String {
    val name=placeNameScript(primary,language)
    fun eastAsian(s: String)=s.any {it in '\u3400'..'\u9fff' || it in '\u3040'..'\u30ff'}
    val expectsLocal=(language.startsWith("zh") || language.startsWith("ja")) && eastAsian(name)
    // Gazetteers sometimes translate the city but leave the same county name in English.
    // Omit untranslated hierarchy components rather than inventing their translations.
    return administrativeName(listOf(name)+parents.map {placeNameScript(it,language)}.filter { !expectsLocal || eastAsian(it) || it.none(Char::isLetter) })
}

/** OSM administrative places only; never a POI, shop, or transliteration invented by the app. */
fun photonPlaces(json: JSONObject,language: String): List<Place> = json.optJSONArray("features")?.objects().orEmpty().groupBy {f->
    val p=f.optJSONObject("properties")
    val extent=p?.optJSONArray("extent")
    // OSM can index one administrative boundary as both a state and a city.
    // Merge only an identical name, country and full boundary; keep smaller districts.
    if(extent?.length()==4) listOf(p.optString("name"),p.optString("countrycode"),extent.toString()).joinToString("|") else f.toString()
}.values.map {group->group.firstOrNull {it.optJSONObject("properties")?.optString("type")=="city"} ?: group.first()}.mapNotNull {f->
    val p=f.optJSONObject("properties") ?: return@mapNotNull null
    if(p.optString("osm_key") !in setOf("place","boundary")) return@mapNotNull null
    val c=f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
    val name=localizedHierarchy(p.optString("name"),listOf("district","city","county","state","country").map {p.optString(it)},language)
    if(name.isBlank()) return@mapNotNull null
    val country=p.optString("countrycode").uppercase(Locale.ROOT)
    Place(name,c.getDouble(1),c.getDouble(0),country,zone="UTC",names=mapOf(language to name),nameLanguage=language)
}.distinctBy {it.id}

fun openMeteoPlaces(json: JSONObject,language: String): List<Place> = json.optJSONArray("results")?.objects().orEmpty().map {p->
    val name=localizedHierarchy(p.optString("name"),listOf("admin2","admin1","country").map {p.optString(it)},language)
    Place(name,p.getDouble("latitude"),p.getDouble("longitude"),p.optString("country_code"),p.optString("timezone","UTC"),names=mapOf(language to name),nameLanguage=language)
}

/** Collapse duplicated city results from two gazetteers, while retaining distinct same-name areas. */
fun distinctPlaces(places: List<Place>): List<Place> = buildList {
    fun primary(p: Place)=p.name.substringBefore(" · ").filter {it.isLetterOrDigit()}.lowercase(Locale.ROOT)
    for(p in places) if(none {it.id==p.id || (primary(it)==primary(p) && distance(it.latitude,it.longitude,p.latitude,p.longitude)<5)}) add(p)
}
