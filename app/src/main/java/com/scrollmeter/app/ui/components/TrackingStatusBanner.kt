package com.scrollmeter.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.ui.theme.StatusAlertRed
import com.scrollmeter.app.ui.theme.StatusPositiveGreen
import com.scrollmeter.app.ui.theme.StatusWarningAmber

/**
 * Prominent top tracking status banner displayed across the application.
 * Accurately reports whether Android Accessibility permission is granted and whether
 * the user has paused tracking.
 */
@Composable
fun TrackingStatusBanner(
    isAccessibilityPermissionEnabled: Boolean,
    isServiceConnectedInProcess: Boolean,
    isTrackingEnabledByUser: Boolean,
    lastEventTimeFormatted: String,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusTitle, statusSubtitle, borderColor) = when {
        !isAccessibilityPermissionEnabled -> Triple(
            "Accessibility Permission Disabled — Tracking Stopped",
            "ScrollMeter cannot detect YouTube Shorts or Instagram Reels swipes until enabled in Android Accessibility Settings.",
            StatusAlertRed
        )
        !isTrackingEnabledByUser -> Triple(
            "Tracking Paused by User",
            "You have paused swipe tracking in ScrollMeter Settings. No events are being counted.",
            StatusWarningAmber
        )
        isServiceConnectedInProcess -> Triple(
            "Tracking Active · Monitoring YouTube Shorts & Instagram Reels",
            "Last event: $lastEventTimeFormatted",
            StatusPositiveGreen
        )
        else -> Triple(
            "Permission Enabled · Waiting for Service Binding",
            "Open YouTube Shorts or Instagram Reels, or inspect Tracking Diagnostics.",
            StatusWarningAmber
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isAccessibilityPermissionEnabled) {
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAlertRed)
                    ) {
                        Text("Enable in Android Settings")
                    }
                }
                OutlinedButton(onClick = onOpenDiagnostics) {
                    Text("Tracking Diagnostics")
                }
            }
        }
    }
}
