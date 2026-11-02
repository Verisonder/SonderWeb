package com.verisonder.sonderweb.browser

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Keeps SonderWeb running while files download, so they finish even when you leave the app
 * (Android would otherwise stop it in the background). It runs only while something downloads,
 * with a quiet notification saying so, and stops on its own when the last download ends.
 */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = DownloadNotifications.working(this, running)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(DownloadNotifications.WORKING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(DownloadNotifications.WORKING_ID, n)
            }
        }
        if (running == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        /** Downloads in progress. */
        @Volatile
        private var running = 0

        /** A download started. */
        fun started(context: Context) {
            running++
            val app = context.applicationContext
            runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)) }
        }

        /** A download ended (done, failed or cancelled). */
        fun ended(context: Context) {
            running = (running - 1).coerceAtLeast(0)
            val app = context.applicationContext
            // Through the service itself (it stops itself when nothing is left), never stopped
            // from outside: a very short download could otherwise end before it had even started,
            // which Android treats as an error.
            runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)) }
        }
    }
}
