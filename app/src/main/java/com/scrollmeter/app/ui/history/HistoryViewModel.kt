package com.scrollmeter.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import com.scrollmeter.app.data.database.entities.UsageSessionEntity
import com.scrollmeter.app.data.repository.ScrollMeterRepository
import com.scrollmeter.app.domain.usecases.ExportStatisticsCsvUseCase
import com.scrollmeter.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val allDailyStats: List<DailyStatisticsEntity> = emptyList(),
    val recentSessions: List<UsageSessionEntity> = emptyList(),
    val statusMessage: String? = null
)

class HistoryViewModel(
    private val repository: ScrollMeterRepository,
    private val exportStatisticsCsvUseCase: ExportStatisticsCsvUseCase
) : ViewModel() {

    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val thirtyDaysAgo = DateTimeUtils.daysAgoIsoString(29)
    private val todayIso = DateTimeUtils.currentLocalDateString()

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeAllDailyStatistics(),
        repository.observeTodaySessions(todayIso),
        statusMessageFlow
    ) { dailyList, todaySessions, message ->
        HistoryUiState(
            allDailyStats = dailyList,
            recentSessions = todaySessions,
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun exportCsv(onCsvReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = exportStatisticsCsvUseCase()
            statusMessageFlow.value = "Generated CSV export (${csv.lines().size - 1} daily records)."
            onCsvReady(csv)
        }
    }

    fun deleteTodayData() {
        viewModelScope.launch {
            repository.deleteTodayData()
            statusMessageFlow.value = "Deleted today's swipe and session records."
        }
    }

    fun deleteDateRange(startDateIso: String, endDateIso: String) {
        viewModelScope.launch {
            repository.deleteDateRangeData(startDateIso, endDateIso)
            statusMessageFlow.value = "Deleted records from $startDateIso to $endDateIso."
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAllData()
            statusMessageFlow.value = "All local swipe and session history has been permanently deleted."
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    companion object {
        fun provideFactory(
            repository: ScrollMeterRepository,
            exportStatisticsCsvUseCase: ExportStatisticsCsvUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(repository, exportStatisticsCsvUseCase) as T
            }
        }
    }
}
