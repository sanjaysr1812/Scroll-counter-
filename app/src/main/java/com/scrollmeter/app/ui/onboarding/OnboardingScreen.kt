package com.scrollmeter.app.ui.onboarding

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.ui.theme.StatusPositiveGreen
import com.scrollmeter.app.ui.theme.StatusWarningAmber

/**
 * First-launch onboarding experience that transparently explains:
 * 1. What ScrollMeter tracks.
 * 2. Why Accessibility permission is needed.
 * 3. Which applications are monitored.
 * 4. What information is stored.
 * 5. How the user can disable tracking.
 * 6. How to delete all recorded data.
 */
@Composable
fun OnboardingScreen(
    isAccessibilityPermissionEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Welcome to ScrollMeter",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Know Every Swipe. Take Back Your Time.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            OnboardingInfoCard(
                stepTitle = "1. What ScrollMeter Tracks",
                body = "ScrollMeter counts vertical feed-navigation swipe events while you browse YouTube Shorts and Instagram Reels, and estimates time spent in those short-video feeds."
            )
        }

        item {
            OnboardingInfoCard(
                stepTitle = "2. Why Accessibility Permission Is Needed",
                body = "Android does not provide a standard API to count vertical swipes inside third-party short-video feeds. ScrollMeter uses Android's AccessibilityService API strictly to detect vertical scroll and pager transitions in real time."
            )
        }

        item {
            OnboardingInfoCard(
                stepTitle = "3. Which Applications Are Monitored",
                body = "Strictly two packages:\n• YouTube (com.google.android.youtube)\n• Instagram (com.instagram.android)\nAll other applications on your phone are ignored at the Android OS service filter level."
            )
        }

        item {
            OnboardingInfoCard(
                stepTitle = "4. What Information Is Stored",
                body = "Only swipe timestamps, platform identifiers, confidence scores, and feed session durations are stored in a local Room database on your device. ScrollMeter has NO internet permission and never reads messages, passwords, typed text, or screen images."
            )
        }

        item {
            OnboardingInfoCard(
                stepTitle = "5. How You Can Disable Tracking",
                body = "You can pause tracking at any time using the toggle in ScrollMeter Settings, or revoke the Accessibility Service permission in Android System Settings."
            )
        }

        item {
            OnboardingInfoCard(
                stepTitle = "6. How to Delete All Recorded Data",
                body = "Open the History tab at any time to delete today's activity, delete a selected date range, export your personal CSV, or permanently wipe all local data."
            )
        }

        // Live Permission Verification Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isAccessibilityPermissionEnabled) StatusPositiveGreen else StatusWarningAmber
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isAccessibilityPermissionEnabled) {
                            "Permission Verified: ScrollMeter Service Enabled"
                        } else {
                            "Permission Status: Not Enabled Yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAccessibilityPermissionEnabled) StatusPositiveGreen else StatusWarningAmber
                    )

                    Text(
                        text = if (isAccessibilityPermissionEnabled) {
                            "Android Accessibility Settings reports that ScrollMeter is active. You can now proceed to your dashboard."
                        } else {
                            "Tap 'Open Android Accessibility Settings' below, select ScrollMeter under Downloaded Apps, and enable the toggle. When you return here, status updates automatically."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open Android Accessibility Settings")
                        }
                    }

                    OutlinedButton(
                        onClick = onCompleteOnboarding,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isAccessibilityPermissionEnabled) {
                                "Continue to ScrollMeter Dashboard"
                            } else {
                                "Continue Without Enabling (Tracking Remains Off)"
                            }
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
private fun OnboardingInfoCard(
    stepTitle: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stepTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
