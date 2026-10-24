package com.lopleec.slopeweather.ui

import kotlin.math.min
import kotlin.math.exp
import kotlin.random.Random

internal const val CLOUD_TEXTURE_WIDTH=448
internal const val CLOUD_TEXTURE_HEIGHT=240
internal const val CLOUD_BLUR_PADDING=36f
internal const val CLOUD_BITMAP_WIDTH=224
internal const val CLOUD_BITMAP_HEIGHT=120
internal data class CloudLobe(val x: Float,val y: Float,val radiusX: Float,val radiusY: Float)
internal data class CloudBody(val left: Float,val top: Float,val right: Float,val bottom: Float,val bend: Float)
internal data class CloudShape(val body: CloudBody,val lobes: List<CloudLobe>,val noiseSeed: Int)

/** Overlapping, uneven masses provide a shared footprint for a diffuse cloud veil. */
internal fun createCloudShape(random: Random): CloudShape {
    fun between(low: Float,high: Float)=low+random.nextFloat()*(high-low)
    val body=CloudBody(between(53f,59f),between(116f,123f),between(389f,395f),between(158f,168f),between(-7f,7f))
    val centers=if(random.nextBoolean()) listOf(108f,175f,250f,324f) else listOf(101f,154f,214f,276f,337f)
    val lobes=centers.mapIndexed {index,center->
        val x=center+between(-12f,12f)
        val y=if(index==0 || index==centers.lastIndex) between(107f,126f) else between(94f,121f)
        val rx=between(57f,82f).coerceAtMost(min(x-48f,CLOUD_TEXTURE_WIDTH-48f-x))
        val ry=between(40f,61f).coerceAtMost(min(y-48f,182f-y)).coerceAtLeast(body.top+12f-y)
        CloudLobe(x,y,rx,ry)
    }
    return CloudShape(body,lobes,random.nextInt())
}

private fun smooth(low: Float,high: Float,value: Float): Float {
    val t=((value-low)/(high-low)).coerceIn(0f,1f)
    return t*t*(3f-2f*t)
}

/** Smooth value noise breaks up the cloud density without grain or circular bright spots. */
private class CloudNoise(seed: Int,private val cell: Float) {
    private val columns=(CLOUD_TEXTURE_WIDTH/cell).toInt()+2
    private val rows=(CLOUD_TEXTURE_HEIGHT/cell).toInt()+2
    private val values=Random(seed).let {random->FloatArray(columns*rows) {random.nextFloat()}}
    fun at(x: Float,y: Float): Float {
        val gx=x/cell;val gy=y/cell
        val ix=gx.toInt().coerceIn(0,columns-2);val iy=gy.toInt().coerceIn(0,rows-2)
        val u=smooth(0f,1f,gx-ix);val v=smooth(0f,1f,gy-iy)
        val top=values[iy*columns+ix]*(1-u)+values[iy*columns+ix+1]*u
        val bottom=values[(iy+1)*columns+ix]*(1-u)+values[(iy+1)*columns+ix+1]*u
        return top*(1-v)+bottom*v
    }
}

/** Static, feathered density: no coverage threshold or solid interior contour. */
internal class CloudRaster(shape: CloudShape) {
    val alpha=FloatArray(CLOUD_BITMAP_WIDTH*CLOUD_BITMAP_HEIGHT)
    init {
        val b=shape.body
        val masses=shape.lobes+CloudLobe((b.left+b.right)*.5f,(b.top+b.bottom)*.5f,(b.right-b.left)*.54f,(b.bottom-b.top)*.9f)
        // Separable Gaussian tables avoid exponential work in the pixel loop.
        val xs=masses.map {lobe->FloatArray(CLOUD_BITMAP_WIDTH) {i->
            val dx=((i+.5f)*2-lobe.x)/lobe.radiusX;exp(-.95f*dx*dx)
        }}
        val ys=masses.map {lobe->FloatArray(CLOUD_BITMAP_HEIGHT) {i->
            val dy=((i+.5f)*2-lobe.y)/lobe.radiusY;exp(-.95f*dy*dy)
        }}
        val broad=CloudNoise(shape.noiseSeed,79f)
        val detail=CloudNoise(shape.noiseSeed xor 0x436C6F75,43f)
        for(row in 0 until CLOUD_BITMAP_HEIGHT) for(column in 0 until CLOUD_BITMAP_WIDTH) {
            val x=(column+.5f)*2;val y=(row+.5f)*2
            var density=0f
            for(i in masses.indices) density+=xs[i][column]*ys[i][row]*if(i==masses.lastIndex) .12f else 1f
            val variation=broad.at(x,y)*.75f+detail.at(x,y)*.25f
            val coverage=1f-exp(-density*(.48f+variation*.16f))
            val edge=minOf(x,CLOUD_TEXTURE_WIDTH-x,y,CLOUD_TEXTURE_HEIGHT-y)
            alpha[row*CLOUD_BITMAP_WIDTH+column]=coverage*(.92f+.08f*variation)*smooth(8f,44f,edge)
        }
    }
}
