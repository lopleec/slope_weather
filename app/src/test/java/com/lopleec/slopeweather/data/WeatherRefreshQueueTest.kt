package com.lopleec.slopeweather.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WeatherRefreshQueueTest {
    private val a=Place("Visible",1.0,1.0)
    private val b=Place("Current",2.0,2.0)
    private val c=Place("Saved C",3.0,3.0)
    private val d=Place("Saved D",4.0,4.0)
    private val e=Place("Saved E",5.0,5.0)
    private fun weather(p: Place,source: Provider=Provider.AUTO,temp: Double=20.0,time: Long=10_000)=
        WeatherBundle(p,source,Reading(time,mapOf("temperature_2m" to temp)),emptyList(),emptyList(),fetched=time)

    private inner class Harness: AutoCloseable {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val cache=mutableMapOf<String,WeatherBundle>()
        val calls=mutableListOf<Call>()
        var clock=10_000L
        var readCache: suspend (Place,Provider)->WeatherBundle?={p,_->cache[p.id]}
        var ignoreCancellation: (Place,Provider)->Boolean={_,_->false}
        val queue=WeatherRefreshQueue(scope,cached={p,s->readCache(p,s)},fetch={p,s->
            val call=Call(p,s);calls.add(call)
            if(ignoreCancellation(p,s)) withContext(NonCancellable) {call.result.await()} else call.result.await()
        },now={clock})
        inner class Call(val place: Place,val source: Provider) {
            val result=CompletableDeferred<WeatherBundle>()
            fun succeed(temp: Double=20.0) {result.complete(weather(place,source,temp,clock))}
            fun fail() {result.completeExceptionally(IOException("NETWORK_ERROR"))}
        }
        fun start(selected: Place?=a,current: Place?=b,saved: List<Place> = listOf(c,d)) {
            queue.updateTargets(selected,current,saved,Provider.AUTO);queue.onForeground()
        }
        override fun close() {scope.cancel()}
    }

    @Test fun visibleAndCurrentLocationsStartBeforeSavedCitiesWithBoundedConcurrency() {
        Harness().use {h->
            h.start(saved=listOf(c,d,e))
            assertEquals(listOf(a.id,b.id),h.calls.map {it.place.id})
            assertEquals(setOf(a.id,b.id,c.id,d.id,e.id),h.queue.state.value.refreshing)
            h.calls[0].succeed()
            assertEquals(listOf(a.id,b.id,c.id),h.calls.map {it.place.id})
            assertEquals(2,h.calls.count {!it.result.isCompleted})
        }
    }

    @Test fun aSharedCurrentVisibleAndSavedCoordinateIsFetchedOnlyOnce() {
        Harness().use {h->
            h.start(a,a.copy(name="Current alias"),listOf(a.copy(name="Saved alias"),c))
            assertEquals(listOf(a.id,c.id),h.calls.map {it.place.id})
            h.calls.forEach {it.succeed()}
            assertTrue(h.queue.state.value.refreshing.isEmpty())
            assertEquals(2,h.calls.size)
        }
    }

    @Test fun everyForegroundEntryRefreshesEvenFreshMemoryDataButDuplicateStartDoesNot() {
        Harness().use {h->
            h.cache[a.id]=weather(a,temp=1.0)
            h.start(saved=emptyList())
            assertEquals(1.0,h.queue.state.value.bundles[a.id]!!.current["temperature_2m"]!!,0.0)
            h.calls[0].succeed(2.0);h.calls[1].succeed()
            h.queue.onForeground();assertEquals(2,h.calls.size)
            h.queue.onBackground();h.queue.onForeground()
            assertEquals(listOf(a.id,b.id,a.id,b.id),h.calls.map {it.place.id})
            assertEquals(2.0,h.queue.state.value.bundles[a.id]!!.current["temperature_2m"]!!,0.0)
        }
    }

    @Test fun returningWhileARequestIsRunningReusesItInsteadOfFetchingTwice() {
        Harness().use {h->
            h.start(saved=listOf(c))
            h.queue.onBackground();h.queue.onForeground()
            assertEquals(2,h.calls.size)
            h.calls[0].succeed();h.calls[1].succeed();h.calls[2].succeed()
            assertEquals(listOf(a.id,b.id,c.id),h.calls.map {it.place.id})
            assertTrue(h.queue.state.value.refreshing.isEmpty())
        }
    }

    @Test fun savedCityFetchesContinueWhenTheActivityLeavesTheForeground() {
        Harness().use {h->
            h.start(saved=listOf(c));h.queue.onBackground()
            h.calls[0].succeed();h.calls[1].succeed()
            assertEquals(c.id,h.calls[2].place.id)
            h.calls[2].succeed(13.0)
            assertEquals(13.0,h.queue.state.value.bundles[c.id]!!.current["temperature_2m"]!!,0.0)
        }
    }

    @Test fun cachedRowsAppearBeforeTheirNetworkTurnAndAreStillRefreshed() {
        Harness().use {h->
            h.cache[c.id]=weather(c,temp=7.0)
            h.start(saved=listOf(c))
            assertEquals(2,h.calls.size)
            assertEquals(7.0,h.queue.state.value.bundles[c.id]!!.current["temperature_2m"]!!,0.0)
            h.calls[0].succeed();h.calls[1].succeed();h.calls[2].succeed(12.0)
            assertEquals(12.0,h.queue.state.value.bundles[c.id]!!.current["temperature_2m"]!!,0.0)
        }
    }

    @Test fun navigatingToAQueuedCityMovesItAheadOfTheOtherPendingCities() {
        Harness().use {h->
            h.start(saved=listOf(c,d,e))
            h.queue.updateTargets(e,b,listOf(a,c,d,e),Provider.AUTO)
            assertEquals(2,h.calls.size)
            h.calls[0].succeed()
            assertEquals(e.id,h.calls[2].place.id)
            assertEquals(1,h.calls.count {it.place.id==e.id})
        }
    }

    @Test fun oneFailedLocationKeepsItsCacheAndDoesNotStopOtherCities() {
        Harness().use {h->
            h.cache[b.id]=weather(b,temp=8.0)
            h.start(saved=listOf(c))
            h.calls[1].fail();h.calls[0].fail()
            assertEquals(8.0,h.queue.state.value.bundles[b.id]!!.current["temperature_2m"]!!,0.0)
            assertEquals("NETWORK_ERROR",h.queue.state.value.errors[b.id])
            assertEquals(c.id,h.calls[2].place.id)
            h.calls[2].succeed()
            assertTrue(c.id in h.queue.state.value.bundles)
            assertFalse(c.id in h.queue.state.value.errors)
        }
    }

    @Test fun aLateOldProviderResponseCannotOverwriteOrFinishANewProviderRequest() {
        Harness().use {h->
            h.ignoreCancellation={_,source->source==Provider.AUTO}
            h.start(current=null,saved=emptyList())
            h.queue.updateTargets(a,null,emptyList(),Provider.CMA)
            assertEquals(2,h.calls.size)
            h.calls[0].succeed(99.0)
            assertTrue(a.id in h.queue.state.value.refreshing)
            assertTrue(h.queue.state.value.bundles.isEmpty())
            h.calls[1].succeed(18.0)
            assertEquals(Provider.CMA,h.queue.state.value.bundles[a.id]!!.provider)
            assertEquals(18.0,h.queue.state.value.bundles[a.id]!!.current["temperature_2m"]!!,0.0)
        }
    }

    @Test fun removingAnInFlightSavedCityCannotRestoreItWhenItsResponseArrives() {
        Harness().use {h->
            h.ignoreCancellation={p,_->p.id==c.id}
            h.start(saved=listOf(c,d));h.calls[0].succeed()
            val removed=h.calls.single {it.place.id==c.id}
            h.queue.updateTargets(a,b,listOf(d),Provider.AUTO)
            removed.succeed()
            assertFalse(c.id in h.queue.state.value.bundles)
            assertFalse(c.id in h.queue.state.value.refreshing)
            assertTrue(h.calls.any {it.place.id==d.id})
        }
    }

    @Test fun aSlowDiskReadCannotReplaceANewerNetworkResultOrDelayItsRequest() {
        Harness().use {h->
            val disk=CompletableDeferred<WeatherBundle?>()
            h.readCache={_,_->disk.await()}
            h.start(current=null,saved=emptyList())
            assertEquals(1,h.calls.size)
            h.calls[0].succeed(25.0)
            disk.complete(weather(a,temp=1.0,time=20_000))
            assertEquals(25.0,h.queue.state.value.bundles[a.id]!!.current["temperature_2m"]!!,0.0)
        }
    }

    @Test fun localizationDoesNotRestartRequestsAndTheLatestPlaceNameIsPreserved() {
        Harness().use {h->
            h.start(current=null,saved=emptyList())
            h.queue.updateTargets(a.copy(name="Localized"),null,emptyList(),Provider.AUTO)
            assertEquals(1,h.calls.size)
            h.calls[0].succeed()
            assertEquals("Localized",h.queue.state.value.bundles[a.id]!!.place.name)
        }
    }

    @Test fun reopeningTheListDoesNotRepeatFreshRequestsButDoesRefreshOldRows() {
        Harness().use {h->
            h.start(current=null,saved=emptyList());h.calls[0].succeed()
            h.queue.refreshStalePlaces();assertEquals(1,h.calls.size)
            h.clock+=301;h.queue.refreshStalePlaces();assertEquals(2,h.calls.size)
        }
    }

    @Test fun savedLocationsRefreshWithoutLocationPermissionOrASelectedCity() {
        Harness().use {h->
            h.start(selected=null,current=null,saved=listOf(c,d))
            assertEquals(listOf(c.id,d.id),h.calls.map {it.place.id})
            h.calls.forEach {it.succeed()}
            assertEquals(setOf(c.id,d.id),h.queue.state.value.bundles.keys)
        }
    }

    @Test fun aSettingsRefreshKeepsVisibleDataButRejectsThePreviousRequest() {
        Harness().use {h->
            var first=true
            h.ignoreCancellation={_,_->first.also {first=false}}
            h.cache[a.id]=weather(a,temp=5.0)
            h.start(current=null,saved=emptyList())
            h.queue.refreshAll(restart=true)
            assertEquals(2,h.calls.size)
            h.calls[0].succeed(99.0)
            assertEquals(5.0,h.queue.state.value.bundles[a.id]!!.current["temperature_2m"]!!,0.0)
            assertTrue(a.id in h.queue.state.value.refreshing)
            h.calls[1].succeed(19.0)
            assertEquals(19.0,h.queue.state.value.bundles[a.id]!!.current["temperature_2m"]!!,0.0)
        }
    }

    @Test fun offlineFailureDoesNotPreventALaterDiskReadFromShowingCachedWeather() {
        Harness().use {h->
            val disk=CompletableDeferred<WeatherBundle?>()
            h.readCache={_,_->disk.await()}
            h.start(current=null,saved=emptyList());h.calls[0].fail()
            disk.complete(weather(a,temp=6.0))
            assertEquals(6.0,h.queue.state.value.bundles[a.id]!!.current["temperature_2m"]!!,0.0)
            assertEquals("NETWORK_ERROR",h.queue.state.value.errors[a.id])
            assertTrue(h.queue.state.value.refreshing.isEmpty())
        }
    }
}
