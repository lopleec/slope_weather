package com.lopleec.slopeweather.data

// WMO codes remain intact. These cover conditions supplied by regional services
// that have no equivalent in the forecast API's WMO subset.
object ExtraWeatherCode {
    const val SLEET=1000
    const val HAIL=1001
    const val DUST=1002
    const val HAZE=1003
    const val SMOKE=1004
    const val WIND=1005
    const val HOT=1006
    const val COLD=1007
    const val DRY=1008
    const val HUMID=1009
    const val WARM=1010
    const val COOL=1011
    const val THUNDER_SNOW=1012
    const val THUNDER_SLEET=1013
}

enum class WeatherKind {
    CLEAR, FAIR, PARTLY_CLOUDY, OVERCAST, FOG, HAZE, DUST, SMOKE,
    DRIZZLE, RAIN, FREEZING_RAIN, SNOW, SLEET, HAIL, THUNDER, THUNDER_HAIL, THUNDER_SNOW, THUNDER_SLEET,
    WIND, HOT, COLD, DRY, HUMID, WARM, COOL, UNKNOWN;

    val wet get()=when(this) {DRIZZLE,RAIN,FREEZING_RAIN,SLEET,THUNDER,THUNDER_HAIL,THUNDER_SLEET->true;else->false}
    val frozen get()=when(this) {SNOW,SLEET,HAIL,THUNDER_HAIL,THUNDER_SNOW,THUNDER_SLEET->true;else->false}
    val storm get()=when(this) {THUNDER,THUNDER_HAIL,THUNDER_SNOW,THUNDER_SLEET->true;else->false}

    companion object {
        fun from(code: Int?): WeatherKind=when(code) {
            0->CLEAR;1->FAIR;2->PARTLY_CLOUDY;3->OVERCAST
            45,48->FOG
            51,53,55->DRIZZLE
            56,57,66,67->FREEZING_RAIN
            61,63,65,80,81,82->RAIN
            71,73,75,77,85,86->SNOW
            95->THUNDER;96,99->THUNDER_HAIL
            ExtraWeatherCode.SLEET->SLEET;ExtraWeatherCode.HAIL->HAIL
            ExtraWeatherCode.DUST->DUST;ExtraWeatherCode.HAZE->HAZE
            ExtraWeatherCode.SMOKE->SMOKE;ExtraWeatherCode.WIND->WIND
            ExtraWeatherCode.HOT->HOT;ExtraWeatherCode.COLD->COLD
            ExtraWeatherCode.DRY->DRY;ExtraWeatherCode.HUMID->HUMID
            ExtraWeatherCode.WARM->WARM;ExtraWeatherCode.COOL->COOL
            ExtraWeatherCode.THUNDER_SNOW->THUNDER_SNOW;ExtraWeatherCode.THUNDER_SLEET->THUNDER_SLEET
            else->UNKNOWN
        }
    }
}

fun precipitationStrength(code: Int?): Float=when(code) {
    51,56,61,66,71,77,80,85->.35f
    55,57,65,67,75,82,86,99->1f
    else->.65f
}

fun weatherSeverity(code: Int): Int=when(WeatherKind.from(code)) {
    WeatherKind.THUNDER_HAIL->12;WeatherKind.THUNDER,WeatherKind.THUNDER_SNOW,WeatherKind.THUNDER_SLEET->11
    WeatherKind.HAIL,WeatherKind.FREEZING_RAIN->10;WeatherKind.SLEET,WeatherKind.SNOW->9
    WeatherKind.RAIN->8;WeatherKind.DRIZZLE->7
    WeatherKind.DUST,WeatherKind.SMOKE->6;WeatherKind.FOG,WeatherKind.HAZE->5
    WeatherKind.WIND,WeatherKind.HOT,WeatherKind.COLD->4;WeatherKind.OVERCAST->3
    WeatherKind.PARTLY_CLOUDY->2;WeatherKind.FAIR->1;else->0
}
