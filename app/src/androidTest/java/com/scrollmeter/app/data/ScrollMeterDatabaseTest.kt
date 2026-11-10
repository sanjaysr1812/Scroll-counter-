package com.scrollmeter.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollmeter.app.data.database.ScrollMeterDatabase
import com.scrollmeter.app.data.database.entities.SwipeEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Instrumentation test verifying Room database persistence, unique signature
 * deduplication constraint, and atomic daily statistics aggregation.
 */
@RunWith(AndroidJUnit4::class)
class ScrollMeterDatabaseTest {

    private lateinit var database: ScrollMeterDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ScrollMeterDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun duplicateEventSignatureIsRejectedByRoomUniqueIndex() = runTest {
        val dao = database.swipeEventDao()
        val event1 = SwipeEventEntity(
            platform = "YOUTUBE_SHORTS",
            eventTimestamp = 100_000L,
            localDate = "2026-11-10",
            hourOfDay = 14,
            eventType = "TYPE_VIEW_SCROLLED",
            confidence = 0.94f,
            isConfirmedSwipe = true,
            trackingSessionId = 1L,
            eventSignatureHash = "YOUTUBE_SHORTS:idx_4:down"
        )

        val firstInsertId = dao.insertSwipeEvent(event1)
        val duplicateInsertId = dao.insertSwipeEvent(event1.copy(eventTimestamp = 100_080L))

        assertTrue("First insert should succeed with rowId > 0", firstInsertId > 0L)
        assertEquals("Duplicate signature insert should be ignored with -1L", -1L, duplicateInsertId)
    }

    @Test
    fun atomicDailyStatisticsIncrementPersistsAndCalculatesTotals() = runTest {
        val statsDao = database.dailyStatisticsDao()
        statsDao.incrementConfirmedSwipe("2026-11-10", isYouTube = true, timestampMs = 1_000L)
        statsDao.incrementConfirmedSwipe("2026-11-10", isYouTube = true, timestampMs = 2_000L)
        val updated = statsDao.incrementConfirmedSwipe("2026-11-10", isYouTube = false, timestampMs = 3_000L)

        assertEquals(2, updated.youtubeSwipeCount)
        assertEquals(1, updated.instagramSwipeCount)
        assertEquals(3, updated.totalSwipeCount)
    }
}
