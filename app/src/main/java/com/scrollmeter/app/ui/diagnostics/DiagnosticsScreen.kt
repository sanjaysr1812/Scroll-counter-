package com.scrollmeter.app.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.domain.models.Platform
import com.scrollmeter.app.ui.theme.StatusAlertRed
import com.scrollmeter.app.ui.theme.StatusPositiveGreen
import com.scrollmeter.app.ui.theme.StatusWarningAmber
import com.scrollmeter.app.utils.DateTimeUtils
import com.scrollmeter.app.utils.PermissionUtils

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    isAccessibilityPermissionEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isYouTubeInstalled = PermissionUtils.isPlatformAppInstalled(context, Platform.YOUTUBE_SHORTS)
    val isInstagramInstalled = PermissionUtils.isPlatformAppInstalled(context, Platform.INSTAGRAM_REELS)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tracking Reliability & Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Verify live AccessibilityService health, test device-specific swipe detection, and inspect rejected or uncertain events.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Service & Permission Health Matrix
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Service & Platform Health",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DiagnosticRow(
                        label = "Accessibility Permission",
                        value = if (isAccessibilityPermissionEnabled) "Enabled in Android Settings" else "Disabled (Action Required)",
                        isPositive = isAccessibilityPermissionEnabled
                    )
                    DiagnosticRow(
                        label = "Tracking Service Health",
                        value = if (state.isServiceConnectedInProcess) "Bound & Listening" else "Not Bound",
                        isPositive = state.isServiceConnectedInProcess
                    )
                    DiagnosticRow(
                        label = "YouTube Detection Status",
                        value = if (isYouTubeInstalled) state.youtubeDetectionStatus else "YouTube App Not Installed",
                        isPositive = isYouTubeInstalled
                    )
                    DiagnosticRow(
                        label = "Instagram Detection Status",
                        value = if (isInstagramInstalled) state.instagramDetectionStatus else "Instagram App Not Installed",
                        isPositive = isInstagramInstalled
                    )
                    DiagnosticRow(
                        label = "Current Detection Confidence",
                        value = "${state.currentConfidence.displayLabel} (${(state.currentConfidence.score * 100).toInt()}%)",
                        isPositive = state.currentConfidence.countsAsConfirmedSwipe
                    )
                }
            }
        }

        // Live Event Telemetry & Uncertainty Warning
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Event Pipeline Counters (Current Session)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Last event received: ${DateTimeUtils.formatTimestampTime(state.lastEventReceivedTimestampMs)}\nDetail: ${state.lastEventDescription}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Last accepted swipe: ${DateTimeUtils.formatTimestampTime(state.lastAcceptedSwipeTimestampMs)} (${state.lastAcceptedSwipePlatform?.displayName ?: "None"})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CounterItem("Accepted Swipes", "${state.acceptedSwipesSession}")
                        CounterItem("Events Ignored", "${state.eventsIgnoredSession}")
                        CounterItem("Duplicates Rejected", "${state.duplicateEventsRejectedSession}")
                        CounterItem("Uncertain", "${state.uncertainEventsSession}")
                    }

                    if (state.uncertainEventsSession > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, StatusWarningAmber)
                        ) {
                            Text(
                                text = "Warning: ${state.uncertainEventsSession} events could not be reliably distinguished from ordinary scrolling on this app build and were NOT counted as confirmed swipes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedButton(onClick = { viewModel.resetCounters() }) {
                        Text("Reset Session Diagnostic Counters")
                    }
                }
            }
        }

        // Manual 5-Step Calibration & Verification Procedure
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Manual Device Calibration & Test Procedure",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1. Start a calibration run below for YouTube Shorts or Instagram Reels.\n" +
                            "2. Open the target app and enter its Shorts or Reels feed.\n" +
                            "3. Perform 5 deliberate vertical swipes.\n" +
                            "4. Return to ScrollMeter and compare detected events.\n" +
                            "5. Repeat with the second platform.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.isCalibrationTestActive) {
                        Text(
                            text = "Calibration Active for ${state.calibrationTargetPlatform.displayName}: " +
                                "${state.calibrationDetectedSwipes} / ${state.calibrationExpectedSwipes} confirmed swipes detected " +
                                "(${state.calibrationUncertainEvents} uncertain).",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(onClick = { viewModel.stopCalibrationTest() }) {
                            Text("Stop Calibration Test")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startCalibrationTest(Platform.YOUTUBE_SHORTS, 5) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test YouTube (5 Swipes)")
                            }
                            Button(
                                onClick = { viewModel.startCalibrationTest(Platform.INSTAGRAM_REELS, 5) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test Instagram (5 Swipes)")
                            }
                        }
                    }
                }
            }
        }

        // Explicit User-Initiated Diagnostic Share Action (Never silently uploaded)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isAccessibilityPermissionEnabled) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Accessibility Settings")
                    }
                }
                OutlinedButton(
                    onClick = {
                        val report = viewModel.buildExplicitDiagnosticReport(
                            isPermissionEnabled = isAccessibilityPermissionEnabled,
                            isYouTubeInstalled = isYouTubeInstalled,
                            isInstagramInstalled = isInstagramInstalled
                        )
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "ScrollMeter Diagnostic Summary")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export Diagnostic Summary")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isPositive: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (isPositive) StatusPositiveGreen else StatusAlertRed
        )
    }
}

@Composable
private fun CounterItem(label: String, count: String) {
    Column {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
