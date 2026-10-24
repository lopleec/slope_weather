package com.lopleec.slopeweather.data

import org.json.JSONArray

/** Issuers may reuse an ID for a later warning; include the actual bulletin in the receipt. */
fun WeatherAlert.notificationFingerprint(): String {
    val bulletin=JSONArray(listOf(source,id,issued,expires,title,text,severity)).toString()
    return java.security.MessageDigest.getInstance("SHA-256").digest(bulletin.toByteArray())
        .joinToString("") {"%02x".format(it)}
}

fun rememberAlertReceipt(previous: Set<String>,receipt: String): Set<String> =
    (previous.filterNot {it==receipt}+receipt).takeLast(256).toCollection(LinkedHashSet())
