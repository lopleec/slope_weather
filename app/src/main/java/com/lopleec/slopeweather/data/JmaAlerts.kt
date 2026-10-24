package com.lopleec.slopeweather.data

import org.json.JSONObject

/** Consume only the latest report for this forecast area; cancelled and empty kinds never become alerts. */
fun jmaAlerts(reports: List<JSONObject>,area: String,office: String): List<WeatherAlert> {
    val report=reports.maxByOrNull {it.optString("reportDatetime")} ?: return emptyList()
    val active=report.optJSONObject("warning")?.optJSONArray("class10Items")?.objects()?.firstOrNull {it.optString("areaCode")==area}?.optJSONArray("kinds")?.objects().orEmpty().filter {it.optString("code").isNotBlank() && it.optString("status") !in listOf("解除","発表警報・注意報はなし","")}
    if(active.isEmpty()) return emptyList()
    val headline=report.optString("headlineText").trim()
    val title=if(active.any {it.optString("code").toIntOrNull()?.let {n->n in 1..9}==true}) "気象警報" else "気象警報・注意報"
    val codes=active.mapNotNull {it.optString("code").toIntOrNull()}
    val severity=when {codes.any {it>=30}->"Extreme";codes.any {it in 1..9}->"Severe";else->"Minor"}
    return listOf(WeatherAlert("JMA-$area-${report.optString("reportDatetime")}",title,headline,"気象庁 JMA",url="https://www.jma.go.jp/bosai/warning/#area_type=offices&area_code=$office",issued=runCatching {timestamp(report.getString("reportDatetime"))}.getOrNull(),severity=severity))
}
