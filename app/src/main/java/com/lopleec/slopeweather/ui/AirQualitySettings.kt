package com.lopleec.slopeweather.ui

import androidx.compose.runtime.*
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.AqiStandard

internal val LocalAqiStandard=staticCompositionLocalOf {AqiStandard.US}
@Composable internal fun aqiStandardName(standard: AqiStandard)=s(when(standard) {AqiStandard.US->R.string.us_aqi;AqiStandard.EU->R.string.eu_aqi;AqiStandard.CN->R.string.cn_aqi})
