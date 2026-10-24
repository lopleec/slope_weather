package com.lopleec.slopeweather.ui

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lopleec.slopeweather.MainActivity
import com.lopleec.slopeweather.data.DayForecast
import com.lopleec.slopeweather.data.Place
import com.lopleec.slopeweather.data.Provider
import com.lopleec.slopeweather.data.Reading
import com.lopleec.slopeweather.data.WeatherBundle
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.json.JSONObject

/** Same scene, same cloud samples, same geometry; only the card's ink changes. */
internal class WeatherLayerIsolation(private val instrumentation: Instrumentation) {
    fun run(directory: File, staticCopy: Boolean,refreshRate: Float?=null): JSONObject = with(instrumentation) {
        val phase=mutableIntStateOf(0)
        val ready=Array(3) {CountDownLatch(1)}
        val place=Place("Paris · Île-de-France · France",48.86,2.35,"FR","Europe/Paris")
        val now=Instant.now().epochSecond
        val reading=Reading(now,mapOf("temperature_2m" to 12.0,"apparent_temperature" to 7.0),3)
        val weather=WeatherBundle(place,Provider.AUTO,reading,listOf(reading),
            listOf(DayForecast(now,14.0,10.0,3)),overview="Fixed sky and fixed layout for layer isolation.")
        val sky=SkyPalette(weatherSky(3,SolarLight(1f)),true)
        val activity=startActivitySync(Intent(targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        runOnMainSync {
            refreshRate?.let {rate->
                @Suppress("DEPRECATION")
                val display=activity.windowManager.defaultDisplay
                val current=display.mode
                val mode=display.supportedModes.first {it.physicalWidth==current.physicalWidth &&
                    it.physicalHeight==current.physicalHeight && abs(it.refreshRate-rate)<1f}
                activity.window.attributes=activity.window.attributes.apply {preferredDisplayModeId=mode.modeId}
            }
            activity.setContent {
                val currentPhase=phase.intValue
                LaunchedEffect(currentPhase) {
                    repeat(3) {withFrameNanos { }}
                    ready[currentPhase].countDown()
                }
                SlopeTheme("dark",false,weatherBackdrop=sky) {
                    val colors=MaterialTheme.colorScheme
                    val transparent=colors.copy(surface=Color.Transparent,surfaceContainerLowest=Color.Transparent,
                        surfaceContainerLow=Color.Transparent,surfaceContainer=Color.Transparent,
                        surfaceContainerHigh=Color.Transparent,surfaceContainerHighest=Color.Transparent,
                        onSurface=Color.Transparent,onSurfaceVariant=Color.Transparent,
                        errorContainer=Color.Transparent,onErrorContainer=Color.Transparent)
                    MaterialTheme(colorScheme=if(currentPhase==1) transparent else colors) {
                        Box(Modifier.fillMaxSize().background(sky.colors.first()).statusBarsPadding()) {
                            HomeScreen(place,Provider.AUTO,weather,false,null,false,"km/h",
                                listOf("alerts","overview"),{},{},{},{},{},{_,_->},rememberScrollState(),
                                effectsActive=false,sky=sky)
                            Text("Rendering isolation · fixed sky",Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
        check(ready[0].await(30,TimeUnit.SECONDS)) {"Initial frame did not draw"}
        fun screenshot(name: String): Bitmap {
            val image=checkNotNull(uiAutomation.takeScreenshot()).copy(Bitmap.Config.ARGB_8888,false)
            File(directory,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            return image
        }
        val withCards=screenshot("isolation-with-cards")
        runOnMainSync {phase.intValue=1}
        check(ready[1].await(30,TimeUnit.SECONDS)) {"Transparent cards did not draw"}
        val withoutCards=screenshot("isolation-without-cards")
        val width=withCards.width;val height=withCards.height
        val density=activity.resources.displayMetrics.density
        val center=width/2
        val top=(height/4 until height*3/4).first {withCards.getPixel(center,it)==AndroidColor.BLACK}
        val margin=(10*density).toInt()
        var changes=0;var maximum=0;var samples=0
        for(y in (top-40*density).toInt().coerceAtLeast(0)..(top+130*density).toInt().coerceAtMost(height-1)) {
            for(side in 0..1) for(i in 0 until margin) {
                val x=if(side==0) i else width-1-i
                val a=withCards.getPixel(x,y);val b=withoutCards.getPixel(x,y)
                val delta=maxOf(abs(AndroidColor.red(a)-AndroidColor.red(b)),
                    abs(AndroidColor.green(a)-AndroidColor.green(b)),abs(AndroidColor.blue(a)-AndroidColor.blue(b)))
                if(delta!=0) changes++
                maximum=maxOf(maximum,delta);samples++
            }
        }
        val sampleY=top+(10*density).toInt()
        val cardsActuallyHidden=withCards.getPixel(center,sampleY)==AndroidColor.BLACK &&
            withoutCards.getPixel(center,sampleY)!=AndroidColor.BLACK
        val report=JSONObject().put("width",width).put("height",height).put("first_card_top",top)
            .put("outside_card_samples",samples).put("outside_card_changed_pixels",changes)
            .put("outside_card_max_channel_delta",maximum).put("cards_actually_hidden",cardsActuallyHidden)
            .put("passed",cardsActuallyHidden && changes==0)
            .put("refresh_hz",activity.display?.refreshRate)
            .put("static_copy_source",if(refreshRate!=null) "isolation-120-reference.png" else "isolation-with-cards.png")
        File(directory,"isolation-results.json").writeText(report.toString(2))
        runOnMainSync {phase.intValue=2}
        check(ready[2].await(30,TimeUnit.SECONDS)) {"Restored cards did not draw"}
        if(staticCopy) {
            val image=if(refreshRate!=null) checkNotNull(BitmapFactory.decodeFile(File(directory,"isolation-120-reference.png").path))
                else withCards
            // Native ImageView, identity pixel mapping: no Compose cards, clip paths or shaders remain.
            runOnMainSync {
                val root=activity.findViewById<ViewGroup>(android.R.id.content)
                val origin=IntArray(2);root.getLocationOnScreen(origin)
                activity.setContentView(ImageView(activity).apply {
                    scaleType=ImageView.ScaleType.MATRIX
                    imageMatrix=Matrix().apply {setTranslate(-origin[0].toFloat(),-origin[1].toFloat())}
                    setImageBitmap(image)
                },ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
            }
            sendStatus(0,Bundle().apply {putString("stream","Static pixel copy ready\n")})
            Thread.sleep(45_000)
        }
        withoutCards.recycle()
        if(!staticCopy) withCards.recycle()
        report
    }
}
