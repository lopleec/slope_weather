package com.lopleec.slopeweather.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

val DarkScheme=darkColorScheme(
    background=Color(0xFF0F0F0F), surface=Color(0xFF0F0F0F),
    surfaceDim=Color(0xFF0F0F0F),surfaceBright=Color(0xFF393939),
    surfaceContainerLowest=Color.Black,surfaceContainerLow=Color(0xFF191919),
    surfaceContainer=Color(0xFF1C1C1C),surfaceContainerHigh=Color(0xFF222222),surfaceContainerHighest=Color(0xFF303030),
    onBackground=Color(0xFFF2F2F2),onSurface=Color(0xFFF2F2F2),
    surfaceVariant=Color(0xFF444444),onSurfaceVariant=Color(0xFFC2C2C2),
    outline=Color(0xFF8F8F8F),outlineVariant=Color(0xFF3B3B3B),surfaceTint=Color(0xFFF2F2F2),
    inverseSurface=Color(0xFFE6E6E6),inverseOnSurface=Color(0xFF303030)
)
val LightScheme=lightColorScheme(
    background=Color(0xFFF8F8F8),surface=Color(0xFFF8F8F8),
    surfaceDim=Color(0xFFDADADA),surfaceBright=Color.White,
    surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFF7F7F7),
    surfaceContainer=Color(0xFFF2F2F2),surfaceContainerHigh=Color(0xFFEAEAEA),surfaceContainerHighest=Color(0xFFE4E4E4),
    onBackground=Color(0xFF1B1B1B),onSurface=Color(0xFF1B1B1B),
    surfaceVariant=Color(0xFFE5E5E5),onSurfaceVariant=Color(0xFF555555),
    outline=Color(0xFF747474),outlineVariant=Color(0xFFCCCCCC),surfaceTint=Color(0xFF1B1B1B),
    inverseSurface=Color(0xFF303030),inverseOnSurface=Color(0xFFF2F2F2)
)
/** Dynamic accents belong to controls; page and card surfaces keep the reference's neutral palette. */
internal fun appColorScheme(dark: Boolean,accents: ColorScheme): ColorScheme {
    val neutral=if(dark) DarkScheme else LightScheme
    return accents.copy(background=neutral.background,onBackground=neutral.onBackground,
        surface=neutral.surface,onSurface=neutral.onSurface,surfaceVariant=neutral.surfaceVariant,onSurfaceVariant=neutral.onSurfaceVariant,
        surfaceDim=neutral.surfaceDim,surfaceBright=neutral.surfaceBright,surfaceTint=neutral.surfaceTint,
        surfaceContainerLowest=neutral.surfaceContainerLowest,surfaceContainerLow=neutral.surfaceContainerLow,
        surfaceContainer=neutral.surfaceContainer,surfaceContainerHigh=neutral.surfaceContainerHigh,surfaceContainerHighest=neutral.surfaceContainerHighest,
        outline=neutral.outline,outlineVariant=neutral.outlineVariant,inverseSurface=neutral.inverseSurface,inverseOnSurface=neutral.inverseOnSurface)
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable internal fun SlopeTheme(mode: String,dynamic: Boolean,weatherBackdrop: SkyPalette?=null,content: @Composable ()->Unit) {
    val dark=when(mode) {"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val context=LocalContext.current
    val accents=if(dynamic && android.os.Build.VERSION.SDK_INT>=31) {
        if(dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if(dark) darkColorScheme() else lightColorScheme()
    val colors=appColorScheme(dark,accents)
    SideEffect {
        (context as? android.app.Activity)?.let {
            val controller=androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView)
            controller.isAppearanceLightStatusBars=weatherBackdrop?.darkForeground ?: !dark
            controller.isAppearanceLightNavigationBars=weatherBackdrop?.darkForeground ?: !dark
            it.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.background.toArgb()))
            if(android.os.Build.VERSION.SDK_INT>=29) it.window.isNavigationBarContrastEnforced=false
            @Suppress("DEPRECATION")
            it.window.navigationBarColor=android.graphics.Color.TRANSPARENT
        }
    }
    MaterialExpressiveTheme(colorScheme=colors,motionScheme=MotionScheme.expressive(),content=content)
}
@Composable fun s(id: Int,vararg args: Any): String=androidx.compose.ui.res.stringResource(id,*args)
