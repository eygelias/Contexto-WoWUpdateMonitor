package com.wowmonitor.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wowmonitor.app.data.AppDatabase
import com.wowmonitor.app.data.ChangeDetector
import com.wowmonitor.app.data.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles test alarm triggers.
 * Fetches from Worker, detects changes (including fake test entries),
 * and shows a notification.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"

        fun runCheckNow(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    doCheck(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Check failed", e)
                }
            }
        }

        private suspend fun doCheck(context: Context) {
            val db = AppDatabase.getInstance(context)
            val network = NetworkMonitor()
            val detector = ChangeDetector()
            val notifHelper = NotificationHelper(context)

            val freshData = network.fetchAll()
            network.shutdown()

            if (freshData.isEmpty()) {
                Log.w(TAG, "No data from Worker")
                return
            }

            val result = detector.detectChanges(db.versionDao(), freshData)

            for (entry in result.newEntries) {
                db.versionDao().insert(entry)
            }

            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            db.versionDao().deleteOlderThan(cutoff)

            if (result.versionChanges.isNotEmpty()) {
                notifHelper.sendVersionNotifications(result.versionChanges)
                Log.i(TAG, "🚨 ${result.versionChanges.size} version change(s)!")
            }
            if (result.cdnChanges.isNotEmpty()) {
                notifHelper.sendCdnNotifications(result.cdnChanges)
            }

            Log.i(TAG, "Check done — ${freshData.size} games, ${result.newEntries.size} entries")
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Alarm triggered — checking via Worker")
        MonitorScheduler.schedule(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                doCheck(context)
            } catch (e: Exception) {
                Log.e(TAG, "Check failed", e)
            } finally {
                try { pending?.finish() } catch (_: Exception) {}
            }
        }
    }
}
