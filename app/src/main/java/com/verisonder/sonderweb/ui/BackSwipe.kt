package com.verisonder.sonderweb.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.verisonder.sonderweb.browser.BackPeek
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Tab
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android's predictive back, for pages: while you swipe back the page shrinks and follows your
 * finger, and the page you'd go back to (or the start page) shows behind it. Let go to go back,
 * swipe back the other way to stay.
 */
class BackSwipe {
    /** A picture of the page as it is now; the moving card. */
    var front by mutableStateOf<ImageBitmap?>(null)

    /** A picture of the page back goes to (null: its plain colour). */
    var behind by mutableStateOf<ImageBitmap?>(null)

    /** Back goes to the start page. */
    var home by mutableStateOf(false)

    /** The layer is on screen. */
    var shown by mutableStateOf(false)
    var fromLeft = true
    val progress = Animatable(0f)
    val leave = Animatable(0f)
    val fade = Animatable(1f)
}

@Composable
fun PageBackGesture(swipe: BackSwipe, tab: Tab?, enabled: Boolean) {
    PredictiveBackHandler(enabled = enabled && tab != null) { events ->
        val t = tab ?: return@PredictiveBackHandler
        begin(swipe, t)
        try {
            events.collect { e ->
                swipe.fromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                swipe.progress.snapTo(e.progress)
            }
        } catch (e: CancellationException) {
            // Swiped back the other way: the page settles back into place.
            withContext(NonCancellable) {
                swipe.progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 600f))
                end(swipe)
            }
            throw e
        }
        land(swipe, t)
    }
}

private suspend fun begin(swipe: BackSwipe, t: Tab) {
    swipe.home = !t.canGoBack
    swipe.behind = if (swipe.home) null else BackPeek.behind(t)
    swipe.front = null
    swipe.progress.snapTo(0f)
    swipe.leave.snapTo(0f)
    swipe.fade.snapTo(1f)
    // The engine draws on its own surface, which can't be moved around: a picture of it can.
    runCatching {
        Browser.host?.geckoViewFor(t)?.capturePixels()?.accept(
            { bmp ->
                if (bmp != null) {
                    swipe.front = bmp.asImageBitmap()
                    swipe.shown = true
                }
            },
            { },
        )
    }
}

/** Let go: the card slides away, back happens, and the picture behind lifts once the page is there. */
private suspend fun land(swipe: BackSwipe, t: Tab) {
    if (swipe.shown) swipe.leave.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
    if (swipe.home) Browser.home(t) else t.session.goBack()
    swipe.front = null
    if (swipe.shown && !swipe.home) {
        // Wait for the page to come back (most come back at once), then uncover it.
        withTimeoutOrNull(250) { snapshotFlow { t.loading }.first { it } }
        withTimeoutOrNull(900) { snapshotFlow { t.loading }.first { !it } }
        delay(60)
    }
    if (swipe.shown) swipe.fade.animateTo(0f, tween(160))
    end(swipe)
}

private fun end(swipe: BackSwipe) {
    swipe.shown = false
    swipe.front = null
    swipe.behind = null
}

/** Drawn over the page while the gesture runs. [pageY] is where the engine view sits. */
@Composable
fun BackSwipeLayer(swipe: BackSwipe, ui: UiState, pageY: () -> Int) {
    if (!swipe.shown) return
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = swipe.fade.value }) {
        val pic = swipe.behind
        when {
            swipe.home -> Box(Modifier.fillMaxSize().background(p.bg)) { StartPage(ui) }
            pic != null -> Box(Modifier.fillMaxSize().background(p.bg)) {
                Image(
                    pic,
                    null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().offset { IntOffset(0, pageY()) },
                )
            }
            else -> Box(Modifier.fillMaxSize().background(p.bg))
        }
        // A light shade on what's behind that lifts as you swipe.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.3f * (1f - swipe.progress.value) * (1f - swipe.leave.value) }
                .background(Color.Black),
        )
        swipe.front?.let { shot ->
            Image(
                shot,
                null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, pageY()) }
                    .graphicsLayer {
                        val g = swipe.progress.value
                        val l = swipe.leave.value
                        val dir = if (swipe.fromLeft) 1f else -1f
                        val s = 1f - 0.1f * g
                        scaleX = s
                        scaleY = s
                        translationX = dir * (size.width * 0.05f * g + size.width * 0.6f * l)
                        alpha = 1f - l
                        shape = RoundedCornerShape(32.dp.toPx() * g)
                        clip = true
                        shadowElevation = 24.dp.toPx() * g
                    },
            )
        }
    }
}
