package com.scrollmeter.app.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity tracking each short-video feed usage session.
 *
 * Only records time spent inside verified YouTube Shorts or Instagram Reels feed contexts.
 * Never counts general browsing on YouTube/Instagram home, DMs, or comments as short-video usage.
 */
@Entity(
    tableName = "usage_sessions",
    indices = [
        Index(value = ["local_date", "platform"]),
        Index(value = ["is_closed"])
    ]
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "platform")
    val platform: String,

    @ColumnInfo(name = "start_timestamp")
    val startTimestamp: Long,

    @ColumnInfo(name = "end_timestamp")
    val endTimestamp: Long,

    @ColumnInfo(name = "local_date")
    val localDate: String,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,

    @ColumnInfo(name = "feed_context")
    val feedContext: String,

    @ColumnInfo(name = "tracking_confidence")
    val trackingConfidence: Float,

    @ColumnInfo(name = "last_activity_timestamp")
    val lastActivityTimestamp: Long,

    @ColumnInfo(name = "is_closed")
    val isClosed: Boolean
)
