package com.lopleec.slopeweather.ui


import android.animation.ValueAnimator
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lopleec.slopeweather.data.WeatherKind
import com.lopleec.slopeweather.data.precipitationStrength
import kotlin.math.*

internal class WeatherRim(var position: Offset,val size: Size,val profile: FloatArray,val shape: Shape?=null) {
    val rainSurface=RainSurface(profile)
    fun topAt(x: Float): Float? {
        rainSurface.left=position.x;rainSurface.top=position.y;rainSurface.width=size.width
        return rainSurface.rimY(x).takeUnless {it.isNaN()}
    }
    fun relativeTo(origin: Offset)=WeatherRim(position-origin,size,profile)
}

@Stable internal class WeatherScene {
    val frame=mutableLongStateOf(0L)
    val cloudFrame=mutableLongStateOf(0L)
    val cloudStreams=createCloudStreams()
    val moving=mutableStateOf(ValueAnimator.areAnimatorsEnabled())
    val rims=LinkedHashMap<Any,WeatherRim>()
    private val visible=ArrayList<RainSurface>(RainField.MAX_SURFACES)
    var geometryVersion=0L
    private var collectedVersion=-1L
    private var viewport=Size.Zero
    var origin=Offset.Zero
        set(value) {if(field!=value) {field=value;geometryVersion++}}
    var scrolling=false
    var reduced=false
    var timeline=0L

    fun surfaces(size: Size,toolbar: Float): List<RainSurface> {
        if(collectedVersion==geometryVersion && viewport==size) return visible
        collectedVersion=geometryVersion;viewport=size;visible.clear()
        for(rim in rims.values) {
            val surface=rim.rainSurface
            surface.left=rim.position.x-origin.x;surface.top=rim.position.y-origin.y
            surface.width=rim.size.width;surface.height=rim.size.height
            surface.visible=surface.top+surface.height>toolbar && surface.top<size.height && surface.left<size.width && surface.left+surface.width>0f
            surface.contactVisible=surface.visible && surface.top>=toolbar
            if(surface.visible) visible.add(surface)
        }
        return visible
    }
}
internal val LocalWeatherScene=staticCompositionLocalOf<WeatherScene?> {null}

/** Observe Material's shape without changing its rendering or input handling. */
@Composable internal fun Modifier.weatherSurface(shape: Shape): Modifier {
    val scene=LocalWeatherScene.current ?: return this
    val density=LocalDensity.current
    val direction=LocalLayoutDirection.current
    val key=remember {Any()}
    DisposableEffect(scene,key) {onDispose {
        scene.rims.remove(key)?.rainSurface?.visible=false
        scene.geometryVersion++
    }}
    return onGloballyPositioned {coordinates->
        val size=Size(coordinates.size.width.toFloat(),coordinates.size.height.toFloat())
        val position=coordinates.positionInWindow()
        val old=scene.rims[key]
        if(old?.size==size && old.shape==shape) {
            if(old.position!=position) {old.position=position;scene.geometryVersion++}
        } else {
            val path=when(val outline=shape.createOutline(size,direction,density)) {
                is Outline.Rectangle->Path().apply {addRect(outline.rect)}
                is Outline.Rounded->Path().apply {addRoundRect(outline.roundRect)}
                is Outline.Generic->outline.path
            }
            val region=Region().apply {setPath(path.asAndroidPath(),Region(0,0,size.width.toInt(),size.height.toInt()))}
            val profile=FloatArray(49) {i->
                val x=(size.width*i/48).toInt().coerceIn(0,(size.width-1).toInt().coerceAtLeast(0))
                var y=0
                while(y<size.height.toInt()/2 && !region.contains(x,y)) y+=2
                y.toFloat()
            }
            old?.rainSurface?.visible=false
            scene.rims[key]=WeatherRim(position,size,profile,shape)
            scene.geometryVersion++
        }
    }
}

