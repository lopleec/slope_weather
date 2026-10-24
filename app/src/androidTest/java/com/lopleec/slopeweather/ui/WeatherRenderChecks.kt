package com.lopleec.slopeweather.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lopleec.slopeweather.MainActivity
import com.lopleec.slopeweather.data.DayForecast
import com.lopleec.slopeweather.data.Place
import com.lopleec.slopeweather.data.Provider
import com.lopleec.slopeweather.data.Reading
import com.lopleec.slopeweather.data.WeatherBundle
import com.lopleec.slopeweather.data.WeatherKind
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.json.JSONObject

/** Exercises the real Android rasterizer; JVM tests stub Canvas and cannot catch invisible spray. */
class WeatherRenderChecks : Instrumentation() {
    private var sceneCase: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        sceneCase = arguments?.getString("scene")
        start()
    }

    override fun onStart() {
        val directory = File(targetContext.getExternalFilesDir(null), "weather-render-checks").apply { mkdirs() }
        val report = JSONObject()
        var passed = true
        for (darkCards in listOf(false, true)) {
            val name = if (darkCards) "dark-cards" else "light-cards"
            val result = splash(directory, name, darkCards)
            report.put(name, result)
            if (!result.getBoolean("passed")) passed = false
        }
        sceneCase?.let {
            try {
                if(it=="isolate" || it.startsWith("pixel-copy")) {
                    val isolation=WeatherLayerIsolation(this).run(directory,it.startsWith("pixel-copy"),
                        if(it=="pixel-copy-60") 60f else null)
                    report.put("isolation",isolation)
                    if(!isolation.getBoolean("passed")) passed=false
                } else renderScene(directory, it)
                report.put("scene", JSONObject().put("case", it).put("passed", passed))
            } catch (error: Exception) {
                passed = false
                report.put("scene", JSONObject().put("case", it).put("passed", false).put("error", error.toString()))
            }
        }
        File(directory, "results.json").writeText(report.toString(2))
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", "\n" + report.toString(2) + "\n") })
    }

    /** A labelled test-only fixture keeps collision checks independent of today's weather feed. */
    private fun renderScene(directory: File, sceneCase: String) {
        val night = sceneCase.startsWith("night")
        val darkCards = sceneCase.endsWith("dark")
        val place = Place("Paris · Île-de-France · France", 48.86, 2.35, "FR", "Europe/Paris")
        val now = Instant.now().epochSecond
        val values = mapOf("temperature_2m" to 13.0, "apparent_temperature" to 7.0, "precipitation" to .1)
        val reading = Reading(now, values, 61, !night)
        val weather = WeatherBundle(place, Provider.AUTO, reading, listOf(reading),
            listOf(DayForecast(now, 14.0, 10.0, 61, probability = 93.0)),
            minutely = List(24) { Reading(now + it * 900, mapOf("precipitation" to .1)) })
        val sky = SkyPalette(weatherSky(61, SolarLight(if (night) 0f else 1f)), !night)
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val ready = CountDownLatch(1)
        runOnMainSync {
            activity.setContent {
                LaunchedEffect(Unit) {
                    repeat(3) { withFrameNanos { } }
                    ready.countDown()
                }
                SlopeTheme(if (darkCards) "dark" else "light", false, weatherBackdrop = sky) {
                    Box(Modifier.fillMaxSize().background(sky.colors.first()).statusBarsPadding()) {
                        HomeScreen(place, Provider.AUTO, weather, false, null, false, "km/h",
                            listOf("alerts", "overview", "minutely"), {}, {}, {}, {}, {}, { _, _ -> },
                            rememberScrollState(), sky = sky)
                        Text("Rendering test · Paris light-rain fixture", Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        check(ready.await(30, TimeUnit.SECONDS)) { "Weather fixture did not become visible" }
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$sceneCase-start.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        sendStatus(0, Bundle().apply { putString("stream", "Scene ready: $sceneCase\n") })
        Thread.sleep(25_000)
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$sceneCase-end.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun splash(directory: File, name: String, darkCards: Boolean): JSONObject {
        val density = 3f
        val width = 360
        val height = 240
        val sky = Color.rgb(190, 204, 215)
        val card = if (darkCards) Color.BLACK else Color.WHITE
        val onCard = if (darkCards) Color.rgb(242, 242, 242) else Color.rgb(27, 27, 27)
        val rain = Color.rgb(82, 109, 134)
        val bitmap = Bitmap.createBitmap(width * 3, height * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(sky)
        val roof = 150 * density
        val paint = android.graphics.Paint().apply { color = card }
        canvas.drawRect(16 * density, roof, (width - 16) * density, height * density, paint)
        val surface = RainSurface(floatArrayOf(0f, 0f)).apply {
            left = 16 * density; top = roof; this.width = (width - 32) * density
            this.height = 90 * density; visible = true
        }
        val field = RainField(WeatherKind.RAIN, .35f).apply {
            setViewport(width * density, height * density, density)
            setSurfaces(listOf(surface), 1, 0f)
        }
        val channel = field.channels.first { it.surface === surface }
        channel.impacts[0].apply { active = true; born = 0f; fraction = .5f }
        RainPainter(density, rain, onCard, false, .35f).draw(canvas, field, .16f, 0f)
        var visibleSprayPixels = 0
        // Count spray well above the rim, so a one-pixel ring cannot pass this check.
        for (y in (roof - 14 * density).toInt() until (roof - 3 * density).toInt()) {
            for (x in (width * density / 2 - 16 * density).toInt()..(width * density / 2 + 16 * density).toInt()) {
                val pixel = bitmap.getPixel(x, y)
                val difference = maxOf(abs(Color.red(pixel) - Color.red(sky)),
                    abs(Color.green(pixel) - Color.green(sky)), abs(Color.blue(pixel) - Color.blue(sky)))
                if (difference >= 22) visibleSprayPixels++
            }
        }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return JSONObject().put("visible_spray_pixels", visibleSprayPixels)
            .put("minimum", 54).put("passed", visibleSprayPixels >= 54)
    }
}
