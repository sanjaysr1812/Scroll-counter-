package com.scrollmeter.app.data.repository

import com.scrollmeter.app.accessibility.DetectionConfidence
import com.scrollmeter.app.accessibility.FeedContext
import com.scrollmeter.app.domain.models.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of live AccessibilityService runtime diagnostics.
 * Powers the dedicated Tracking Diagnostics screen and manual calibration mode.
 */
data class DiagnosticsState(
    val isServiceConnectedInProcess: Boolean = false,
    val serviceConnectedSinceMs: Long? = null,
    val lastInterruptionTimestampMs: Long? = null,
    val youtubeDetectionStatus: String = "Waiting for YouTube Shorts activity",
    val instagramDetectionStatus: String = "Waiting for Instagram Reels activity",
    val activePlatform: Platform? = null,
    val activeFeedContext: FeedContext = FeedContext.UNKNOWN_CONTEXT,
    val currentConfidence: DetectionConfidence = DetectionConfidence.UNAVAILABLE,
    val lastEventReceivedTimestampMs: Long? = null,
    val lastEventDescription: String = "None",
    val lastAcceptedSwipeTimestampMs: Long? = null,
    val lastAcceptedSwipePlatform: Platform? = null,
    val totalEventsInspectedSession: Long = 0L,
    val acceptedSwipesSession: Long = 0L,
    val eventsIgnoredSession: Long = 0L,
    val duplicateEventsRejectedSession: Long = 0L,
    val uncertainEventsSession: Long = 0L,
    // Calibration / Manual Testing Mode
    val isCalibrationTestActive: Boolean = false,
    val calibrationTargetPlatform: Platform = Platform.YOUTUBE_SHORTS,
    val calibrationExpectedSwipes: Int = 5,
    val calibrationDetectedSwipes: Int = 0,
    val calibrationUncertainEvents: Int = 0,
    val calibrationStartedAtMs: Long? = null
)

object DiagnosticsStateHolder {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun onServiceConnected(timestampMs: Long = System.currentTimeMillis()) {
        _state.update {
            it.copy(
                isServiceConnectedInProcess = true,
                serviceConnectedSinceMs = timestampMs,
                currentConfidence = DetectionConfidence.IGNORED_NON_SHORT_FEED
            )
        }
    }

    fun onServiceDisconnected(timestampMs: Long = System.currentTimeMillis()) {
        _state.update {
            it.copy(
                isServiceConnectedInProcess = false,
                lastInterruptionTimestampMs = timestampMs,
                activePlatform = null,
                activeFeedContext = FeedContext.UNKNOWN_CONTEXT,
                currentConfidence = DetectionConfidence.UNAVAILABLE
            )
        }
    }

    fun onEventEvaluated(
        platform: Platform?,
        feedContext: FeedContext,
        confidence: DetectionConfidence,
        isAcceptedSwipe: Boolean,
        isDuplicateRejected: Boolean,
        isUncertainEvent: Boolean,
        diagnosticReason: String,
        timestampMs: Long
    ) {
        _state.update { current ->
            val ytStatus = if (platform == Platform.YOUTUBE_SHORTS) {
                "${feedContext.displayLabel} · ${confidence.code}"
            } else {
                current.youtubeDetectionStatus
            }

            val igStatus = if (platform == Platform.INSTAGRAM_REELS) {
                "${feedContext.displayLabel} · ${confidence.code}"
            } else {
                current.instagramDetectionStatus
            }

            val calDetected = if (current.isCalibrationTestActive &&
                isAcceptedSwipe &&
                platform == current.calibrationTargetPlatform
            ) {
                current.calibrationDetectedSwipes + 1
            } else {
                current.calibrationDetectedSwipes
            }

            val calUncertain = if (current.isCalibrationTestActive &&
                isUncertainEvent &&
                platform == current.calibrationTargetPlatform
            ) {
                current.calibrationUncertainEvents + 1
            } else {
                current.calibrationUncertainEvents
            }

            current.copy(
                activePlatform = platform,
                activeFeedContext = feedContext,
                currentConfidence = confidence,
                youtubeDetectionStatus = ytStatus,
                instagramDetectionStatus = igStatus,
                lastEventReceivedTimestampMs = timestampMs,
                lastEventDescription = diagnosticReason,
                lastAcceptedSwipeTimestampMs = if (isAcceptedSwipe) timestampMs else current.lastAcceptedSwipeTimestampMs,
                lastAcceptedSwipePlatform = if (isAcceptedSwipe) platform else current.lastAcceptedSwipePlatform,
                totalEventsInspectedSession = current.totalEventsInspectedSession + 1,
                acceptedSwipesSession = if (isAcceptedSwipe) current.acceptedSwipesSession + 1 else current.acceptedSwipesSession,
                eventsIgnoredSession = if (!isAcceptedSwipe && !isDuplicateRejected && !isUncertainEvent) {
                    current.eventsIgnoredSession + 1
                } else {
                    current.eventsIgnoredSession
                },
                duplicateEventsRejectedSession = if (isDuplicateRejected) {
                    current.duplicateEventsRejectedSession + 1
                } else {
                    current.duplicateEventsRejectedSession
                },
                uncertainEventsSession = if (isUncertainEvent) {
                    current.uncertainEventsSession + 1
                } else {
                    current.uncertainEventsSession
                },
                calibrationDetectedSwipes = calDetected,
                calibrationUncertainEvents = calUncertain
            )
        }
    }

    fun startCalibrationTest(targetPlatform: Platform, expectedSwipes: Int = 5) {
        _state.update {
            it.copy(
                isCalibrationTestActive = true,
                calibrationTargetPlatform = targetPlatform,
                calibrationExpectedSwipes = expectedSwipes.coerceIn(3, 25),
                calibrationDetectedSwipes = 0,
                calibrationUncertainEvents = 0,
                calibrationStartedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun stopCalibrationTest() {
        _state.update {
            it.copy(
                isCalibrationTestActive = false
            )
        }
    }

    fun resetSessionCounters() {
        _state.update {
            it.copy(
                totalEventsInspectedSession = 0L,
                acceptedSwipesSession = 0L,
                eventsIgnoredSession = 0L,
                duplicateEventsRejectedSession = 0L,
                uncertainEventsSession = 0L,
                calibrationDetectedSwipes = 0,
                calibrationUncertainEvents = 0
            )
        }
    }
}
