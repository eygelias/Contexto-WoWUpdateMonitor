package com.wowmonitor.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val appName: String,
    val apkName: String,
    val apkUrl: String,
    val required: Boolean,
    val message: String
)

object AppUpdater {
    private const val WORKER_URL = "https://orange-meadow-c3f6.eygelias.workers.dev"
    private const val PREFS = "app_update"

    fun save(context: Context, info: AppUpdateInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("versionCode", info.versionCode)
            .putString("versionName", info.versionName)
            .putString("appName", info.appName)
            .putString("apkName", info.apkName)
            .putString("apkUrl", info.apkUrl)
            .putBoolean("required", info.required)
            .putString("message", info.message)
            .apply()
    }

    fun getSaved(context: Context): AppUpdateInfo? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = p.getInt("versionCode", 0)
        if (code == 0) return null
        return AppUpdateInfo(
            code,
            p.getString("versionName", "") ?: "",
            p.getString("appName", "WoWUpdateMonitor") ?: "WoWUpdateMonitor",
            p.getString("apkName", "WoWUpdateMonitor.apk") ?: "WoWUpdateMonitor.apk",
            p.getString("apkUrl", "") ?: "",
            p.getBoolean("required", true),
            p.getString("message", "Actualización obligatoria disponible") ?: "Actualización obligatoria disponible"
        )
    }

    fun fromJson(json: String): AppUpdateInfo {
        val o = JSONObject(json)
        return AppUpdateInfo(
            o.optInt("versionCode", 0),
            o.optString("versionName", ""),
            o.optString("appName", "WoWUpdateMonitor"),
            o.optString("apkName", "WoWUpdateMonitor.apk"),
            o.optString("apkUrl", ""),
            o.optBoolean("required", true),
            o.optString("message", "Actualización obligatoria disponible")
        )
    }

    fun fromMap(data: Map<String, String>) = AppUpdateInfo(
        data["versionCode"]?.toIntOrNull() ?: 0,
        data["versionName"].orEmpty(),
        data["appName"] ?: "WoWUpdateMonitor",
        data["apkName"] ?: "WoWUpdateMonitor.apk",
        data["apkUrl"].orEmpty(),
        data["required"] != "false",
        data["message"] ?: "Actualización obligatoria disponible"
    )

    fun fetchLatest(): AppUpdateInfo {
        val conn = URL("$WORKER_URL/app-version").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        return try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            fromJson(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    fun apkFile(context: Context, info: AppUpdateInfo): File {
        val safeName = info.apkName.ifBlank { "WoWUpdateMonitor-${info.versionName}.apk" }
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        return File(context.getExternalFilesDir(null), safeName)
    }

    fun download(context: Context, info: AppUpdateInfo, onProgress: (Int) -> Unit): File {
        val out = apkFile(context, info)
        if (out.exists() && out.length() > 0) return out
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        return try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLength.takeIf { it > 0 } ?: -1
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(((done * 100) / total).toInt())
                    }
                }
            }
            out
        } finally {
            conn.disconnect()
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
