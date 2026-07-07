package com.wowmonitor.app.data

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class NetworkMonitor {

    companion object {
        private const val TAG = "NetworkMonitor"
        private const val WORKER_URL = "https://orange-meadow-c3f6.eygelias.workers.dev/fetch"
    }

    /**
     * Fetch all game data from the Cloudflare Worker (HTTPS, JSON).
     */
    fun fetchAll(): List<GameData> {
        Log.i(TAG, "=== fetchAll via Worker ===")

        val json = httpGet(WORKER_URL)
        if (json == null) {
            Log.e(TAG, "Worker returned null")
            return emptyList()
        }

        return try {
            val root = JSONObject(json)
            val results = mutableListOf<GameData>()

            for (key in listOf("anniversary", "mop", "era")) {
                if (!root.has(key)) continue
                val gameObj = root.getJSONObject(key)
                val gameName = gameObj.getString("name")
                val regionsArray = gameObj.getJSONArray("regions")

                val regions = mutableListOf<GameRegionData>()
                for (i in 0 until regionsArray.length()) {
                    val r = regionsArray.getJSONObject(i)
                    regions.add(GameRegionData(
                        region = r.getString("region"),
                        versionsLine = r.optString("versionsLine", ""),
                        cdnsLine = r.optString("cdnsLine", ""),
                        buildVersion = r.optString("buildVersion", ""),
                        buildNumber = r.optString("buildNumber", ""),
                        buildConfig = r.optString("buildConfig", ""),
                        cdnHosts = r.optString("cdnHosts", ""),
                        cdnPath = r.optString("cdnPath", "")
                    ))
                }

                results.add(GameData(key, gameName, regions))
                Log.i(TAG, "✅ $gameName: ${regions.size} regions")
            }

            Log.i(TAG, "=== fetchAll DONE: ${results.size} games ===")
            results
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
            emptyList()
        }
    }

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "WoWUpdateMonitor/2.0")

            val code = conn.responseCode
            Log.i(TAG, "GET $urlStr → $code")

            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val text = reader.readText()
                reader.close()
                return text
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "httpGet failed: ${e.message}")
            return null
        } finally {
            conn?.disconnect()
        }
    }

    fun shutdown() {}
}
