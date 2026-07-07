package com.wowmonitor.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wowmonitor.app.R
import com.wowmonitor.app.WowMonitorApp
import com.wowmonitor.app.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val WORKER_URL = "https://orange-meadow-c3f6.eygelias.workers.dev"
        private const val PREFS_NAME = "wow_notifications"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY = 20

        fun registerToken(context: Context, token: String) {
            Thread {
                try {
                    val url = java.net.URL("$WORKER_URL/register-token")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val prefs = RegionPrefs.getSelectedRegions(context)
                    val json = """{"token":"$token","regions":${org.json.JSONArray(prefs)}}"""
                    conn.outputStream.write(json.toByteArray())
                    conn.responseCode
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Token registration failed", e)
                }
            }.start()
        }

        fun saveNotification(context: Context, title: String, body: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
            val arr = JSONArray(historyJson)

            val entry = JSONObject().apply {
                put("title", title)
                put("body", body)
                put("time", SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date()))
            }

            // Store oldest -> newest so UI behaves like a chat.
            val newArr = JSONArray()
            val start = maxOf(0, arr.length() - (MAX_HISTORY - 1))
            for (i in start until arr.length()) newArr.put(arr.getJSONObject(i))
            newArr.put(entry)

            prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
        }

        fun getNotifications(context: Context): List<Triple<String, String, String>> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val historyJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
            val arr = JSONArray(historyJson)
            val result = mutableListOf<Triple<String, String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(Triple(
                    obj.optString("title", ""),
                    obj.optString("body", ""),
                    obj.optString("time", "")
                ))
            }
            return result
        }

        fun deleteNotification(context: Context, index: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
            val newArr = JSONArray()
            for (i in 0 until arr.length()) if (i != index) newArr.put(arr.getJSONObject(i))
            prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        registerToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "WoW Update"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        if (message.data["type"] == "app_update") {
            AppUpdater.save(this, AppUpdater.fromMap(message.data))
        }

        saveNotification(this, title, body)
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            WowMonitorApp.CHANNEL_VERSIONS, "Version Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) }
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, WowMonitorApp.CHANNEL_VERSIONS)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
