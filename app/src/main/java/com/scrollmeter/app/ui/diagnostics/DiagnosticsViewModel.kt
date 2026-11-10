package com.scrollmeter.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.scrollmeter.app.data.repository.DiagnosticsState
import com.scrollmeter.app.data.repository.DiagnosticsStateHolder
import com.scrollmeter.app.domain.models.Platform
import com.scrollmeter.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.StateFlow

class DiagnosticsViewModel : ViewModel() {

    val diagnosticsState: StateFlow<DiagnosticsState> = DiagnosticsStateHolder.state

    fun startCalibrationTest(platform: Platform, expectedSwipes: Int = 5) {
        DiagnosticsStateHolder.startCalibrationTest(platform, expectedSwipes)
    }

    fun stopCalibrationTest() {
        DiagnosticsStateHolder.stopCalibrationTest()
    }

    fun resetCounters() {
        DiagnosticsStateHolder.resetSessionCounters()
    }

    fun buildExplicitDiagnosticReport(
        isPermissionEnabled: Boolean,
        isYouTubeInstalled: Boolean,
        isInstagramInstalled: Boolean
    ): String {
        val s = diagnosticsState.value
        return buildString {
            appendLine("=== ScrollMeter Diagnostic Report (User-Initiated Export) ===")
            appendLine("Accessibility Permission Enabled: $isPermissionEnabled")
            appendLine("Service Connected In-Process: ${s.isServiceConnectedInProcess}")
            appendLine("YouTube App Installed: $isYouTubeInstalled")
            appendLine("Instagram App Installed: $isInstagramInstalled")
            appendLine("YouTube Detection State: ${s.youtubeDetectionStatus}")
            appendLine("Instagram Detection State: ${s.instagramDetectionStatus}")
            appendLine("Current Confidence: ${s.currentConfidence.displayLabel}")
            appendLine("Last Event Received: ${DateTimeUtils.formatTimestampTime(s.lastEventReceivedTimestampMs)} (${s.lastEventDescription})")
            appendLine("Last Accepted Swipe: ${DateTimeUtils.formatTimestampTime(s.lastAcceptedSwipeTimestampMs)} (${s.lastAcceptedSwipePlatform?.displayName ?: "None"})")
            appendLine("Session Events Inspected: ${s.totalEventsInspectedSession}")
            appendLine("Confirmed Swipes Accepted: ${s.acceptedSwipesSession}")
            appendLine("Events Ignored (Non-Short/Overlay): ${s.eventsIgnoredSession}")
            appendLine("Duplicate Events Rejected: ${s.duplicateEventsRejectedSession}")
            appendLine("Uncertain Events (Not Counted): ${s.uncertainEventsSession}")
        }
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiagnosticsViewModel() as T
            }
        }
    }
}
