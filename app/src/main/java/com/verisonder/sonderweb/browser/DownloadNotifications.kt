package com.verisonder.sonderweb.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.verisonder.sonderweb.R
import com.verisonder.sonderweb.ui.MainActivity

/**
 * A notification for each download: its progress with a Cancel button while it runs, then
 * "Download complete" (tap to open the file) or "Download failed" (tap for your downloads).
 * Files from private tabs never show their name.
 */
object DownloadNotifications {
    private const val CHANNEL = "downloads"
    const val ACTION_CANCEL = "com.verisonder.sonderweb.action.CANCEL_DOWNLOAD"
    const val EXTRA_KEY = "key"

    /** On the intent that opens the app from a notification: show the Downloads page. */
    const val EXTRA_SHOW_DOWNLOADS = "com.verisonder.sonderweb.extra.SHOW_DOWNLOADS"

    private val app get() = Browser.app
    private var channelMade = false

    private fun id(key: Long) = 4000 + (key % 100_000).toInt()

    private fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!channelMade && Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Progress of your downloads, and when they're done"
            ch.setShowBadge(false)
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            channelMade = true
        }
        return NotificationManagerCompat.from(app).areNotificationsEnabled()
    }

    /** First download: Android 13+ asks once whether SonderWeb may show notifications. */
    fun askOnce() {
        if (Build.VERSION.SDK_INT < 33 || Browser.prefs.askedNotifications) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        Browser.prefs.askedNotifications = true
        Browser.host?.requestAndroidPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { }
    }

    private fun title(name: String, isPrivate: Boolean) = if (isPrivate) "File from a private tab" else name

    private fun showDownloads(): PendingIntent = PendingIntent.getActivity(
        app,
        1,
        Intent(app, MainActivity::class.java)
            .putExtra(EXTRA_SHOW_DOWNLOADS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(key: Long, icon: Int) = NotificationCompat.Builder(app, CHANNEL)
        .setSmallIcon(icon)
        .setSilent(true)
        .setOnlyAlertOnce(true)

    @SuppressLint("MissingPermission")
    private fun post(key: Long, b: NotificationCompat.Builder) {
        runCatching { NotificationManagerCompat.from(app).notify(id(key), b.build()) }
    }

    /** While it runs. [total] -1 = size unknown. */
    fun progress(key: Long, name: String, isPrivate: Boolean, received: Long, total: Long, speed: Long) {
        if (!allowed()) return
        val cancel = PendingIntent.getBroadcast(
            app,
            id(key),
            Intent(app, DownloadActions::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pct = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0
        val b = base(key, R.drawable.ic_notify_download)
            .setContentTitle(title(name, isPrivate))
            .setContentText(Downloads.progressText(app, received, total, speed))
            .setProgress(100, pct, total <= 0)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(showDownloads())
            .addAction(0, "Cancel", cancel)
        post(key, b)
    }

    /** Done: tap to open the file. */
    fun done(key: Long, name: String, isPrivate: Boolean, uri: Uri, mime: String) {
        if (!allowed()) return
        val open = PendingIntent.getActivity(
            app,
            id(key),
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = base(key, R.drawable.ic_notify_done)
            .setContentTitle(title(name, isPrivate))
            .setContentText("Download complete · tap to open")
            .setAutoCancel(true)
            .setContentIntent(open)
        post(key, b)
    }

    fun failed(key: Long, name: String, isPrivate: Boolean) {
        if (!allowed()) return
        val b = base(key, R.drawable.ic_notify_download)
            .setContentTitle(title(name, isPrivate))
            .setContentText("Download failed")
            .setAutoCancel(true)
            .setContentIntent(showDownloads())
        post(key, b)
    }

    fun remove(key: Long) {
        runCatching { NotificationManagerCompat.from(app).cancel(id(key)) }
    }
}

/** The Cancel button on a download's notification. */
class DownloadActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadNotifications.ACTION_CANCEL) return
        val key = intent.getLongExtra(DownloadNotifications.EXTRA_KEY, Long.MIN_VALUE)
        if (key != Long.MIN_VALUE) Downloads.cancel(key)
    }
}
