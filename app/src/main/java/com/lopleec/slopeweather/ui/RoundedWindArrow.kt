package com.lopleec.slopeweather.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache

/** Meteorological direction is where wind comes FROM. North is always at the top. */
@Composable internal fun RoundedWindArrow(degrees: Double,color: Color,modifier: Modifier=Modifier) {
    Box(modifier.drawWithCache {
        val w=size.minDimension
        val path=Path().apply {
            moveTo(.43f*w,.12f*w)
            cubicTo(.46f*w,.035f*w,.54f*w,.035f*w,.57f*w,.12f*w)
            lineTo(.89f*w,.78f*w)
            cubicTo(.95f*w,.92f*w,.85f*w,.98f*w,.73f*w,.93f*w)
            lineTo(.56f*w,.87f*w)
            cubicTo(.52f*w,.85f*w,.48f*w,.85f*w,.44f*w,.87f*w)
            lineTo(.27f*w,.93f*w)
            cubicTo(.15f*w,.98f*w,.05f*w,.92f*w,.11f*w,.78f*w)
            close()
        }
        // Centre the filled silhouette, not its rectangular bounds. Keep a distinct
        // nose and recessed tail, so rotating it retains an unambiguous direction.
        val samples=path.asAndroidPath().approximate(.25f)
        var area=0f;var x=0f;var y=0f
        val count=samples.size/3
        repeat(count) {i->
            val j=(i+1)%count
            val ax=samples[i*3+1];val ay=samples[i*3+2]
            val bx=samples[j*3+1];val by=samples[j*3+2]
            val cross=ax*by-bx*ay
            area+=cross;x+=(ax+bx)*cross;y+=(ay+by)*cross
        }
        path.translate(Offset(size.width/2-x/(3*area),size.height/2-y/(3*area)))
        onDrawBehind {rotate(degrees.toFloat()) {drawPath(path,color)}}
    })
}
