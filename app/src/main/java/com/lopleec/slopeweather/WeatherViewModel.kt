package com.lopleec.slopeweather

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lopleec.slopeweather.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    private val _refreshingPlaces=MutableStateFlow<Set<String>>(emptySet())
    val refreshingPlaces=_refreshingPlaces.asStateFlow()
    private val _previewErrors=MutableStateFlow<Map<String,String>>(emptyMap())
    val previewErrors=_previewErrors.asStateFlow()
    private val refresher=WeatherRefreshQueue(viewModelScope,
        cached={p,source->withContext(Dispatchers.IO) {repository.cached(p,source)}},
        fetch={p,source->if(!online()) throw java.io.IOException("OFFLINE");repository.load(p,source)},
        errorMessage={e->if(!online()) "OFFLINE" else e.message ?: "NETWORK_ERROR"})
    init {
        syncRefreshTargets()
        viewModelScope.launch {refresher.state.collect {applyRefreshState(it)}}
    }
    private fun readPlaces(): List<Place> = runCatching {
        val list=JSONArray(prefs.getString("places","[]")).objects().map(Place::from).distinctBy {it.id}
        val legacy=list.firstOrNull {it.name in setOf("Current location","当前位置","目前位置","現在地")}
        if(legacy!=null && !prefs.contains("currentPlace")) prefs.edit().putString("currentPlace",legacy.json().toString()).apply()
        list.filterNot {it===legacy}
    }.getOrDefault(emptyList())
    private fun persist() { prefs.edit().putString("places",JSONArray(_places.value.map { it.json() }).toString()).putString("selected",_selected.value?.id).apply() }
    fun select(p: Place) {
        if(_selected.value!=p) {_selected.value=p;persist()}
        syncRefreshTargets();refresher.refreshSelectedIfStale();applyRefreshState(refresher.state.value)
    }
    fun add(p: Place) {
        if(_places.value.none {it.id==p.id}) _places.value=_places.value+p
        _selected.value=p;persist();syncRefreshTargets()
    }
    fun setCurrentLocation(p: Place,open: Boolean=true) {
        val selectedCurrent=_selected.value==null || _selected.value?.id==_currentLocation.value?.id
        _currentLocation.value=p
        prefs.edit().putString("currentPlace",p.json().toString()).putLong("locationUpdated",System.currentTimeMillis()).apply()
        if(open || selectedCurrent) {
            _selected.value=p;persist()
        }
        syncRefreshTargets()
    }
    fun remove(p: Place) {
        _places.value=_places.value.filterNot {it.id==p.id}
        if(_selected.value?.id==p.id) _selected.value=_currentLocation.value ?: _places.value.firstOrNull()
        persist();syncRefreshTargets()
    }
    fun restorePlace(p: Place,index: Int) {if(_places.value.none {it.id==p.id}) {_places.value=_places.value.toMutableList().apply {add(index.coerceIn(0,size),p)};persist();syncRefreshTargets()}}
    fun movePlace(p: Place,offset: Int) {
        val list=_places.value.toMutableList();val from=list.indexOfFirst {it.id==p.id};val to=from+offset
        if(from<0 || to !in list.indices) return
        list.add(to,list.removeAt(from));_places.value=list;persist();syncRefreshTargets()
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
                if(_selected.value?.id==p.id) _selected.value=named
                persist();syncRefreshTargets()
            }
        }
    }
    fun setProvider(p: Provider) {
        if(_provider.value==p) {refresh();return}
        _provider.value=p;prefs.edit().putString("provider",p.name).apply();syncRefreshTargets()
    }
    fun refresh() {
        syncRefreshTargets();refresher.refreshAll(restart=true);applyRefreshState(refresher.state.value)
    }
    fun onForeground() {refresher.onForeground();applyRefreshState(refresher.state.value)}
    fun onBackground() {refresher.onBackground()}
    fun refreshStalePlaces() {refresher.refreshStalePlaces();applyRefreshState(refresher.state.value)}
    private fun syncRefreshTargets() {
        refresher.updateTargets(_selected.value,_currentLocation.value,_places.value,_provider.value)
        applyRefreshState(refresher.state.value)
    }
    private fun applyRefreshState(state: WeatherRefreshState) {
        if(state.provider!=_provider.value) return
        _previews.value=state.bundles
        _refreshingPlaces.value=state.refreshing;_previewErrors.value=state.errors
        val selected=_selected.value
        _weather.value=selected?.let {state.bundles[it.id]?.copy(place=it)}
        _loading.value=selected?.let {it.id in state.refreshing} ?: false
        _error.value=selected?.let {state.errors[it.id]}
    }
    private fun online(): Boolean {
        val manager=getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    }
}
