package com.scrollmeter.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mandatory two-action Prominent Disclosure & Consent Dialog required by store review policies
 * before redirecting the user to system Accessibility Settings.
 *
 * Clearly states:
 * 1. Why the AccessibilityService API is requested.
 * 2. What specific window/scroll metadata is processed.
 * 3. That zero personal data or text is ever collected, stored, or transmitted off-device.
 * 4. Explicit "Accept & Open Settings" and "Decline" actions.
 */
@Composable
fun AccessibilityProminentDisclosureDialog(
    onAcceptAndContinue: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = "Accessibility Service Disclosure",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ScrollMeter requires your explicit consent to use the Android AccessibilityService API for core short-video swipe counting.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "What Data Is Accessed:\n" +
                        "ScrollMeter inspects structural scroll events (TYPE_VIEW_SCROLLED and window state transitions) strictly within two target packages: com.google.android.youtube and com.instagram.android.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Privacy & Offline Guarantee:\n" +
                        "• ScrollMeter NEVER reads messages, passwords, typed text, or personal content.\n" +
                        "• ScrollMeter has NO internet permission in its manifest; all swipe counts remain 100% offline on your device.\n" +
                        "• You can disable this permission or delete all stored records at any time.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onAcceptAndContinue) {
                Text("Accept & Open Settings")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}
