package com.lopleec.slopeweather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.lopleec.slopeweather.BuildConfig
import com.lopleec.slopeweather.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val PROJECT_REPOSITORY="https://github.com/lopleec/slope_weather"
private data class LicensePage(val title: String,val file: String,val attribution: String="")

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AboutScreen(onBack: ()->Unit) {
    val context=LocalContext.current;val chinese=LocalConfiguration.current.locales[0].language=="zh"
    val uri=LocalUriHandler.current
    var page by remember {mutableStateOf<LicensePage?>(null)}
    BackHandler {if(page!=null) page=null else onBack()}
    val file=page?.file ?: if(chinese) "about-zh.txt" else "about-en.txt"
    val text by produceState("",file) {value=withContext(Dispatchers.IO) {context.assets.open(file).bufferedReader().use {it.readText()}}}
    val components by produceState<List<LicensePage>>(emptyList()) {
        value=withContext(Dispatchers.IO) {
            val entries=JSONArray(context.assets.open("third-party-components.json").bufferedReader().use {it.readText()})
            (0 until entries.length()).map {index->
                val item=entries.getJSONObject(index);val artifacts=item.getJSONArray("artifacts")
                LicensePage(item.getString("name")+" · "+item.getString("license"),"licenses/"+item.getString("licenseFile"),
                    item.getString("name")+"\n\n"+item.getString("copyright")+"\n\n"+item.getString("projectUrl")+"\n\n"+(0 until artifacts.length()).joinToString("\n") {artifacts.getString(it)})
            }
        }
    }
    val scroll=rememberScrollState()
    LaunchedEffect(file) {scroll.scrollTo(0)}
    Scaffold(topBar={TopAppBar(title={Text(page?.title ?: s(R.string.about_data))},navigationIcon={IconButton(onClick={if(page!=null) page=null else onBack()}) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,s(R.string.close))}})}) {padding->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            if(page==null) {
                Text("Slope Weather · ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.headlineSmall)
                Text("Copyright 2026 Lopleec\nLicensed under the Apache License, Version 2.0",style=MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick={uri.openUri(PROJECT_REPOSITORY)},modifier=Modifier.fillMaxWidth()) {Text(if(chinese) "GitHub 仓库 · lopleec/slope_weather" else "GitHub · lopleec/slope_weather")}
                OutlinedButton(onClick={page=LicensePage(if(chinese) "Slope Weather · 项目许可证" else "Slope Weather · Project license","licenses/Slope-Weather-Apache-2.0.txt","Copyright 2026 Lopleec\nLicensed under the Apache License, Version 2.0\n\n$PROJECT_REPOSITORY")},modifier=Modifier.fillMaxWidth()) {Text(if(chinese) "项目 LICENSE · Apache-2.0" else "Project LICENSE · Apache-2.0")}
                Text(s(R.string.licenses),style=MaterialTheme.typography.titleLarge)
                components.forEach {component->OutlinedButton(onClick={page=component},modifier=Modifier.fillMaxWidth()) {Text(component.title)}}
                OutlinedButton(onClick={page=LicensePage(if(chinese) "运行时依赖清单" else "Runtime dependency inventory","licenses/Runtime-dependencies.txt")},modifier=Modifier.fillMaxWidth()) {Text(if(chinese) "完整依赖与版本" else "All runtime artifacts and versions")}
            }
            page?.attribution?.takeIf {it.isNotBlank()}?.let {LinkText(it)}
            LinkText(text)
        }
    }
}

@Composable private fun LinkText(text: String) {
    SelectionContainer {
        Text(buildAnnotatedString {
            var index=0
            Regex("https://[^\\s，。、；（）]+[A-Za-z0-9/]").findAll(text).forEach {match->
                append(text.substring(index,match.range.first));withLink(LinkAnnotation.Url(match.value)) {append(match.value)};index=match.range.last+1
            }
            append(text.substring(index))
        },style=MaterialTheme.typography.bodyLarge)
    }
}
