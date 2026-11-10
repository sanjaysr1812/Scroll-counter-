package com.scrollmeter.app.ui.dashboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.domain.models.Platform
import com.scrollmeter.app.ui.components.TrackingStatusBanner
import com.scrollmeter.app.ui.theme.InstagramReelsMagenta
import com.scrollmeter.app.ui.theme.StatusAlertRed
import com.scrollmeter.app.ui.theme.StatusWarningAmber
import com.scrollmeter.app.ui.theme.YouTubeShortsRed
import com.scrollmeter.app.utils.DateTimeUtils

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    isAccessibilityPermissionEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.todayStats
    val settings = state.settings
    val limitEval = state.limitEvaluation
    val totalEstimatedSeconds = stats.youtubeDuration + stats.instagramDuration

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ScrollMeter",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Know Every Swipe. Take Back Your Time. · ${DateTimeUtils.formatLocalDateForDisplay(state.todayDateIso)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Prominent Tracking Status Indicator
        item {
            TrackingStatusBanner(
                isAccessibilityPermissionEnabled = isAccessibilityPermissionEnabled,
                isServiceConnectedInProcess = state.diagnostics.isServiceConnectedInProcess,
                isTrackingEnabledByUser = settings.trackingEnabled,
                lastEventTimeFormatted = DateTimeUtils.formatTimestampTime(
                    state.diagnostics.lastAcceptedSwipeTimestampMs ?: state.diagnostics.lastEventReceivedTimestampMs
                ),
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenDiagnostics = onOpenDiagnostics
            )
        }

        // Threshold Warning Banner (50% / 75% / 90% / 100%)
        limitEval.warningBannerMessage?.let { warningMsg ->
            item {
                val isFullLimit = (limitEval.activeWarningLevelPercent ?: 0) >= 100
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isFullLimit) StatusAlertRed else StatusWarningAmber)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (isFullLimit) "Daily Allowance Reached" else "Daily Threshold Warning (${limitEval.activeWarningLevelPercent}%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isFullLimit) StatusAlertRed else StatusWarningAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = warningMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Primary Hero Counter: Total Confirmed Swipes Today
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Confirmed Swipes Today",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stats.totalSwipeCount}",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LinearProgressIndicator(
                        progress = { limitEval.combinedProgressRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Daily limit: ${settings.dailyCombinedLimit}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Remaining allowance: ${limitEval.remainingCombinedSwipes}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (stats.uncertainEventCount > 0) {
                        Text(
                            text = "Uncertain transitions excluded from confirmed total: ${stats.uncertainEventCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Platform Cards: YouTube Shorts & Instagram Reels
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlatformCounterCard(
                    title = "YouTube Shorts",
                    swipeCount = stats.youtubeSwipeCount,
                    dailyLimit = settings.youtubeDailyLimit,
                    estimatedDurationSeconds = stats.youtubeDuration,
                    accentColor = YouTubeShortsRed,
                    modifier = Modifier.weight(1f)
                )
                PlatformCounterCard(
                    title = "Instagram Reels",
                    swipeCount = stats.instagramSwipeCount,
                    dailyLimit = settings.instagramDailyLimit,
                    estimatedDurationSeconds = stats.instagramDuration,
                    accentColor = InstagramReelsMagenta,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Estimated Time & Last Event Summary
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Short-Feed Usage Today",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = DateTimeUtils.formatDurationHumanReadable(totalEstimatedSeconds),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Time is estimated from active YouTube Shorts and Instagram Reels feed sessions only (general app browsing is excluded). Target limit: ${settings.timeLimitMinutes}m.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Last confirmed swipe: ${DateTimeUtils.formatTimestampTime(state.diagnostics.lastAcceptedSwipeTimestampMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Today's Activity Timeline
        item {
            Text(
                text = "Today's Activity Timeline",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (state.recentSwipes.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No Swipe Activity Recorded Today",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Once ScrollMeter Accessibility Service is enabled and you swipe vertically in YouTube Shorts or Instagram Reels, confirmed events will appear here in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.recentSwipes, key = { it.id }) { event ->
                val platform = Platform.fromId(event.platform)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = platform.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Confidence ${(event.confidence * 100).toInt()}% · ${event.eventType}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = DateTimeUtils.formatTimestampTime(event.eventTimestamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlatformCounterCard(
    title: String,
    swipeCount: Int,
    dailyLimit: Int,
    estimatedDurationSeconds: Long,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor
            )
            Text(
                text = "$swipeCount",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            LinearProgressIndicator(
                progress = { (swipeCount.toFloat() / dailyLimit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "Limit: $dailyLimit · Est. ${DateTimeUtils.formatDurationHumanReadable(estimatedDurationSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
