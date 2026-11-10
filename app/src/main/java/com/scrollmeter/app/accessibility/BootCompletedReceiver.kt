package com.scrollmeter.app.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scrollmeter.app.ScrollMeterApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Cleans up any unclosed usage sessions after a device reboot or app update.
 * Does not attempt to forcibly start services or bypass Android security;
 * Android automatically rebinds enabled AccessibilityServices on boot when permitted by the OEM OS.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as? ScrollMeterApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app?.appContainer?.repository?.recoverOrphanedSessions()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
