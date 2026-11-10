package com.scrollmeter.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollmeter.app.data.database.entities.UserSettingsEntity
import com.scrollmeter.app.data.repository.ScrollMeterRepository
import com.scrollmeter.app.domain.models.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ScrollMeterRepository
) : ViewModel() {

    val settingsState: StateFlow<UserSettingsEntity> = repository.observeUserSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserSettingsEntity()
        )

    fun saveLimits(
        dailyCombinedLimit: Int,
        youtubeDailyLimit: Int,
        instagramDailyLimit: Int,
        timeLimitMinutes: Int
    ) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateUserSettings(
                current.copy(
                    dailyCombinedLimit = dailyCombinedLimit.coerceIn(10, 5000),
                    youtubeDailyLimit = youtubeDailyLimit.coerceIn(10, 5000),
                    instagramDailyLimit = instagramDailyLimit.coerceIn(10, 5000),
                    timeLimitMinutes = timeLimitMinutes.coerceIn(5, 720)
                )
            )
        }
    }

    fun toggleThreshold(percent: Int, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsState.value
            val updated = when (percent) {
                50 -> current.copy(warnAt50 = enabled)
                75 -> current.copy(warnAt75 = enabled)
                90 -> current.copy(warnAt90 = enabled)
                100 -> current.copy(warnAt100 = enabled)
                else -> current
            }
            repository.updateUserSettings(updated)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserSettings(settingsState.value.copy(notificationEnabled = enabled))
        }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserSettings(settingsState.value.copy(trackingEnabled = enabled))
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            repository.updateUserSettings(settingsState.value.copy(themePreference = theme.id))
        }
    }

    fun setCooldownMs(cooldownMs: Long) {
        viewModelScope.launch {
            repository.updateUserSettings(
                settingsState.value.copy(cooldownMs = cooldownMs.coerceIn(250L, 1500L))
            )
        }
    }

    companion object {
        fun provideFactory(repository: ScrollMeterRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository) as T
                }
            }
    }
}
