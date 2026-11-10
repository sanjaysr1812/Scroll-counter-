package com.scrollmeter.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import com.scrollmeter.app.data.database.entities.SwipeEventEntity
import com.scrollmeter.app.data.database.entities.UsageSessionEntity
import com.scrollmeter.app.data.database.entities.UserSettingsEntity
import com.scrollmeter.app.data.repository.DiagnosticsState
import com.scrollmeter.app.data.repository.DiagnosticsStateHolder
import com.scrollmeter.app.data.repository.ScrollMeterRepository
import com.scrollmeter.app.domain.usecases.EvaluateLimitThresholdsUseCase
import com.scrollmeter.app.domain.usecases.LimitStatusEvaluation
import com.scrollmeter.app.utils.DateTimeUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val todayDateIso: String = DateTimeUtils.currentLocalDateString(),
    val todayStats: DailyStatisticsEntity = DailyStatisticsEntity(date = DateTimeUtils.currentLocalDateString()),
    val settings: UserSettingsEntity = UserSettingsEntity(),
    val limitEvaluation: LimitStatusEvaluation = EvaluateLimitThresholdsUseCase().invoke(
        DailyStatisticsEntity(date = DateTimeUtils.currentLocalDateString()),
        UserSettingsEntity()
    ),
    val recentSwipes: List<SwipeEventEntity> = emptyList(),
    val todaySessions: List<UsageSessionEntity> = emptyList(),
    val diagnostics: DiagnosticsState = DiagnosticsState()
)

class DashboardViewModel(
    repository: ScrollMeterRepository,
    evaluateLimitThresholdsUseCase: EvaluateLimitThresholdsUseCase
) : ViewModel() {

    private val todayIso = DateTimeUtils.currentLocalDateString()

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeTodayStatistics(todayIso),
        repository.observeUserSettings(),
        repository.observeTodayRecentSwipes(todayIso, limit = 30),
        repository.observeTodaySessions(todayIso),
        DiagnosticsStateHolder.state
    ) { todayStats, settings, recentSwipes, sessions, diagnostics ->
        DashboardUiState(
            todayDateIso = todayIso,
            todayStats = todayStats,
            settings = settings,
            limitEvaluation = evaluateLimitThresholdsUseCase(todayStats, settings),
            recentSwipes = recentSwipes,
            todaySessions = sessions,
            diagnostics = diagnostics
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    companion object {
        fun provideFactory(
            repository: ScrollMeterRepository,
            evaluateLimitThresholdsUseCase: EvaluateLimitThresholdsUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(repository, evaluateLimitThresholdsUseCase) as T
            }
        }
    }
}
