package com.scrollmeter.app.accessibility

import android.view.accessibility.AccessibilityEvent
import com.scrollmeter.app.domain.models.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying [SwipeDetectionEngine] and [FeedContextDetector] across all
 * required acceptance, exclusion, deduplication, cooldown, and uncertainty scenarios.
 */
class SwipeDetectionEngineTest {

    private lateinit var engine: SwipeDetectionEngine

    @Before
    fun setUp() {
        engine = SwipeDetectionEngine(
            feedContextDetector = FeedContextDetector(),
            initialCooldownMs = 450L
        )
    }

    @Test
    fun `opening YouTube Shorts establishes baseline without counting a swipe`() {
        val entrySnapshot = createSnapshot(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            timestampMs = 1_000L,
            viewId = "com.google.android.youtube:id/reel_watch_fragment_root",
            fromIndex = 0,
            toIndex = 0,
            scrollDeltaY = 0,
            hasShortsOrReelsMarker = true
        )

        val outcome = engine.evaluate(entrySnapshot)
        assertFalse("Opening Shorts feed must not count as a swipe", outcome.isAcceptedSwipe)
        assertEquals(Platform.YOUTUBE_SHORTS, outcome.platform)
        assertEquals(FeedContext.YOUTUBE_SHORTS_FEED, outcome.feedContext)
    }

    @Test
    fun `vertical swipe in YouTube Shorts after baseline entry is accepted with high confidence`() {
        // 1. Enter Shorts feed
        engine.evaluate(
            createSnapshot(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                timestampMs = 1_000L,
                viewId = "com.google.android.youtube:id/reel_recycler",
                fromIndex = 0,
                toIndex = 0,
                scrollDeltaY = 0,
                hasShortsOrReelsMarker = true
            )
        )

        // 2. Perform vertical swipe to next Short (index 0 -> 1)
        val swipeOutcome = engine.evaluate(
            createSnapshot(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                timestampMs = 2_200L,
                viewId = "com.google.android.youtube:id/reel_recycler",
                fromIndex = 0,
                toIndex = 1,
                scrollDeltaY = 1420,
                hasShortsOrReelsMarker = true
            )
        )

        assertTrue("Vertical swipe in YouTube Shorts should be accepted", swipeOutcome.isAcceptedSwipe)
        assertEquals(Platform.YOUTUBE_SHORTS, swipeOutcome.platform)
        assertEquals(DetectionConfidence.CONFIRMED_HIGH, swipeOutcome.confidence)
    }

    @Test
    fun `vertical swipe in Instagram Reels is accepted and attributed to Instagram`() {
        // 1. Enter Instagram Reels feed
        engine.evaluate(
            createSnapshot(
                packageName = "com.instagram.android",
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                timestampMs = 5_000L,
                viewId = "com.instagram.android:id/clips_viewer_view_pager",
                fromIndex = 0,
                toIndex = 0,
                scrollDeltaY = 0,
                hasShortsOrReelsMarker = true
            )
        )

        // 2. Vertical swipe to next Reel
        val swipeOutcome = engine.evaluate(
            createSnapshot(
                packageName = "com.instagram.android",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                timestampMs = 6_100L,
                viewId = "com.instagram.android:id/clips_viewer_view_pager",
                fromIndex = 0,
                toIndex = 1,
                scrollDeltaY = 1600,
                hasShortsOrReelsMarker = true
            )
        )

        assertTrue("Vertical swipe in Instagram Reels should be accepted", swipeOutcome.isAcceptedSwipe)
        assertEquals(Platform.INSTAGRAM_REELS, swipeOutcome.platform)
        assertEquals(DetectionConfidence.CONFIRMED_HIGH, swipeOutcome.confidence)
    }

    @Test
    fun `scrolling Instagram comments drawer is excluded`() {
        val commentScroll = createSnapshot(
            packageName = "com.instagram.android",
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            timestampMs = 10_000L,
            viewId = "com.instagram.android:id/layout_comment_thread_list",
            fromIndex = 2,
            toIndex = 8,
            scrollDeltaY = 650,
            hasShortsOrReelsMarker = true,
            hasCommentsOverlay = true
        )

        val outcome = engine.evaluate(commentScroll)
        assertFalse("Scrolling comments must never count as a Reel swipe", outcome.isAcceptedSwipe)
        assertEquals(FeedContext.COMMENTS_OVERLAY, outcome.feedContext)
    }

    @Test
    fun `scrolling ordinary YouTube video watch page is excluded`() {
        val watchScroll = createSnapshot(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            timestampMs = 12_000L,
            viewId = "com.google.android.youtube:id/watch_next_feed",
            fromIndex = 1,
            toIndex = 4,
            scrollDeltaY = 900,
            hasShortsOrReelsMarker = false,
            hasStandardWatchLayout = true
        )

        val outcome = engine.evaluate(watchScroll)
        assertFalse("Scrolling standard YouTube watch page must be excluded", outcome.isAcceptedSwipe)
        assertEquals(FeedContext.ORDINARY_VIDEO_WATCH, outcome.feedContext)
    }

    @Test
    fun `scrolling search results and profile grids is excluded`() {
        val searchScroll = createSnapshot(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            timestampMs = 15_000L,
            viewId = "com.google.android.youtube:id/results_recycler_view",
            fromIndex = 0,
            toIndex = 5,
            scrollDeltaY = 800,
            hasShortsOrReelsMarker = false,
            hasSearchInput = true
        )
        val searchOutcome = engine.evaluate(searchScroll)
        assertFalse(searchOutcome.isAcceptedSwipe)
        assertEquals(FeedContext.SEARCH_RESULTS, searchOutcome.feedContext)

        val profileScroll = createSnapshot(
            packageName = "com.instagram.android",
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            timestampMs = 16_000L,
            viewId = "com.instagram.android:id/profile_tab_layout",
            fromIndex = 0,
            toIndex = 6,
            scrollDeltaY = 750,
            hasShortsOrReelsMarker = false,
            hasProfileGrid = true
        )
        val profileOutcome = engine.evaluate(profileScroll)
        assertFalse(profileOutcome.isAcceptedSwipe)
        assertEquals(FeedContext.PROFILE_GRID, profileOutcome.feedContext)
    }

