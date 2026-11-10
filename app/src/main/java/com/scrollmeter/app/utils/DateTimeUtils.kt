package com.scrollmeter.app.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Timezone-aware date and session duration utilities.
 *
 * Ensures all daily statistics use the device's current local timezone, handles
 * Daylight Saving Time (DST) transitions via [ZonedDateTime], and splits active
 * usage sessions cleanly across local midnight boundaries without double-counting.
 */
object DateTimeUtils {

    private val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    data class MidnightSplitSegment(
        val localDate: String,
        val startTimestampMs: Long,
        val endTimestampMs: Long,
        val durationSeconds: Long
    )

    fun currentLocalDateString(zoneId: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zoneId).format(ISO_DATE_FORMATTER)

    fun timestampToLocalDateString(
        timestampMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val localDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
        return localDate.format(ISO_DATE_FORMATTER)
    }

    fun timestampToHourOfDay(
        timestampMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        return Instant.ofEpochMilli(timestampMs).atZone(zoneId).hour
    }

    fun formatTimestampTime(
        timestampMs: Long?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (timestampMs == null || timestampMs <= 0L) return "No events recorded yet"
        return Instant.ofEpochMilli(timestampMs)
            .atZone(zoneId)
            .toLocalTime()
            .format(TIME_FORMATTER)
    }

    fun formatLocalDateForDisplay(isoDate: String): String {
        return runCatching {
            LocalDate.parse(isoDate, ISO_DATE_FORMATTER).format(DISPLAY_DATE_FORMATTER)
        }.getOrDefault(isoDate)
    }

    fun formatDurationHumanReadable(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        if (safeSeconds == 0L) return "0m"
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun daysAgoIsoString(daysAgo: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return LocalDate.now(zoneId).minusDays(daysAgo).format(ISO_DATE_FORMATTER)
    }

    /**
     * Splits a usage session [startTimestampMs, endTimestampMs] across local midnight boundaries
     * in the given [zoneId] so that a session spanning 23:50 -> 00:15 contributes 10 minutes
     * to yesterday and 15 minutes to today without overlap or double-counting.
     */
    fun splitSessionAcrossMidnight(
        startTimestampMs: Long,
        endTimestampMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<MidnightSplitSegment> {
        if (endTimestampMs <= startTimestampMs) return emptyList()

        val segments = mutableListOf<MidnightSplitSegment>()
        var cursorMs = startTimestampMs

        while (cursorMs < endTimestampMs) {
            val zonedCursor = Instant.ofEpochMilli(cursorMs).atZone(zoneId)
            val currentLocalDate = zonedCursor.toLocalDate()
            val nextMidnightZoned: ZonedDateTime = currentLocalDate
                .plusDays(1)
                .atTime(LocalTime.MIDNIGHT)
                .atZone(zoneId)
            val nextMidnightMs = nextMidnightZoned.toInstant().toEpochMilli()

            val segmentEndMs = minOf(endTimestampMs, nextMidnightMs)
            val durationSec = ChronoUnit.SECONDS.between(
                Instant.ofEpochMilli(cursorMs),
                Instant.ofEpochMilli(segmentEndMs)
            ).coerceAtLeast(0L)

            if (durationSec > 0L) {
                segments.add(
                    MidnightSplitSegment(
                        localDate = currentLocalDate.format(ISO_DATE_FORMATTER),
                        startTimestampMs = cursorMs,
                        endTimestampMs = segmentEndMs,
                        durationSeconds = durationSec
                    )
                )
            }
            cursorMs = segmentEndMs
        }

        return segments
    }
}
