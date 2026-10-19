package com.biotrack.app

import android.app.*
import android.content.Intent
import android.os.IBinder

class BioApp : Application() {
    val store by lazy { ReadingStore(this) }
    val meter by lazy { MeterClient(this, store) }
}

class MeterService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("meter", "Meter connection", NotificationManager.IMPORTANCE_LOW))
        if(intent?.action=="stop") {
            (application as BioApp).meter.disconnect(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        val open=PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this, 1, Intent(this, MeterService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"meter").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Bio Track meter session").setContentText("Keeping your meter connection active. Open Bio Track for sync status.")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Disconnect",stop).build()).build()
        startForeground(1,notification)
        return START_NOT_STICKY
    }
}
