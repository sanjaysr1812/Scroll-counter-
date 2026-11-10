package com.scrollmeter.app

import android.app.Application
import android.content.Context
import com.scrollmeter.app.accessibility.SwipeDetectionEngine
import com.scrollmeter.app.data.database.ScrollMeterDatabase
import com.scrollmeter.app.data.repository.ScrollMeterRepository
import com.scrollmeter.app.data.repository.UserPreferencesDataStore
import com.scrollmeter.app.domain.usecases.EvaluateLimitThresholdsUseCase
import com.scrollmeter.app.domain.usecases.ExportStatisticsCsvUseCase
import com.scrollmeter.app.notifications.ScrollMeterNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Dependency container initialized by [ScrollMeterApplication].
 */
class AppContainer(context: Context) {
    val database: ScrollMeterDatabase = ScrollMeterDatabase.getInstance(context)
    val preferencesDataStore: UserPreferencesDataStore = UserPreferencesDataStore(context)
    val notificationManager: ScrollMeterNotificationManager = ScrollMeterNotificationManager(context)
    val swipeDetectionEngine: SwipeDetectionEngine = SwipeDetectionEngine()

    val repository: ScrollMeterRepository = ScrollMeterRepository(
        database = database,
        preferencesDataStore = preferencesDataStore,
        notificationManager = notificationManager
    )

    val evaluateLimitThresholdsUseCase = EvaluateLimitThresholdsUseCase()
    val exportStatisticsCsvUseCase = ExportStatisticsCsvUseCase(repository)
}

class ScrollMeterApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext)

        appScope.launch {
            // Recover any sessions interrupted by prior process death or reboot
            appContainer.repository.recoverOrphanedSessions()

            // Verify whether device timezone changed since last run
            val currentZoneId = ZoneId.systemDefault().id
            val prefs = appContainer.preferencesDataStore.preferencesFlow.first()
            if (prefs.lastKnownZoneId.isNotEmpty() && prefs.lastKnownZoneId != currentZoneId) {
                appContainer.repository.recalculateDailyStatsForCurrentTimezone()
            } else if (prefs.lastKnownZoneId.isEmpty()) {
                appContainer.preferencesDataStore.setLastKnownZoneId(currentZoneId)
            }
        }
    }
}
