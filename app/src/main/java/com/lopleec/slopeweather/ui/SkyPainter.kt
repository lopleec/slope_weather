package com.lopleec.slopeweather.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

internal interface SkyPainter {
    fun draw(canvas: Canvas,width: Float,height: Float,colors: List<Color>)
}

internal fun createSkyPainter(): SkyPainter =
    if(Build.VERSION.SDK_INT>=33) FeatheredSkyPainter() else LegacySkyPainter()

/** Sub-gray-level spatial dithering prevents long, coherent gradient bands. It never animates. */
@RequiresApi(33)
private class FeatheredSkyPainter: SkyPainter {
    private val shader=RuntimeShader("""
        uniform float height;
        layout(color) uniform float4 skyTop;
        layout(color) uniform float4 skyMiddle;
        layout(color) uniform float4 skyBottom;

        half4 main(float2 position) {
            float y=clamp(position.y/height,0.0,1.0);
            float3 color=y<0.5 ? mix(skyTop.rgb,skyMiddle.rgb,y*2.0)
                : mix(skyMiddle.rgb,skyBottom.rgb,y*2.0-1.0);
            float2 pixel=floor(position);
            float dither=fract(52.9829189*fract(dot(pixel,float2(0.06711056,0.00583715))))-0.5;
            return half4(clamp(color+dither/255.0,0.0,1.0),1.0);
        }
    """.trimIndent())
    private val paint=Paint().apply {this.shader=this@FeatheredSkyPainter.shader}

    override fun draw(canvas: Canvas,width: Float,height: Float,colors: List<Color>) {
        shader.setFloatUniform("height",height.coerceAtLeast(1f))
        shader.setColorUniform("skyTop",colors[0].toArgb())
        shader.setColorUniform("skyMiddle",colors[1].toArgb())
        shader.setColorUniform("skyBottom",colors[2].toArgb())
        canvas.drawRect(0f,0f,width,height,paint)
    }
}

private class LegacySkyPainter: SkyPainter {
    private val paint=Paint(Paint.DITHER_FLAG)
    private var previousColors: List<Color> = emptyList()
    private var previousHeight=0f

    override fun draw(canvas: Canvas,width: Float,height: Float,colors: List<Color>) {
        if(colors!=previousColors || height!=previousHeight) {
            previousColors=colors;previousHeight=height
            paint.shader=LinearGradient(0f,0f,0f,height.coerceAtLeast(1f),
                colors.map {it.toArgb()}.toIntArray(),null,Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f,0f,width,height,paint)
    }
}
