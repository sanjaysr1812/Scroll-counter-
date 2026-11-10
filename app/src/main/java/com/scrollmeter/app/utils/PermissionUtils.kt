package com.scrollmeter.app.utils

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.scrollmeter.app.accessibility.ScrollAccessibilityService
import com.scrollmeter.app.domain.models.Platform

/**
 * Verifies Android AccessibilityService permission state, notification permission state,
 * and target application installation without ever attempting to bypass Android OS security.
 */
object PermissionUtils {

    /**
     * Checks both Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES and AccessibilityManager
     * to determine whether the user has explicitly enabled [ScrollAccessibilityService].
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, ScrollAccessibilityService::class.java)

        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
        }.getOrDefault(false)

        if (accessibilityEnabled) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!settingValue.isNullOrBlank()) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    val componentNameString = splitter.next()
                    val enabledService = ComponentName.unflattenFromString(componentNameString)
                    if (enabledService != null && enabledService == expectedComponent) {
                        return true
                    }
                }
            }
        }

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val runningServices = am?.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).orEmpty()

        return runningServices.any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo
            serviceInfo?.packageName == context.packageName &&
                serviceInfo?.name == ScrollAccessibilityService::class.java.name
        }
    }

    /**
     * Builds an Intent that opens the system Android Accessibility Settings screen.
     */
    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Checks if POST_NOTIFICATIONS is granted on Android 13+ (API 33+).
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks whether YouTube or Instagram is installed on the device using the explicit
     * <queries> declaration in AndroidManifest.xml.
     */
    fun isPlatformAppInstalled(context: Context, platform: Platform): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    platform.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(platform.packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
