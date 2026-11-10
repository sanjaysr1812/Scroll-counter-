package com.scrollmeter.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit tests verifying midnight session splitting and timezone-aware date calculations.
 */
class DateTimeUtilsTest {

    @Test
    fun `session crossing local midnight is split into two non-overlapping daily segments`() {
        val zone = ZoneId.of("America/New_York")
        val start = ZonedDateTime.of(2026, 11, 9, 23, 50, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 10, 0, 15, 0, 0, zone).toInstant().toEpochMilli()

        val segments = DateTimeUtils.splitSessionAcrossMidnight(
            startTimestampMs = start,
            endTimestampMs = end,
            zoneId = zone
        )

        assertEquals(2, segments.size)
        assertEquals("2026-11-09", segments[0].localDate)
        assertEquals(600L, segments[0].durationSeconds) // 10 minutes before midnight

        assertEquals("2026-11-10", segments[1].localDate)
        assertEquals(900L, segments[1].durationSeconds) // 15 minutes after midnight
    }

    @Test
    fun `single-day session returns one exact segment`() {
        val zone = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 11, 10, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 10, 14, 12, 30, 0, zone).toInstant().toEpochMilli()

        val segments = DateTimeUtils.splitSessionAcrossMidnight(start, end, zone)
        assertEquals(1, segments.size)
        assertEquals("2026-11-10", segments[0].localDate)
        assertEquals(750L, segments[0].durationSeconds)
    }
}
