package com.scrollmeter.app.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.ui.components.DailySwipeLineChart
import com.scrollmeter.app.ui.components.DailyTimeSpentChart
import com.scrollmeter.app.ui.components.HourlyActivityChart
import com.scrollmeter.app.ui.components.PlatformComparisonBarChart
import com.scrollmeter.app.utils.DateTimeUtils

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var customStartInput by remember(state.startDateIso) { mutableStateOf(state.startDateIso) }
    var customEndInput by remember(state.endDateIso) { mutableStateOf(state.endDateIso) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Analytics & Platform Reports",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Calculated strictly from local Room database records (${state.startDateIso} to ${state.endDateIso})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Date Range Filter Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsRangePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.rangePreset == preset,
                        onClick = { viewModel.selectPreset(preset) },
                        label = { Text(preset.label) }
                    )
                }
            }
        }

        if (state.rangePreset == AnalyticsRangePreset.CUSTOM) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customStartInput,
                        onValueChange = {
                            customStartInput = it
                            if (it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                viewModel.setCustomDateRange(it, customEndInput)
                            }
                        },
                        label = { Text("Start (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customEndInput,
                        onValueChange = {
                            customEndInput = it
                            if (it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                viewModel.setCustomDateRange(customStartInput, it)
                            }
                        },
                        label = { Text("End (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Platform Section Selector: Combined | YouTube Shorts | Instagram Reels
        item {
            TabRow(
                selectedTabIndex = state.selectedPlatformTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AnalyticsPlatformTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedPlatformTab == tab,
                        onClick = { viewModel.selectPlatformTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }
        }

        // Combined & Platform-Specific Summary KPIs
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
                        text = "${state.selectedPlatformTab.label} Summary",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn("Period Swipes", "${state.totalSwipesInPeriod}")
                        MetricColumn("Daily Average", "${state.averageDailySwipes}")
                        MetricColumn("7-Day Total", "${state.totalWeeklySwipes}")
                        MetricColumn("30-Day Total", "${state.totalMonthlySwipes}")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn(
                            "YouTube Shorts",
                            "${state.youtubeSwipesInPeriod} (${state.youtubeSharePercent}%)"
                        )
                        MetricColumn(
                            "Instagram Reels",
                            "${state.instagramSwipesInPeriod} (${state.instagramSharePercent}%)"
                        )
                        MetricColumn(
                            "Est. Feed Time",
                            DateTimeUtils.formatDurationHumanReadable(state.totalEstimatedDurationSeconds)
                        )
                    }

                    val highestLabel = state.highestActivityDay?.let { "${it.first} (${it.second})" } ?: "None"
                    val lowestLabel = state.lowestActivityDay?.let { "${it.first} (${it.second})" } ?: "None"
                    val peakHourLabel = state.mostActiveHour?.let { String.format("%02d:00", it) } ?: "N/A"

                    Text(
                        text = "Highest day: $highestLabel · Lowest day: $lowestLabel · Most active hour: $peakHourLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Chart 1: Daily Swipe-Count Line Chart
        item {
            ChartContainerCard(title = "1. Daily Swipe-Count Trend") {
                DailySwipeLineChart(dailyStats = state.dailyStatsInRange)
            }
        }

        // Chart 2: YouTube Shorts vs Instagram Reels Bar Chart
        item {
            ChartContainerCard(title = "2. YouTube Shorts vs Instagram Reels") {
                PlatformComparisonBarChart(dailyStats = state.dailyStatsInRange)
            }
        }

        // Chart 3: Hourly Activity Distribution Chart
        item {
            ChartContainerCard(title = "3. Hourly Swipe Distribution (${state.selectedPlatformTab.label})") {
                HourlyActivityChart(
                    hourlyBuckets = state.hourlyBucketsInRange,
                    platformFilter = state.selectedPlatformTab.platform
                )
            }
        }

        // Chart 4: Daily Time-Spent Chart
        item {
            ChartContainerCard(title = "4. Estimated Daily Short-Feed Time") {
                DailyTimeSpentChart(dailyStats = state.dailyStatsInRange)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChartContainerCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}
