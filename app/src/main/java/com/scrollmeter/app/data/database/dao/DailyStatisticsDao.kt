package com.scrollmeter.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatisticsDao {

    @Query("SELECT * FROM daily_statistics WHERE date = :localDate LIMIT 1")
    fun observeStatsForDate(localDate: String): Flow<DailyStatisticsEntity?>

    @Query("SELECT * FROM daily_statistics WHERE date = :localDate LIMIT 1")
    suspend fun getStatsForDate(localDate: String): DailyStatisticsEntity?

    @Query(
        """
        SELECT * FROM daily_statistics
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date ASC
        """
    )
    fun observeStatsInRange(startDate: String, endDate: String): Flow<List<DailyStatisticsEntity>>

    @Query("SELECT * FROM daily_statistics ORDER BY date DESC")
    fun observeAllDailyStats(): Flow<List<DailyStatisticsEntity>>

    @Query("SELECT * FROM daily_statistics ORDER BY date ASC")
    suspend fun getAllDailyStatsSnapshot(): List<DailyStatisticsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStats(stats: DailyStatisticsEntity)

    @Transaction
    suspend fun incrementConfirmedSwipe(
        localDate: String,
        isYouTube: Boolean,
        timestampMs: Long
    ): DailyStatisticsEntity {
        val existing = getStatsForDate(localDate) ?: DailyStatisticsEntity(date = localDate)
        val updatedYt = if (isYouTube) existing.youtubeSwipeCount + 1 else existing.youtubeSwipeCount
        val updatedIg = if (!isYouTube) existing.instagramSwipeCount + 1 else existing.instagramSwipeCount
        val updated = existing.copy(
            youtubeSwipeCount = updatedYt,
            instagramSwipeCount = updatedIg,
            totalSwipeCount = updatedYt + updatedIg,
            updatedAtTimestamp = timestampMs
        )
        upsertDailyStats(updated)
        return updated
    }

    @Transaction
    suspend fun incrementUncertainEvent(localDate: String, timestampMs: Long) {
        val existing = getStatsForDate(localDate) ?: DailyStatisticsEntity(date = localDate)
        val updated = existing.copy(
            uncertainEventCount = existing.uncertainEventCount + 1,
            updatedAtTimestamp = timestampMs
        )
        upsertDailyStats(updated)
    }

    @Transaction
    suspend fun addSessionDurationSeconds(
        localDate: String,
        isYouTube: Boolean,
        addedSeconds: Long,
        timestampMs: Long
    ): DailyStatisticsEntity {
        val safeDelta = addedSeconds.coerceAtLeast(0L)
        val existing = getStatsForDate(localDate) ?: DailyStatisticsEntity(date = localDate)
        val updated = existing.copy(
            youtubeDuration = if (isYouTube) existing.youtubeDuration + safeDelta else existing.youtubeDuration,
            instagramDuration = if (!isYouTube) existing.instagramDuration + safeDelta else existing.instagramDuration,
            updatedAtTimestamp = timestampMs
        )
        upsertDailyStats(updated)
        return updated
    }

    @Query("DELETE FROM daily_statistics WHERE date = :localDate")
    suspend fun deleteStatsByDate(localDate: String)

    @Query("DELETE FROM daily_statistics WHERE date BETWEEN :startDate AND :endDate")
    suspend fun deleteStatsInRange(startDate: String, endDate: String)

    @Query("DELETE FROM daily_statistics")
    suspend fun deleteAllStats()
}
