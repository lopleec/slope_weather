package com.lopleec.slopeweather.ui

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal const val CLOUD_STREAM_COUNT=2
internal const val CLOUDS_PER_STREAM=2

internal data class CloudLayer(
    val shape: CloudShape,val height: Float,val widthScale: Float,val heightScale: Float,
    val opacity: Float,val mirrored: Boolean,
    val bobPhase: Float,val bobSpeed: Float,val bobDistance: Float
) {
    fun offsetY(seconds: Float): Float=height+sin(seconds*bobSpeed+bobPhase)*bobDistance
}

/** Alternating cloud bodies: the successor is already entering before its predecessor leaves. */
internal data class CloudStream(val clouds: List<CloudLayer>,val phase: Float,val spacing: Float,val speed: Float) {
    fun offsetX(slot: Int,seconds: Float,width: Float,windy: Boolean=false): Float {
        val center=(phase+seconds*speed*(if(windy) 1.6f else 1f)+slot*spacing)%(spacing*CLOUDS_PER_STREAM)-1f
        return center-width*.5f
    }
}

/** Sample only on entry. Animation and weather refresh cannot re-roll the cloud field. */
internal fun createCloudStreams(random: Random=Random.Default): List<CloudStream> {
    fun between(low: Float,high: Float)=low+random.nextFloat()*(high-low)
    return List(CLOUD_STREAM_COUNT) {stream->
        val spacing=between(1.46f,1.56f)
        CloudStream(List(CLOUDS_PER_STREAM) {slot->
            val width=between(1.02f,1.32f)
            CloudLayer(
                shape=createCloudShape(random),
                height=if(stream==0) {if(slot==0) between(-.04f,.06f) else between(.17f,.23f)}
                    else {if(slot==0) between(.28f,.40f) else between(.52f,.64f)},
                widthScale=width,heightScale=width*between(.85f,1.05f),
                opacity=between(.82f,1.08f),mirrored=random.nextBoolean(),
                bobPhase=between(0f,2f*PI.toFloat()),bobSpeed=between(.065f,.12f),bobDistance=between(.003f,.007f)
            )
        },phase=between(0f,spacing*CLOUDS_PER_STREAM),spacing=spacing,speed=between(.0175f,.0275f))
    }
}
