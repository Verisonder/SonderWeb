package com.verisonder.sonderweb.ui

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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.verisonder.sonderweb.browser.Browser
import com.verisonder.sonderweb.browser.Pip
import com.verisonder.sonderweb.browser.Tab
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView
import kotlin.math.roundToInt

/**
 * The floating video window inside the app: a tab you left while its video played keeps playing
 * here. Drag it anywhere; tap it to go back to that tab; × stops the video.
 *
 * It is a second engine view showing that tab's page (drawn as a texture, so it can float, be
 * rounded and be dragged over the main page), with the page's video filling it.
 */
@Composable
fun MiniPlayer(main: GeckoView, bottomGap: androidx.compose.ui.unit.Dp) {
    val tab = Browser.mini ?: return
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
        var x by remember { mutableFloatStateOf(maxX) }
        var y by remember { mutableFloatStateOf(maxY) }
        Box(
            Modifier
                .offset { IntOffset(x.coerceIn(0f, maxX).roundToInt(), y.coerceIn(0f, maxY).roundToInt()) }
                .size(width, height)
                .shadow(12.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        x = (x + drag.x).coerceIn(0f, maxX)
                        y = (y + drag.y).coerceIn(0f, maxY)
                    }
                },
        ) {
            MiniView(tab, main)
            // Taps go to the window, not to the page inside it.
            Box(Modifier.fillMaxSize().clickable { Browser.select(tab) })
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { Browser.closeMini(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Stop video", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
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
        // The main view lets go of this tab's page when it switches to the new tab; a page can
        // only be shown in one view at a time.
        var waited = 0
        while (main.session === tab.session && waited++ < 60) delay(16)
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
        }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}
