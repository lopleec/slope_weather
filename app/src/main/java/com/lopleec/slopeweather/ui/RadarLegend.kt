package com.lopleec.slopeweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lopleec.slopeweather.R

/** Matches the selected overlay: RainViewer Universal Blue dBZ, JMA rainfall rate, NMC image legend. */
@Composable internal fun RadarLegend(source: String,modifier: Modifier=Modifier) {
    var expanded by remember(source) {mutableStateOf(false)}
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    val colors=if(source=="jma") listOf(0xFFA0D2FF,0xFF218CFF,0xFF0041FF,0xFFFAF500,0xFFFF9900,0xFFFF2800,0xFFB40068) else listOf(0xFF88DDEE,0xFF00A3E0,0xFF005588,0xFFFFEE00,0xFFFFAA00,0xFFC10000,0xFFFF77FF)
    val labels=if(source=="jma") listOf("1","5","10","20","30","50","80+") else listOf("15","20","30","35","40","50","60+")
    Surface(onClick={expanded=!expanded;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)},modifier=modifier,shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(source=="imerg") Text("NASA GPM IMERG · mm/h",style=MaterialTheme.typography.labelMedium)
            else if(source=="nmc") Text(s(R.string.nmc_legend),style=MaterialTheme.typography.labelLarge)
            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(s(R.string.radar_light),style=MaterialTheme.typography.labelMedium)
                Row(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50))) {colors.forEach {Box(Modifier.weight(1f).fillMaxHeight().background(Color(it)))}}
                Text(s(R.string.radar_heavy),style=MaterialTheme.typography.labelMedium)
                Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,Modifier.size(18.dp))
            }
            if(expanded && source!="nmc" && source!="imerg") {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {labels.forEach {Text(it,style=MaterialTheme.typography.labelSmall)}}
                Text(if(source=="jma") "JMA · mm/h" else "RainViewer · dBZ · Universal Blue",style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
