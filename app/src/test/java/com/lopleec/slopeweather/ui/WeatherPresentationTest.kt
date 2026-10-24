package com.lopleec.slopeweather.ui

import androidx.compose.ui.graphics.luminance
import com.lopleec.slopeweather.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WeatherPresentationTest {
    @Test fun officialSeverityIsPreservedAndAnAdvisoryDoesNotBecomeSevereFromItsTitle() {
        val advisory=WeatherAlert("id","Typhoon information","Official text","Source",severity="Minor")
        assertFalse(advisory.critical)
        val warning=advisory.copy(severity="Severe")
        assertTrue(warning.critical);assertEquals(warning,WeatherAlert.from(warning.json()))
        assertFalse(advisory.copy(severity="").critical)
    }
    @Test fun heavyRainActuallyHasMoreAndFasterDrops() {
        fun field(strength: Float)=RainField(WeatherKind.RAIN,strength).apply {setViewport(400f,900f,1f);tick(0f)}
        val light=field(rainIntensity(61,.4));val heavy=field(rainIntensity(65,8.0))
        assertTrue(heavy.dropCount>light.dropCount)
        assertTrue(heavy.drops.take(heavy.dropCount).map {it.speed}.average()>light.drops.take(light.dropCount).map {it.speed}.average())
        assertTrue(weatherSky(61,SolarLight(1f)).first().luminance()<weatherSky(3,SolarLight(1f)).first().luminance())
    }
    @Test fun lightningHasQuietIntervalsAndChangesPositionBetweenBursts() {
        val flash=LightningField(Random(42))
        assertEquals(0f,flash.pulse(0f),0f)
        val first=flash.nextAt;flash.pulse(first);val x=flash.x
        assertTrue(flash.pulse(first+.06f)>0f);assertEquals(0f,flash.pulse(first+1f),0f)
        val next=flash.nextAt;assertTrue(next-first>=6f)
        flash.pulse(next);assertNotEquals(x,flash.x);assertEquals(2,flash.burst)
    }
    @Test fun lightningBranchesAreConnectedIrregularAndStayInTheSky() {
        val bolts=(0..30).map {lightningChannels(it,.5f)}
        assertTrue(bolts.all {it.size>=5})
        for(channels in bolts) {
            val trunk=channels.first()
            assertEquals(1f,trunk.weight,0f)
            assertTrue(trunk.points.last().y in .30f.. .39f)
            for(branch in channels.drop(1)) {
                assertTrue(channels.any {it!==branch && branch.points.first() in it.points})
                assertTrue(branch.weight<trunk.weight)
            }
            assertTrue(channels.flatMap {it.points}.all {it.x in 0f..1f && it.y in 0f.. .5f})
            assertTrue(trunk.points.zipWithNext().all {(a,b)->b.y>a.y})
            val bends=trunk.points.zipWithNext().map {(a,b)->b.x-a.x}
            assertTrue(bends.any {it>0f} && bends.any {it<0f})
        }
        assertNotEquals(bolts[0],bolts[1])
    }

}
