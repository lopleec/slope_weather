package com.lopleec.slopeweather.ui

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.*
import kotlin.random.Random

/** Randomness is sampled per discharge, never per animation frame. */
internal class LightningField(private val random: Random=Random.Default) {
    var nextAt=2.5f+random.nextFloat()*3f
        private set
    var x=.5f
        private set
    var seed=0
        private set
    var burst=0
        private set
    private var began=Float.NEGATIVE_INFINITY
    fun pulse(time: Float): Float {
        if(time>=nextAt) {
            began=time;nextAt=time+6f+random.nextFloat()*10f
            x=.19f+random.nextFloat()*.62f;seed=random.nextInt();burst++
        }
        val age=time-began
        // Fast return stroke, a brief dark gap, then a weaker restrike along the same channel.
        fun stroke(t: Float,decay: Float): Float=if(t<0f || t>.22f) 0f else min(1f,t/.012f)*exp(-t/decay)
        return (stroke(age,.065f)+stroke(age-.24f,.045f)*.65f).coerceIn(0f,1f)
    }
}

internal data class BoltPoint(val x: Float,val y: Float)
internal data class BoltChannel(val points: List<BoltPoint>,val weight: Float)

/** Normalized, connected, irregular channels. Fine bends come from midpoint displacement. */
internal fun lightningChannels(seed: Int,originX: Float): List<BoltChannel> {
    val random=Random(seed)
    val channels=ArrayList<BoltChannel>()
    fun jagged(a: BoltPoint,b: BoltPoint,depth: Int,spread: Float): List<BoltPoint> {
        if(depth==0) return listOf(a,b)
        val middle=BoltPoint(((a.x+b.x)*.5f+(random.nextFloat()-.5f)*spread).coerceIn(.035f,.965f),(a.y+b.y)*.5f)
        return jagged(a,middle,depth-1,spread*.52f).dropLast(1)+jagged(middle,b,depth-1,spread*.52f)
    }
    val start=BoltPoint(originX,.015f+random.nextFloat()*.025f)
    val end=BoltPoint((originX+(random.nextFloat()-.5f)*.23f).coerceIn(.12f,.88f),.30f+random.nextFloat()*.09f)
    val trunk=jagged(start,end,5,.19f)
    channels+=BoltChannel(trunk,1f)
    val side=if(random.nextBoolean()) 1f else -1f
    listOf(9,16,23).forEachIndexed {index,at->
        val root=trunk[at]
        val direction=if(index==1) -side else side
        val tip=BoltPoint((root.x+direction*(.08f+random.nextFloat()*.10f)).coerceIn(.03f,.97f),root.y+.055f+random.nextFloat()*.07f)
        val branch=jagged(root,tip,4,.045f)
        channels+=BoltChannel(branch,.48f-index*.07f)
        if(index<2) {
            val fork=branch[8+random.nextInt(4)]
            val forkEnd=BoltPoint((fork.x+direction*(.035f+random.nextFloat()*.04f)).coerceIn(.025f,.975f),fork.y+.035f+random.nextFloat()*.025f)
            channels+=BoltChannel(jagged(fork,forkEnd,3,.018f),.20f)
        }
    }
    return channels
}

/** Paths, tapered segment widths and the cloud glow shader are built only once per discharge. */
private class LightningPainter(private val density: Float) {
    private data class Stroke(val path: android.graphics.Path,val width: Float,val alpha: Float)
    private val strokes=ArrayList<Stroke>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val glow=Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowX=0f
    private var glowY=0f
    private var radius=0f
    fun prepare(seed: Int,x: Float,size: Size) {
        strokes.clear()
        lightningChannels(seed,x).forEach {channel->
            // Overlapping short paths taper towards each channel's tip without tiny gaps.
            val last=channel.points.lastIndex
            for(i in 0 until last) {
                val a=channel.points[i];val b=channel.points[i+1]
                val fraction=i.toFloat()/last
                strokes+=Stroke(android.graphics.Path().apply {moveTo(a.x*size.width,a.y*size.height);lineTo(b.x*size.width,b.y*size.height)},
                    density*channel.weight*(1.45f-.85f*fraction),(.98f-.35f*fraction)*sqrt(channel.weight))
            }
        }
        glowX=x*size.width;glowY=size.height*.10f;radius=size.width*.52f
        glow.shader=RadialGradient(glowX,glowY,radius,intArrayOf(0xB3DDE8FF.toInt(),0x32CCDFFF,0x00CCDFFF),floatArrayOf(0f,.35f,1f),Shader.TileMode.CLAMP)
    }
    fun draw(canvas: android.graphics.Canvas,pulse: Float) {
        glow.alpha=(pulse*95).toInt()
        canvas.drawCircle(glowX,glowY,radius,glow)
        paint.style=Paint.Style.STROKE
        // Soft outer halo, narrower luminous channel, and a white core. No sharp white zigzag icon.
        for(layer in 0..2) {
            paint.color=when(layer) {0->0xFFB7CEFF.toInt();1->0xFFD7E6FF.toInt();else->0xFFF6F8FF.toInt()}
            for(stroke in strokes) {
                paint.strokeWidth=stroke.width+when(layer) {0->7f*density;1->2.3f*density;else->0f}
                paint.alpha=(255*pulse*stroke.alpha*when(layer) {0->.045f;1->.18f;else->.92f}).toInt()
                canvas.drawPath(stroke.path,paint)
            }
        }
    }
}

/** Lightning for the actual provider's thunderstorm condition. */
@Composable internal fun WeatherLightning(scene: WeatherScene,code: Int?) {
    if(!com.lopleec.slopeweather.data.WeatherKind.from(code).storm || !scene.moving.value) return
    val flash=remember(scene) {LightningField()}
    val density=androidx.compose.ui.platform.LocalDensity.current.density
    val painter=remember(density) {LightningPainter(density)}
    var drawnBurst=remember {0}
    var drawnSize=remember {Size.Zero}
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val pulse=flash.pulse(scene.frame.longValue/1_000_000_000f)
        if(pulse>0f) {
            if(drawnBurst!=flash.burst || drawnSize!=size) {
                drawnBurst=flash.burst;drawnSize=size;painter.prepare(flash.seed,flash.x,size)
            }
            drawRect(Color(0xFFE1E9FF).copy(alpha=.055f*pulse))
            drawIntoCanvas {painter.draw(it.nativeCanvas,pulse)}
        }
    }
}
