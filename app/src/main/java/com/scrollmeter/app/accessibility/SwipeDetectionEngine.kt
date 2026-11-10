package com.scrollmeter.app.accessibility

import android.view.accessibility.AccessibilityEvent
import com.scrollmeter.app.domain.models.Platform
import kotlin.math.abs

/**
 * Outcome of evaluating a single [AccessibilitySignalSnapshot] through [SwipeDetectionEngine].
 */
data class SwipeEvaluationOutcome(
    val platform: Platform?,
    val feedContext: FeedContext,
    val confidence: DetectionConfidence,
    val isAcceptedSwipe: Boolean,
    val isDuplicateRejected: Boolean,
    val isUncertainEvent: Boolean,
    val eventSignatureHash: String,
    val diagnosticReason: String
)

/**
 * Core 8-stage Swipe Detection Engine for ScrollMeter:
 * 1. Package-name verification ("com.google.android.youtube" or "com.instagram.android" only).
 * 2. App foreground & screen interactive state validation.
 * 3. Feed-context identification via [FeedContextDetector].
 * 4. Exclusion of non-short feeds (comments, descriptions, search, profile, horizontal carousels).
 * 5. Initial-entry baseline suppression (opening a Short/Reel without swiping is NOT counted).
 * 6. Vertical navigation event evaluation (handles both TYPE_VIEW_SCROLLED and custom pager transitions).
 * 7. Event deduplication & signature hashing.
 * 8. Configurable cooldown enforcement.
 */