@Composable internal fun rememberWeatherScene(placeId: String,code: Int?,scrolling: Boolean,active: Boolean): WeatherScene {
    val scene=remember(placeId) {WeatherScene()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val context=LocalContext.current
    SideEffect {scene.scrolling=scrolling}
    LaunchedEffect(scene,code,lifecycle,active) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            scene.moving.value=active && ValueAnimator.areAnimatorsEnabled()
            if(scene.moving.value && WeatherKind.from(code)!=WeatherKind.UNKNOWN) {
                val powerSave=context.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode
                val pacer=WeatherFramePacer()
                var start=0L
                while(true) withFrameNanos {now->
                    if(start==0L) start=now-scene.timeline
                    scene.timeline=now-start
                    val flags=pacer.onVsync(now,scene.scrolling,powerSave)
                    scene.reduced=pacer.reduced
                    if(flags and WeatherFramePacer.RAIN!=0) scene.frame.longValue=scene.timeline
                    if(flags and WeatherFramePacer.CLOUD!=0) scene.cloudFrame.longValue=scene.timeline
                }
            }
        }
    }
    return scene
}

internal fun weatherSky(code: Int?,light: SolarLight): List<Color> {
    val kind=WeatherKind.from(code)
    val day=when {
        kind.storm->listOf(Color(0xFF8FA5B9),Color(0xFF9CAFC0),Color(0xFFB5C3D0))
        kind.wet || kind.frozen->listOf(Color(0xFFB3C5D4),Color(0xFFC0CDD8),Color(0xFFD0D9E3))
        kind==WeatherKind.OVERCAST || kind==WeatherKind.FOG->listOf(Color(0xFFDCEFF6),Color(0xFFDDE7ED),Color(0xFFC6CFDF))
        kind==WeatherKind.UNKNOWN->listOf(Color(0xFFD4E8F4),Color(0xFFD5E9F1),Color(0xFFC8DAE8))
        else->listOf(Color(0xFF63B6FA),Color(0xFF7DE2F7),Color(0xFFDDF2FC))
    }
    val night=when {
        kind.storm->listOf(Color(0xFF1C2857),Color(0xFF354775),Color(0xFF697488))
        kind in setOf(WeatherKind.CLEAR,WeatherKind.FAIR)->listOf(Color(0xFF20245F),Color(0xFF343D8D),Color(0xFF626ABB))
        else->listOf(Color(0xFF242B65),Color(0xFF3E486E),Color(0xFF738197))
    }
    return day.indices.map {i->
        val base=lerp(night[i],day[i],light.daylight)
        val morning=lerp(base,listOf(Color(0xFF99B3ED),Color(0xFFACCFF3),Color(0xFFF1DFCF))[i],light.dawn*listOf(.12f,.18f,.32f)[i])
        lerp(morning,listOf(Color(0xFF626FA6),Color(0xFF9AACC6),Color(0xFFE8CFD1))[i],light.dusk*listOf(.12f,.2f,.36f)[i])
    }
}

