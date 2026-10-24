package com.lopleec.slopeweather.ui

import com.lopleec.slopeweather.data.WeatherKind
import org.junit.Assert.*
import org.junit.Test

class RainFieldTest {
    private fun surface(y: Float, x: Float=16f, width: Float=368f, height: Float=130f) = RainSurface(floatArrayOf(24f,0f,0f,0f,24f)).apply {
        left=x;top=y;this.width=width;this.height=height;visible=true
    }
    private fun field(surfaces: List<RainSurface>)=RainField(WeatherKind.RAIN,.65f).apply {
        setViewport(400f,900f,1f);setSurfaces(surfaces,1,0f)
    }
    private fun impacted(field: RainField, surface: RainSurface)=field.channels.any {it.surface===surface && it.impacts.any {impact->impact.active}}

    @Test fun firstCardSheltersAllCardsAndGapsBelowIt() {
        val cards=listOf(surface(280f),surface(418f),surface(556f),surface(694f))
        val field=field(cards)
        var topWasHit=false
        repeat(600) {frame->
            field.tick(frame/30f)
            if(impacted(field,cards[0])) topWasHit=true
            for(card in cards.drop(1)) assertFalse("Sheltered cards must not create water",impacted(field,card))
        }
        assertTrue("Rain must actually reach the top card",topWasHit)
        assertFalse(field.backgroundVisible(200f,330f))
        assertFalse("The vertical gap is still in the top card's rain shadow",field.backgroundVisible(200f,415f))
        assertTrue("Rain outside the card's width can continue",field.backgroundVisible(5f,800f))
    }

    @Test fun contactsRequireARealDropAndUseItsActualLandingPoint() {
        val card=surface(100f)
        val field=field(listOf(card))
        assertFalse(impacted(field,card))
        field.tick(0f)
        for(drop in field.drops) {drop.x=5f;drop.y=0f;drop.speed=0f;drop.drift=0f}
        val drop=field.drops[0]
        drop.x=200f;drop.y=70f;drop.speed=500f
        field.tick(.08f)
        assertTrue(impacted(field,card))
        val impact=field.channels.first {it.surface===card}.impacts.first {it.active}
        assertEquals(.5f,impact.fraction,.001f)
        assertTrue("The incoming drop must recycle above the scene",drop.y<=0f)
    }

    @Test fun onlyUncoveredAreasCanReceiveRainAndCoveringCancelsTheirWater() {
        val smallRoof=surface(80f,100f,200f)
        val lower=surface(400f)
        val field=field(listOf(smallRoof,lower))
        assertSame(smallRoof,field.firstSurfaceAt(200f))
        assertSame(lower,field.firstSurfaceAt(60f))
        field.tick(0f)
        for(drop in field.drops) {drop.x=5f;drop.y=0f;drop.speed=0f;drop.drift=0f}
        field.drops[0].apply {x=60f;y=380f;speed=500f}
        field.tick(.08f)
        assertTrue(impacted(field,lower))
        val channel=field.channels.first {it.surface===lower}
        val impact=channel.impacts.first {it.active}
        val before=lower.rimY(lower.left+lower.width*impact.fraction)
        lower.top-=100f;smallRoof.top-=100f
        field.setSurfaces(listOf(lower,smallRoof),2,.1f)
        assertSame(channel,field.channels.first {it.surface===lower})
        assertEquals(before-100f,lower.rimY(lower.left+lower.width*impact.fraction),.001f)
        smallRoof.left=16f;smallRoof.width=368f
        field.setSurfaces(listOf(lower,smallRoof),3,.1f)
        assertFalse("An arriving roof must hide stale splashes below it",impacted(field,lower))
    }

    @Test fun clippedRoofStillBlocksLowerCardsAndRemovedCardsLeaveNoWater() {
        val clipped=surface(20f).apply {contactVisible=false}
        val lower=surface(400f)
        val field=field(listOf(clipped,lower))
        repeat(180) {field.tick(it/30f)}
        assertFalse(impacted(field,clipped))
        assertFalse(impacted(field,lower))
        assertFalse(field.backgroundVisible(200f,380f))
        clipped.visible=false;lower.visible=false
        field.setSurfaces(emptyList(),2,6f)
        assertTrue(field.channels.all {it.surface==null && it.impacts.none {impact->impact.active}})
    }

    @Test fun fixedPoolsAndLongPausesDoNotGenerateBursts() {
        val cards=(0 until 20).map {surface(80f+it*35f,height=25f)}
        val field=field(cards)
        val drops=field.drops.copyOf()
        val impacts=field.channels.flatMap {it.impacts.toList()}
        repeat(50_000) {field.tick(it/30f,it%2==0)}
        assertTrue(field.dropCount<=RainField.MAX_DROPS)
        assertEquals(RainField.MAX_SURFACES,field.channels.count {it.surface!=null})
        field.drops.forEachIndexed {i,drop->assertSame(drops[i],drop)}
        field.channels.flatMap {it.impacts.toList()}.forEachIndexed {i,impact->assertSame(impacts[i],impact)}
        field.tick(50_000f)
        for(card in cards.drop(1)) assertFalse(impacted(field,card))
    }

    @Test fun normalSixtyHzVsyncKeepsContinuousRainMotion() {
        val pacer=WeatherFramePacer()
        var rainFrames=0
        repeat(60) {frame->
            val flags=pacer.onVsync(1_000_000_000L+frame*1_000_000_000L/60,false,false)
            if(flags and WeatherFramePacer.RAIN!=0) rainFrames++
        }
        assertEquals(60,rainFrames)
    }

    @Test fun framePacerReducesWorkDuringGesturesAndSlowFrames() {
        val pacer=WeatherFramePacer()
        repeat(60) {pacer.onVsync(1_000_000_000L+it*16_666_667L,false,false)}
        assertFalse(pacer.reduced)
        val scrolling=pacer.onVsync(2_100_000_000L,true,false)
        assertTrue(pacer.reduced)
        assertEquals(WeatherFramePacer.CLOUD,scrolling and WeatherFramePacer.CLOUD)
        repeat(4) {pacer.onVsync(2_200_000_000L+it*70_000_000L,false,false)}
        assertTrue(pacer.reduced)
        repeat(240) {pacer.onVsync(2_500_000_000L+it*16_666_667L,false,false)}
        assertFalse(pacer.reduced)
        pacer.onVsync(7_000_000_000L,false,true)
        assertTrue(pacer.reduced)
    }

    @Test fun cloudsKeepMovingAtLowerCadenceDuringScrollAndPowerSaving() {
        fun count(scrolling: Boolean,powerSave: Boolean): Int {
            val pacer=WeatherFramePacer()
            return (0 until 60).count {frame->pacer.onVsync(1_000_000_000L+frame*16_666_667L,scrolling,powerSave) and WeatherFramePacer.CLOUD!=0}
        }
        assertEquals(30,count(false,false))
        assertEquals(15,count(true,false))
        assertEquals(15,count(false,true))
    }
}
