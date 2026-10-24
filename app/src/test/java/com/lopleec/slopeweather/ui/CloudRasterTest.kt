package com.lopleec.slopeweather.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CloudRasterTest {
    @Test fun renderedCloudCoresKeepArrivingAfterTheFirstWave() {
        for(seed in 0..7) for(stream in createCloudStreams(Random(seed))) {
            val spans=stream.clouds.map {cloud->
                val alpha=CloudRaster(cloud.shape).alpha
                val columns=(0 until CLOUD_BITMAP_WIDTH).filter {x->
                    (0 until CLOUD_BITMAP_HEIGHT).any {y->alpha[y*CLOUD_BITMAP_WIDTH+x]>.35f}
                }
                var left=columns.first().toFloat()/CLOUD_BITMAP_WIDTH
                var right=(columns.last()+1f)/CLOUD_BITMAP_WIDTH
                if(cloud.mirrored) {val before=left;left=1f-right;right=1f-before}
                left to right
            }
            for(tick in 0..14400) {
                val time=tick*.25f
                var visible=0f
                for(slot in stream.clouds.indices) {
                    val width=stream.clouds[slot].widthScale
                    val x=stream.offsetX(slot,time,width)
                    val left=x+width*spans[slot].first;val right=x+width*spans[slot].second
                    visible+=(minOf(1f,right)-maxOf(0f,left)).coerceAtLeast(0f)
                }
                assertTrue("Only transparent cloud tails remained at $time",visible>.08f)
            }
        }
    }

    @Test fun cloudDensityFormsOneSoftMassInsteadOfSeparateBrightDots() {
        for(seed in 0..15) for(stream in createCloudStreams(Random(seed))) for(cloud in stream.clouds) {
            val pixels=CloudRaster(cloud.shape).alpha
            val width=CLOUD_BITMAP_WIDTH;val height=CLOUD_BITMAP_HEIGHT
            assertTrue("Diffuse cloud must remain visible without an opaque core",pixels.maxOrNull()!! in .45f.. .82f)
            val visible=pixels.count {it>.04f}
            val feather=pixels.count {it>.04f && it<.4f}
            assertTrue("Most of the cloud should be softly feathered",feather>visible*.55f)
            for(x in 0 until width) {
                assertEquals(0f,pixels[x],0f);assertEquals(0f,pixels[(height-1)*width+x],0f)
            }
            for(y in 0 until height) {
                assertEquals(0f,pixels[y*width],0f);assertEquals(0f,pixels[y*width+width-1],0f)
            }
            val threshold=.36f
            val seen=BooleanArray(pixels.size);val queue=IntArray(pixels.size)
            var total=0;var largest=0;var largestWidth=0
            for(start in pixels.indices) if(pixels[start]>=threshold && !seen[start]) {
                var head=0;var tail=1;queue[0]=start;seen[start]=true
                var left=width;var right=0
                while(head<tail) {
                    val at=queue[head++];val x=at%width;val y=at/width
                    left=minOf(left,x);right=maxOf(right,x)
                    fun visit(next: Int) {if(!seen[next] && pixels[next]>=threshold) {seen[next]=true;queue[tail++]=next}}
                    if(x>0) visit(at-1);if(x<width-1) visit(at+1)
                    if(y>0) visit(at-width);if(y<height-1) visit(at+width)
                }
                total+=tail
                if(tail>largest) {largest=tail;largestWidth=right-left+1}
            }
            assertTrue("Cloud broke into isolated bright spots",largest>total*.92f)
            assertTrue(largestWidth>width*.55f)
            for(y in 1 until height) for(x in 1 until width) {
                val at=y*width+x
                assertTrue(abs(pixels[at]-pixels[at-1])<.20f)
                assertTrue(abs(pixels[at]-pixels[at-width])<.20f)
            }
        }
    }
}
