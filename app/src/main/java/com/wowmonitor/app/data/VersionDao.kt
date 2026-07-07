package com.wowmonitor.app.data

import androidx.room.*

@Dao
interface VersionDao {

    @Insert
    suspend fun insert(entry: VersionEntry)

    @Query("SELECT * FROM version_history ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 100): List<VersionEntry>

    @Query("""
        SELECT * FROM version_history 
        WHERE gameKey = :gameKey AND region = :region 
        ORDER BY detectedAt DESC LIMIT 1
    """)
    suspend fun getLatest(gameKey: String, region: String): VersionEntry?

    @Query("""
        SELECT * FROM version_history 
        GROUP BY gameKey, region 
        HAVING detectedAt = MAX(detectedAt)
        ORDER BY gameKey, region
    """)
    suspend fun getAllLatest(): List<VersionEntry>

    @Query("""
        SELECT * FROM version_history 
        WHERE gameKey = :gameKey 
        GROUP BY region 
        HAVING detectedAt = MAX(detectedAt)
        ORDER BY region
    """)
    suspend fun getLatestByGame(gameKey: String): List<VersionEntry>

    @Query("DELETE FROM version_history WHERE detectedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
