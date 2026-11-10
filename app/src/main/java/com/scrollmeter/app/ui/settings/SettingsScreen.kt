package com.scrollmeter.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.domain.models.ThemePreference

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isAccessibilityPermissionEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    var combinedInput by remember(settings.dailyCombinedLimit) {
        mutableStateOf(settings.dailyCombinedLimit.toString())
    }
    var youtubeInput by remember(settings.youtubeDailyLimit) {
        mutableStateOf(settings.youtubeDailyLimit.toString())
    }
    var instagramInput by remember(settings.instagramDailyLimit) {
        mutableStateOf(settings.instagramDailyLimit.toString())
    }
    var timeInput by remember(settings.timeLimitMinutes) {
        mutableStateOf(settings.timeLimitMinutes.toString())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Settings & Privacy Controls",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Configure daily swipe limits, warning thresholds, theme, and local privacy settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Tracking Control & Accessibility Permission
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable ScrollMeter Tracking",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pause or resume swipe event counting at any time without losing history.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.trackingEnabled,
                            onCheckedChange = { viewModel.setTrackingEnabled(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isAccessibilityPermissionEnabled) "Manage OS Permission" else "Grant OS Permission")
                        }
                        OutlinedButton(
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Diagnostics & Test")
                        }
                    }
                }
            }
        }

        // Daily Limits Configuration
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
                        text = "Daily Swipe & Time Limits",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = combinedInput,
                            onValueChange = { combinedInput = it.filter(Char::isDigit) },
                            label = { Text("Combined Swipes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = timeInput,
                            onValueChange = { timeInput = it.filter(Char::isDigit) },
                            label = { Text("Time Limit (min)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = youtubeInput,
                            onValueChange = { youtubeInput = it.filter(Char::isDigit) },
                            label = { Text("YouTube Limit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = instagramInput,
                            onValueChange = { instagramInput = it.filter(Char::isDigit) },
                            label = { Text("Instagram Limit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveLimits(
                                dailyCombinedLimit = combinedInput.toIntOrNull() ?: settings.dailyCombinedLimit,
                                youtubeDailyLimit = youtubeInput.toIntOrNull() ?: settings.youtubeDailyLimit,
                                instagramDailyLimit = instagramInput.toIntOrNull() ?: settings.instagramDailyLimit,
                                timeLimitMinutes = timeInput.toIntOrNull() ?: settings.timeLimitMinutes
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Daily Limits")
                    }
                }
            }
        }

        // Warning Thresholds (50%, 75%, 90%, 100%)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Threshold Notifications",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Non-intrusive alerts when you reach your chosen thresholds. Never blocks or closes apps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.notificationEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setNotificationsEnabled(enabled)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ThresholdCheckbox("50%", settings.warnAt50) { viewModel.toggleThreshold(50, it) }
                        ThresholdCheckbox("75%", settings.warnAt75) { viewModel.toggleThreshold(75, it) }
                        ThresholdCheckbox("90%", settings.warnAt90) { viewModel.toggleThreshold(90, it) }
                        ThresholdCheckbox("100%", settings.warnAt100) { viewModel.toggleThreshold(100, it) }
                    }
                }
            }
        }

        // Theme & Cooldown Calibration
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
                        text = "Appearance & Detection Cooldown",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.themePreference == pref.id,
                                onClick = { viewModel.setThemePreference(pref) },
                                label = { Text(pref.label) }
                            )
                        }
                    }

                    Text(
                        text = "Swipe Deduplication Cooldown: ${settings.cooldownMs} ms",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = settings.cooldownMs.toFloat(),
                        onValueChange = { viewModel.setCooldownMs(it.toLong()) },
                        valueRange = 250f..1500f,
                        steps = 24
                    )
                    Text(
                        text = "Suppresses multi-event scroll bursts emitted during a single finger fling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // In-App Privacy Guarantee
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
                        text = "Privacy & Security Architecture",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Zero Internet Permission: AndroidManifest.xml omits android.permission.INTERNET. ScrollMeter cannot connect to any server.\n" +
                            "• Strict Package Scope: Only com.google.android.youtube and com.instagram.android are monitored.\n" +
                            "• Zero Content Capture: ScrollMeter never reads messages, passwords, typed text, comments, or screen pixels.\n" +
                            "• Complete User Control: Disable tracking or wipe all local Room database records at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenOnboarding) {
                        Text("Review Permission & Privacy Onboarding")
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
private fun ThresholdCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
