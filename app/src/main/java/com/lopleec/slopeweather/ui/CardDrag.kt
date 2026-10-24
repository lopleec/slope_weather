package com.lopleec.slopeweather.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

internal enum class CardSize(val columns: Int,val rows: Int) {COMPACT(1,1),WIDE(2,1)}
internal fun defaultCardSize(key: String)=if(metrics.any {it.key==key} || key in setOf("air","sun","moon")) CardSize.COMPACT else CardSize.WIDE
internal fun cardRows(keys: List<String>): List<List<String>> = buildList {
    var i=0
    while(i<keys.size) {
        val key=keys[i];val width=defaultCardSize(key).columns
        if(width==1 && i+1<keys.size && defaultCardSize(keys[i+1]).columns==1) {add(listOf(key,keys[i+1]));i+=2} else {add(listOf(key));i++}
    }
}
@Stable internal class CardDragState {
    val bounds=mutableStateMapOf<String,Rect>()
    var key by mutableStateOf<String?>(null)
    var initial=Rect.Zero
    var total by mutableStateOf(Offset.Zero)
    var pointer by mutableStateOf(Offset.Zero)
    var origin=Offset.Zero
    var allowed=emptySet<String>()
    var top=0f;var bottom=0f
}
@Composable internal fun rememberCardDrag(scroll: ScrollState,onMoveTo: (String,String)->Unit): CardDragState {
    val state=remember {CardDragState()};val move by rememberUpdatedState(onMoveTo)
    val density=LocalDensity.current.density
    val haptic=LocalHapticFeedback.current
    LaunchedEffect(state.key) {
        while(state.key!=null) {
            val step=when {state.pointer.y<state.top+90*density->-13*density;state.pointer.y>state.bottom-100*density->13*density;else->0f}
            if(step!=0f) {
                scroll.scrollBy(step)
                state.bounds.entries.firstOrNull {it.key in state.allowed && it.key!=state.key && it.value.contains(state.pointer)}?.let {move(state.key!!,it.key);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
            }
            delay(24)
        }
    }
    return state
}
@Composable internal fun Modifier.dragWeatherCards(state: CardDragState,onMoveTo: (String,String)->Unit): Modifier {
    val move by rememberUpdatedState(onMoveTo);val density=LocalDensity.current.density;val haptic=LocalHapticFeedback.current
    return onGloballyPositioned {val b=it.boundsInWindow();state.origin=b.topLeft;state.top=b.top+64*density;state.bottom=b.bottom}.pointerInput(state) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            val at=state.origin+down.position
            val hit=state.bounds.entries.firstOrNull {it.key in state.allowed && it.value.contains(at)} ?: return@awaitEachGesture
            val cancelled=withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var finished=false
                while(!finished) {
                    val change=awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {it.id==down.id}
                    finished=change==null || !change.pressed || (change.position-down.position).getDistance()>viewConfiguration.touchSlop
                }
                true
            }
            if(cancelled!=null) return@awaitEachGesture
            state.initial=hit.value;state.key=hit.key;state.total=Offset.Zero;state.pointer=at
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                while(true) {
                    val change=awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {it.id==down.id} ?: break
                    change.consume()
                    state.total=change.position-down.position;state.pointer=state.origin+change.position
                    if(!change.pressed) break
                    state.bounds.entries.firstOrNull {it.key in state.allowed && it.key!=hit.key && it.value.deflate(6*density).contains(state.pointer)}?.let {move(hit.key,it.key);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
                }
                if(state.total.getDistance()>=10*density) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } finally {state.key=null}
        }
    }
}
@Composable internal fun DraggableCard(key: String,state: CardDragState,modifier: Modifier=Modifier,content: @Composable ()->Unit) {
    val dragging=state.key==key
    Box(modifier.zIndex(if(dragging) 20f else 0f).onGloballyPositioned {state.bounds[key]=it.boundsInWindow()}.graphicsLayer {
        if(dragging) {val now=state.bounds[key] ?: Rect.Zero;translationX=state.initial.left+state.total.x-now.left;translationY=state.initial.top+state.total.y-now.top;scaleX=1.025f;scaleY=1.025f;alpha=.94f}
    }) {CompositionLocalProvider(LocalCardLongPress provides null,content=content)}
}
