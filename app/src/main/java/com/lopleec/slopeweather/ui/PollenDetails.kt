package com.lopleec.slopeweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lopleec.slopeweather.R
import com.lopleec.slopeweather.data.*
import kotlin.math.*

internal val pollenGroups=listOf(listOf("grass_pollen"),listOf("birch_pollen","alder_pollen","olive_pollen"),listOf("ragweed_pollen","mugwort_pollen"))
internal fun pollenAmount(r: Reading?,group: Int)=pollenGroups[group].mapNotNull {r?.get(it)}.takeIf {it.isNotEmpty()}?.sum()
@Composable private fun pollenGroupName(group: Int)=s(listOf(R.string.grass,R.string.trees,R.string.weeds)[group])
@Composable private fun pollenIcon(group: Int)=listOf(Icons.Outlined.Grass,Icons.Outlined.Park,Icons.Outlined.Spa)[group]
@Composable internal fun PollenRings(w: WeatherBundle,onClick: (() -> Unit)?=null) {
    WeatherCard(onClick=onClick) {
        CardHeading(s(R.string.pollen),Icons.Outlined.LocalFlorist)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            (0..2).filter {pollenAmount(w.air,it)!=null}.forEach {group->
                val value=pollenAmount(w.air,group)!!
                val peak=(w.airHourly.mapNotNull {pollenAmount(it,group)}+value).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val track=MaterialTheme.colorScheme.surfaceContainerHighest
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f),contentAlignment=Alignment.Center) {
                        Canvas(Modifier.fillMaxSize().padding(5.dp)) {drawArc(track,135f,270f,false,style=Stroke(5.dp.toPx(),cap=StrokeCap.Round));if(value>0) drawArc(Color(0xFF43CB70),135f,(value/peak).toFloat()*270f,false,style=Stroke(5.dp.toPx(),cap=StrokeCap.Round))}
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {Icon(pollenIcon(group),null,Modifier.size(22.dp));Text(pollenGroupName(group),style=MaterialTheme.typography.labelLarge)}
                    }
                    Text(value.roundToInt().toString(),fontSize=24.sp)
                    Text(s(R.string.pollen_unit),style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
@Composable internal fun PollenDetailContent(w: WeatherBundle) {
    val available=(0..2).filter {g->pollenAmount(w.air,g)!=null || w.airHourly.any {pollenAmount(it,g)!=null}}
    if(available.isEmpty()) {Text(s(R.string.missing_data));return}
    var group by remember(w) {mutableIntStateOf(available.first())}
    var day by remember(w) {mutableStateOf(localTime(w.current.time,w.zone,"yyyy-MM-dd"))}
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {available.forEachIndexed {index,g->SegmentedButton(selected=group==g,onClick={group=g;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)},shape=SegmentedButtonDefaults.itemShape(index,available.size),icon={Icon(pollenIcon(g),null,Modifier.size(20.dp))}) {Text(pollenGroupName(g))}}}
    val days=w.airHourly.groupBy {localTime(it.time,w.zone,"yyyy-MM-dd")}.entries.filter {it.key>=localTime(w.current.time,w.zone,"yyyy-MM-dd")}.take(5)
    val max=days.flatMap {it.value}.mapNotNull {pollenAmount(it,group)}.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    if(days.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {items(days,key={it.key}) {entry->
        val amount=entry.value.mapNotNull {pollenAmount(it,group)}.maxOrNull()
        Surface(onClick={day=entry.key;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)},shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surfaceContainer,border=if(day==entry.key) androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.onSurface) else null) {
            Column(Modifier.width(62.dp).padding(vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(amount?.roundToInt()?.toString() ?: "—",style=MaterialTheme.typography.titleMedium)
                Box(Modifier.width(26.dp).height(146.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),contentAlignment=Alignment.BottomCenter) {if(amount!=null) Box(Modifier.fillMaxWidth().fillMaxHeight((amount/max).toFloat().coerceIn(0f,1f)).background(Color(0xFF43CB70),RoundedCornerShape(20.dp)))}
                Text(localTime(entry.value.first().time,w.zone,"EEE"),style=MaterialTheme.typography.labelLarge)
            }
        }
    }} else PollenRings(w)
    Text(s(R.string.pollen_relative),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    val readings=days.firstOrNull {it.key==day}?.value ?: listOfNotNull(w.air)
    WeatherCard {pollenGroups[group].forEach {key->val amount=readings.mapNotNull {it[key]}.maxOrNull();if(amount!=null) ValueRow(s(pollenKeys.first {it.first==key}.second),"${amount.roundToInt()} ${s(R.string.pollen_unit)}")}}
    Text(s(R.string.pollen_help),style=MaterialTheme.typography.bodyLarge)
}
@Composable internal fun AirDetailContent(w: WeatherBundle) {
    AirDetailCard(w)
    Text(s(R.string.aqi_help),style=MaterialTheme.typography.bodyLarge)
}
