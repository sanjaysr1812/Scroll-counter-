package com.scrollmeter.app.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollmeter.app.data.database.entities.SwipeEventEntity
import kotlinx.coroutines.flow.Flow

data class HourlyBucketCount(
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int,
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "swipe_count") val swipeCount: Int
)

@Dao
interface SwipeEventDao {

    /**
     * Inserts a new [SwipeEventEntity].
     * Uses OnConflictStrategy.IGNORE on the unique (platform, event_signature_hash) index
     * to guarantee duplicate events are never inserted.
     *
     * @return rowId > 0 if inserted, or -1L if rejected as duplicate.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSwipeEvent(event: SwipeEventEntity): Long

    @Query(
        """
        SELECT * FROM swipe_events
        WHERE local_date = :localDate AND is_confirmed_swipe = 1
        ORDER BY event_timestamp DESC
        LIMIT :limit
        """
    )
    fun observeRecentSwipesForDate(localDate: String, limit: Int = 50): Flow<List<SwipeEventEntity>>

    @Query(
        """
        SELECT hour_of_day, platform, COUNT(*) AS swipe_count
        FROM swipe_events
        WHERE local_date BETWEEN :startDate AND :endDate
          AND is_confirmed_swipe = 1
        GROUP BY hour_of_day, platform
        ORDER BY hour_of_day ASC
        """
    )
    fun observeHourlyDistribution(startDate: String, endDate: String): Flow<List<HourlyBucketCount>>

    @Query("SELECT * FROM swipe_events ORDER BY event_timestamp ASC")
    suspend fun getAllEventsSnapshot(): List<SwipeEventEntity>

    @Query("DELETE FROM swipe_events WHERE local_date = :localDate")
    suspend fun deleteEventsByDate(localDate: String)

    @Query("DELETE FROM swipe_events WHERE local_date BETWEEN :startDate AND :endDate")
    suspend fun deleteEventsInRange(startDate: String, endDate: String)

    @Query("DELETE FROM swipe_events")
    suspend fun deleteAllEvents()
}
