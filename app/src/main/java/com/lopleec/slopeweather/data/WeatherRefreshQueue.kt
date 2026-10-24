package com.lopleec.slopeweather.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

internal data class WeatherRefreshState(
    val provider: Provider = Provider.AUTO,
    val bundles: Map<String, WeatherBundle> = emptyMap(),
    val refreshing: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
)

/** Entry points and state changes are confined to the owning (main) dispatcher. */
internal class WeatherRefreshQueue(
    private val scope: CoroutineScope,
    private val cached: suspend (Place, Provider) -> WeatherBundle?,
    private val fetch: suspend (Place, Provider) -> WeatherBundle,
    private val errorMessage: (Exception) -> String = { it.message ?: "NETWORK_ERROR" },
    private val now: () -> Long = { java.time.Instant.now().epochSecond },
    private val concurrency: Int = 2,
) {
    private val _state = MutableStateFlow(WeatherRefreshState())
    val state = _state.asStateFlow()
    private var targets = linkedMapOf<String, Place>()
    private var selectedId: String? = null
    private var provider = Provider.AUTO
    private var started = false
    private var foreground = false
    private var generation = 0
    private var pumping = false
    private val pending = linkedSetOf<String>()
    private val attempted = mutableSetOf<String>()
    private val revisions = mutableMapOf<String, Int>()
    private class Request(val generation: Int, val provider: Provider) { lateinit var job: Job }
    private val running = mutableMapOf<String, Request>()
    private val cachePending = linkedSetOf<String>()
    private val warming = mutableSetOf<String>()
    private var cacheJob: Job? = null

    init { require(concurrency > 0) }

    fun updateTargets(selected: Place?, current: Place?, saved: List<Place>, source: Provider) {
        val changedSource = provider != source
        val changedSelection = selectedId != selected?.id
        if (changedSource) invalidate()
        provider = source
        selectedId = selected?.id
        targets = (listOfNotNull(selected, current) + saved).distinctBy { it.id }
            .associateByTo(linkedMapOf()) { it.id }
        val valid = targets.keys
        val obsolete = running.keys.filterNot { it in valid }
        obsolete.forEach { running.remove(it)?.job?.cancel() }
        pending.retainAll(valid); attempted.retainAll(valid)
        cachePending.retainAll(valid); warming.retainAll(valid); revisions.keys.retainAll(valid)
        val previous = if (changedSource) WeatherRefreshState(provider) else _state.value
        _state.value = previous.copy(provider = provider,
            bundles = previous.bundles.filterKeys { it in valid }.mapValues { (id, w) -> w.copy(place = targets.getValue(id)) },
            errors = previous.errors.filterKeys { it in valid })
        if (started) {
            targets.keys.forEach { enqueue(it) }
            if (changedSelection) selectedId?.takeIf(::needsRefresh)?.let { enqueue(it, force = true) }
            warmCaches()
        }
        updateLoading(); pump()
    }

    fun onForeground() {
        if (foreground) return
        foreground = true
        refreshAll()
    }

    // An already-started pass can finish while the activity is out of view.
    fun onBackground() { foreground = false }

    fun refreshAll(restart: Boolean = false) {
        started = true
        if (restart) invalidate()
        attempted.clear()
        _state.value = _state.value.copy(errors = emptyMap())
        targets.keys.forEach { enqueue(it) }
        warmCaches(); updateLoading(); pump()
    }

    fun refreshStalePlaces() {
        if (!started) return
        targets.keys.filter(::needsRefresh).forEach { enqueue(it, force = true) }
        warmCaches(); updateLoading(); pump()
    }

    fun refreshSelectedIfStale() {
        if (!started) return
        selectedId?.takeIf(::needsRefresh)?.let { enqueue(it, force = true) }
        updateLoading(); pump()
    }

    private fun needsRefresh(id: String): Boolean {
        val bundle = _state.value.bundles[id] ?: return true
        return id in _state.value.errors || now() - bundle.fetched !in 0 until 300L
    }

    private fun enqueue(id: String, force: Boolean = false) {
        if (id !in targets || id in running || id in pending) return
        if (force) attempted.remove(id)
        if (id !in attempted) pending.add(id)
    }

    private fun invalidate() {
        generation++
        val old = running.values.toList()
        running.clear(); pending.clear(); attempted.clear(); revisions.clear()
        cachePending.clear(); warming.clear()
        cacheJob?.cancel(); cacheJob = null
        old.forEach { it.job.cancel() }
    }

    private fun updateLoading() {
        _state.value = _state.value.copy(refreshing = (pending + running.keys).toSet())
    }

    private fun warmCaches() {
        targets.keys.filter { it !in _state.value.bundles && warming.add(it) }.forEach { cachePending.add(it) }
        if (cachePending.isEmpty() || cacheJob?.isActive == true) return
        val version = generation; val source = provider
        cacheJob = scope.launch(start = CoroutineStart.LAZY) {
            while (cachePending.isNotEmpty() && version == generation) {
                val id = cachePending.first(); cachePending.remove(id)
                val place = targets[id] ?: continue
                val revision = revisions[id] ?: 0
                val bundle = try { cached(place, source) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                if (version != generation) return@launch
                warming.remove(id)
                val current = targets[id] ?: continue
                if (bundle?.provider != source || (revisions[id] ?: 0) != revision) continue
                val existing = _state.value.bundles[id]
                if (existing == null || bundle.fetched > existing.fetched) {
                    _state.value = _state.value.copy(bundles = _state.value.bundles + (id to bundle.copy(place = current)))
                }
            }
        }
        cacheJob!!.start()
    }

    private fun pump() {
        if (pumping || !started || !scope.isActive) return
        pumping = true
        try {
            while (running.size < concurrency && pending.isNotEmpty()) {
                // Recompute priority after navigation: visible city, current location, saved order.
                val id = targets.keys.firstOrNull { it in pending } ?: break
                pending.remove(id)
                val place = targets.getValue(id)
                val request = Request(generation, provider)
                running[id] = request
                request.job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val bundle = fetch(place, request.provider)
                        currentCoroutineContext().ensureActive()
                        if (running[id] !== request || generation != request.generation) return@launch
                        val current = targets[id] ?: return@launch
                        require(bundle.provider == request.provider) { "NETWORK_ERROR" }
                        revisions[id] = (revisions[id] ?: 0) + 1
                        _state.value = _state.value.copy(
                            bundles = _state.value.bundles + (id to bundle.copy(place = current)),
                            errors = _state.value.errors - id)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (running[id] === request && generation == request.generation) {
                            _state.value = _state.value.copy(errors = _state.value.errors + (id to errorMessage(e)))
                        }
                    } finally {
                        if (running[id] === request) {
                            running.remove(id); attempted.add(id)
                            updateLoading(); pump()
                        }
                    }
                }
                updateLoading()
                request.job.start()
            }
        } finally { pumping = false }
    }
}
