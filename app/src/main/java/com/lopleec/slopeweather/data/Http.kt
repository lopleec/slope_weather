package com.lopleec.slopeweather.data

import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class WeatherHttp(
    private val cacheDir: File,
    private val maxResponseChars: Int=16*1024*1024,
    private val connect: (URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}
) {
    private fun cacheFile(url: String)=File(cacheDir,"http-"+java.security.MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray()).joinToString("") {"%02x".format(it)}+".json")
    private fun parse(raw: String)=if(raw.trimStart().startsWith("[")) JSONObject().put("items",JSONArray(raw)) else JSONObject(raw)

    suspend fun json(url: String, ttl: Long = 0, userAgentContact: String = "", timeoutMillis: Int? = null): JSONObject = withContext(Dispatchers.IO) {
        require(URL(url).protocol=="https") {"HTTPS required"}
        val cache=cacheFile(url)
        readFreshCache(cache,ttl)?.let {raw->
            try {return@withContext parse(raw)} catch(_: JSONException) {
                try {cache.delete()} catch(_: SecurityException) {}
            }
        }
        val raw=text(url,0,userAgentContact,timeoutMillis)
        val result=parse(raw)
        if(ttl>0) {writeCacheAtomically(cache,raw);trimHttpCache(cacheDir)}
        result
    }
    suspend fun text(url: String, ttl: Long = 0,userAgentContact: String = "",timeoutMillis: Int? = null): String = withContext(Dispatchers.IO) {
        val target=URL(url)
        require(target.protocol=="https") {"HTTPS required"}
        val cache=cacheFile(url)
        readFreshCache(cache,ttl)?.let {return@withContext it}
        currentCoroutineContext().ensureActive()
        val connection=connect(target)
        try {
            connection.connectTimeout=timeoutMillis ?: 15_000; connection.readTimeout=timeoutMillis ?: 25_000
            connection.setRequestProperty("User-Agent","Slope Weather/${com.lopleec.slopeweather.BuildConfig.VERSION_NAME} (Android; com.lopleec.slopeweather) $userAgentContact")
            connection.setRequestProperty("Accept","application/geo+json, application/json, text/plain")
            if (connection.responseCode !in 200..299) throw java.io.IOException("HTTP ${connection.responseCode} · ${URL(url).host}")
            currentCoroutineContext().ensureActive()
            val raw=connection.inputStream.bufferedReader().use {reader->
                val result=StringBuilder()
                val buffer=CharArray(16384)
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val count=reader.read(buffer)
                    if(count<0) break
                    if(result.length.toLong()+count>maxResponseChars) throw java.io.IOException("Response exceeds size limit")
                    result.append(buffer,0,count)
                }
                result.toString()
            }
            currentCoroutineContext().ensureActive()
            if(ttl>0) {writeCacheAtomically(cache,raw);trimHttpCache(cacheDir)}
            raw
        } finally { connection.disconnect() }
    }
}
