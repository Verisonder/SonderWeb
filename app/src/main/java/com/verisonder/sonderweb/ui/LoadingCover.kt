package com.verisonder.sonderweb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verisonder.sonderweb.browser.Tab
import com.verisonder.sonderweb.core.UrlText
import com.verisonder.sonderweb.data.SiteIcons
import kotlinx.coroutines.delay

/**
 * Hides the engine view until the page has drawn, in the page's own colours (remembered from
 * last time), so opening the app or a site never flashes white or grey. If the page takes a
 * moment, a small mark fades in: the site's icon, breathing, inside a ring that follows the
 * loading progress. When the page is ready the mark grows a little as everything fades away.
 */
@Composable
fun LoadingCover(tab: Tab?, visible: Boolean, safeTop: Int) {
    if (tab != null && visible) {
        // Never hides a page for good, even if it never reports drawing.
        LaunchedEffect(tab) {
            delay(8000)
            tab.covered = false
        }
    }
    AnimatedVisibility(
        visible = visible && tab != null,
        enter = EnterTransition.None,
        exit = fadeOut(tween(240)),
    ) {
        val t = tab ?: return@AnimatedVisibility
        val p = LocalPalette.current
        val bottom = t.edgeBottom?.let { Color(it) } ?: p.bg
        val top = t.edgeTop?.let { Color(it) } ?: bottom
        val ink = if (bottom.luminance() > 0.5f) Color.Black else Color.White
        Box(Modifier.fillMaxSize().background(bottom)) {
            if (safeTop > 0) {
                Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { safeTop.toDp() }).background(top))
            }
            LoadingMark(
                tab = t,
                ink = ink,
                accent = if (t.isPrivate) p.privateAccent else p.accent,
                leaving = !visible,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun LoadingMark(tab: Tab, ink: Color, accent: Color, leaving: Boolean, modifier: Modifier) {
    // Fast pages are ready before it would show: it only fades in if loading takes a moment.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(280)
        appear.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
    }
    val leave = animateFloatAsState(if (leaving) 1f else 0f, tween(240), label = "leave")
    val motion = rememberInfiniteTransition(label = "loading")
    val angle = motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "angle",
    )
    val breathe = motion.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val progress = animateFloatAsState((tab.progress / 100f).coerceIn(0.08f, 1f), tween(320), label = "progress")

    val host = UrlText.host(tab.url)
    val ctx = LocalContext.current
    // Private tabs: no icon fetched or saved for their sites.
    if (!tab.isPrivate) LaunchedEffect(host) { SiteIcons.load(ctx, host) }
    val bmp = if (tab.isPrivate) null else SiteIcons.icons[host]

    // Animated values are read only in graphicsLayer / drawBehind: each frame just redraws.
    Box(
        modifier
            .size(76.dp)
            .graphicsLayer {
                val a = appear.value
                val l = leave.value
                alpha = a * (1f - l)
                val s = (0.85f + 0.15f * a) * (1f + 0.3f * l)
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2f
                drawCircle(ink.copy(alpha = 0.12f), radius = size.minDimension / 2f - inset, style = Stroke(stroke))
                drawArc(
                    color = accent,
                    startAngle = angle.value - 90f,
                    sweepAngle = 40f + 280f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .graphicsLayer {
                    val b = breathe.value
                    scaleX = b
                    scaleY = b
                }
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            // A logo (Gmail's M, YouTube's play button: wider than tall) fits whole inside the
            // circle; a square app-style icon fills it.
            val logo = bmp != null && bmp.width >= 64 &&
                kotlin.math.abs(bmp.width - bmp.height) > minOf(bmp.width, bmp.height) / 8
            when {
                logo -> Image(
                    remember(bmp) { bmp!!.asImageBitmap() },
                    null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp),
                )
                bmp != null && bmp.width >= 96 -> Image(
                    remember(bmp) { bmp.asImageBitmap() },
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                bmp != null -> Image(
                    remember(bmp) { bmp.asImageBitmap() },
                    null,
                    modifier = Modifier.size(26.dp),
                )
                else -> Text(
                    host.firstOrNull()?.uppercase() ?: "•",
                    color = ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
