package com.scrollmeter.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scrollmeter.app.data.database.dao.DailyStatisticsDao
import com.scrollmeter.app.data.database.dao.SwipeEventDao
import com.scrollmeter.app.data.database.dao.UsageSessionDao
import com.scrollmeter.app.data.database.dao.UserSettingsDao
import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import com.scrollmeter.app.data.database.entities.SwipeEventEntity
import com.scrollmeter.app.data.database.entities.UsageSessionEntity
import com.scrollmeter.app.data.database.entities.UserSettingsEntity

/**
 * Primary offline Room database for ScrollMeter.
 *
 * Includes explicit schema migrations so user history survives app updates and reboots.
 */
@Database(
    entities = [
        SwipeEventEntity::class,
        UsageSessionEntity::class,
        DailyStatisticsEntity::class,
        UserSettingsEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class ScrollMeterDatabase : RoomDatabase() {

    abstract fun swipeEventDao(): SwipeEventDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun dailyStatisticsDao(): DailyStatisticsDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        private const val DATABASE_NAME = "scrollmeter_offline.db"

        /**
         * Explicit non-destructive migration from version 1 to version 2:
         * Adds uncertain_event_count to daily_statistics and cooldown_ms to user_settings,
         * and enforces the unique index on (platform, event_signature_hash) in swipe_events.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_statistics ADD COLUMN uncertain_event_count INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN cooldown_ms INTEGER NOT NULL DEFAULT 450"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_swipe_events_platform_event_signature_hash ON swipe_events(platform, event_signature_hash)"
                )
            }
        }

        @Volatile
        private var INSTANCE: ScrollMeterDatabase? = null

        fun getInstance(context: Context): ScrollMeterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScrollMeterDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
