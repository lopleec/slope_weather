package com.lopleec.slopeweather

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.lopleec.slopeweather.data.*
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class WeatherApplication: Application() {
    override fun onCreate() { super.onCreate(); scheduleAlerts(this) }
}
fun scheduleAlerts(context: Context) {
    val work=WorkManager.getInstance(context)
    if(context.getSharedPreferences("weather",Context.MODE_PRIVATE).getBoolean("alerts",false)) {
        val request=PeriodicWorkRequestBuilder<AlertWorker>(3,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        work.enqueueUniquePeriodicWork("official-weather-alerts",ExistingPeriodicWorkPolicy.KEEP,request)
    } else work.cancelUniqueWork("official-weather-alerts")
}
class AlertWorker(appContext: Context,params: WorkerParameters): CoroutineWorker(appContext,params) {
    override suspend fun doWork(): Result {
        val c=applicationContext
        val prefs=c.getSharedPreferences("weather",Context.MODE_PRIVATE)
        if(!prefs.getBoolean("alerts",false)) return Result.success()
        val places=(runCatching { JSONArray(prefs.getString("places","[]")).objects().map(Place::from) }.getOrDefault(emptyList())+listOfNotNull(runCatching {Place.from(org.json.JSONObject(prefs.getString("currentPlace",null) ?: ""))}.getOrNull())).distinctBy {it.id}
        val repo=WeatherRepository(c)
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("weather-alerts",c.getString(R.string.alerts),NotificationManager.IMPORTANCE_DEFAULT))
        if(!manager.areNotificationsEnabled() || (android.os.Build.VERSION.SDK_INT>=33 &&
            ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)) return Result.success()
        var receipts=runCatching {val saved=JSONArray(prefs.getString("alert-receipts-v2","[]"));(0 until saved.length()).map {saved.getString(it)}.toCollection(LinkedHashSet())}.getOrDefault(linkedSetOf<String>()).toSet()
        var failed=false
        for(p in places) {
            val bundle=try { repo.officialAlerts(p) } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(_: Exception) { failed=true; continue }
            if(bundle.status=="unavailable") {failed=true;continue}
            for(alert in bundle.alerts) {
                if(alert.expires!=null && alert.expires<=java.time.Instant.now().epochSecond) continue
                val receipt=alert.notificationFingerprint()
                if(receipt in receipts) continue
                if(android.os.Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) continue
                val pending=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val notification=NotificationCompat.Builder(c,"weather-alerts").setSmallIcon(R.drawable.ic_launcher).setContentTitle("${p.name} · ${alert.title}").setContentText(alert.text).setStyle(NotificationCompat.BigTextStyle().bigText(alert.text)).setContentIntent(pending).setAutoCancel(true).build()
                try {manager.notify((alert.source+":"+alert.id).hashCode(),notification)} catch(_: SecurityException) {return Result.success()}
                receipts=rememberAlertReceipt(receipts,receipt)
                prefs.edit().putString("alert-receipts-v2",JSONArray(receipts.toList()).toString()).apply()
            }
        }
        return if(failed) Result.retry() else Result.success()
    }
}
