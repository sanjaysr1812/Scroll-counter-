package com.scrollmeter.app.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing user-configured daily limits, alert thresholds, theme preference,
 * and tracking controls.
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "daily_combined_limit")
    val dailyCombinedLimit: Int = 250,

    @ColumnInfo(name = "youtube_daily_limit")
    val youtubeDailyLimit: Int = 150,

    @ColumnInfo(name = "instagram_daily_limit")
    val instagramDailyLimit: Int = 150,

    @ColumnInfo(name = "time_limit_minutes")
    val timeLimitMinutes: Int = 60,

    @ColumnInfo(name = "notification_enabled")
    val notificationEnabled: Boolean = true,

    @ColumnInfo(name = "warning_threshold")
    val warningThreshold: Int = 75,

    @ColumnInfo(name = "warn_at_50")
    val warnAt50: Boolean = true,

    @ColumnInfo(name = "warn_at_75")
    val warnAt75: Boolean = true,

    @ColumnInfo(name = "warn_at_90")
    val warnAt90: Boolean = true,

    @ColumnInfo(name = "warn_at_100")
    val warnAt100: Boolean = true,

    @ColumnInfo(name = "theme_preference")
    val themePreference: String = "SYSTEM",

    @ColumnInfo(name = "tracking_enabled")
    val trackingEnabled: Boolean = true,

    @ColumnInfo(name = "cooldown_ms", defaultValue = "450")
    val cooldownMs: Long = 450L
)
