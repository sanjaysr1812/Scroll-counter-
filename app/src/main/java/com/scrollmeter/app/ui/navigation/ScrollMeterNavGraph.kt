package com.scrollmeter.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scrollmeter.app.AppContainer
import com.scrollmeter.app.ui.analytics.AnalyticsScreen
import com.scrollmeter.app.ui.analytics.AnalyticsViewModel
import com.scrollmeter.app.ui.dashboard.DashboardScreen
import com.scrollmeter.app.ui.dashboard.DashboardViewModel
import com.scrollmeter.app.ui.diagnostics.DiagnosticsScreen
import com.scrollmeter.app.ui.diagnostics.DiagnosticsViewModel
import com.scrollmeter.app.ui.history.HistoryScreen
import com.scrollmeter.app.ui.history.HistoryViewModel
import com.scrollmeter.app.ui.onboarding.OnboardingScreen
import com.scrollmeter.app.ui.settings.SettingsScreen
import com.scrollmeter.app.ui.settings.SettingsViewModel

sealed class ScrollMeterDestination(
    val route: String,
    val label: String,
    val icon: ImageVector? = null
) {
    data object Home : ScrollMeterDestination("home", "Home", Icons.Outlined.Home)
    data object Analytics : ScrollMeterDestination("analytics", "Analytics", Icons.Outlined.Analytics)
    data object History : ScrollMeterDestination("history", "History", Icons.Outlined.History)
    data object Settings : ScrollMeterDestination("settings", "Settings", Icons.Outlined.Settings)
    data object Onboarding : ScrollMeterDestination("onboarding", "Onboarding")
    data object Diagnostics : ScrollMeterDestination("diagnostics", "Diagnostics")
}

private val bottomNavDestinations = listOf(
    ScrollMeterDestination.Home,
    ScrollMeterDestination.Analytics,
    ScrollMeterDestination.History,
    ScrollMeterDestination.Settings
)

@Composable
fun ScrollMeterNavGraph(
    appContainer: AppContainer,
    startInOnboarding: Boolean,
    isAccessibilityPermissionEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onMarkOnboardingCompleted: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                dest.icon?.let { Icon(imageVector = it, contentDescription = dest.label) }
                            },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startInOnboarding) {
                ScrollMeterDestination.Onboarding.route
            } else {
                ScrollMeterDestination.Home.route
            },
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ScrollMeterDestination.Onboarding.route) {
                OnboardingScreen(
                    isAccessibilityPermissionEnabled = isAccessibilityPermissionEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onCompleteOnboarding = {
                        onMarkOnboardingCompleted()
                        navController.navigate(ScrollMeterDestination.Home.route) {
                            popUpTo(ScrollMeterDestination.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(ScrollMeterDestination.Home.route) {
                val vm: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.provideFactory(
                        repository = appContainer.repository,
                        evaluateLimitThresholdsUseCase = appContainer.evaluateLimitThresholdsUseCase
                    )
                )
                DashboardScreen(
                    viewModel = vm,
                    isAccessibilityPermissionEnabled = isAccessibilityPermissionEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenDiagnostics = {
                        navController.navigate(ScrollMeterDestination.Diagnostics.route)
                    }
                )
            }

            composable(ScrollMeterDestination.Analytics.route) {
                val vm: AnalyticsViewModel = viewModel(
                    factory = AnalyticsViewModel.provideFactory(appContainer.repository)
                )
                AnalyticsScreen(viewModel = vm)
            }

            composable(ScrollMeterDestination.History.route) {
                val vm: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.provideFactory(
                        repository = appContainer.repository,
                        exportStatisticsCsvUseCase = appContainer.exportStatisticsCsvUseCase
                    )
                )
                HistoryScreen(viewModel = vm)
            }

            composable(ScrollMeterDestination.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.provideFactory(appContainer.repository)
                )
                SettingsScreen(
                    viewModel = vm,
                    isAccessibilityPermissionEnabled = isAccessibilityPermissionEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOnboarding = {
                        navController.navigate(ScrollMeterDestination.Onboarding.route)
                    },
                    onOpenDiagnostics = {
                        navController.navigate(ScrollMeterDestination.Diagnostics.route)
                    }
                )
            }

            composable(ScrollMeterDestination.Diagnostics.route) {
                val vm: DiagnosticsViewModel = viewModel(
                    factory = DiagnosticsViewModel.provideFactory()
                )
                DiagnosticsScreen(
                    viewModel = vm,
                    isAccessibilityPermissionEnabled = isAccessibilityPermissionEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }
        }
    }
}
