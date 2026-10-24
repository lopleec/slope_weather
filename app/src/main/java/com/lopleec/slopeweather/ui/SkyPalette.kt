package com.lopleec.slopeweather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lopleec.slopeweather.data.Place
import org.shredzone.commons.suncalc.SunPosition
import java.time.Instant
import kotlin.math.abs

/** Presentation lighting calculated at the location now, independently of weather age or UI theme. */
internal data class SolarLight(val daylight: Float,val dawn: Float=0f,val dusk: Float=0f) {
    val day get()=daylight>=.5f
}
internal fun solarLight(latitude: Double,longitude: Double,time: Long): SolarLight {
    fun altitude(at: Long)=SunPosition.compute().on(Instant.ofEpochSecond(at)).at(latitude,longitude).execute().altitude
    val elevation=altitude(time)
    val progress=((elevation+6)/12).coerceIn(0.0,1.0).toFloat()
    val daylight=progress*progress*(3-2*progress)
    val twilight=(1-abs(elevation-1)/9).coerceIn(0.0,1.0).toFloat()
    val rising=altitude(time+600)>elevation
    return SolarLight(daylight,if(rising) twilight else 0f,if(rising) 0f else twilight)
}

internal data class SkyPalette(val colors: List<Color>,val day: Boolean) {
    val darkForeground get()=colors.first().luminance()>.32f
    val foreground get()=if(darkForeground) Color(0xFF142A36) else Color(0xFFF5F5FF)
    val secondary get()=foreground.copy(alpha=.78f)
}

@Composable internal fun rememberSkyPalette(place: Place?,code: Int?): SkyPalette {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val minute by produceState(Instant.now().epochSecond/60*60,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                value=Instant.now().epochSecond/60*60
                kotlinx.coroutines.delay(60_000-System.currentTimeMillis()%60_000)
            }
        }
    }
    val light=remember(place?.id,minute) {place?.let {solarLight(it.latitude,it.longitude,minute)} ?: SolarLight(.5f)}
    return key(place?.id) {
        val target=weatherSky(code,light)
        val colors=target.map {animateColorAsState(it,tween(800),label="City sky").value}
        SkyPalette(colors,light.day)
    }
}
