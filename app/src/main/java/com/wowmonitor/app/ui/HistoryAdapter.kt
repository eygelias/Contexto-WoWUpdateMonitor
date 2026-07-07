package com.wowmonitor.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wowmonitor.app.data.VersionEntry
import com.wowmonitor.app.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter : ListAdapter<VersionEntry, HistoryAdapter.ViewHolder>(DiffCallback()) {

    private val regionEmojis = mapOf(
        "us" to "🌎", "eu" to "🇪🇺", "cn" to "🇨🇳",
        "tw" to "🇹🇼", "kr" to "🇰🇷", "sg" to "🌏"
    )

    private val dateFormat = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            val emoji = regionEmojis[item.region] ?: "🌍"
            gameText.text = "${item.gameName} $emoji"

            // Show old ➡️ new if previousVersion exists
            if (!item.previousVersion.isNullOrEmpty() && item.previousVersion != item.buildVersion) {
                buildText.text = "📌 ${item.previousVersion} ➡️ ${item.buildVersion}"
            } else {
                buildText.text = "📌 Versión: ${item.buildVersion}"
            }

            timeText.text = dateFormat.format(Date(item.detectedAt))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<VersionEntry>() {
        override fun areItemsTheSame(a: VersionEntry, b: VersionEntry) = a.id == b.id
        override fun areContentsTheSame(a: VersionEntry, b: VersionEntry) = a == b
    }
}
