package com.lopleec.slopeweather

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lopleec.slopeweather.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class WeatherViewModel(app: Application): AndroidViewModel(app) {
    val prefs=app.getSharedPreferences("weather",Context.MODE_PRIVATE)
    val repository=WeatherRepository(app)
    private val _places=MutableStateFlow(readPlaces())
    val places=_places.asStateFlow()
    private val _currentLocation=MutableStateFlow(runCatching { Place.from(org.json.JSONObject(prefs.getString("currentPlace",null) ?: "")) }.getOrNull())
    val currentLocation=_currentLocation.asStateFlow()
    private val _selected=MutableStateFlow<Place?>(_currentLocation.value)
    val selected=_selected.asStateFlow()
    private val _provider=MutableStateFlow(runCatching { Provider.valueOf(prefs.getString("provider","AUTO")!!) }.getOrDefault(Provider.AUTO))
    val provider=_provider.asStateFlow()
    private val _weather=MutableStateFlow<WeatherBundle?>(null)
    val weather=_weather.asStateFlow()
    private val _loading=MutableStateFlow(false)
    val loading=_loading.asStateFlow()
    private val _error=MutableStateFlow<String?>(null)
    val error=_error.asStateFlow()
    private val _previews=MutableStateFlow<Map<String,WeatherBundle>>(emptyMap())
    val previews=_previews.asStateFlow()
    private var requestVersion=0
    private var loadJob: Job?=null
    private var previewJob: Job?=null
    init { refresh() }
    private fun readPlaces(): List<Place> = runCatching {
        val list=JSONArray(prefs.getString("places","[]")).objects().map(Place::from).distinctBy {it.id}
        val legacy=list.firstOrNull {it.name in setOf("Current location","当前位置","目前位置","現在地")}
        if(legacy!=null && !prefs.contains("currentPlace")) prefs.edit().putString("currentPlace",legacy.json().toString()).apply()
        list.filterNot {it===legacy}
    }.getOrDefault(emptyList())
    private fun persist() { prefs.edit().putString("places",JSONArray(_places.value.map { it.json() }).toString()).putString("selected",_selected.value?.id).apply() }
    fun select(p: Place) { if(_selected.value?.id!=p.id || _selected.value?.name!=p.name) { _selected.value=p; persist(); refresh() } }
    fun add(p: Place) { if(_places.value.none { it.id==p.id }) _places.value=_places.value+p; _selected.value=p; persist(); refresh() }
    fun setCurrentLocation(p: Place,open: Boolean=true) {
        val selectedCurrent=_selected.value==null || _selected.value?.id==_currentLocation.value?.id
        _currentLocation.value=p
        prefs.edit().putString("currentPlace",p.json().toString()).putLong("locationUpdated",System.currentTimeMillis()).apply()
        if(open || selectedCurrent) {
            val renamed=_selected.value?.id==p.id && _weather.value?.place?.id==p.id
            _selected.value=p;persist()
            if(renamed) _weather.value=_weather.value?.copy(place=p) else refresh()
        }
    }
    fun remove(p: Place) {
        _places.value=_places.value.filterNot {it.id==p.id}
        _previews.value=_previews.value-p.id
        if(_selected.value?.id==p.id) {_selected.value=_currentLocation.value ?: _places.value.firstOrNull();refresh()}
        persist()
    }
    fun restorePlace(p: Place,index: Int) {if(_places.value.none {it.id==p.id}) {_places.value=_places.value.toMutableList().apply {add(index.coerceIn(0,size),p)};persist()}}
    fun movePlace(p: Place,offset: Int) {
        val list=_places.value.toMutableList();val from=list.indexOfFirst {it.id==p.id};val to=from+offset
        if(from<0 || to !in list.indices) return
        list.add(to,list.removeAt(from));_places.value=list;persist()
    }
    private var languageJob: Job?=null
    fun localizePlaces(language: String) {
        languageJob?.cancel()
        languageJob=viewModelScope.launch {
            for(p in (_places.value+listOfNotNull(_currentLocation.value)).distinctBy {it.id}) {
                val named=repository.localizedPlace(p,language)
                if(named==p) continue
                _places.value=_places.value.map {if(it.id==p.id) named else it}
                if(_currentLocation.value?.id==p.id) {_currentLocation.value=named;prefs.edit().putString("currentPlace",named.json().toString()).apply()}
                if(_selected.value?.id==p.id) {_selected.value=named;_weather.value=_weather.value?.copy(place=named)}
                _previews.value=_previews.value.mapValues {(_,w)->if(w.place.id==p.id) w.copy(place=named) else w}
                persist()
            }
        }
    }
    fun setProvider(p: Provider) { cancelPreviews();_previews.value=emptyMap(); _provider.value=p; prefs.edit().putString("provider",p.name).apply(); refresh() }
    fun refresh() {
        val version=++requestVersion
        loadJob?.cancel()
        val p=_selected.value ?: run {_weather.value=null;_loading.value=false;_error.value=null;return}; val source=_provider.value
        // The places page already has real cached previews. Use that same bundle on
        // the very first destination frame instead of briefly drawing an unknown sky.
        _weather.value=(_weather.value?.takeIf {it.place.id==p.id && it.provider==source}
            ?: _previews.value[p.id]?.takeIf {it.provider==source})?.copy(place=p)
        _loading.value=true; _error.value=null
        loadJob=viewModelScope.launch {
            try {
                val cached=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {repository.cached(p,source)}
                if(version!=requestVersion) return@launch
                cached?.copy(place=_selected.value ?: p)?.let {_weather.value=it;_previews.value=_previews.value+(p.id to it)}
                if(!online()) throw java.io.IOException("OFFLINE")
                val result=repository.load(p,source)
                if(version==requestVersion) {
                    val named=result.copy(place=_selected.value ?: p)
                    _weather.value=named;_previews.value=_previews.value+(p.id to named)
                }
            }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { _error.value=if(!online()) "OFFLINE" else e.message ?: "NETWORK_ERROR" }
            finally { if(version==requestVersion) _loading.value=false }
        }
    }
    private fun online(): Boolean {
        val manager=getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    }
    fun cancelPreviews() {previewJob?.cancel();previewJob=null}
    fun loadPreviews() {
        cancelPreviews()
        val source=_provider.value
        previewJob=viewModelScope.launch {
            for(p in (_places.value+listOfNotNull(_currentLocation.value)).distinctBy {it.id}) {
                if(_previews.value[p.id]!=null || (loadJob?.isActive==true && _selected.value?.id==p.id)) continue
                val w=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {repository.cached(p,source)}
                    ?: try {repository.load(p,source)} catch(e: CancellationException) {throw e} catch(_: Exception) {null}
                val current=(_places.value+listOfNotNull(_currentLocation.value)).firstOrNull {it.id==p.id}
                if(w!=null && current!=null && _provider.value==source) _previews.value=_previews.value+(p.id to w.copy(place=current))
            }
        }
    }
}
