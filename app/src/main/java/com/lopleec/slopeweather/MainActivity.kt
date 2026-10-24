package com.lopleec.slopeweather

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lopleec.slopeweather.ui.WeatherApp
import androidx.lifecycle.ViewModelProvider
import java.util.Locale

class MainActivity: ComponentActivity() {
    private val weatherModel by lazy {ViewModelProvider(this)[WeatherViewModel::class.java]}
    override fun attachBaseContext(base: Context) {
        val tag=base.getSharedPreferences("weather",MODE_PRIVATE).getString("language","").orEmpty()
        val configuration=Configuration(base.resources.configuration)
        if(android.os.Build.VERSION.SDK_INT<33 && tag.isNotBlank()) configuration.setLocale(Locale.forLanguageTag(tag))
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        setContent { WeatherApp(weatherModel) }
    }
    override fun onStart() {super.onStart();weatherModel.onForeground()}
    override fun onStop() {
        if(!isChangingConfigurations) weatherModel.onBackground()
        super.onStop()
    }
}
