package com.scrollmeter.app.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scrollmeter.app.domain.models.Platform

/**
 * Lightweight, privacy-safe snapshot of an AccessibilityEvent and active window geometry.
 *
 * PRIVACY GUARANTEE:
 * Only structural signals (package name, view class names, resource ID suffixes, scroll indices,
 * scroll deltas, and normalized viewport bounds) are inspected. Usernames, video titles,
 * comments, messages, and typed text are NEVER read or stored.
 */
data class AccessibilitySignalSnapshot(
    val packageName: String,
    val eventType: Int,
    val eventTimestampMs: Long,
    val className: String,
    val viewIdResourceName: String,
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    val scrollDeltaX: Int,
    val scrollDeltaY: Int,
    val maxScrollX: Int,
    val maxScrollY: Int,
    val isFullScreenVerticalContainer: Boolean,
    val containerHeightRatio: Float,
    val containerWidthRatio: Float,
    val hasActiveShortsOrReelsMarker: Boolean,
    val hasCommentsOrSheetOverlay: Boolean,
    val hasSearchInputOrGrid: Boolean,
    val hasProfileGridPattern: Boolean,
    val hasStandardWatchPlayerLayout: Boolean,
    val isScreenInteractive: Boolean = true,
    val structuralNodeSignature: Int = 0
)

/**
 * Result of evaluating whether the user is currently inside YouTube Shorts or Instagram Reels
 * versus an excluded screen (comments, search, profile, standard video, home feed).
 */
data class FeedContextResult(
    val platform: Platform,
    val feedContext: FeedContext,
    val contextConfidence: Float,
    val reason: String
)

/**
 * Multi-signal, adaptable detector that determines whether the active window in YouTube or
 * Instagram is displaying the full-screen vertical Shorts/Reels player without an active
 * comments drawer, description sheet, search page, or profile grid.
 *
 * Avoids brittle dependence on a single undocumented resource ID by combining:
 * 1. Resource ID token hints (when present in the current app build).
 * 2. Full-screen vertical pager geometry (container height >= 80% of screen height).
 * 3. Exclusion overlay detection (comments bottom sheet, search bar, multi-column profile grid,
 *    or standard 16:9 landscape watch player).
 */
class FeedContextDetector {

    companion object {
        private val YOUTUBE_SHORTS_VIEW_TOKENS = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "shorts_player",
            "reel_watch_fragment_root",
            "reel_multi_format_link",
            "shorts_pivot_button"
        )

        private val INSTAGRAM_REELS_VIEW_TOKENS = listOf(
            "clips_viewer_view_pager",
            "clips_video_container",
            "reel_viewer",
            "clips_item_overlay",
            "clips_tab"
        )

        private val COMMENTS_AND_OVERLAY_TOKENS = listOf(
            "engagement_panel",
            "comment_composer",
            "comments_list",
            "row_comment",
            "layout_comment_thread",
            "bottom_sheet_container",
            "design_bottom_sheet",
            "description_content",
            "share_sheet"
        )

        private val SEARCH_TOKENS = listOf(
            "search_edit_text",
            "search_typeahead",
            "action_bar_search_edit_text",
            "results_recycler_view"
        )

        private val PROFILE_GRID_TOKENS = listOf(
            "profile_tab_layout",
            "profile_header",
            "channel_header",
            "grid_card"
        )

