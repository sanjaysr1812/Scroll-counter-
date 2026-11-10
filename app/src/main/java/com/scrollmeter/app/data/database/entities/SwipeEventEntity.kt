package com.scrollmeter.app.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing each accepted or diagnostic swipe event.
 *
 * Enforces a UNIQUE index on (platform, eventSignatureHash) so duplicate event bursts
 * are rejected at the database constraint level as well as in memory.
 */
@Entity(
    tableName = "swipe_events",
    indices = [
        Index(value = ["local_date", "platform"]),
        Index(value = ["tracking_session_id"]),
        Index(value = ["platform", "event_signature_hash"], unique = true)
    ]
)
data class SwipeEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "platform")
    val platform: String,

    @ColumnInfo(name = "event_timestamp")
    val eventTimestamp: Long,

    @ColumnInfo(name = "local_date")
    val localDate: String,

    @ColumnInfo(name = "hour_of_day")
    val hourOfDay: Int,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "is_confirmed_swipe")
    val isConfirmedSwipe: Boolean,

    @ColumnInfo(name = "tracking_session_id")
    val trackingSessionId: Long,

    @ColumnInfo(name = "event_signature_hash")
    val eventSignatureHash: String
)
