package com.lopleec.slopeweather.ui

/** Stable persisted IDs include every movable weather card, not only the metric grid. */
internal val defaultHomeCards = listOf("alerts", "overview", "minutely", "hourly", "daily", "radar") +
    listOf("air", "uv_index", "uv_index_clear_sky", "wind_speed_10m", "relative_humidity_2m", "visibility", "pressure_msl",
        "precipitation", "cloud_cover", "dew_point_2m", "wind_gusts_10m", "surface_pressure",
        "snowfall", "shortwave_radiation", "cape") + listOf("sun", "pollen", "moon")

internal fun normalizeCardOrder(saved: List<String>?): List<String> {
    val known = saved.orEmpty().map {if(it in setOf("cn_aqi","us_aqi","european_aqi")) "air" else it}.filter { it in defaultHomeCards }.distinct()
    return known + defaultHomeCards.filterNot { it in known }
}

internal fun moveCard(order: List<String>, key: String, offset: Int): List<String> {
    val from = order.indexOf(key)
    val to = from + offset
    if (from < 0 || to !in order.indices || from == to) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