        private val STANDARD_YOUTUBE_WATCH_TOKENS = listOf(
            "watch_while_layout",
            "player_fragment_container",
            "watch_next_feed",
            "related_chip_cloud"
        )
    }

    /**
     * Evaluates a privacy-safe [AccessibilitySignalSnapshot] and classifies the current [FeedContext].
     */
    fun detectContext(snapshot: AccessibilitySignalSnapshot): FeedContextResult {
        val platform = Platform.fromPackageName(snapshot.packageName)
            ?: return FeedContextResult(
                platform = Platform.YOUTUBE_SHORTS,
                feedContext = FeedContext.UNKNOWN_CONTEXT,
                contextConfidence = 0f,
                reason = "Unsupported package: ${snapshot.packageName}"
            )

        val viewIdLower = snapshot.viewIdResourceName.lowercase()
        val classLower = snapshot.className.lowercase()

        // 1. Check explicit exclusions first (Comments, Description/Share sheets, Search, Profile, Horizontal)
        if (snapshot.hasCommentsOrSheetOverlay ||
            COMMENTS_AND_OVERLAY_TOKENS.any { viewIdLower.contains(it) }
        ) {
            return FeedContextResult(
                platform = platform,
                feedContext = FeedContext.COMMENTS_OVERLAY,
                contextConfidence = 0.95f,
                reason = "Comments or engagement bottom sheet is active"
            )
        }

        if (snapshot.hasSearchInputOrGrid ||
            SEARCH_TOKENS.any { viewIdLower.contains(it) }
        ) {
            return FeedContextResult(
                platform = platform,
                feedContext = FeedContext.SEARCH_RESULTS,
                contextConfidence = 0.92f,
                reason = "Search input or search results list detected"
            )
        }

        if (snapshot.hasProfileGridPattern ||
            PROFILE_GRID_TOKENS.any { viewIdLower.contains(it) }
        ) {
            return FeedContextResult(
                platform = platform,
                feedContext = FeedContext.PROFILE_GRID,
                contextConfidence = 0.90f,
                reason = "Profile header or multi-column media grid detected"
            )
        }

        if (platform == Platform.YOUTUBE_SHORTS &&
            (snapshot.hasStandardWatchPlayerLayout ||
                STANDARD_YOUTUBE_WATCH_TOKENS.any { viewIdLower.contains(it) }) &&
            !snapshot.hasActiveShortsOrReelsMarker
        ) {
            return FeedContextResult(
                platform = platform,
                feedContext = FeedContext.ORDINARY_VIDEO_WATCH,
                contextConfidence = 0.90f,
                reason = "Standard YouTube video watch page (non-Shorts) detected"
            )
        }

        // Check horizontal swipe / carousel dominance
        val absDx = kotlin.math.abs(snapshot.scrollDeltaX)
        val absDy = kotlin.math.abs(snapshot.scrollDeltaY)
        if (absDx > 0 && absDx > absDy * 1.25f) {
            return FeedContextResult(
                platform = platform,
                feedContext = FeedContext.HORIZONTAL_STORIES_OR_CAROUSEL,
                contextConfidence = 0.93f,
                reason = "Horizontal scroll vector (deltaX=$absDx > deltaY=$absDy)"
            )
        }

        // 2. Verify positive Shorts / Reels feed signals
        val hasKnownPlatformToken = when (platform) {
            Platform.YOUTUBE_SHORTS -> YOUTUBE_SHORTS_VIEW_TOKENS.any { viewIdLower.contains(it) }
            Platform.INSTAGRAM_REELS -> INSTAGRAM_REELS_VIEW_TOKENS.any { viewIdLower.contains(it) }
        }

        val isPagerOrRecycler = classLower.contains("viewpager") ||
            classLower.contains("recyclerview") ||
            classLower.contains("framelayout") ||
            classLower.contains("viewgroup")

        val isFullHeightGeometry = snapshot.isFullScreenVerticalContainer ||
            snapshot.containerHeightRatio >= 0.80f

        return when {
            (hasKnownPlatformToken || snapshot.hasActiveShortsOrReelsMarker) && isFullHeightGeometry -> {
                val targetFeed = if (platform == Platform.YOUTUBE_SHORTS) {
                    FeedContext.YOUTUBE_SHORTS_FEED
                } else {
                    FeedContext.INSTAGRAM_REELS_FEED
                }
                FeedContextResult(
                    platform = platform,
                    feedContext = targetFeed,
                    contextConfidence = if (hasKnownPlatformToken && snapshot.hasActiveShortsOrReelsMarker) 0.95f else 0.84f,
                    reason = "Verified ${platform.displayName} full-screen vertical feed container"
                )
            }

            (hasKnownPlatformToken || snapshot.hasActiveShortsOrReelsMarker) && isPagerOrRecycler -> {
                val targetFeed = if (platform == Platform.YOUTUBE_SHORTS) {
                    FeedContext.YOUTUBE_SHORTS_FEED
                } else {
                    FeedContext.INSTAGRAM_REELS_FEED
                }
                FeedContextResult(
                    platform = platform,
                    feedContext = targetFeed,
                    contextConfidence = 0.76f,
                    reason = "Identified ${platform.displayName} marker on scrollable pager container"
                )
            }

            isFullHeightGeometry && isPagerOrRecycler && snapshot.containerWidthRatio >= 0.90f -> {
                // Geometry matches a full-screen vertical pager, but platform marker is absent
                FeedContextResult(
                    platform = platform,
                    feedContext = FeedContext.UNKNOWN_CONTEXT,
                    contextConfidence = 0.45f,
                    reason = "Full-screen pager geometry without confirmed Shorts/Reels marker"
                )
            }

            else -> {
                FeedContextResult(
                    platform = platform,
                    feedContext = FeedContext.HOME_OR_OTHER_SCREEN,
                    contextConfidence = 0.85f,
                    reason = "Main feed or non-Short navigation screen"
                )
            }
        }
    }

    /**
     * Extracts a privacy-safe [AccessibilitySignalSnapshot] from a live Android [AccessibilityEvent]
     * and the current active window root node.
     */
    fun buildSignalSnapshot(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        screenWidthPx: Int,
        screenHeightPx: Int,
        isScreenInteractive: Boolean
    ): AccessibilitySignalSnapshot {
        val pkg = event.packageName?.toString().orEmpty()
        val viewId = event.source?.viewIdResourceName.orEmpty()
        val eventClass = event.className?.toString().orEmpty()

        var hasShortsOrReelsMarker = false
        var hasCommentsOverlay = false
        var hasSearchInput = false
        var hasProfileGrid = false
        var hasStandardWatch = false
        var maxContainerHeightRatio = 0f
        var maxContainerWidthRatio = 0f
        var structuralHash = 17

        val safeScreenW = screenWidthPx.coerceAtLeast(1)
        val safeScreenH = screenHeightPx.coerceAtLeast(1)

        if (rootNode != null) {
            val bounds = Rect()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visitedNodes = 0
            val maxNodesToInspect = 75 // Battery-conscious upper bound per event

            while (queue.isNotEmpty() && visitedNodes < maxNodesToInspect) {
                val node = queue.removeFirst()
                visitedNodes++

                val nodeId = node.viewIdResourceName?.lowercase().orEmpty()
                val nodeClass = node.className?.toString()?.lowercase().orEmpty()
                node.getBoundsInScreen(bounds)

                val widthRatio = bounds.width().toFloat() / safeScreenW
                val heightRatio = bounds.height().toFloat() / safeScreenH

                if (node.isScrollable && heightRatio > maxContainerHeightRatio) {
                    maxContainerHeightRatio = heightRatio
                    maxContainerWidthRatio = widthRatio
                }

                if (YOUTUBE_SHORTS_VIEW_TOKENS.any { nodeId.contains(it) } ||
                    INSTAGRAM_REELS_VIEW_TOKENS.any { nodeId.contains(it) }
                ) {
                    hasShortsOrReelsMarker = true
                }

                // Check selected tab content description for "Shorts" or "Reels" (structural navigation cue only)
                if (node.isSelected) {
                    val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                    if (desc == "shorts" || desc == "reels" || desc.startsWith("shorts") || desc.startsWith("reels")) {
                        hasShortsOrReelsMarker = true
                    }
                }

                if (COMMENTS_AND_OVERLAY_TOKENS.any { nodeId.contains(it) } ||
                    (node.isEditable && heightRatio < 0.25f && bounds.bottom > safeScreenH * 0.45f)
                ) {
                    hasCommentsOverlay = true
                }

                if (SEARCH_TOKENS.any { nodeId.contains(it) }) {
                    hasSearchInput = true
                }

                if (PROFILE_GRID_TOKENS.any { nodeId.contains(it) }) {
                    hasProfileGrid = true
                }

                if (STANDARD_YOUTUBE_WATCH_TOKENS.any { nodeId.contains(it) } && heightRatio in 0.22f..0.42f) {
                    hasStandardWatch = true
                }

                if (heightRatio >= 0.75f && widthRatio >= 0.85f) {
                    structuralHash = 31 * structuralHash + nodeClass.hashCode()
                    structuralHash = 31 * structuralHash + bounds.top
                    structuralHash = 31 * structuralHash + bounds.bottom
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        // Also inspect the event source node bounds if available
        event.source?.let { src ->
            val srcBounds = Rect()
            src.getBoundsInScreen(srcBounds)
            val hRatio = srcBounds.height().toFloat() / safeScreenH
            val wRatio = srcBounds.width().toFloat() / safeScreenW
            if (hRatio > maxContainerHeightRatio) {
                maxContainerHeightRatio = hRatio
                maxContainerWidthRatio = wRatio
            }
        }

        return AccessibilitySignalSnapshot(
            packageName = pkg,
            eventType = event.eventType,
            eventTimestampMs = if (event.eventTime > 0) event.eventTime else System.currentTimeMillis(),
            className = eventClass,
            viewIdResourceName = viewId,
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            itemCount = event.itemCount,
            scrollDeltaX = event.scrollDeltaX,
            scrollDeltaY = event.scrollDeltaY,
            maxScrollX = event.maxScrollX,
            maxScrollY = event.maxScrollY,
            isFullScreenVerticalContainer = maxContainerHeightRatio >= 0.80f && maxContainerWidthRatio >= 0.85f,
            containerHeightRatio = maxContainerHeightRatio,
            containerWidthRatio = maxContainerWidthRatio,
            hasActiveShortsOrReelsMarker = hasShortsOrReelsMarker,
            hasCommentsOrSheetOverlay = hasCommentsOverlay,
            hasSearchInputOrGrid = hasSearchInput,
            hasProfileGridPattern = hasProfileGrid,
            hasStandardWatchPlayerLayout = hasStandardWatch,
            isScreenInteractive = isScreenInteractive,
            structuralNodeSignature = structuralHash
        )
    }
}
