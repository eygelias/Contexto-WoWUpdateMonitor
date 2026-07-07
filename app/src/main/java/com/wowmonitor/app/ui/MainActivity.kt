package com.wowmonitor.app.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.firebase.messaging.FirebaseMessaging
import com.wowmonitor.app.BuildConfig
import com.wowmonitor.app.R
import com.wowmonitor.app.data.AppDatabase
import com.wowmonitor.app.data.ChangeDetector
import com.wowmonitor.app.data.VersionEntry
import com.wowmonitor.app.databinding.ActivityMainBinding
import com.wowmonitor.app.databinding.CardGameBinding
import com.wowmonitor.app.service.AppUpdateInfo
import com.wowmonitor.app.service.AppUpdater
import com.wowmonitor.app.service.MyFirebaseMessagingService
import com.wowmonitor.app.service.RegionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val WORKER_URL = "https://orange-meadow-c3f6.eygelias.workers.dev"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var historyVisible = false
    private var updateDialogShowing = false
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = AppDatabase.getInstance(this)
        setupRecyclerViews()
        setupToggle()
        setupTools()
        setupRegionChips()
        setupNotifications()
        requestPermissions()
        setupFCM()
        checkForAppUpdate()
    }

    override fun onResume() {
        super.onResume()
        loadCurrentVersions()
        loadNotifications()
        enforceSavedAppUpdate()
    }

    // ─── FCM ────────────────────────────────────────────

    private fun setupFCM() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) { binding.statusText.text = "⚠️ FCM error"; return@addOnCompleteListener }
            MyFirebaseMessagingService.registerToken(this, task.result)
            binding.statusText.text = "🟢 FCM Push activo"
        }
    }

    // ─── App Updater ─────────────────────────────────────

    private fun checkForAppUpdate() {
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { AppUpdater.fetchLatest() }
                AppUpdater.save(this@MainActivity, info)
                showUpdateIfNeeded(info)
            } catch (_: Exception) {
                enforceSavedAppUpdate()
            }
        }
    }

    private fun enforceSavedAppUpdate() {
        AppUpdater.getSaved(this)?.let { showUpdateIfNeeded(it) }
    }

    private fun showUpdateIfNeeded(info: AppUpdateInfo) {
        if (updateDialogShowing || info.versionCode <= BuildConfig.VERSION_CODE || info.apkUrl.isBlank()) return
        updateDialogShowing = true
        val file = AppUpdater.apkFile(this, info)
        val msg = StringBuilder()
            .appendLine("${info.appName} ${info.versionName}")
            .appendLine(info.message)
            .appendLine()
            .append(if (file.exists()) "✅ Actualización lista para instalar." else "Descarga obligatoria para seguir usando la app.")
            .toString()

        val dialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("🔔 Nueva versión disponible")
            .setMessage(msg)
            .setCancelable(!info.required)
            .setPositiveButton(if (file.exists()) "Instalar" else "Descargar", null)
            .apply { if (!info.required) setNegativeButton("Luego", null) }
            .create()
        dialog.setOnDismissListener { updateDialogShowing = false }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.gold))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (file.exists()) {
                AppUpdater.install(this, file)
                return@setOnClickListener
            }
            dialog.setMessage("Descargando ${info.apkName}... 0%")
            lifecycleScope.launch {
                try {
                    val apk = withContext(Dispatchers.IO) {
                        AppUpdater.download(this@MainActivity, info) { p ->
                            runOnUiThread { dialog.setMessage("Descargando ${info.apkName}... $p%") }
                        }
                    }
                    dialog.setMessage("✅ Actualización descargada. Toca Instalar.")
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "Instalar"
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { AppUpdater.install(this@MainActivity, apk) }
                } catch (e: Exception) {
                    dialog.setMessage("❌ Error descargando actualización:\n${e.message}")
                }
            }
        }
    }

    // ─── Notifications Section ──────────────────────────

    private fun setupNotifications() {
        // Chat list is populated in loadNotifications().
    }

    private fun loadNotifications() {
        val notifs = MyFirebaseMessagingService.getNotifications(this)
        val chat = findViewById<LinearLayout>(R.id.notifChat)
        val scroll = findViewById<ScrollView>(R.id.notifScroll)
        chat.removeAllViews()

        if (notifs.isEmpty()) {
            chat.addView(TextView(this).apply {
                text = "Sin notificaciones aún"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
                gravity = Gravity.CENTER
                setPadding(8, 80, 8, 80)
            })
            return
        }

        notifs.forEachIndexed { index, n ->
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 16, 24, 12)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card_bg))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 10)
                layoutParams = lp
                setOnLongClickListener {
                    PopupMenu(this@MainActivity, this).apply {
                        menu.add("Eliminar")
                        setOnMenuItemClickListener {
                            MyFirebaseMessagingService.deleteNotification(this@MainActivity, index)
                            loadNotifications()
                            true
                        }
                    }.show()
                    true
                }
            }
            bubble.addView(TextView(this).apply {
                text = n.first
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_green))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            if (n.second.isNotBlank()) bubble.addView(TextView(this).apply {
                text = n.second
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(0, 4, 0, 0)
            })
            bubble.addView(TextView(this).apply {
                text = n.third
                textSize = 10f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
                gravity = Gravity.END
                setPadding(0, 4, 0, 0)
            })
            chat.addView(bubble)
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ─── Region Chips ───────────────────────────────────

    private fun setupRegionChips() {
        val chipGroup = findViewById<com.google.android.material.chip.ChipGroup>(R.id.regionChipGroup)
        val selected = RegionPrefs.getSelectedRegions(this)
        for (region in RegionPrefs.ALL_REGIONS) {
            val chip = Chip(this).apply {
                text = RegionPrefs.REGION_DISPLAY[region] ?: region.uppercase()
                isCheckable = true
                isChecked = selected.contains(region)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.card_bg))
                setOnClickListener {
                    RegionPrefs.toggleRegion(this@MainActivity, region)
                    RegionPrefs.syncWithWorker(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Regiones actualizadas", Toast.LENGTH_SHORT).show()
                }
            }
            chipGroup.addView(chip)
        }
    }

    // ─── UI Setup ───────────────────────────────────────

    private fun setupRecyclerViews() {
        historyAdapter = HistoryAdapter()
        binding.historyRecycler.apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = historyAdapter }
    }

    private fun setupToggle() {
        binding.btnToggleHistory.setOnClickListener {
            historyVisible = !historyVisible
            binding.historySection.visibility = if (historyVisible) View.VISIBLE else View.GONE
            binding.btnToggleHistory.text = if (historyVisible) "📜 Ocultar Historial" else "📜 Ver Historial"
            if (historyVisible) loadHistory()
        }
    }

    private fun setupTools() {
        binding.btnDownloadVersions.setOnClickListener { showVersionsDialog(false) }
        binding.btnSimulate.setOnClickListener {
            Toast.makeText(this, "Cargando versiones falsas...", Toast.LENGTH_SHORT).show()
            showVersionsDialog(true)
        }
    }

    // ─── Download & Detect ──────────────────────────────

    private fun showVersionsDialog(useFake: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_versions, null)
        val contentText = dialogView.findViewById<TextView>(R.id.versionsContent)
        val loadingBar = dialogView.findViewById<View>(R.id.loadingBar)
        val dialog = AlertDialog.Builder(this, R.style.DialogTheme).setView(dialogView).setPositiveButton("Cerrar", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(this, R.color.gold))

        lifecycleScope.launch {
            loadingBar.visibility = View.VISIBLE
            contentText.text = "⏳ Consultando..."
            try {
                val games = withContext(Dispatchers.IO) { fetchGamesFromWorker(useFake) }
                if (games.isEmpty()) { contentText.text = "❌ Sin datos"; return@launch }

                val result = withContext(Dispatchers.IO) { ChangeDetector().detectChanges(db.versionDao(), games) }
                withContext(Dispatchers.IO) { result.newEntries.forEach { db.versionDao().insert(it) } }

                val sb = StringBuilder()
                var total = 0
                for (game in games) {
                    val emoji = when (game.key) { "anniversary" -> "🔥"; "mop" -> "🐼"; "era" -> "⚔️"; else -> "🎮" }
                    sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    sb.appendLine("$emoji ${game.name}")
                    sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    for (r in game.regions) {
                        val flag = mapOf("us" to "🌎", "eu" to "🇪🇺", "cn" to "🇨🇳", "tw" to "🇹🇼", "kr" to "🇰🇷", "sg" to "🌏")[r.region] ?: "🌍"
                        sb.appendLine("  $flag ${r.region.uppercase()} — Versión: ${r.buildVersion}")
                        total++
                    }
                    sb.appendLine()
                }
                if (result.versionChanges.isNotEmpty()) {
                    sb.appendLine("🔄 CAMBIOS DETECTADOS:")
                    for (c in result.versionChanges) {
                        val flag = mapOf("us" to "🌎", "eu" to "🇪🇺", "cn" to "🇨🇳", "tw" to "🇹🇼", "kr" to "🇰🇷", "sg" to "🌏")[c.region] ?: "🌍"
                        sb.appendLine("  $flag ${c.region.uppercase()}: ${c.oldBuild} ➡️ ${c.newBuild}")
                    }
                }
                sb.appendLine("📊 $total regiones — ${dateFormat.format(Date())}")
                contentText.text = sb.toString()
                binding.lastCheckText.text = "Última descarga: ${dateFormat.format(Date())}"
                loadCurrentVersions()
            } catch (e: Exception) { contentText.text = "❌ ${e.message}" }
            finally { loadingBar.visibility = View.GONE }
        }
    }

    private fun fetchGamesFromWorker(useFake: Boolean = false): List<com.wowmonitor.app.data.GameData> {
        var conn: HttpURLConnection? = null
        try {
            val urlStr = if (useFake) "$WORKER_URL/fetch?fake=1" else "$WORKER_URL/fetch"
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"; conn.connectTimeout = 15_000; conn.readTimeout = 15_000
            if (conn.responseCode != 200) return emptyList()
            val json = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val root = JSONObject(json)
            val games = mutableListOf<com.wowmonitor.app.data.GameData>()
            for (key in listOf("anniversary", "mop", "era")) {
                if (!root.has(key)) continue
                val obj = root.getJSONObject(key)
                val arr = obj.getJSONArray("regions")
                val regions = mutableListOf<com.wowmonitor.app.data.GameRegionData>()
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    regions.add(com.wowmonitor.app.data.GameRegionData(
                        r.getString("region"), r.optString("versionsLine",""), r.optString("cdnsLine",""),
                        r.optString("buildVersion",""), r.optString("buildNumber",""),
                        r.optString("buildConfig",""), r.optString("cdnHosts",""), r.optString("cdnPath","")
                    ))
                }
                games.add(com.wowmonitor.app.data.GameData(key, obj.getString("name"), regions))
            }
            return games
        } catch (e: Exception) { return emptyList() }
        finally { conn?.disconnect() }
    }

    // ─── Load Data ──────────────────────────────────────

    private fun loadCurrentVersions() {
        lifecycleScope.launch {
            try {
                val allLatest = db.versionDao().getAllLatest()
                val grouped = allLatest.groupBy { it.gameKey }
                populateCard(binding.cardAnniversary, grouped["anniversary"], "🔥 ANNIVERSARY / TBC", "World of Warcraft Anniversary")
                populateCard(binding.cardMop, grouped["mop"], "🐼 MOP CLASSIC", "Mists of Pandaria Classic")
                populateCard(binding.cardEra, grouped["era"], "⚔️ ERA CLASSIC", "WoW Classic Era")
            } catch (_: Exception) {}
        }
    }

    private fun populateCard(cardBinding: CardGameBinding, entries: List<VersionEntry>?, title: String, subtitle: String) {
        cardBinding.gameTitle.text = title
        if (entries.isNullOrEmpty()) {
            cardBinding.gameSubtitle.text = "⏳ Esperando primera revisión..."
            cardBinding.expandIcon.visibility = View.GONE
            cardBinding.regionsContainer.visibility = View.GONE
            return
        }
        val sorted = entries.sortedBy { when (it.region) { "us" -> 0; "eu" -> 1; "cn" -> 2; "tw" -> 3; "kr" -> 4; "sg" -> 5; else -> 9 } }
        cardBinding.gameSubtitle.text = "${sorted.size} regiones — toca para ver"
        cardBinding.expandIcon.visibility = View.VISIBLE
        val adapter = VersionAdapter()
        cardBinding.regionsRecycler.apply { layoutManager = LinearLayoutManager(this@MainActivity); this.adapter = adapter }
        adapter.submitList(sorted)
        cardBinding.regionsContainer.visibility = View.GONE
        cardBinding.expandIcon.text = "▶"
        cardBinding.gameHeader.setOnClickListener {
            if (cardBinding.regionsContainer.visibility == View.GONE) {
                cardBinding.regionsContainer.visibility = View.VISIBLE; cardBinding.expandIcon.text = "▼"
            } else {
                cardBinding.regionsContainer.visibility = View.GONE; cardBinding.expandIcon.text = "▶"
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try { historyAdapter.submitList(db.versionDao().getRecentHistory(50)) }
            catch (e: Exception) { Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}
