package com.scrollmeter.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.scrollmeter.app.ScrollMeterApplication
import com.scrollmeter.app.data.repository.DiagnosticsStateHolder
import com.scrollmeter.app.domain.models.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Native Android [AccessibilityService] for ScrollMeter.
 *
 * Monitors only "com.google.android.youtube" and "com.instagram.android" after the user
 * explicitly enables the service in Android System Settings.
 *
 * Handles:
 * - Service connection and interruption lifecycle.
 * - Screen lock (ACTION_SCREEN_OFF) and timezone change broadcasts.
 * - Battery-conscious node geometry extraction via [FeedContextDetector].
 */
class ScrollAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val feedContextDetector = FeedContextDetector()
    private var eventProcessor: AccessibilityEventProcessor? = null
    private var isReceiverRegistered = false

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    eventProcessor?.onScreenLockedOrAppSwitched(System.currentTimeMillis())
                }
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED -> {
                    val app = applicationContext as? ScrollMeterApplication ?: return
                    serviceScope.launch {
                        app.appContainer.repository.recalculateDailyStatsForCurrentTimezone()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = applicationContext as? ScrollMeterApplication
        if (app != null) {
            eventProcessor = AccessibilityEventProcessor(
                repository = app.appContainer.repository,
                swipeDetectionEngine = app.appContainer.swipeDetectionEngine,
                externalScope = serviceScope
            )
            serviceScope.launch {
                app.appContainer.repository.recoverOrphanedSessions()
            }
        }

        registerSystemStateReceiver()
        DiagnosticsStateHolder.onServiceConnected(System.currentTimeMillis())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: return

        // Strict package allowlist verification
        if (Platform.fromPackageName(pkgName) == null) {
            eventProcessor?.onScreenLockedOrAppSwitched(System.currentTimeMillis())
            return
        }

        val processor = eventProcessor ?: return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = powerManager?.isInteractive ?: true

        val (screenW, screenH) = getScreenDimensionsPx()
        val rootNode = runCatching { rootInActiveWindow }.getOrNull()

        val snapshot = feedContextDetector.buildSignalSnapshot(
            event = event,
            rootNode = rootNode,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
            isScreenInteractive = isInteractive
        )

        processor.processSnapshot(snapshot)
    }

    override fun onInterrupt() {
        eventProcessor?.closeActiveSessionNow(System.currentTimeMillis())
        DiagnosticsStateHolder.onServiceDisconnected(System.currentTimeMillis())
    }

    override fun onUnbind(intent: Intent?): Boolean {
        eventProcessor?.closeActiveSessionNow(System.currentTimeMillis())
        DiagnosticsStateHolder.onServiceDisconnected(System.currentTimeMillis())
        unregisterSystemStateReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        eventProcessor?.closeActiveSessionNow(System.currentTimeMillis())
        DiagnosticsStateHolder.onServiceDisconnected(System.currentTimeMillis())
        unregisterSystemStateReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerSystemStateReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        runCatching {
            registerReceiver(systemStateReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterSystemStateReceiver() {
        if (!isReceiverRegistered) return
        runCatching {
            unregisterReceiver(systemStateReceiver)
            isReceiverRegistered = false
        }
    }

    private fun getScreenDimensionsPx(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return if (wm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().coerceAtLeast(1080) to bounds.height().coerceAtLeast(1920)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(metrics)
            metrics.widthPixels.coerceAtLeast(1080) to metrics.heightPixels.coerceAtLeast(1920)
        }
    }
}
