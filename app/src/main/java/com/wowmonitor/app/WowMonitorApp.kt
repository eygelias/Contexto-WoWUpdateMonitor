package com.wowmonitor.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class WowMonitorApp : Application() {

    companion object {
        const val CHANNEL_VERSIONS = "wow_versions"
        const val CHANNEL_CDNS = "wow_cdns"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val versionsChannel = NotificationChannel(
            CHANNEL_VERSIONS,
            "Version Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Game version change notifications"
            enableVibration(true)
            enableLights(true)
        }

        val cdnsChannel = NotificationChannel(
            CHANNEL_CDNS,
            "CDN Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "CDN change notifications"
            enableVibration(true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(versionsChannel)
        manager.createNotificationChannel(cdnsChannel)
    }
}
