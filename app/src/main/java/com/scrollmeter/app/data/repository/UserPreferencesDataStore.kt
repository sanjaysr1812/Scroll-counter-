package com.scrollmeter.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scrollMeterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scrollmeter_preferences"
)

data class FastServicePreferences(
    val onboardingCompleted: Boolean = false,
    val trackingEnabled: Boolean = true,
    val cooldownMs: Long = 450L,
    val lastAlertDate: String = "",
    val highestNotifiedThresholdPercent: Int = 0,
    val lastKnownZoneId: String = ""
)

class UserPreferencesDataStore(private val context: Context) {

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        private val KEY_COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        private val KEY_LAST_ALERT_DATE = stringPreferencesKey("last_alert_date")
        private val KEY_HIGHEST_NOTIFIED_THRESHOLD = intPreferencesKey("highest_notified_threshold")
        private val KEY_LAST_KNOWN_ZONE_ID = stringPreferencesKey("last_known_zone_id")
    }

    val preferencesFlow: Flow<FastServicePreferences> = context.scrollMeterDataStore.data.map { prefs ->
        FastServicePreferences(
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
            trackingEnabled = prefs[KEY_TRACKING_ENABLED] ?: true,
            cooldownMs = prefs[KEY_COOLDOWN_MS] ?: 450L,
            lastAlertDate = prefs[KEY_LAST_ALERT_DATE] ?: "",
            highestNotifiedThresholdPercent = prefs[KEY_HIGHEST_NOTIFIED_THRESHOLD] ?: 0,
            lastKnownZoneId = prefs[KEY_LAST_KNOWN_ZONE_ID] ?: ""
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.scrollMeterDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.scrollMeterDataStore.edit { prefs ->
            prefs[KEY_TRACKING_ENABLED] = enabled
        }
    }

    suspend fun setCooldownMs(cooldownMs: Long) {
        context.scrollMeterDataStore.edit { prefs ->
            prefs[KEY_COOLDOWN_MS] = cooldownMs.coerceIn(250L, 1500L)
        }
    }

    suspend fun recordThresholdAlertSent(localDate: String, thresholdPercent: Int) {
        context.scrollMeterDataStore.edit { prefs ->
            prefs[KEY_LAST_ALERT_DATE] = localDate
            prefs[KEY_HIGHEST_NOTIFIED_THRESHOLD] = thresholdPercent
        }
    }

    suspend fun setLastKnownZoneId(zoneId: String) {
        context.scrollMeterDataStore.edit { prefs ->
            prefs[KEY_LAST_KNOWN_ZONE_ID] = zoneId
        }
    }

    suspend fun resetAlertHistory() {
        context.scrollMeterDataStore.edit { prefs ->
            prefs[KEY_LAST_ALERT_DATE] = ""
            prefs[KEY_HIGHEST_NOTIFIED_THRESHOLD] = 0
        }
    }
}
