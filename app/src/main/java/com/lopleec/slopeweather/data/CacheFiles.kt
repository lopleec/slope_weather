package com.lopleec.slopeweather.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Cache failures must not discard a valid network response or replace a complete older file. */
internal fun writeCacheAtomically(file: File,text: String): Boolean {
    var temporary: File?=null
    return try {
        val parent=file.parentFile ?: return false
        if(!parent.isDirectory && !parent.mkdirs()) return false
        temporary=File.createTempFile("weather-", ".tmp",parent)
        temporary.writeText(text)
        Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        true
    } catch(_: IOException) {
        false
    } catch(_: SecurityException) {
        false
    } finally {
        try {temporary?.delete()} catch(_: SecurityException) {}
    }
}

internal fun readFreshCache(file: File,ttl: Long,now: Long=System.currentTimeMillis()): String? {
    if(ttl<=0) return null
    return try {
        val age=now-file.lastModified()
        if(file.isFile && file.length()<=32L*1024*1024 && age>=0 && age<ttl) file.readText() else null
    } catch(_: IOException) {
        null
    } catch(_: SecurityException) {
        null
    }
}

/** HTTP cache is disposable; saved weather bundles live separately in filesDir. */
internal fun trimHttpCache(directory: File,maxBytes: Long=64L*1024*1024,now: Long=System.currentTimeMillis()) {
    try {
        var retained=0L
        directory.listFiles()?.filter {it.isFile && it.name.startsWith("http-") && it.extension=="json"}
            ?.sortedByDescending {it.lastModified()}?.forEach {file->
                if(now-file.lastModified()>30L*24*60*60*1000 || retained+file.length()>maxBytes) file.delete()
                else retained+=file.length()
            }
    } catch(_: SecurityException) {
        // Storage is best-effort, and must never hide successfully fetched data.
    }
}
