package com.scrollmeter.app.accessibility

import android.view.accessibility.AccessibilityEvent
import com.scrollmeter.app.data.repository.DiagnosticsStateHolder
import com.scrollmeter.app.data.repository.ScrollMeterRepository
import com.scrollmeter.app.domain.models.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates [SwipeDetectionEngine], short-video feed session timing, and database persistence.
 *
 * Ensures:
 * - Only time inside verified YouTube Shorts or Instagram Reels feed contexts is counted.
 * - Switching to Comments, Search, Profile, or another app closes the active short-video session.
 * - Locking the screen or interrupting the service flushes and closes the active session.
 * - Idle timeout (90 seconds without interaction) caps inactive session tails.
 */
class AccessibilityEventProcessor(
    private val repository: ScrollMeterRepository,
    private val swipeDetectionEngine: SwipeDetectionEngine = SwipeDetectionEngine(),
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val SESSION_IDLE_TIMEOUT_MS = 90_000L
    }

    private val sessionMutex = Mutex()

    @Volatile
    private var isTrackingEnabledByUser: Boolean = true

    private var activeSessionId: Long? = null
    private var activeSessionPlatform: Platform? = null
    private var activeSessionLastEventMs: Long = 0L

    init {
        externalScope.launch {
            repository.fastPreferencesFlow.collectLatest { prefs ->
                isTrackingEnabledByUser = prefs.trackingEnabled
                swipeDetectionEngine.cooldownMs = prefs.cooldownMs
                if (!prefs.trackingEnabled) {
                    closeActiveSessionNow(System.currentTimeMillis())
                }
            }
        }
    }

    fun processSnapshot(snapshot: AccessibilitySignalSnapshot) {
        if (!isTrackingEnabledByUser) return

        val outcome = swipeDetectionEngine.evaluate(snapshot)
        val nowMs = snapshot.eventTimestampMs

        DiagnosticsStateHolder.onEventEvaluated(
            platform = outcome.platform,
            feedContext = outcome.feedContext,
            confidence = outcome.confidence,
            isAcceptedSwipe = outcome.isAcceptedSwipe,
            isDuplicateRejected = outcome.isDuplicateRejected,
            isUncertainEvent = outcome.isUncertainEvent,
            diagnosticReason = outcome.diagnosticReason,
            timestampMs = nowMs
        )

        externalScope.launch {
            sessionMutex.withLock {
                handleSessionLifecycleLocked(snapshot, outcome, nowMs)

                if (outcome.isAcceptedSwipe && outcome.platform != null) {
                    val sessionId = activeSessionId ?: 0L
                    repository.recordConfirmedSwipe(
                        platform = outcome.platform,
                        timestampMs = nowMs,
                        eventTypeLabel = eventTypeToString(snapshot.eventType),
                        confidence = outcome.confidence,
                        sessionId = sessionId,
                        eventSignatureHash = outcome.eventSignatureHash
                    )
                } else if (outcome.isUncertainEvent) {
                    repository.recordUncertainEvent(nowMs)
                }
            }
        }
    }

    private suspend fun handleSessionLifecycleLocked(
        snapshot: AccessibilitySignalSnapshot,
        outcome: SwipeEvaluationOutcome,
        nowMs: Long
    ) {
        val currentSessionId = activeSessionId
        val currentPlatform = activeSessionPlatform

        // Check if an existing session exceeded the idle timeout
        if (currentSessionId != null && (nowMs - activeSessionLastEventMs) > SESSION_IDLE_TIMEOUT_MS) {
            val cappedEndMs = activeSessionLastEventMs + 15_000L
            repository.finalizeUsageSession(currentSessionId, minOf(nowMs, cappedEndMs))
            activeSessionId = null
            activeSessionPlatform = null
        }

        if (outcome.feedContext.isEligibleShortFeed && outcome.platform != null) {
            if (activeSessionId != null && activeSessionPlatform != outcome.platform) {
                // Switched from YouTube Shorts to Instagram Reels (or vice versa)
                repository.finalizeUsageSession(activeSessionId!!, nowMs)
                activeSessionId = null
                activeSessionPlatform = null
            }

            if (activeSessionId == null) {
                val createdId = repository.startUsageSession(
                    platform = outcome.platform,
                    feedContext = outcome.feedContext,
                    startTimestampMs = nowMs,
                    confidenceScore = outcome.confidence.score.coerceAtLeast(0.75f)
                )
                activeSessionId = createdId
                activeSessionPlatform = outcome.platform
            }
            activeSessionLastEventMs = nowMs
        } else if (currentSessionId != null) {
            // Left the eligible Shorts/Reels context (e.g., opened comments, search, home tab)
            if (outcome.feedContext != FeedContext.UNKNOWN_CONTEXT || !snapshot.isScreenInteractive) {
                repository.finalizeUsageSession(currentSessionId, nowMs)
                activeSessionId = null
                activeSessionPlatform = null
            }
        }
    }

    fun onScreenLockedOrAppSwitched(timestampMs: Long = System.currentTimeMillis()) {
        swipeDetectionEngine.resetState()
        closeActiveSessionNow(timestampMs)
    }

    fun closeActiveSessionNow(timestampMs: Long = System.currentTimeMillis()) {
        externalScope.launch {
            sessionMutex.withLock {
                val sessionId = activeSessionId
                if (sessionId != null) {
                    repository.finalizeUsageSession(sessionId, timestampMs)
                    activeSessionId = null
                    activeSessionPlatform = null
                }
            }
        }
    }

    private fun eventTypeToString(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
        else -> "TYPE_OTHER_$eventType"
    }
}
