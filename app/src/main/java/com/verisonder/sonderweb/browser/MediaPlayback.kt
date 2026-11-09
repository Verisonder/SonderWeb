package com.verisonder.sonderweb.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.verisonder.sonderweb.R
import com.verisonder.sonderweb.core.UrlText
import org.mozilla.geckoview.MediaSession

/**
 * Sound and video that keep playing when you leave the app or turn the screen off, with the
 * phone's media controls (lock screen, notification shade): title, picture, play/pause, next,
 * previous and a seek bar, like a music app. Private tabs show "Private tab", never the title.
 */
object MediaPlayback {
    private const val CHANNEL = "media"
    const val NOTIFICATION_ID = 4200
    const val ACTION = "com.verisonder.sonderweb.MEDIA"
    const val EXTRA_CMD = "cmd"

    /** The tab whose media the controls show, and the engine's handle on it. */
    var tab: Tab? = null
        private set
    private var media: MediaSession? = null

    var playing = false
        private set
    private var title = ""
    private var artist = ""
    private var art: Bitmap? = null
    private var features = 0L
    private var duration = 0.0
    private var position = 0.0
    private var rate = 1.0
    private var positionAt = 0L

    private var session: android.media.session.MediaSession? = null
    private var channelMade = false

    /** A tab's media is playing in the tab on screen and should keep going when you leave. */
    /**
     * The video in the small window (or playing with the app in the background) is being kept
     * going, even when the page says it paused. The service keeps running meanwhile: without it
     * the phone treats SonderWeb as idle in the background and stops it.
     */
    var held = false
        set(v) {
            if (field == v) return
            field = v
            if (tab != null) sync()
        }

    val keepAwake: Boolean
        get() = playing && tab != null && tab === Browser.current

    // ---- From the engine (the media session delegate in Pip.kt) ----

    fun activated(t: Tab, m: MediaSession) {
        if (tab !== t) {
            tab = t
            title = ""
            artist = ""
            art = null
            duration = 0.0
            position = 0.0
        }
        media = m
    }

    fun metadata(t: Tab, m: MediaSession.Metadata) {
        if (tab !== t) return
        title = m.title.orEmpty()
        artist = m.artist.orEmpty()
        art = null
        // Its picture, if it has one (never for private tabs).
        if (!t.isPrivate) {
            runCatching {
                m.artwork?.getBitmap(320)?.accept({ b ->
                    Browser.main.post {
                        if (tab === t && b != null) {
                            art = b
                            sync()
                        }
                    }
                }, { _ -> })
            }
        }
        sync()
    }

    fun features(t: Tab, f: Long) {
        if (tab !== t) return
        features = f
        sync()
    }

    fun position(t: Tab, p: MediaSession.PositionState) {
        if (tab !== t) return
        duration = p.duration
        position = p.position
        rate = p.playbackRate
        positionAt = SystemClock.elapsedRealtime()
        sync()
    }

    fun played(t: Tab, m: MediaSession) {
        activated(t, m)
        playing = true
        positionAt = SystemClock.elapsedRealtime()
        sync()
    }

    fun paused(t: Tab) {
        if (tab !== t) return
        // Where it stopped, so the seek bar stays put.
        position += (SystemClock.elapsedRealtime() - positionAt) / 1000.0 * rate * (if (playing) 1 else 0)
        positionAt = SystemClock.elapsedRealtime()
        playing = false
        sync()
    }

    /** The media ended, the page went away or the tab closed: the controls go too. */
    fun ended(t: Tab) {
        if (tab !== t) return
        tab = null
        media = null
        playing = false
        sync()
    }

    // ---- From the phone's controls ----

    fun command(cmd: String) {
        val m = media ?: return
        val pausing = cmd == "pause" || (cmd == "toggle" && playing)
        if (cmd == "play" || cmd == "pause" || cmd == "toggle") tab?.let { Pip.userPaused(it, pausing) }
        when (cmd) {
            "play" -> m.play()
            "pause" -> m.pause()
            "toggle" -> if (playing) m.pause() else m.play()
            "next" -> m.nextTrack()
            "previous" -> m.previousTrack()
        }
    }

    private val callback = object : android.media.session.MediaSession.Callback() {
        override fun onPlay() = command("play")
        override fun onPause() = command("pause")
        override fun onStop() = command("pause")
        override fun onSkipToNext() = command("next")
        override fun onSkipToPrevious() = command("previous")
        override fun onSeekTo(pos: Long) {
            media?.seekTo(pos / 1000.0, false)
            position = pos / 1000.0
            positionAt = SystemClock.elapsedRealtime()
            sync()
        }
    }

    // ---- To the phone ----

