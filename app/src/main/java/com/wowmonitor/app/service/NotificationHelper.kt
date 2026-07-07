package com.wowmonitor.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wowmonitor.app.R
import com.wowmonitor.app.WowMonitorApp
import com.wowmonitor.app.data.CdnChange
import com.wowmonitor.app.data.VersionChange
import com.wowmonitor.app.ui.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        private const val NOTIF_VERSION_BASE = 2000
        private const val NOTIF_CDN_BASE = 3000
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    private val regionEmojis = mapOf(
        "us" to "🌎", "eu" to "🇪🇺", "cn" to "🇨🇳",
        "tw" to "🇹🇼", "kr" to "🇰🇷", "sg" to "🌏"
    )

    private val regionNames = mapOf(
        "us" to "Americas", "eu" to "Europe", "cn" to "China",
        "tw" to "Taiwan", "kr" to "Korea", "sg" to "Southeast Asia (SG)"
    )

    private fun gameEmojis(key: String): String = when (key) {
        "anniversary" -> "🔥"
        "mop" -> "🐼"
        "era" -> "⚔️"
        else -> "🎮"
    }

    fun sendVersionNotifications(changes: List<VersionChange>) {
        val grouped = changes.groupBy { it.gameName }
        var notifId = NOTIF_VERSION_BASE

        for ((gameName, gameChanges) in grouped) {
            val text = buildString {
                appendLine("🔥 $gameName")
                appendLine("Las siguientes regiones han sido actualizadas:")
                appendLine()
                for (change in gameChanges) {
                    val rEmoji = regionEmojis[change.region] ?: "🌍"
                    val rName = regionNames[change.region] ?: change.region.uppercase()
                    appendLine("$rEmoji $rName")
                    appendLine("⬆️ ${change.oldBuild} → ${change.newBuild}")
                    appendLine()
                }
            }.trim()

            val title = "🚨 $gameName ACTUALIZÓ"

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, WowMonitorApp.CHANNEL_VERSIONS)
                .setContentTitle(title)
                .setContentText("${gameChanges.size} región(es) actualizada(s)")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            manager.notify(notifId++, notification)
        }
    }

    fun sendCdnNotifications(changes: List<CdnChange>) {
        val grouped = changes.groupBy { it.gameName }
        var notifId = NOTIF_CDN_BASE

        for ((gameName, gameChanges) in grouped) {
            val text = buildString {
                appendLine("🌐 $gameName")
                appendLine("CDN actualizado en:")
                appendLine()
                for (change in gameChanges) {
                    val rEmoji = regionEmojis[change.region] ?: "🌍"
                    val rName = regionNames[change.region] ?: change.region.uppercase()
                    appendLine("$rEmoji $rName")
                    if (change.oldHosts != change.newHosts) {
                        appendLine("⬆️ CDN: ${change.oldHosts.take(30)}... → ${change.newHosts.take(30)}...")
                    }
                    appendLine()
                }
            }.trim()

            val title = "🚨 $gameName CDN NUEVO"

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, WowMonitorApp.CHANNEL_CDNS)
                .setContentTitle(title)
                .setContentText("${gameChanges.size} región(es) con CDN nuevo")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            manager.notify(notifId++, notification)
        }
    }
}
