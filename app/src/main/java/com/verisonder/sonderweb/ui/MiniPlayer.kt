package com.verisonder.sonderweb.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Pip
import com.verisonder.sonderweb.browser.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import kotlin.math.roundToInt

/**
 * The floating video window inside the app. Drag it anywhere. Top left opens the video full
 * size, top right stops it; a tap shows play/pause.
 *
 * It is a second engine view showing that tab's page (drawn as a texture, so it can float, be
 * rounded and be dragged over everything else), with the page's video filling it.
 */
@Composable
fun MiniPlayer(tab: Tab, main: GeckoView, bottomGap: androidx.compose.ui.unit.Dp, onOpen: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomGap),
    ) {
        val d = LocalDensity.current
        val width = 200.dp
        val height = width / tab.videoRatio.coerceIn(0.5f, 2.4f)
        val maxX = with(d) { (maxWidth - width).toPx() }.coerceAtLeast(0f)
        val maxY = with(d) { (maxHeight - height).toPx() }.coerceAtLeast(0f)
        // Starts in the bottom-right corner, above the bar.
        val x = remember { Animatable(maxX) }
        val y = remember { Animatable(maxY) }
        var onRight by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        val decay = rememberSplineBasedDecay<Float>()
        // The space changed (bar, turning the phone): it stays on its side, inside the screen.
        LaunchedEffect(maxX, maxY) {
            if (!x.isRunning) x.snapTo(if (onRight) maxX else 0f)
            if (!y.isRunning) y.snapTo(y.value.coerceIn(0f, maxY))
        }
        // Thrown like the phone's own picture-in-picture: it glides on with the speed of your
        // finger, then settles against the nearest side, at the height it was heading for.
        fun throwTo(vx: Float, vy: Float) {
            val px = decay.calculateTargetValue(x.value, vx)
            val py = decay.calculateTargetValue(y.value, vy)
            onRight = px > maxX / 2
            scope.launch { x.animateTo(if (onRight) maxX else 0f, spring(dampingRatio = 0.78f, stiffness = 320f), vx) }
            scope.launch { y.animateTo(py.coerceIn(0f, maxY), spring(dampingRatio = 0.85f, stiffness = 320f), vy) }
        }
        var controls by remember { mutableStateOf(false) }
        var paused by remember(tab) { mutableStateOf(false) }
        // The buttons fade away again on their own.
        LaunchedEffect(controls, paused) {
            if (controls) {
                delay(3000)
                controls = false
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                .size(width, height)
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .pointerInput(maxX, maxY) {
                    // The window moves with the finger, so its speed is measured from how far it
                    // has been dragged, not from where the finger is on it.
                    val tracker = VelocityTracker()
                    var moved = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            moved = Offset.Zero
                            scope.launch { x.stop() }
                            scope.launch { y.stop() }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            throwTo(v.x, v.y)
                        },
                        onDragCancel = { throwTo(0f, 0f) },
                    ) { change, drag ->
                        change.consume()
                        moved += drag
                        tracker.addPosition(change.uptimeMillis, moved)
                        scope.launch {
                            // A little give past the edges while dragging, like a rubber band.
                            x.snapTo(rubber(x.value + drag.x, maxX))
                            y.snapTo(rubber(y.value + drag.y, maxY))
                        }
                    }
                },
        ) {
            MiniView(tab, main)
            // Taps go to the window (its buttons), never to the page inside it.
            Box(Modifier.fillMaxSize().clickable { controls = !controls })
            // Tap: play/pause in the middle, for a moment.
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                    Box(Modifier.align(Alignment.Center)) {
                        MiniButton(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (paused) "Play" else "Pause", 44) {
                            Pip.togglePlay(tab)
                            paused = !paused
                        }
                    }
                }
            }
            // Always there: open the page (top left) and stop (top right).
            Box(Modifier.align(Alignment.TopStart).padding(4.dp)) {
                MiniButton(Icons.Outlined.OpenInFull, "Open", 28) { onOpen() }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                MiniButton(Icons.Outlined.Close, "Stop video", 28) { Browser.closeMini(tab) }
            }
        }
    }
}

/** Past an edge, the window follows the finger less and less. */
private fun rubber(v: Float, max: Float): Float = when {
    v < 0f -> v * 0.85f
    v > max -> max + (v - max) * 0.85f
    else -> v
}

@Composable
private fun MiniButton(icon: ImageVector, label: String, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size((size * 0.57f).dp))
    }
}

@Composable
private fun MiniView(tab: Tab, main: GeckoView) {
    val ctx = LocalContext.current
    val view = remember(tab) {
        GeckoView(ctx).apply { setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW) }
    }
    var attached by remember(tab) { mutableStateOf(false) }
    LaunchedEffect(tab) {
        // A page can only be shown in one view at a time. A tab you left: the main view lets go
        // of it as it switches. The tab on screen (a panel over it): it is taken from the main view.
        var waited = 0
        while (main.session === tab.session && Browser.current !== tab && waited++ < 60) delay(16)
        if (main.session === tab.session) runCatching { main.releaseSession() }
        if (main.session === tab.session || !tab.session.isOpen) return@LaunchedEffect
        runCatching {
            view.setSession(tab.session)
            tab.session.setActive(true)
            attached = true
        }
        // The page reflows for the small window first, then its video is made to fill it.
        delay(150)
        Pip.fill(tab, true)
    }
    DisposableEffect(tab) {
        onDispose {
            Pip.fill(tab, false)
            if (attached) runCatching { view.releaseSession() }
            // Back on the main view when it is the tab on screen again.
            if (Browser.current === tab && main.session == null && tab.session.isOpen) {
                runCatching { main.setSession(tab.session) }
            }
        }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}