    @Test
    fun `horizontal navigation in Stories or carousels is excluded`() {
        val horizontalSwipe = createSnapshot(
            packageName = "com.instagram.android",
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            timestampMs = 18_000L,
            viewId = "com.instagram.android:id/reel_viewer",
            fromIndex = 0,
            toIndex = 1,
            scrollDeltaX = 1080,
            scrollDeltaY = 40,
            hasShortsOrReelsMarker = true
        )

        val outcome = engine.evaluate(horizontalSwipe)
        assertFalse("Horizontal navigation must not count as a vertical swipe", outcome.isAcceptedSwipe)
        assertEquals(FeedContext.HORIZONTAL_STORIES_OR_CAROUSEL, outcome.feedContext)
    }

    @Test
    fun `duplicate burst events within cooldown window are rejected while rapid swipes after cooldown are accepted`() {
        // Baseline entry
        engine.evaluate(
            createSnapshot(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                timestampMs = 20_000L,
                viewId = "com.google.android.youtube:id/reel_recycler",
                fromIndex = 0,
                toIndex = 0,
                scrollDeltaY = 0,
                hasShortsOrReelsMarker = true
            )
        )

        // First swipe at t=21,000ms
        val firstSwipe = engine.evaluate(
            createSnapshot(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                timestampMs = 21_000L,
                viewId = "com.google.android.youtube:id/reel_recycler",
                fromIndex = 0,
                toIndex = 1,
                scrollDeltaY = 1400,
                hasShortsOrReelsMarker = true
            )
        )
        assertTrue(firstSwipe.isAcceptedSwipe)

        // Burst event 120ms later during same fling settle (t=21,120ms)
        val duplicateBurst = engine.evaluate(
            createSnapshot(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                timestampMs = 21_120L,
                viewId = "com.google.android.youtube:id/reel_recycler",
                fromIndex = 0,
                toIndex = 1,
                scrollDeltaY = 320,
                hasShortsOrReelsMarker = true
            )
        )
        assertFalse("Burst event within 450ms cooldown must be rejected", duplicateBurst.isAcceptedSwipe)
        assertTrue(duplicateBurst.isDuplicateRejected)

        // Next genuine rapid swipe at t=21,550ms (550ms > 450ms cooldown, index 1 -> 2)
        val secondSwipe = engine.evaluate(
            createSnapshot(
                packageName = "com.google.android.youtube",
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
                timestampMs = 21_550L,
                viewId = "com.google.android.youtube:id/reel_recycler",
                fromIndex = 1,
                toIndex = 2,
                scrollDeltaY = 1450,
                hasShortsOrReelsMarker = true
            )
        )
        assertTrue("Genuine swipe after cooldown window should be accepted", secondSwipe.isAcceptedSwipe)
    }

    @Test
    fun `ambiguous full-screen scroll without platform marker is reported as uncertain and not counted`() {
        val ambiguousScroll = createSnapshot(
            packageName = "com.instagram.android",
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            timestampMs = 30_000L,
            viewId = "com.instagram.android:id/obfuscated_pager_0x7f",
            fromIndex = 1,
            toIndex = 2,
            scrollDeltaY = 1200,
            hasShortsOrReelsMarker = false
        )

        val outcome = engine.evaluate(ambiguousScroll)
        assertFalse("Uncertain event must never be silently counted as confirmed", outcome.isAcceptedSwipe)
        assertTrue("Ambiguous full-screen scroll must be flagged as uncertain", outcome.isUncertainEvent)
        assertEquals(DetectionConfidence.UNCERTAIN, outcome.confidence)
    }

    private fun createSnapshot(
        packageName: String,
        eventType: Int,
        timestampMs: Long,
        viewId: String,
        fromIndex: Int,
        toIndex: Int,
        scrollDeltaX: Int = 0,
        scrollDeltaY: Int = 0,
        hasShortsOrReelsMarker: Boolean = true,
        hasCommentsOverlay: Boolean = false,
        hasSearchInput: Boolean = false,
        hasProfileGrid: Boolean = false,
        hasStandardWatchLayout: Boolean = false,
        isScreenInteractive: Boolean = true
    ): AccessibilitySignalSnapshot {
        return AccessibilitySignalSnapshot(
            packageName = packageName,
            eventType = eventType,
            eventTimestampMs = timestampMs,
            className = "androidx.viewpager2.widget.ViewPager2",
            viewIdResourceName = viewId,
            fromIndex = fromIndex,
            toIndex = toIndex,
            itemCount = 50,
            scrollDeltaX = scrollDeltaX,
            scrollDeltaY = scrollDeltaY,
            maxScrollX = 0,
            maxScrollY = 10000,
            isFullScreenVerticalContainer = true,
            containerHeightRatio = 0.92f,
            containerWidthRatio = 1.0f,
            hasActiveShortsOrReelsMarker = hasShortsOrReelsMarker,
            hasCommentsOrSheetOverlay = hasCommentsOverlay,
            hasSearchInputOrGrid = hasSearchInput,
            hasProfileGridPattern = hasProfileGrid,
            hasStandardWatchPlayerLayout = hasStandardWatchLayout,
            isScreenInteractive = isScreenInteractive,
            structuralNodeSignature = toIndex + 100
        )
    }
}
