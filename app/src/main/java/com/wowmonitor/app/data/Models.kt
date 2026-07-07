package com.wowmonitor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "version_history")
data class VersionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameKey: String,
    val gameName: String,
    val region: String,
    val versionsLine: String,
    val cdnsLine: String,
    val buildVersion: String,
    val buildNumber: String,
    val buildConfig: String,
    val cdnHosts: String,
    val cdnPath: String,
    val detectedAt: Long = System.currentTimeMillis(),
    val previousVersion: String? = null
)

data class GameRegionData(
    val region: String,
    val versionsLine: String,
    val cdnsLine: String,
    val buildVersion: String,
    val buildNumber: String,
    val buildConfig: String,
    val cdnHosts: String,
    val cdnPath: String
)

data class GameData(
    val key: String,
    val name: String,
    val regions: List<GameRegionData>
)

data class VersionChange(
    val gameName: String,
    val region: String,
    val oldBuild: String,
    val newBuild: String
)

data class CdnChange(
    val gameName: String,
    val region: String,
    val oldHosts: String,
    val newHosts: String
)
