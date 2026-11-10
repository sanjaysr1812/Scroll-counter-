package com.scrollmeter.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.database.dao.HourlyBucketCount
import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import com.scrollmeter.app.data.repository.ScrollMeterRepository
import com.scrollmeter.app.domain.models.Platform
import com.scrollmeter.app.utils.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class AnalyticsRangePreset(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    CUSTOM("Custom Range")
}

enum class AnalyticsPlatformTab(val label: String, val platform: Platform?) {
    COMBINED("Combined", null),
    YOUTUBE_SHORTS("YouTube Shorts", Platform.YOUTUBE_SHORTS),
    INSTAGRAM_REELS("Instagram Reels", Platform.INSTAGRAM_REELS)
}

data class AnalyticsUiState(
    val rangePreset: AnalyticsRangePreset = AnalyticsRangePreset.LAST_7_DAYS,
    val selectedPlatformTab: AnalyticsPlatformTab = AnalyticsPlatformTab.COMBINED,
    val startDateIso: String = DateTimeUtils.daysAgoIsoString(6),
    val endDateIso: String = DateTimeUtils.currentLocalDateString(),
    val dailyStatsInRange: List<DailyStatisticsEntity> = emptyList(),
    val hourlyBucketsInRange: List<HourlyBucketCount> = emptyList(),
    // Summary metrics calculated strictly from Room records
    val totalSwipesInPeriod: Int = 0,
    val youtubeSwipesInPeriod: Int = 0,
    val instagramSwipesInPeriod: Int = 0,
    val totalWeeklySwipes: Int = 0,
    val totalMonthlySwipes: Int = 0,
    val averageDailySwipes: Int = 0,
    val highestActivityDay: Pair<String, Int>? = null,
    val lowestActivityDay: Pair<String, Int>? = null,
    val mostActiveHour: Int? = null,
    val totalEstimatedDurationSeconds: Long = 0L,
    val youtubeSharePercent: Int = 0,
    val instagramSharePercent: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val repository: ScrollMeterRepository
) : ViewModel() {

    private val rangeState = MutableStateFlow(
        Triple(
            AnalyticsRangePreset.LAST_7_DAYS,
            DateTimeUtils.daysAgoIsoString(6),
            DateTimeUtils.currentLocalDateString()
        )
    )
    private val platformTabState = MutableStateFlow(AnalyticsPlatformTab.COMBINED)

    private val todayIso = DateTimeUtils.currentLocalDateString()
    private val sevenDaysAgoIso = DateTimeUtils.daysAgoIsoString(6)
    private val thirtyDaysAgoIso = DateTimeUtils.daysAgoIsoString(29)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        rangeState.flatMapLatest { (preset, start, end) ->
            combine(
                repository.observeStatisticsInRange(start, end),
                repository.observeHourlyDistribution(start, end)
            ) { stats, hourly ->
                Triple(preset to (start to end), stats, hourly)
            }
        },
        repository.observeStatisticsInRange(sevenDaysAgoIso, todayIso),
        repository.observeStatisticsInRange(thirtyDaysAgoIso, todayIso),
        platformTabState
    ) { (rangeMeta, statsInRange, hourlyInRange), weeklyStats, monthlyStats, platformTab ->
        val (preset, datePair) = rangeMeta
        val (startIso, endIso) = datePair

        val ytSwipes = statsInRange.sumOf { it.youtubeSwipeCount }
        val igSwipes = statsInRange.sumOf { it.instagramSwipeCount }
        val totalSwipes = ytSwipes + igSwipes

        val dayCount = statsInRange.size.coerceAtLeast(1)
        val avgDaily = if (statsInRange.isEmpty()) 0 else totalSwipes / dayCount

        val highestDay = statsInRange.maxByOrNull { it.totalSwipeCount }?.let { it.date to it.totalSwipeCount }
        val lowestDay = statsInRange.minByOrNull { it.totalSwipeCount }?.let { it.date to it.totalSwipeCount }

        val hourTotals = IntArray(24)
        for (bucket in hourlyInRange) {
            if (bucket.hourOfDay in 0..23) {
                if (platformTab.platform == null || bucket.platform == platformTab.platform.id) {
                    hourTotals[bucket.hourOfDay] += bucket.swipeCount
                }
            }
        }
        val peakHour = hourTotals.indices.maxByOrNull { hourTotals[it] }?.takeIf { hourTotals[it] > 0 }

        val totalDurationSec = statsInRange.sumOf {
            when (platformTab.platform) {
                Platform.YOUTUBE_SHORTS -> it.youtubeDuration
                Platform.INSTAGRAM_REELS -> it.instagramDuration
                null -> it.youtubeDuration + it.instagramDuration
            }
        }

        val ytPct = if (totalSwipes > 0) (ytSwipes * 100) / totalSwipes else 0
        val igPct = if (totalSwipes > 0) 100 - ytPct else 0

        AnalyticsUiState(
            rangePreset = preset,
            selectedPlatformTab = platformTab,
            startDateIso = startIso,
            endDateIso = endIso,
            dailyStatsInRange = statsInRange,
            hourlyBucketsInRange = hourlyInRange,
            totalSwipesInPeriod = totalSwipes,
            youtubeSwipesInPeriod = ytSwipes,
            instagramSwipesInPeriod = igSwipes,
            totalWeeklySwipes = weeklyStats.sumOf { it.totalSwipeCount },
            totalMonthlySwipes = monthlyStats.sumOf { it.totalSwipeCount },
            averageDailySwipes = avgDaily,
            highestActivityDay = highestDay,
            lowestActivityDay = lowestDay,
            mostActiveHour = peakHour,
            totalEstimatedDurationSeconds = totalDurationSec,
            youtubeSharePercent = ytPct,
            instagramSharePercent = igPct
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalyticsUiState()
    )

    fun selectPreset(preset: AnalyticsRangePreset) {
        val today = DateTimeUtils.currentLocalDateString()
        val (start, end) = when (preset) {
            AnalyticsRangePreset.TODAY -> today to today
            AnalyticsRangePreset.YESTERDAY -> {
                val y = DateTimeUtils.daysAgoIsoString(1)
                y to y
            }
            AnalyticsRangePreset.LAST_7_DAYS -> DateTimeUtils.daysAgoIsoString(6) to today
            AnalyticsRangePreset.LAST_30_DAYS -> DateTimeUtils.daysAgoIsoString(29) to today
            AnalyticsRangePreset.CUSTOM -> rangeState.value.second to rangeState.value.third
        }
        rangeState.value = Triple(preset, start, end)
    }

    fun setCustomDateRange(startDateIso: String, endDateIso: String) {
        val safeStart = minOf(startDateIso, endDateIso)
        val safeEnd = maxOf(startDateIso, endDateIso)
        rangeState.value = Triple(AnalyticsRangePreset.CUSTOM, safeStart, safeEnd)
    }

    fun selectPlatformTab(tab: AnalyticsPlatformTab) {
        platformTabState.value = tab
    }

    companion object {
        fun provideFactory(repository: ScrollMeterRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AnalyticsViewModel(repository) as T
                }
            }
    }
}
