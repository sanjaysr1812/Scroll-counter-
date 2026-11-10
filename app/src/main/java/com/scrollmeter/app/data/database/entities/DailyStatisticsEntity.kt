package com.scrollmeter.app.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing aggregated daily statistics keyed by local calendar date (YYYY-MM-DD).
 */
@Entity(tableName = "daily_statistics")
data class DailyStatisticsEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "youtube_swipe_count")
    val youtubeSwipeCount: Int = 0,

    @ColumnInfo(name = "instagram_swipe_count")
    val instagramSwipeCount: Int = 0,

    @ColumnInfo(name = "total_swipe_count")
    val totalSwipeCount: Int = 0,

    @ColumnInfo(name = "uncertain_event_count", defaultValue = "0")
    val uncertainEventCount: Int = 0,

    @ColumnInfo(name = "youtube_duration")
    val youtubeDuration: Long = 0L,

    @ColumnInfo(name = "instagram_duration")
    val instagramDuration: Long = 0L,

    @ColumnInfo(name = "updated_at_timestamp")
    val updatedAtTimestamp: Long = 0L
)
