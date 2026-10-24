package com.lopleec.slopeweather.ui

import com.lopleec.slopeweather.data.WeatherKind
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

class RainFieldAllocationTest {
    @Test fun steadyFrameLoopDoesNotAllocateParticlesOrTemporaryCollections() {
        val bean=ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return
        if(!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled=true
        val surfaces=List(8) {i->RainSurface(floatArrayOf(18f,0f,0f,18f)).apply {
            left=16f;top=100f+i*95f;width=368f;height=80f;visible=true
        }}
        val field=RainField(WeatherKind.RAIN,1f)
        field.setViewport(400f,900f,1f);field.setSurfaces(surfaces,1,0f)
        repeat(30_000) {field.tick(it/30f)}
        val thread=Thread.currentThread().threadId()
        bean.getThreadAllocatedBytes(thread)
        val bytesBefore=bean.getThreadAllocatedBytes(thread)
        val start=System.nanoTime()
        repeat(100_000) {field.tick((30_000+it)/30f)}
        val nanos=System.nanoTime()-start
        val bytes=bean.getThreadAllocatedBytes(thread)-bytesBefore
        println("RAIN_POOL_BENCH frames=100000 nsPerFrame=${nanos/100000.0} bytesPerFrame=${bytes/100000.0}")
        assertTrue("The steady particle loop should not create garbage each frame: $bytes bytes",bytes<1_600_000)
    }
}
