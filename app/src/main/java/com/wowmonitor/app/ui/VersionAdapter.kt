package com.wowmonitor.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wowmonitor.app.data.VersionEntry
import com.wowmonitor.app.databinding.ItemVersionBinding

class VersionAdapter : ListAdapter<VersionEntry, VersionAdapter.ViewHolder>(DiffCallback()) {

    private val regionEmojis = mapOf(
        "us" to "🌎", "eu" to "🇪🇺", "cn" to "🇨🇳",
        "tw" to "🇹🇼", "kr" to "🇰🇷", "sg" to "🌏"
    )

    private val regionNames = mapOf(
        "us" to "Americas", "eu" to "Europa", "cn" to "China",
        "tw" to "Taiwan", "kr" to "Korea", "sg" to "Sudeste Asiático"
    )

    inner class ViewHolder(val binding: ItemVersionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVersionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            val emoji = regionEmojis[item.region] ?: "🌍"
            val name = regionNames[item.region] ?: item.region.uppercase()
            regionText.text = "$emoji $name"

            if (!item.previousVersion.isNullOrEmpty() && item.previousVersion != item.buildVersion) {
                buildText.text = "📌 ${item.previousVersion} ➡️ ${item.buildVersion}"
            } else {
                buildText.text = "📌 Versión: ${item.buildVersion}"
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<VersionEntry>() {
        override fun areItemsTheSame(a: VersionEntry, b: VersionEntry) =
            a.gameKey == b.gameKey && a.region == b.region
        override fun areContentsTheSame(a: VersionEntry, b: VersionEntry) =
            a.buildNumber == b.buildNumber && a.previousVersion == b.previousVersion
    }
}
