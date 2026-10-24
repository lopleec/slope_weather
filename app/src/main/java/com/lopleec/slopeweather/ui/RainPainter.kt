package com.lopleec.slopeweather.ui

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sqrt

/** Fading tails and denser heads, batched without per-drop draw calls or allocations. */
internal class RainPainter(private val density: Float,tint: Int,surfaceTint: Int,private val drizzle: Boolean,private val strength: Float) {
    private val trails=FloatArray(RainField.MAX_DROPS*4)
    private val middles=FloatArray(RainField.MAX_DROPS*4)
    private val heads=FloatArray(RainField.MAX_DROPS*4)
    private val trailPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=tint;alpha=22;strokeWidth=.85f*density;strokeCap=Paint.Cap.ROUND}
    private val middlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=tint;alpha=25;strokeWidth=density;strokeCap=Paint.Cap.ROUND}
    private val headPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=tint;alpha=35;strokeWidth=1.15f*density;strokeCap=Paint.Cap.ROUND}
    private val icePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=tint;alpha=80}
    private val impactPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=surfaceTint;strokeWidth=.55f*density}
    private val sprayPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=tint;strokeWidth=.8f*density;strokeCap=Paint.Cap.ROUND}

    fun draw(canvas: Canvas,field: RainField,time: Float,toolbar: Float) {
        var count=0
        for(i in 0 until field.dropCount) {
            val drop=field.drops[i]
            if(drop.y<toolbar) continue
            // tick() has already recycled every drop that crossed the first roof.
            if(drop.ice) {
                canvas.drawCircle(drop.x,drop.y,drop.radius,icePaint)
            } else {
                val depth=(drop.radius/density).coerceIn(.7f,1.3f)
                val length=(if(drizzle) 7f else 14f+strength*8f)*density*depth
                val slopee=drop.drift/drop.speed
                trails[count]=drop.x-slopee*length;trails[count+1]=drop.y-length
                trails[count+2]=drop.x;trails[count+3]=drop.y
                middles[count]=drop.x-slopee*length*.65f;middles[count+1]=drop.y-length*.65f
                middles[count+2]=drop.x;middles[count+3]=drop.y
                heads[count]=drop.x-slopee*length*.35f;heads[count+1]=drop.y-length*.35f
                heads[count+2]=drop.x;heads[count+3]=drop.y
                count+=4
            }
        }
        if(count>0) {canvas.drawLines(trails,0,count,trailPaint);canvas.drawLines(middles,0,count,middlePaint);canvas.drawLines(heads,0,count,headPaint)}
        for(channel in field.channels) {
            val surface=channel.surface ?: continue
            if(!surface.visible || !surface.contactVisible) continue
            for(impact in channel.impacts) {
                if(!impact.active) continue
                val progress=((time-impact.born)/RainField.IMPACT_LIFETIME).coerceIn(0f,1f)
                val x=surface.left+surface.width*impact.fraction
                val y=surface.rimY(x)
                val radius=(1.5f+progress*(2-progress)*7f)*density
                val fade=1-progress
                impactPaint.alpha=(fade*sqrt(fade)*136).toInt()
                sprayPaint.alpha=(fade*sqrt(fade)*176).toInt()
                impactPaint.style=Paint.Style.FILL
                if(impact.ice) {
                    val flight=(progress/.7f).coerceAtMost(1f)
                    canvas.drawCircle(x+radius*.6f,y-4*flight*(1-flight)*3*density,density,sprayPaint)
                }
                else {
                    // The upper half lies over the sky, while the lower half lies on the card.
                    // Using onSurface for both halves hides pale spray above a dark card in daylight.
                    impactPaint.style=Paint.Style.STROKE
                    sprayPaint.style=Paint.Style.STROKE
                    canvas.drawArc(x-radius,y-density,x+radius,y+density,0f,180f,false,impactPaint)
                    canvas.drawArc(x-radius,y-density,x+radius,y+density,180f,180f,false,sprayPaint)
                    sprayPaint.style=Paint.Style.FILL
                    for(direction in -1..1) {
                        val flight=progress/(if(direction==0) .66f else .82f)
                        if(flight>=1f) continue
                        val lift=4*flight*(1-flight)
                        val spread=if(direction==0) .6f else direction*8f
                        val rise=if(direction==0) 9f else 6.5f
                        val px=x+spread*flight*density
                        val py=y-lift*rise*density
                        val tail=(1-flight)*1.3f*density
                        canvas.drawLine(px,py+tail,px,py,sprayPaint)
                        canvas.drawCircle(px,py,(1.05f-flight*.4f)*density,sprayPaint)
                    }
                }
            }
        }
    }
}
