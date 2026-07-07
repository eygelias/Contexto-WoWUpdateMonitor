package com.wowmonitor.app.service

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages region notification preferences.
 * Users can select which regions they want to receive push notifications for.
 */
object RegionPrefs {

    private const val PREFS_NAME = "wow_region_prefs"
    private const val KEY_SELECTED = "selected_regions"

    val ALL_REGIONS = listOf("us", "eu", "cn", "tw", "kr", "sg")

    val REGION_DISPLAY = mapOf(
        "us" to "🌎 Americas",
        "eu" to "🇪🇺 Europe",
        "cn" to "🇨🇳 China",
        "tw" to "🇹🇼 Taiwan",
        "kr" to "🇰🇷 Korea",
        "sg" to "🌏 Southeast Asia"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get selected region codes. Default: all regions.
     */
    fun getSelectedRegions(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return prefs.getStringSet(KEY_SELECTED, null) ?: ALL_REGIONS.toSet()
    }

    /**
     * Set selected region codes.
     */
    fun setSelectedRegions(context: Context, regions: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_SELECTED, regions)
            .apply()
    }

    /**
     * Toggle a single region.
     */
    fun toggleRegion(context: Context, region: String) {
        val current = getSelectedRegions(context).toMutableSet()
        if (current.contains(region)) {
            current.remove(region)
        } else {
            current.add(region)
        }
        // Must have at least one region
        if (current.isEmpty()) current.add("us")
        setSelectedRegions(context, current)
    }

    /**
     * Check if a specific region is selected.
     */
    fun isRegionSelected(context: Context, region: String): Boolean {
        return getSelectedRegions(context).contains(region)
    }

    /**
     * Update the Worker with current preferences.
     */
    fun syncWithWorker(context: Context) {
        Thread {
            try {
                val tokenTask = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                tokenTask.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        MyFirebaseMessagingService.registerToken(context, task.result)
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }
}
