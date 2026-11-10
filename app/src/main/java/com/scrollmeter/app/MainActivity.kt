package com.scrollmeter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollmeter.app.data.database.entities.UserSettingsEntity
import com.scrollmeter.app.data.repository.FastServicePreferences
import com.scrollmeter.app.domain.models.ThemePreference
import com.scrollmeter.app.ui.navigation.ScrollMeterNavGraph
import com.scrollmeter.app.ui.onboarding.AccessibilityProminentDisclosureDialog
import com.scrollmeter.app.ui.theme.ScrollMeterTheme
import com.scrollmeter.app.utils.PermissionUtils
import kotlinx.coroutines.launch

/**
 * Main entry point Activity for ScrollMeter.
 *
 * Enforces the mandatory two-step Prominent Disclosure dialog prior to opening system
 * Accessibility Settings and automatically re-verifies permission status on ON_RESUME.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ScrollMeterApplication).appContainer

        setContent {
            val userSettings by appContainer.repository
                .observeUserSettings()
                .collectAsStateWithLifecycle(initialValue = UserSettingsEntity())

            val fastPrefs by appContainer.repository
                .fastPreferencesFlow
                .collectAsStateWithLifecycle(initialValue = FastServicePreferences(onboardingCompleted = true))

            var isPermissionEnabled by remember {
                mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(this@MainActivity))
            }

            var showProminentDisclosureDialog by remember {
                mutableStateOf(false)
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        isPermissionEnabled = PermissionUtils.isAccessibilityServiceEnabled(this@MainActivity)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val coroutineScope = rememberCoroutineScope()
            val themePref = ThemePreference.fromId(userSettings.themePreference)

            ScrollMeterTheme(themePreference = themePref) {
                if (showProminentDisclosureDialog) {
                    AccessibilityProminentDisclosureDialog(
                        onAcceptAndContinue = {
                            showProminentDisclosureDialog = false
                            runCatching {
                                startActivity(PermissionUtils.createAccessibilitySettingsIntent())
                            }
                        },
                        onDecline = {
                            showProminentDisclosureDialog = false
                        }
                    )
                }

                ScrollMeterNavGraph(
                    appContainer = appContainer,
                    startInOnboarding = !fastPrefs.onboardingCompleted,
                    isAccessibilityPermissionEnabled = isPermissionEnabled,
                    onOpenAccessibilitySettings = {
                        // Always present the Prominent Disclosure & Consent dialog before redirecting to OS settings
                        showProminentDisclosureDialog = true
                    },
                    onMarkOnboardingCompleted = {
                        coroutineScope.launch {
                            appContainer.repository.setOnboardingCompleted(true)
                        }
                    }
                )
            }
        }
    }
}
