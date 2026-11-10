package com.scrollmeter.app.data.repository

import com.scrollmeter.app.accessibility.DetectionConfidence
import com.scrollmeter.app.accessibility.FeedContext
import com.scrollmeter.app.data.database.ScrollMeterDatabase
import com.scrollmeter.app.data.database.dao.HourlyBucketCount
import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import com.scrollmeter.app.data.database.entities.SwipeEventEntity
import com.scrollmeter.app.data.database.entities.UsageSessionEntity
import com.scrollmeter.app.data.database.entities.UserSettingsEntity
import com.scrollmeter.app.domain.models.Platform
import com.scrollmeter.app.notifications.ScrollMeterNotificationManager
import com.scrollmeter.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZoneId

/**
 * Single source of truth for ScrollMeter data operations.
 * Coordinates Room database entities, DataStore Preferences, timezone recalculation,
 * threshold alerts, CSV export, and data deletion controls.
 */
class ScrollMeterRepository(
    private val database: ScrollMeterDatabase,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val notificationManager: ScrollMeterNotificationManager
) {
    private val swipeEventDao = database.swipeEventDao()
    private val usageSessionDao = database.usageSessionDao()
    private val dailyStatisticsDao = database.dailyStatisticsDao()
    private val userSettingsDao = database.userSettingsDao()

    val fastPreferencesFlow: Flow<FastServicePreferences> = preferencesDataStore.preferencesFlow

    fun observeUserSettings(): Flow<UserSettingsEntity> {
        return userSettingsDao.observeSettings().map { existing ->
            existing ?: UserSettingsEntity()
        }
    }

    suspend fun getUserSettingsSnapshot(): UserSettingsEntity {
        val existing = userSettingsDao.getSettingsSnapshot()
        if (existing != null) return existing
        val defaultSettings = UserSettingsEntity()
        userSettingsDao.upsertSettings(defaultSettings)
        return defaultSettings
    }

    suspend fun updateUserSettings(updated: UserSettingsEntity) {
        userSettingsDao.upsertSettings(updated.copy(id = 1))
        preferencesDataStore.setTrackingEnabled(updated.trackingEnabled)
        preferencesDataStore.setCooldownMs(updated.cooldownMs)
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        preferencesDataStore.setOnboardingCompleted(completed)
    }

    fun observeTodayStatistics(localDate: String = DateTimeUtils.currentLocalDateString()): Flow<DailyStatisticsEntity> {
        return dailyStatisticsDao.observeStatsForDate(localDate).map { stats ->
            stats ?: DailyStatisticsEntity(date = localDate)
        }
    }

    fun observeTodayRecentSwipes(
        localDate: String = DateTimeUtils.currentLocalDateString(),
        limit: Int = 40
    ): Flow<List<SwipeEventEntity>> {
        return swipeEventDao.observeRecentSwipesForDate(localDate, limit)
    }

    fun observeTodaySessions(
        localDate: String = DateTimeUtils.currentLocalDateString()
    ): Flow<List<UsageSessionEntity>> {
        return usageSessionDao.observeSessionsForDate(localDate)
    }

    fun observeStatisticsInRange(
        startDate: String,
        endDate: String
    ): Flow<List<DailyStatisticsEntity>> {
        return dailyStatisticsDao.observeStatsInRange(startDate, endDate)
    }

    fun observeAllDailyStatistics(): Flow<List<DailyStatisticsEntity>> {
        return dailyStatisticsDao.observeAllDailyStats()
    }

    fun observeHourlyDistribution(
        startDate: String,
        endDate: String
    ): Flow<List<HourlyBucketCount>> {
        return swipeEventDao.observeHourlyDistribution(startDate, endDate)
    }

    /**
     * Records a confirmed vertical swipe event and updates today's [DailyStatisticsEntity]
     * only if the event signature hash is not a duplicate. Also evaluates daily limit thresholds.
     */
    suspend fun recordConfirmedSwipe(
        platform: Platform,
        timestampMs: Long,
        eventTypeLabel: String,
        confidence: DetectionConfidence,
        sessionId: Long,
        eventSignatureHash: String
    ): Boolean {
        val localDate = DateTimeUtils.timestampToLocalDateString(timestampMs)
        val hourOfDay = DateTimeUtils.timestampToHourOfDay(timestampMs)

        val rowId = swipeEventDao.insertSwipeEvent(
            SwipeEventEntity(
                platform = platform.id,
                eventTimestamp = timestampMs,
                localDate = localDate,
                hourOfDay = hourOfDay,
                eventType = eventTypeLabel,
                confidence = confidence.score,
                isConfirmedSwipe = true,
                trackingSessionId = sessionId,
                eventSignatureHash = eventSignatureHash
            )
        )

        if (rowId <= 0L) {
            // Rejected by SQLite unique constraint on (platform, event_signature_hash)
            return false
        }

        val updatedStats = dailyStatisticsDao.incrementConfirmedSwipe(
            localDate = localDate,
            isYouTube = platform == Platform.YOUTUBE_SHORTS,
            timestampMs = timestampMs
        )

        evaluateAndNotifyThresholds(localDate, updatedStats)
        return true
    }

    /**
     * Records an uncertain event strictly in the diagnostic counter without inflating
     * the user's confirmed swipe total.
     */
    suspend fun recordUncertainEvent(timestampMs: Long) {
        val localDate = DateTimeUtils.timestampToLocalDateString(timestampMs)
        dailyStatisticsDao.incrementUncertainEvent(localDate, timestampMs)
    }

    /**
     * Opens a new short-video usage session when the user enters YouTube Shorts or Instagram Reels.
     */
    suspend fun startUsageSession(
        platform: Platform,
        feedContext: FeedContext,
        startTimestampMs: Long,
        confidenceScore: Float
    ): Long {
        val localDate = DateTimeUtils.timestampToLocalDateString(startTimestampMs)
        return usageSessionDao.insertSession(
            UsageSessionEntity(
                platform = platform.id,
                startTimestamp = startTimestampMs,
                endTimestamp = startTimestampMs,
                localDate = localDate,
                durationSeconds = 0L,
                feedContext = feedContext.name,
                trackingConfidence = confidenceScore,
                lastActivityTimestamp = startTimestampMs,
                isClosed = false
            )
        )
    }

    /**
     * Closes or flushes an active short-video usage session, splitting cleanly across midnight
     * boundaries if the session crossed 00:00:00 in the device's local timezone.
     */
    suspend fun finalizeUsageSession(
        sessionId: Long,
        endTimestampMs: Long
    ) {
        val existing = usageSessionDao.getSessionById(sessionId) ?: return
        if (existing.isClosed) return

        val safeEndMs = maxOf(existing.startTimestamp, endTimestampMs)
        val segments = DateTimeUtils.splitSessionAcrossMidnight(
            startTimestampMs = existing.startTimestamp,
            endTimestampMs = safeEndMs
        )

        val isYouTube = existing.platform == Platform.YOUTUBE_SHORTS.id

        if (segments.isEmpty()) {
            usageSessionDao.updateSession(
                existing.copy(
                    endTimestamp = safeEndMs,
                    durationSeconds = 0L,
                    lastActivityTimestamp = safeEndMs,
                    isClosed = true
                )
            )
            return
        }

        // Update the first segment in place
        val firstSeg = segments.first()
        usageSessionDao.updateSession(
            existing.copy(
                localDate = firstSeg.localDate,
                startTimestamp = firstSeg.startTimestampMs,
                endTimestamp = firstSeg.endTimestampMs,
                durationSeconds = firstSeg.durationSeconds,
                lastActivityTimestamp = firstSeg.endTimestampMs,
                isClosed = true
            )
        )
        dailyStatisticsDao.addSessionDurationSeconds(
            localDate = firstSeg.localDate,
            isYouTube = isYouTube,
            addedSeconds = firstSeg.durationSeconds,
            timestampMs = firstSeg.endTimestampMs
        )

        // If the session crossed midnight, insert additional closed segments for subsequent local days
        for (i in 1 until segments.size) {
            val seg = segments[i]
            usageSessionDao.insertSession(
                UsageSessionEntity(
                    platform = existing.platform,
                    startTimestamp = seg.startTimestampMs,
                    endTimestamp = seg.endTimestampMs,
                    localDate = seg.localDate,
                    durationSeconds = seg.durationSeconds,
                    feedContext = existing.feedContext,
                    trackingConfidence = existing.trackingConfidence,
                    lastActivityTimestamp = seg.endTimestampMs,
                    isClosed = true
                )
            )
            val updatedDaily = dailyStatisticsDao.addSessionDurationSeconds(
                localDate = seg.localDate,
                isYouTube = isYouTube,
                addedSeconds = seg.durationSeconds,
                timestampMs = seg.endTimestampMs
            )
            evaluateAndNotifyThresholds(seg.localDate, updatedDaily)
        }
    }

    /**
     * Closes any unclosed sessions left over after an app crash, service interruption, or reboot
     * using each session's lastActivityTimestamp so time is never fabricated.
     */
    suspend fun recoverOrphanedSessions() {
        val openSessions = usageSessionDao.getOpenSessions()
        for (session in openSessions) {
            val recoveryEndMs = maxOf(session.startTimestamp, session.lastActivityTimestamp)
            finalizeUsageSession(session.id, recoveryEndMs)
        }
    }

    /**
     * Recalculates daily statistics according to the device's current local timezone if the user
     * traveled across timezones or experienced a timezone change.
     */
    suspend fun recalculateDailyStatsForCurrentTimezone(zoneId: ZoneId = ZoneId.systemDefault()) {
        val allEvents = swipeEventDao.getAllEventsSnapshot()
        val allSessions = usageSessionDao.getAllSessionsSnapshot()

        val statsByDate = linkedMapOf<String, DailyStatisticsEntity>()

        for (event in allEvents) {
            val recalcDate = DateTimeUtils.timestampToLocalDateString(event.eventTimestamp, zoneId)
            val current = statsByDate.getOrPut(recalcDate) { DailyStatisticsEntity(date = recalcDate) }
            val isYt = event.platform == Platform.YOUTUBE_SHORTS.id
            statsByDate[recalcDate] = if (event.isConfirmedSwipe) {
                val newYt = if (isYt) current.youtubeSwipeCount + 1 else current.youtubeSwipeCount
                val newIg = if (!isYt) current.instagramSwipeCount + 1 else current.instagramSwipeCount
                current.copy(
                    youtubeSwipeCount = newYt,
                    instagramSwipeCount = newIg,
                    totalSwipeCount = newYt + newIg,
                    updatedAtTimestamp = maxOf(current.updatedAtTimestamp, event.eventTimestamp)
                )
            } else {
                current.copy(
                    uncertainEventCount = current.uncertainEventCount + 1,
                    updatedAtTimestamp = maxOf(current.updatedAtTimestamp, event.eventTimestamp)
                )
            }
        }

        for (session in allSessions) {
            if (!session.isClosed || session.durationSeconds <= 0L) continue
            val segments = DateTimeUtils.splitSessionAcrossMidnight(
                startTimestampMs = session.startTimestamp,
                endTimestampMs = session.endTimestamp,
                zoneId = zoneId
            )
            val isYt = session.platform == Platform.YOUTUBE_SHORTS.id
            for (seg in segments) {
                val current = statsByDate.getOrPut(seg.localDate) { DailyStatisticsEntity(date = seg.localDate) }
                statsByDate[seg.localDate] = current.copy(
                    youtubeDuration = if (isYt) current.youtubeDuration + seg.durationSeconds else current.youtubeDuration,
                    instagramDuration = if (!isYt) current.instagramDuration + seg.durationSeconds else current.instagramDuration,
                    updatedAtTimestamp = maxOf(current.updatedAtTimestamp, seg.endTimestampMs)
                )
            }
        }

        dailyStatisticsDao.deleteAllStats()
        for (entry in statsByDate.values) {
            dailyStatisticsDao.upsertDailyStats(entry)
        }
        preferencesDataStore.setLastKnownZoneId(zoneId.id)
    }

    private suspend fun evaluateAndNotifyThresholds(
        localDate: String,
        stats: DailyStatisticsEntity
    ) {
        val settings = getUserSettingsSnapshot()
        if (!settings.notificationEnabled) return

        val fastPrefs = preferencesDataStore.preferencesFlow.first()
        val highestSentToday = if (fastPrefs.lastAlertDate == localDate) {
            fastPrefs.highestNotifiedThresholdPercent
        } else {
            0
        }

        val combinedRatio = if (settings.dailyCombinedLimit > 0) {
            (stats.totalSwipeCount * 100) / settings.dailyCombinedLimit
        } else 0

        val ytRatio = if (settings.youtubeDailyLimit > 0) {
            (stats.youtubeSwipeCount * 100) / settings.youtubeDailyLimit
        } else 0

        val igRatio = if (settings.instagramDailyLimit > 0) {
            (stats.instagramSwipeCount * 100) / settings.instagramDailyLimit
        } else 0

        val totalMinutes = (stats.youtubeDuration + stats.instagramDuration) / 60L
        val timeRatio = if (settings.timeLimitMinutes > 0) {
            ((totalMinutes * 100) / settings.timeLimitMinutes).toInt()
        } else 0

        val maxRatio = maxOf(combinedRatio, ytRatio, igRatio, timeRatio)

        val candidateThresholds = buildList {
            if (settings.warnAt50) add(50)
            if (settings.warnAt75) add(75)
            if (settings.warnAt90) add(90)
            if (settings.warnAt100) add(100)
        }.sorted()

        val newlyCrossed = candidateThresholds.lastOrNull { threshold ->
            maxRatio >= threshold && threshold > highestSentToday
        } ?: return

        val title = if (newlyCrossed >= 100) {
            "ScrollMeter: Daily Limit Reached (100%)"
        } else {
            "ScrollMeter: $newlyCrossed% of Daily Limit Reached"
        }

        val body = buildString {
            append("Today: ${stats.totalSwipeCount} / ${settings.dailyCombinedLimit} confirmed swipes ")
            append("(YouTube Shorts: ${stats.youtubeSwipeCount}, Instagram Reels: ${stats.instagramSwipeCount}). ")
            append("Estimated feed time: ${DateTimeUtils.formatDurationHumanReadable(stats.youtubeDuration + stats.instagramDuration)}.")
        }

        notificationManager.sendThresholdAlertIfPermitted(
            thresholdPercent = newlyCrossed,
            currentSwipes = stats.totalSwipeCount,
            dailyLimit = settings.dailyCombinedLimit,
            reasonTitle = title,
            reasonBody = body
        )

        preferencesDataStore.recordThresholdAlertSent(localDate, newlyCrossed)
    }

    /**
     * Generates a RFC-4180 compliant CSV export of all recorded daily statistics.
     */
    suspend fun exportDailyStatisticsCsv(): String {
        val rows = dailyStatisticsDao.getAllDailyStatsSnapshot()
        val sb = StringBuilder()
        sb.appendLine("date,youtube_shorts_swipes,instagram_reels_swipes,total_confirmed_swipes,uncertain_events,youtube_duration_seconds,instagram_duration_seconds,total_duration_seconds")
        for (row in rows) {
            val totalDuration = row.youtubeDuration + row.instagramDuration
            sb.appendLine(
                "${row.date},${row.youtubeSwipeCount},${row.instagramSwipeCount},${row.totalSwipeCount},${row.uncertainEventCount},${row.youtubeDuration},${row.instagramDuration},$totalDuration"
            )
        }
        return sb.toString()
    }

    suspend fun deleteTodayData(todayIso: String = DateTimeUtils.currentLocalDateString()) {
        swipeEventDao.deleteEventsByDate(todayIso)
        usageSessionDao.deleteSessionsByDate(todayIso)
        dailyStatisticsDao.deleteStatsByDate(todayIso)
        preferencesDataStore.resetAlertHistory()
    }

    suspend fun deleteDateRangeData(startDate: String, endDate: String) {
        swipeEventDao.deleteEventsInRange(startDate, endDate)
        usageSessionDao.deleteSessionsInRange(startDate, endDate)
        dailyStatisticsDao.deleteStatsInRange(startDate, endDate)
    }

    suspend fun deleteAllData() {
        swipeEventDao.deleteAllEvents()
        usageSessionDao.deleteAllSessions()
        dailyStatisticsDao.deleteAllStats()
        preferencesDataStore.resetAlertHistory()
        DiagnosticsStateHolder.resetSessionCounters()
    }
}
