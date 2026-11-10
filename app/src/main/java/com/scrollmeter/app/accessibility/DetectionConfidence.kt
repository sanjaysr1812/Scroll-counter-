package com.scrollmeter.app.accessibility

/**
 * Represents the confidence level of an AccessibilityEvent analyzed by [SwipeDetectionEngine].
 *
 * Critical requirement: Confirmed swipe counts are strictly separated from uncertain or
 * ambiguous events. Uncertain events are reported in Tracking Diagnostics and never silently
 * added to the user's confirmed swipe total.
 */
enum class DetectionConfidence(
    val score: Float,
    val code: String,
    val displayLabel: String,
    val countsAsConfirmedSwipe: Boolean
) {
    CONFIRMED_HIGH(
        score = 0.94f,
        code = "CONFIRMED_HIGH",
        displayLabel = "High Confidence (Verified Vertical Pager Advance)",
        countsAsConfirmedSwipe = true
    ),
    CONFIRMED_MEDIUM(
        score = 0.78f,
        code = "CONFIRMED_MEDIUM",
        displayLabel = "Medium Confidence (Verified Short Feed + Vertical Scroll Delta)",
        countsAsConfirmedSwipe = true
    ),
    UNCERTAIN(
        score = 0.42f,
        code = "UNCERTAIN",
        displayLabel = "Uncertain (Ambiguous Transition — Not Counted as Swipe)",
        countsAsConfirmedSwipe = false
    ),
    IGNORED_NON_SHORT_FEED(
        score = 0.0f,
        code = "IGNORED_NON_SHORT_FEED",
        displayLabel = "Excluded Context (Comments, Home, Search, Profile, or Horizontal)",
        countsAsConfirmedSwipe = false
    ),
    UNAVAILABLE(
        score = 0.0f,
        code = "UNAVAILABLE",
        displayLabel = "Tracking Unavailable (Service Disconnected or Window Inaccessible)",
        countsAsConfirmedSwipe = false
    );

    companion object {
        fun fromCode(code: String): DetectionConfidence =
            entries.firstOrNull { it.code == code } ?: UNCERTAIN
    }
}

/**
 * Identifies the current screen context within YouTube or Instagram.
 */
enum class FeedContext(
    val isEligibleShortFeed: Boolean,
    val displayLabel: String
) {
    YOUTUBE_SHORTS_FEED(true, "YouTube Shorts Vertical Feed"),
    INSTAGRAM_REELS_FEED(true, "Instagram Reels Vertical Feed"),
    COMMENTS_OVERLAY(false, "Comments Sheet / Engagement Drawer (Excluded)"),
    DESCRIPTION_OR_SHARE_OVERLAY(false, "Description / Share Overlay (Excluded)"),
    SEARCH_RESULTS(false, "Search Results Screen (Excluded)"),
    PROFILE_GRID(false, "Profile Page / Multi-Column Grid (Excluded)"),
    ORDINARY_VIDEO_WATCH(false, "Standard YouTube Video Watch Page (Excluded)"),
    HORIZONTAL_STORIES_OR_CAROUSEL(false, "Horizontal Stories / Carousel (Excluded)"),
    HOME_OR_OTHER_SCREEN(false, "Main Home Feed or Unrelated Screen (Excluded)"),
    UNKNOWN_CONTEXT(false, "Unverified Screen Context (Excluded)")
}