/** A small cached cloud texture replaces per-frame, full-screen radial shader creation. */
private fun cloudSprite(shape: CloudShape,tint: Color,day: Boolean): ImageBitmap {
    val density=CloudRaster(shape)
    val shade=if(day) Color(0xFF718AA2) else Color(0xFF687EB1)
    val colors=IntArray(CLOUD_BITMAP_WIDTH*CLOUD_BITMAP_HEIGHT) {index->
        val y=(index/CLOUD_BITMAP_WIDTH+.5f)/CLOUD_BITMAP_HEIGHT
        val lower=((y-(if(day) .28f else .39f))/.40f).coerceIn(0f,1f)
        val shadeAmount=if(day) lower*lower*(3-2*lower)*.65f else lower*lower*.18f
        val color=lerp(tint,shade,shadeAmount)
        color.copy(alpha=density.alpha[index]*(if(day) .90f else .80f)).toArgb()
    }
    return android.graphics.Bitmap.createBitmap(colors,CLOUD_BITMAP_WIDTH,CLOUD_BITMAP_HEIGHT,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable internal fun WeatherClouds(scene: WeatherScene,code: Int?,sky: SkyPalette) {
    val kind=WeatherKind.from(code)
    if(kind==WeatherKind.UNKNOWN) return
    val mist=kind==WeatherKind.FOG || kind==WeatherKind.HAZE || kind==WeatherKind.SMOKE || kind==WeatherKind.DUST
    val tint=if(kind==WeatherKind.DUST) Color(0xFFE7DDCA) else if(sky.day) Color.White else Color(0xFFDCE4FF)
    val sprites=remember(scene,tint,sky.day) {scene.cloudStreams.map {stream->stream.clouds.map {cloudSprite(it.shape,tint,sky.day)}}}
    val count=when(kind) {WeatherKind.CLEAR,WeatherKind.HOT,WeatherKind.DRY,WeatherKind.FAIR,WeatherKind.WARM->1;else->CLOUD_STREAM_COUNT}
    val clear=kind in setOf(WeatherKind.CLEAR,WeatherKind.FAIR)
    val opacity=if(sky.day) {if(clear) .56f else .64f} else {if(clear) .20f else .28f}
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val density=LocalDensity.current
        val width=with(density) {maxWidth.toPx()}
        val height=with(density) {maxHeight.toPx()}
        val mistScale=if(mist) 1.3f else 1f
        repeat(count) {i->
          val stream=scene.cloudStreams[i]
          repeat(CLOUDS_PER_STREAM) {slot->
            val cloud=stream.clouds[slot]
            val cloudWidth=cloud.widthScale*mistScale
            // The bitmap is static. Only RenderNode translation changes, with subpixel motion.
            Image(sprites[i][slot],contentDescription=null,modifier=Modifier.size(maxWidth,maxWidth*CLOUD_TEXTURE_HEIGHT/CLOUD_TEXTURE_WIDTH).graphicsLayer {
                val seconds=scene.cloudFrame.longValue/1_000_000_000f
                scaleX=if(cloud.mirrored) -cloudWidth else cloudWidth
                scaleY=cloud.heightScale*mistScale;transformOrigin=TransformOrigin(0f,0f)
                val x=stream.offsetX(slot,seconds,cloudWidth,kind==WeatherKind.WIND)
                translationX=(x+if(cloud.mirrored) cloudWidth else 0f)*width
                translationY=height*cloud.offsetY(seconds)
                alpha=if(x>=1f || x+cloudWidth<=0f) 0f else opacity*cloud.opacity
                compositingStrategy=CompositingStrategy.ModulateAlpha
            },filterQuality=FilterQuality.Low)
          }
        }
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        if(!sky.day && (kind==WeatherKind.CLEAR || kind==WeatherKind.FAIR || kind==WeatherKind.PARTLY_CLOUDY)) {
            val seconds=scene.cloudFrame.longValue/1_000_000_000f
            repeat(27) {i->
                val x=((i*0.618034f+.07f)%1f)*size.width;val y=((i*0.381966f+.03f)%.72f)*size.height
                val alpha=(.15f+.07f*sin(seconds*.18f+i))*if(kind==WeatherKind.PARTLY_CLOUDY) .5f else 1f
                drawCircle(Color.White.copy(alpha=alpha),if(i%9==0) 1.4f.dp.toPx() else .65f.dp.toPx(),Offset(x,y))
                if(i%9==0) {drawLine(Color.White.copy(alpha=alpha*.5f),Offset(x-4.dp.toPx(),y),Offset(x+4.dp.toPx(),y),.6f.dp.toPx());drawLine(Color.White.copy(alpha=alpha*.5f),Offset(x,y-4.dp.toPx()),Offset(x,y+4.dp.toPx()),.6f.dp.toPx())}
            }
        }
    }
    WeatherLightning(scene,code)
}

@Composable internal fun WeatherPrecipitation(scene: WeatherScene,code: Int?,rain: Double?=null,darkSky: Boolean=true) {
    val kind=WeatherKind.from(code)
    val strength=rainIntensity(code,rain)
    val field=remember(scene,strength) {RainField(kind,strength)}
    val density=LocalDensity.current.density
    val rainTint=if(darkSky) Color(0xFFCDDAEE) else Color(0xFF526D86)
    val surfaceTint=lerp(MaterialTheme.colorScheme.onSurface,rainTint,.15f)
    val painter=remember(density,rainTint,surfaceTint,kind,strength) {RainPainter(density,rainTint.toArgb(),surfaceTint.toArgb(),kind==WeatherKind.DRIZZLE,strength)}
    if((!kind.wet && !kind.frozen) || !scene.moving.value) return
    Canvas(Modifier.fillMaxSize().graphicsLayer().onGloballyPositioned {scene.origin=it.positionInWindow()}) {
        val time=scene.frame.longValue/1_000_000_000f
        val toolbar=64*density
        field.setViewport(size.width,size.height,density)
        field.setSurfaces(scene.surfaces(size,toolbar),scene.geometryVersion,time)
        field.tick(time,scene.reduced)
        clipRect(top=toolbar) {drawIntoCanvas {painter.draw(it.nativeCanvas,field,time,toolbar)}}
    }
}
