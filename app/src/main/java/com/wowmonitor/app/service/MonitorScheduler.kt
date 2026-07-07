package com.wowmonitor.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Schedules periodic WoW update checks using AlarmManager.
 * Minimum practical interval: 5 minutes.
 * No foreground service, no persistent notification.
 */
object MonitorScheduler {

    private const val TAG = "MonitorScheduler"
    const val INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes
    private const val REQUEST_CODE_PERIODIC = 7741
    private const val REQUEST_CODE_ONESHOT = 7742

    private val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    /**
     * Schedule recurring alarm every 5 minutes.
     */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE_PERIODIC, intent, pendingIntentFlags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + INTERVAL_MS,
                    pending
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + INTERVAL_MS,
                    pending
                )
            }
            Log.i(TAG, "Periodic alarm scheduled in 5 minutes")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied, falling back to inexact", e)
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + INTERVAL_MS,
                pending
            )
        }
    }

    /**
     * Schedule a one-shot alarm after a delay (for testing).
     * Uses a different request code so it doesn't interfere with periodic alarm.
     */
    fun scheduleOneShot(context: Context, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE_ONESHOT, intent, pendingIntentFlags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + delayMs,
                    pending
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + delayMs,
                    pending
                )
            }
            Log.i(TAG, "One-shot alarm scheduled in ${delayMs}ms")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied for one-shot", e)
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + delayMs,
                pending
            )
        }
    }

    /**
     * Cancel one-shot alarm.
     */
    fun cancelOneShot(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE_ONESHOT, intent, pendingIntentFlags)
        alarmManager.cancel(pending)
    }

    /**
     * Cancel all alarms.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val periodic = PendingIntent.getBroadcast(context, REQUEST_CODE_PERIODIC,
            Intent(context, AlarmReceiver::class.java), pendingIntentFlags)
        alarmManager.cancel(periodic)

        val oneshot = PendingIntent.getBroadcast(context, REQUEST_CODE_ONESHOT,
            Intent(context, AlarmReceiver::class.java), pendingIntentFlags)
        alarmManager.cancel(oneshot)

        Log.i(TAG, "All alarms cancelled")
    }

    /**
     * Check if periodic alarm is scheduled.
     */
    fun isScheduled(context: Context): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_PERIODIC, intent,
            pendingIntentFlags or PendingIntent.FLAG_NO_CREATE
        ) != null
    }
}