    /** Brings the phone's controls and the notification up to date. */
    private fun sync() {
        val app = Browser.app
        val t = tab
        if (t == null) {
            session?.isActive = false
            session?.release()
            session = null
            MediaService.instance?.refresh()
                ?: app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            return
        }
        val s = session ?: android.media.session.MediaSession(app, "SonderWeb").also {
            it.setCallback(callback, Browser.main)
            session = it
        }
        val name = shownTitle(t)
        val meta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, name)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, shownArtist(t))
        if (duration > 0 && duration.isFinite()) meta.putLong(MediaMetadata.METADATA_KEY_DURATION, (duration * 1000).toLong())
        art?.let { meta.putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
        s.setMetadata(meta.build())
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        if ((features and MediaSession.Feature.NEXT_TRACK) != 0L) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if ((features and MediaSession.Feature.PREVIOUS_TRACK) != 0L) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (duration > 0 && duration.isFinite()) actions = actions or PlaybackState.ACTION_SEEK_TO
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    (position * 1000).toLong().coerceAtLeast(0),
                    if (playing) rate.toFloat() else 0f,
                    positionAt,
                )
                .build(),
        )
        s.isActive = true
        val running = MediaService.instance
        if (running != null) {
            running.refresh()
        } else if (playing || held) {
            runCatching { ContextCompat.startForegroundService(app, Intent(app, MediaService::class.java)) }
        } else {
            app.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(app))
        }
    }

    private fun shownTitle(t: Tab) = when {
        t.isPrivate -> "Private tab"
        title.isNotBlank() -> title
        else -> t.title.ifBlank { UrlText.host(t.url) }
    }

    private fun shownArtist(t: Tab) = when {
        t.isPrivate -> "SonderWeb"
        artist.isNotBlank() -> artist
        else -> UrlText.host(t.url)
    }

    private fun channel(ctx: Context) {
        if (channelMade) return
        val ch = NotificationChannel(CHANNEL, "Playing media", NotificationManager.IMPORTANCE_LOW)
        ch.description = "Controls for sound and video playing in SonderWeb"
        ch.setShowBadge(false)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        channelMade = true
    }

    private fun button(ctx: Context, cmd: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        code,
        Intent(ctx, MediaActions::class.java).setAction(ACTION).putExtra(EXTRA_CMD, cmd),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notification(ctx: Context): Notification {
        channel(ctx)
        val t = tab
        val open = PendingIntent.getActivity(
            ctx,
            7,
            Intent(ctx, com.verisonder.sonderweb.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_play)
            .setContentTitle(t?.let { shownTitle(it) } ?: "SonderWeb")
            .setContentText(t?.let { shownArtist(it) } ?: "")
            .setContentIntent(open)
            .setShowWhen(false)
            .setOngoing(playing)
            .setVisibility(if (t?.isPrivate == true) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
        art?.let { b.setLargeIcon(it) }
        fun icon(id: Int) = android.graphics.drawable.Icon.createWithResource(ctx, id)
        var count = 0
        if ((features and MediaSession.Feature.PREVIOUS_TRACK) != 0L) {
            b.addAction(Notification.Action.Builder(icon(android.R.drawable.ic_media_previous), "Previous", button(ctx, "previous", 1)).build())
            count++
        }
        b.addAction(
            Notification.Action.Builder(
                icon(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                if (playing) "Pause" else "Play",
                button(ctx, "toggle", 2),
            ).build(),
        )
        count++
        if ((features and MediaSession.Feature.NEXT_TRACK) != 0L) {
            b.addAction(Notification.Action.Builder(icon(android.R.drawable.ic_media_next), "Next", button(ctx, "next", 3)).build())
            count++
        }
        // All of them (at most three) show in the small version too.
        val compact = IntArray(count) { it }
        val style = Notification.MediaStyle().setShowActionsInCompactView(*compact)
        session?.let { style.setMediaSession(it.sessionToken) }
        b.setStyle(style)
        return b.build()
    }
}

/** The buttons on the media notification (older Android; newer ones use the media session). */
class MediaActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra(MediaPlayback.EXTRA_CMD) ?: return
        Browser.main.post { MediaPlayback.command(cmd) }
    }
}

/**
 * Keeps SonderWeb running while something plays and you're elsewhere or the screen is off,
 * with the media notification. Paused: the notification stays (swipe it away). Ended: it goes.
 */
class MediaService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started as a foreground service: it has to show itself straight away.
        val n = MediaPlayback.notification(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(MediaPlayback.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(MediaPlayback.NOTIFICATION_ID, n)
            }
        }
        refresh()
        return START_NOT_STICKY
    }

    fun refresh() {
        val nm = getSystemService(NotificationManager::class.java)
        when {
            MediaPlayback.tab == null -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                nm.cancel(MediaPlayback.NOTIFICATION_ID)
                stopSelf()
            }
            MediaPlayback.playing || MediaPlayback.held -> runCatching {
                val n = MediaPlayback.notification(this)
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(MediaPlayback.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(MediaPlayback.NOTIFICATION_ID, n)
                }
            }
            else -> {
                // Paused: no longer kept running, but the controls stay until swiped away.
                stopForeground(STOP_FOREGROUND_DETACH)
                nm.notify(MediaPlayback.NOTIFICATION_ID, MediaPlayback.notification(this))
                stopSelf()
            }
        }
    }

    /** Swiped out of recent apps: the playing stops with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        MediaPlayback.command("pause")
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(MediaPlayback.NOTIFICATION_ID)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        var instance: MediaService? = null
            private set
    }
}
