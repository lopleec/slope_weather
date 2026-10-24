package com.lopleec.slopeweather.data

import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReleaseStorageTest {
    @get:Rule val temporary=TemporaryFolder()
    private class Response(url: URL,private val body: String): HttpURLConnection(url) {
        var closed=false
        override fun connect() {}
        override fun disconnect() {closed=true}
        override fun usingProxy()=false
        override fun getResponseCode()=200
        override fun getInputStream()=ByteArrayInputStream(body.toByteArray())
    }

    @Test fun unavailableCacheDirectoryDoesNotDiscardValidWeather() = runBlocking {
        val blocked=temporary.newFile("not-a-directory")
        lateinit var response: Response
        val http=WeatherHttp(blocked) {Response(it,"{\"temperature\":12}").also {r->response=r}}
        assertEquals(12,http.json("https://weather.example.test/",60000).getInt("temperature"))
        assertTrue(response.closed)
    }

    @Test fun corruptCachedJsonIsReplacedWithAnActualNetworkResponse() = runBlocking {
        val cache=temporary.newFolder()
        var requests=0
        val http=WeatherHttp(cache) {requests++;Response(it,"{\"value\":$requests}")}
        assertEquals(1,http.json("https://weather.example.test/",60000).getInt("value"))
        cache.listFiles()!!.single().writeText("{truncated")
        assertEquals(2,http.json("https://weather.example.test/",60000).getInt("value"))
        assertEquals(2,http.json("https://weather.example.test/",60000).getInt("value"))
        assertEquals(2,requests)
    }

    @Test fun anUncachedRequestDoesNotLeaveAnUnusedDiskResponse() = runBlocking {
        val cache=temporary.newFolder()
        val http=WeatherHttp(cache) {Response(it,"{\"value\":1}")}
        http.json("https://weather.example.test/")
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun invalidNetworkJsonIsNotCachedAsSuccessfulWeather() {
        val cache=temporary.newFolder()
        val http=WeatherHttp(cache) {Response(it,"<html>upstream error</html>")}
        assertThrows(JSONException::class.java) {runBlocking {http.json("https://weather.example.test/",60000)}}
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun plaintextNetworkUrlsAreRejectedBeforeOpeningAConnection() {
        var connected=false
        val http=WeatherHttp(temporary.newFolder()) {connected=true;Response(it,"{}")}
        assertThrows(IllegalArgumentException::class.java) {runBlocking {http.json("http://weather.example.test/")}}
        assertFalse(connected)
    }
    @Test fun anOversizedUpstreamBodyIsClosedAndNeverStored() {
        val cache=temporary.newFolder()
        lateinit var response: Response
        val http=WeatherHttp(cache,64) {Response(it,"x".repeat(65)).also {r->response=r}}
        assertThrows(java.io.IOException::class.java) {runBlocking {http.text("https://weather.example.test/",60000)}}
        assertTrue(response.closed)
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test fun readersObserveWholeOldOrNewWeatherFilesDuringReplacement() {
        val directory=temporary.newFolder();val file=File(directory,"bundle.json")
        val first="a".repeat(8192);val second="b".repeat(8192)
        assertTrue(writeCacheAtomically(file,first))
        val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        try {
            val writer=pool.submit {start.await();repeat(100) {assertTrue(writeCacheAtomically(file,if(it%2==0) second else first))}}
            val reader=pool.submit {start.await();repeat(1000) {val text=file.readText();assertTrue(text==first || text==second)}}
            start.countDown();writer.get(10,TimeUnit.SECONDS);reader.get(10,TimeUnit.SECONDS)
            assertEquals(listOf("bundle.json"),directory.list()!!.toList())
        } finally {pool.shutdownNow()}
    }

    @Test fun aFailedReplacementLeavesThePreviousDestinationIntactAndCleansItsTempFile() {
        val directory=temporary.newFolder();val target=File(directory,"bundle.json").apply {mkdir()}
        File(target,"existing").writeText("keep")
        assertFalse(writeCacheAtomically(target,"replacement"))
        assertEquals("keep",File(target,"existing").readText())
        assertEquals(listOf("bundle.json"),directory.list()!!.toList())
    }

    @Test fun cacheTrimmingKeepsNewestResponsesAndDoesNotTouchUnrelatedFiles() {
        val directory=temporary.newFolder();val now=System.currentTimeMillis()
        val old=File(directory,"http-old.json").apply {writeText("123456");setLastModified(now-10000)}
        val fresh=File(directory,"http-fresh.json").apply {writeText("123456");setLastModified(now-1000)}
        val other=File(directory,"user-data.json").apply {writeText("keep")}
        trimHttpCache(directory,8,now)
        assertFalse(old.exists());assertTrue(fresh.exists());assertEquals("keep",other.readText())
    }
}
