package com.lopleec.slopeweather.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CloudMotionTest {
    @Test fun visitsVaryAndRemainStableWhileDrawing() {
        val visits=(0..31).map {createCloudStreams(Random(it))}
        assertEquals(visits[7],createCloudStreams(Random(7)))
        assertEquals(visits.size,visits.distinct().size)
        for(index in 0 until CLOUD_STREAM_COUNT) {
            val streams=visits.map {it[index]}
            assertTrue(streams.maxOf {it.phase}-streams.minOf {it.phase}>2f)
            assertTrue(streams.maxOf {it.speed}-streams.minOf {it.speed}>.006f)
            for(slot in 0 until CLOUDS_PER_STREAM) {
                val clouds=streams.map {it.clouds[slot]}
                assertTrue(clouds.map {it.shape}.distinct().size>24)
                assertTrue(clouds.maxOf {it.widthScale}-clouds.minOf {it.widthScale}>.20f)
                for(stream in streams) {
                    val cloud=stream.clouds[slot]
                    val x=stream.offsetX(slot,11f,cloud.widthScale)
                    stream.offsetX(slot,0f,cloud.widthScale);stream.offsetX(slot,900f,cloud.widthScale)
                    assertEquals(x,stream.offsetX(slot,11f,cloud.widthScale),0f)
                }
            }
        }
    }

    private fun visibleBody(stream: CloudStream,time: Float,mist: Boolean,windy: Boolean): Float =
        stream.clouds.indices.sumOf {slot->
            val cloud=stream.clouds[slot];val body=cloud.shape.body
            val width=cloud.widthScale*if(mist) 1.3f else 1f
            val x=stream.offsetX(slot,time,width,windy)
            val left=x+width*if(cloud.mirrored) 1f-body.right/CLOUD_TEXTURE_WIDTH else body.left/CLOUD_TEXTURE_WIDTH
            val right=x+width*if(cloud.mirrored) 1f-body.left/CLOUD_TEXTURE_WIDTH else body.right/CLOUD_TEXTURE_WIDTH
            (minOf(1f,right)-maxOf(0f,left)).coerceAtLeast(0f).toDouble()
        }.toFloat()

    @Test fun successorsKeepArrivingWithoutAnEmptySkyOverManyCycles() {
        for(seed in 0..31) for(stream in createCloudStreams(Random(seed))) {
            for(mist in listOf(false,true)) for(windy in listOf(false,true)) {
                // One hour covers dozens of complete crossings, including the former first-wave gap.
                for(tick in 0..14400) {
                    val time=tick*.25f
                    assertTrue("Empty cloud stream at $time",visibleBody(stream,time,mist,windy)>.15f)
                }
            }
            assertTrue(abs(stream.clouds[0].height-stream.clouds[1].height)>.10f)
        }
    }

    @Test fun eachPooledCloudMovesSmoothlyAndOnlyRecyclesBeyondTheScreen() {
        for(seed in 0..7) for(stream in createCloudStreams(Random(seed))) for(slot in stream.clouds.indices) {
            val cloud=stream.clouds[slot]
            for(mist in listOf(false,true)) for(windy in listOf(false,true)) {
                val width=cloud.widthScale*if(mist) 1.3f else 1f
                var previous=stream.offsetX(slot,0f,width,windy);var wraps=0
                var previousY=cloud.offsetY(0f)
                for(tick in 1..12000) {
                    val seconds=tick/30f;val current=stream.offsetX(slot,seconds,width,windy)
                    if(current<previous) {
                        assertTrue(previous>1f);assertTrue(current+width<0f);wraps++
                    } else assertTrue(current-previous in .0005f.. .0016f)
                    val y=cloud.offsetY(seconds);assertTrue(abs(y-previousY)<.00004f)
                    previous=current;previousY=y
                }
                assertTrue(wraps>=2)
            }
        }
    }

    @Test fun billowsJoinAContinuousBodyWithRoomForBlurAndShadow() {
        for(seed in 0..499) for(stream in createCloudStreams(Random(seed))) for(cloud in stream.clouds) {
            val shape=cloud.shape;val b=shape.body
            val left=minOf(b.left,shape.lobes.minOf {it.x-it.radiusX})
            val right=maxOf(b.right,shape.lobes.maxOf {it.x+it.radiusX})
            val top=minOf(b.top-7f,shape.lobes.minOf {it.y-it.radiusY})
            val bottom=maxOf(b.bottom+abs(b.bend)+2f,shape.lobes.maxOf {it.y+it.radiusY})
            assertTrue(left-CLOUD_BLUR_PADDING>=10f)
            assertTrue(right+CLOUD_BLUR_PADDING<=CLOUD_TEXTURE_WIDTH-10f)
            assertTrue(top-CLOUD_BLUR_PADDING>=10f)
            assertTrue(bottom+12f+CLOUD_BLUR_PADDING<=CLOUD_TEXTURE_HEIGHT-10f)
            assertTrue((right-left)*cloud.widthScale/((bottom-top)*cloud.heightScale)>2f)
            for(lobe in shape.lobes) {
                // Each billow substantially intersects the common base instead of floating as a dot.
                assertTrue(lobe.y+lobe.radiusY>=b.top+11.99f)
                assertTrue(minOf(b.right,lobe.x+lobe.radiusX)-maxOf(b.left,lobe.x-lobe.radiusX)>30f)
            }
        }
    }
}
