package com.verisonder.sonderweb.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** One tab. Its fields are Compose state, so the UI follows the page as it loads. */
class Tab(val id: Long, val isPrivate: Boolean) {
    val session: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .useTrackingProtection(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .suspendMediaWhenInactive(false)
            .build(),
    )

    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var secure by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var thumbnail by mutableStateOf<ImageBitmap?>(null)

    /** The address its picture is saved under on the phone (pages like Gmail change address
     *  without reloading, so it's saved again under the new one). */
    var thumbUrl: String? = null

    /** The page's colour along its top and bottom edge (ARGB), to fill the strips by the camera
     *  hole and the gesture bar so the page looks edge to edge without anything hidden. */
    var edgeTop by mutableStateOf<Int?>(null)
    var edgeBottom by mutableStateOf<Int?>(null)

    /** Whether the page looked like one flat colour the last time it was looked at (null: not yet). */
    var lastFlat: Boolean? = null

    /** [lastFlat] when you left the app: whether the page had something on it before. Pictures
     *  taken after coming back can't tell (a broken surface looks like a plain page). */
    var flatWhenLeft: Boolean? = null

    /** True until the page has drawn: meanwhile a cover in the page's colours hides the engine's
     *  empty view, so nothing flashes white. */
    var covered by mutableStateOf(true)

    /** A real page (not the session's empty first page) has started loading since the start page. */
    var startedPage = false

    /** The page is scrolled all the way up (the strip by the camera hole shows only then). */
    var atTop by mutableStateOf(true)

    /** True once the engine session is open and can be shown. */
    var ready by mutableStateOf(false)

    /** uBlock Origin's count of blocked requests on this page (its badge text). */
    var blockedBadge by mutableStateOf("")

    /** Address to load the first time this tab is shown (restored tabs load lazily). */
    var pendingUrl: String? = null

    /** Translation: the page's language, the phone's, and what the page is translated to (null = original). */
    var pageLang: String? = null
    var userLang: String? = null
    var translatedTo by mutableStateOf<String?>(null)
    var translating by mutableStateOf(false)

    /** A video is playing in this tab, and its width / height (for picture-in-picture). */
    var videoPlaying = false

    /** The page's media controls, from the engine (to pause when picture-in-picture closes). */
    var media: org.mozilla.geckoview.MediaSession? = null

    /** The engine says media is playing here (sound or video). */
    var mediaPlaying = false
    var videoRatio = 16f / 9f

    /** The site's address before desktop mode was turned on (to go back to its mobile address). */
    var hostBeforeDesktop: String? = null

    /** The address back would go to (null: back leads to the start page). */
    var backUrl: String? = null

    /** The tab that opened this one; closing returns to it. */
    var openerId: Long? = null

    /** When this tab was last the current one. Tabs left alone long enough go under "inactive". */
    var lastUsed: Long = System.currentTimeMillis()

    /** True while nothing has been loaded yet: the start page is shown instead of the engine. */
    val isBlank: Boolean get() = url.isEmpty() && pendingUrl == null && !loading

    val displayTitle: String get() = title.ifBlank { url.ifBlank { "New tab" } }
}
