package com.verisonder.sonderweb.browser

import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Picture-in-picture: leave the app while a video plays and it keeps playing in a small window.
 * A small built-in extension tells us when a video plays (and its shape), and fills the small
 * window with that video while it's open.
 */
object Pip {
    const val ID = "pip@sonderweb"
    private const val LOCATION = "resource://android/assets/extensions/pip/"
    private const val APP = "pip"
    private var extension: WebExtension? = null

    /** The video's page in each tab (one port per frame that played a video). */
    private val ports = HashMap<Tab, WebExtension.Port>()

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept({ ext ->
            Browser.main.post {
                extension = ext ?: return@post
                runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                Browser.tabs.forEach { attach(it) }
            }
        }, { _ -> })
    }

    fun attach(tab: Tab) {
        // Second way to know a video plays: the engine's own media session (works without the
        // extension, for videos with sound). Fullscreen videos also tell their shape here.
        tab.session.setMediaSessionDelegate(
            object : org.mozilla.geckoview.MediaSession.Delegate {
                override fun onActivated(session: org.mozilla.geckoview.GeckoSession, media: org.mozilla.geckoview.MediaSession) {
                    tab.media = media
                    MediaPlayback.activated(tab, media)
                }

                override fun onMetadata(
                    session: org.mozilla.geckoview.GeckoSession,
                    media: org.mozilla.geckoview.MediaSession,
                    meta: org.mozilla.geckoview.MediaSession.Metadata,
                ) {
                    MediaPlayback.metadata(tab, meta)
                }

                override fun onFeatures(session: org.mozilla.geckoview.GeckoSession, media: org.mozilla.geckoview.MediaSession, features: Long) {
                    MediaPlayback.features(tab, features)
                }

                override fun onPositionState(
                    session: org.mozilla.geckoview.GeckoSession,
                    media: org.mozilla.geckoview.MediaSession,
                    state: org.mozilla.geckoview.MediaSession.PositionState,
                ) {
                    MediaPlayback.position(tab, state)
                }

                override fun onPlay(session: org.mozilla.geckoview.GeckoSession, media: org.mozilla.geckoview.MediaSession) {
                    tab.media = media
                    tab.mediaPlaying = true
                    MediaPlayback.played(tab, media)
                    changed()
                }

                override fun onPause(session: org.mozilla.geckoview.GeckoSession, media: org.mozilla.geckoview.MediaSession) {
                    stopping(tab)
                    tab.mediaPlaying = false
                    MediaPlayback.paused(tab)
                    changed()
                }

                override fun onStop(session: org.mozilla.geckoview.GeckoSession, media: org.mozilla.geckoview.MediaSession) {
                    stopping(tab)
                    tab.mediaPlaying = false
                    MediaPlayback.paused(tab)
                    changed()
                }

                override fun onDeactivated(session: org.mozilla.geckoview.GeckoSession, media: org.mozilla.geckoview.MediaSession) {
                    stopping(tab)
                    tab.mediaPlaying = false
                    MediaPlayback.ended(tab)
                    changed()
                }

                override fun onFullscreen(
                    session: org.mozilla.geckoview.GeckoSession,
                    media: org.mozilla.geckoview.MediaSession,
                    enabled: Boolean,
                    meta: org.mozilla.geckoview.MediaSession.ElementMetadata?,
                ) {
                    if (enabled && meta != null && meta.width > 0 && meta.height > 0) {
                        tab.videoRatio = meta.width.toFloat() / meta.height
                        changed()
                    }
                }
            },
        )
        val ext = extension ?: return
        tab.session.webExtensionController.setMessageDelegate(
            ext,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    port.setDelegate(
                        object : WebExtension.PortDelegate {
                            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                val m = message as? JSONObject ?: return
                                val playing = m.optBoolean("playing")
                                // The frame that's playing is the one to talk to; others only when they're the last one.
                                if (playing || ports[tab] === port) {
                                    ports[tab] = port
                                    if (!playing) stopping(tab)
                                    tab.videoPlaying = playing
                                    val w = m.optInt("w", 16).coerceAtLeast(1)
                                    val h = m.optInt("h", 9).coerceAtLeast(1)
                                    tab.videoRatio = w.toFloat() / h
                                    changed()
                                }
                            }

                            override fun onDisconnect(port: WebExtension.Port) {
                                if (ports[tab] === port) {
                                    ports.remove(tab)
                                    stopping(tab)
                                    tab.videoPlaying = false
                                    changed()
                                }
                            }
                        },
                    )
                }
            },
            APP,
        )
    }

    fun forget(tab: Tab) {
        if (kept === tab) {
            kept = null
            MediaPlayback.held = false
        }
        ports.remove(tab)
        stoppedAt.remove(tab)
        MediaPlayback.ended(tab)
    }

    /** A video is playing in the tab on screen (or just was), and picture-in-picture is on. */
    val ready: Boolean
        get() = Browser.prefs.pip && Browser.current.let { it != null && playingNow(it) }

    /**
     * YouTube pauses its own video the moment you swipe home, often just before the phone tells
     * us you're leaving. So a video that stopped a moment ago still counts as playing: the small
     * window opens, and the helper undoes that pause.
     */
    private const val GRACE_MS = 1500L
    private val stoppedAt = HashMap<Tab, Long>()

    fun playingNow(tab: Tab): Boolean =
        tab.playing || (stoppedAt[tab]?.let { android.os.SystemClock.elapsedRealtime() - it < GRACE_MS } ?: false)

    /** Called just before a tab's playing flags go off. */
    private fun stopping(tab: Tab) {
        if (!tab.playing) return
        stoppedAt[tab] = android.os.SystemClock.elapsedRealtime()
        // When the moment has passed, the small window stops opening by itself.
        Browser.main.postDelayed({ changed() }, GRACE_MS + 50)
    }

    private fun changed() {
        Browser.host?.updatePip()
    }

    /** The small window opened or closed: the video fills it, or goes back into its page. */
    fun shown(on: Boolean) {
        val port = Browser.current?.let { ports[it] } ?: return
        runCatching { port.postMessage(JSONObject().put("pip", on)) }
    }

    /** The floating window in the app: the tab's video fills it, or goes back into its page. */
    fun fill(tab: Tab, on: Boolean) {
        val port = ports[tab] ?: return
        runCatching { port.postMessage(JSONObject().put("pip", on)) }
    }

    /** The app went to the background with the video playing (or came back): the page may not pause it meanwhile. */
    fun hold(tab: Tab, on: Boolean) {
        keepAlive(tab, on)
        val port = ports[tab] ?: return
        runCatching { port.postMessage(JSONObject().put("hold", on)) }
    }

    /**
     * Out of the app (small window, background), the phone may freeze or slow down the engine's
     * page process after a short while: the video stopped after about 30 seconds in the small
     * window, while the floating window inside the app kept going. While a video is held its page
     * process is marked important and the media service keeps running.
     */
    private var kept: Tab? = null

    private fun keepAlive(tab: Tab, on: Boolean) {
        if (on) {
            if (kept !== tab) kept?.let { release(it) }
            kept = tab
            runCatching { tab.session.setPriorityHint(org.mozilla.geckoview.GeckoSession.PRIORITY_HIGH) }
            MediaPlayback.held = true
        } else if (kept === tab) {
            release(tab)
            kept = null
        }
    }

    private fun release(tab: Tab) {
        runCatching { tab.session.setPriorityHint(org.mozilla.geckoview.GeckoSession.PRIORITY_DEFAULT) }
        MediaPlayback.held = false
    }

    /** You paused or played from the phone's media controls: a held video respects that. */
    fun userPaused(tab: Tab, paused: Boolean) {
        // Paused by you: the service may stop like for any paused video (it comes back if you play).
        if (kept === tab) MediaPlayback.held = !paused
        val port = ports[tab] ?: return
        runCatching { port.postMessage(JSONObject().put("userPaused", paused)) }
    }

    /** Stop the video in a tab that isn't the one on screen. */
    fun pauseTab(tab: Tab) {
        keepAlive(tab, false)
        runCatching { tab.media?.pause() }
        runCatching { ports[tab]?.postMessage(JSONObject().put("pause", true)) }
    }

    /** The small window was closed: the video stops. */
    fun pause() {
        val tab = Browser.current ?: return
        keepAlive(tab, false)
        runCatching { tab.media?.pause() }
        runCatching { ports[tab]?.postMessage(JSONObject().put("pause", true)) }
    }

    /** Play/pause for a tab's video, from the floating window. */
    fun togglePlay(tab: Tab) {
        val port = ports[tab] ?: return
        runCatching { port.postMessage(JSONObject().put("toggle", true)) }
    }

    /** The play/pause button in the small window. */
    fun togglePlay() {
        val tab = Browser.current ?: return
        val port = ports[tab] ?: return
        runCatching { port.postMessage(JSONObject().put("toggle", true)) }
    }
}