class SwipeDetectionEngine(
    private val feedContextDetector: FeedContextDetector = FeedContextDetector(),
    initialCooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    companion object {
        const val DEFAULT_COOLDOWN_MS = 450L
        const val MIN_COOLDOWN_MS = 250L
        const val MAX_COOLDOWN_MS = 1500L
        private const val MIN_VERTICAL_SCROLL_DELTA_PX = 140
    }

    @Volatile
    var cooldownMs: Long = initialCooldownMs.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)

    private data class PlatformTrackerState(
        var isInsideEligibleFeed: Boolean = false,
        var feedEnteredAtMs: Long = 0L,
        var lastAcceptedSwipeTimestampMs: Long = -10_000L,
        var lastPagerIndex: Int = -1,
        var lastStructuralSignature: Int = 0,
        var recentSignatureHashes: ArrayDeque<String> = ArrayDeque(16)
    )

    private val stateByPlatform = mapOf(
        Platform.YOUTUBE_SHORTS to PlatformTrackerState(),
        Platform.INSTAGRAM_REELS to PlatformTrackerState()
    )

    @Synchronized
    fun resetState() {
        stateByPlatform.values.forEach { state ->
            state.isInsideEligibleFeed = false
            state.feedEnteredAtMs = 0L
            state.lastAcceptedSwipeTimestampMs = -10_000L
            state.lastPagerIndex = -1
            state.lastStructuralSignature = 0
            state.recentSignatureHashes.clear()
        }
    }

    @Synchronized
    fun evaluate(snapshot: AccessibilitySignalSnapshot): SwipeEvaluationOutcome {
        // Stage 1: Package-name verification
        val platform = Platform.fromPackageName(snapshot.packageName)
            ?: return SwipeEvaluationOutcome(
                platform = null,
                feedContext = FeedContext.UNKNOWN_CONTEXT,
                confidence = DetectionConfidence.IGNORED_NON_SHORT_FEED,
                isAcceptedSwipe = false,
                isDuplicateRejected = false,
                isUncertainEvent = false,
                eventSignatureHash = "",
                diagnosticReason = "Ignored non-target package: ${snapshot.packageName}"
            )

        val tracker = stateByPlatform.getValue(platform)

        // Stage 2: App foreground / interactive screen validation
        if (!snapshot.isScreenInteractive) {
            tracker.isInsideEligibleFeed = false
            return SwipeEvaluationOutcome(
                platform = platform,
                feedContext = FeedContext.UNKNOWN_CONTEXT,
                confidence = DetectionConfidence.UNAVAILABLE,
                isAcceptedSwipe = false,
                isDuplicateRejected = false,
                isUncertainEvent = false,
                eventSignatureHash = "",
                diagnosticReason = "Ignored event while device screen is non-interactive/locked"
            )
        }

        // Stage 3: Feed-context identification
        val contextResult = feedContextDetector.detectContext(snapshot)
        val feedContext = contextResult.feedContext

        // Stage 4: Handle non-eligible contexts & uncertainty
        if (!feedContext.isEligibleShortFeed) {
            if (feedContext != FeedContext.UNKNOWN_CONTEXT) {
                // Explicitly entered an excluded screen (Home, Comments, Search, Profile, Standard Watch)
                if (feedContext == FeedContext.HOME_OR_OTHER_SCREEN ||
                    feedContext == FeedContext.SEARCH_RESULTS ||
                    feedContext == FeedContext.PROFILE_GRID ||
                    feedContext == FeedContext.ORDINARY_VIDEO_WATCH
                ) {
                    tracker.isInsideEligibleFeed = false
                    tracker.lastPagerIndex = -1
                }
                return SwipeEvaluationOutcome(
                    platform = platform,
                    feedContext = feedContext,
                    confidence = DetectionConfidence.IGNORED_NON_SHORT_FEED,
                    isAcceptedSwipe = false,
                    isDuplicateRejected = false,
                    isUncertainEvent = false,
                    eventSignatureHash = "",
                    diagnosticReason = contextResult.reason
                )
            } else {
                // Ambiguous / unknown context inside YouTube or Instagram
                val isScrollEvent = snapshot.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                val isUncertain = isScrollEvent && snapshot.isFullScreenVerticalContainer
                return SwipeEvaluationOutcome(
                    platform = platform,
                    feedContext = FeedContext.UNKNOWN_CONTEXT,
                    confidence = if (isUncertain) DetectionConfidence.UNCERTAIN else DetectionConfidence.IGNORED_NON_SHORT_FEED,
                    isAcceptedSwipe = false,
                    isDuplicateRejected = false,
                    isUncertainEvent = isUncertain,
                    eventSignatureHash = "",
                    diagnosticReason = if (isUncertain) {
                        "Uncertain scroll inside full-screen container without verified Shorts/Reels marker (not counted)"
                    } else {
                        contextResult.reason
                    }
                )
            }
        }

        // Stage 5: Initial feed entry baseline (Opening a video without swiping MUST NOT count as a swipe)
        if (!tracker.isInsideEligibleFeed) {
            tracker.isInsideEligibleFeed = true
            tracker.feedEnteredAtMs = snapshot.eventTimestampMs
            if (snapshot.toIndex >= 0) {
                tracker.lastPagerIndex = snapshot.toIndex
            } else if (snapshot.fromIndex >= 0) {
                tracker.lastPagerIndex = snapshot.fromIndex
            }
            tracker.lastStructuralSignature = snapshot.structuralNodeSignature

            // If the first event upon entering Shorts/Reels is a window state/content setup event,
            // record entry baseline without incrementing swipe count.
            if (snapshot.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                abs(snapshot.scrollDeltaY) < MIN_VERTICAL_SCROLL_DELTA_PX
            ) {
                return SwipeEvaluationOutcome(
                    platform = platform,
                    feedContext = feedContext,
                    confidence = DetectionConfidence.IGNORED_NON_SHORT_FEED,
                    isAcceptedSwipe = false,
                    isDuplicateRejected = false,
                    isUncertainEvent = false,
                    eventSignatureHash = "",
                    diagnosticReason = "Initial ${platform.displayName} feed entry baseline established (opening video is not a swipe)"
                )
            }
        }

        // Stage 6: Evaluate vertical navigation signal
        val currentIndex = when {
            snapshot.toIndex >= 0 -> snapshot.toIndex
            snapshot.fromIndex >= 0 -> snapshot.fromIndex
            else -> -1
        }

        val hasIndexAdvancement = currentIndex >= 0 &&
            tracker.lastPagerIndex >= 0 &&
            currentIndex != tracker.lastPagerIndex

        val hasFirstIndexScroll = currentIndex > 0 &&
            tracker.lastPagerIndex == -1 &&
            snapshot.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

        val hasSignificantVerticalDelta = snapshot.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            abs(snapshot.scrollDeltaY) >= MIN_VERTICAL_SCROLL_DELTA_PX &&
            abs(snapshot.scrollDeltaY) > abs(snapshot.scrollDeltaX) * 1.5f

        val hasCustomPagerTransition =
            snapshot.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                snapshot.isFullScreenVerticalContainer &&
                snapshot.structuralNodeSignature != 0 &&
                tracker.lastStructuralSignature != 0 &&
                snapshot.structuralNodeSignature != tracker.lastStructuralSignature &&
                (snapshot.eventTimestampMs - tracker.feedEnteredAtMs) > 600L

        if (!hasIndexAdvancement && !hasFirstIndexScroll && !hasSignificantVerticalDelta && !hasCustomPagerTransition) {
            if (currentIndex >= 0 && tracker.lastPagerIndex == -1) {
                tracker.lastPagerIndex = currentIndex
            }
            if (snapshot.structuralNodeSignature != 0 && tracker.lastStructuralSignature == 0) {
                tracker.lastStructuralSignature = snapshot.structuralNodeSignature
            }
            return SwipeEvaluationOutcome(
                platform = platform,
                feedContext = feedContext,
                confidence = DetectionConfidence.IGNORED_NON_SHORT_FEED,
                isAcceptedSwipe = false,
                isDuplicateRejected = false,
                isUncertainEvent = false,
                eventSignatureHash = "",
                diagnosticReason = "Sub-threshold or non-navigation event in ${platform.displayName}"
            )
        }

        // Stage 7: Cooldown check & Duplicate-event prevention
        val elapsedSinceLastSwipe = snapshot.eventTimestampMs - tracker.lastAcceptedSwipeTimestampMs
        val timeBucket = snapshot.eventTimestampMs / cooldownMs
        val signatureHash = buildString {
            append(platform.id)
            append(':')
            append(if (currentIndex >= 0) "idx_$currentIndex" else "bucket_$timeBucket")
            append(':')
            append(if (snapshot.scrollDeltaY >= 0) "down" else "up")
        }

        if (elapsedSinceLastSwipe in 0 until cooldownMs ||
            tracker.recentSignatureHashes.contains(signatureHash)
        ) {
            return SwipeEvaluationOutcome(
                platform = platform,
                feedContext = feedContext,
                confidence = DetectionConfidence.IGNORED_NON_SHORT_FEED,
                isAcceptedSwipe = false,
                isDuplicateRejected = true,
                isUncertainEvent = false,
                eventSignatureHash = signatureHash,
                diagnosticReason = "Duplicate/cooldown burst suppressed (${elapsedSinceLastSwipe}ms < ${cooldownMs}ms cooldown)"
            )
        }

        // Stage 8: Classify confidence & commit state
        val confidence = when {
            (hasIndexAdvancement || hasFirstIndexScroll) && hasSignificantVerticalDelta ->
                DetectionConfidence.CONFIRMED_HIGH
            hasIndexAdvancement || hasFirstIndexScroll ->
                DetectionConfidence.CONFIRMED_HIGH
            hasSignificantVerticalDelta && contextResult.contextConfidence >= 0.80f ->
                DetectionConfidence.CONFIRMED_MEDIUM
            hasCustomPagerTransition && contextResult.contextConfidence >= 0.85f ->
                DetectionConfidence.CONFIRMED_MEDIUM
            else ->
                DetectionConfidence.UNCERTAIN
        }

        if (!confidence.countsAsConfirmedSwipe) {
            return SwipeEvaluationOutcome(
                platform = platform,
                feedContext = feedContext,
                confidence = DetectionConfidence.UNCERTAIN,
                isAcceptedSwipe = false,
                isDuplicateRejected = false,
                isUncertainEvent = true,
                eventSignatureHash = signatureHash,
                diagnosticReason = "Uncertain transition in ${platform.displayName} (confidence=${confidence.score}); logged to diagnostics only"
            )
        }

        // Accepted swipe! Update tracker state
        tracker.lastAcceptedSwipeTimestampMs = snapshot.eventTimestampMs
        if (currentIndex >= 0) {
            tracker.lastPagerIndex = currentIndex
        }
        if (snapshot.structuralNodeSignature != 0) {
            tracker.lastStructuralSignature = snapshot.structuralNodeSignature
        }
        if (tracker.recentSignatureHashes.size >= 16) {
            tracker.recentSignatureHashes.removeFirst()
        }
        tracker.recentSignatureHashes.addLast(signatureHash)

        return SwipeEvaluationOutcome(
            platform = platform,
            feedContext = feedContext,
            confidence = confidence,
            isAcceptedSwipe = true,
            isDuplicateRejected = false,
            isUncertainEvent = false,
            eventSignatureHash = signatureHash,
            diagnosticReason = "Accepted ${platform.displayName} vertical swipe (${confidence.code})"
        )
    }
}
