package com.verisonder.sonderweb.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * The page goes into a shredder: it slides down into a glowing slot and comes out the other side
 * as strips that curl apart and fall away. About a second; [onDone] runs at the end.
 */
@Composable
fun ShredAnimation(page: Bitmap, onDone: () -> Unit) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val image = remember(page) { page.asImageBitmap() }
    val run = remember { Animatable(0f) }
    val slot = remember { Animatable(0f) }
    LaunchedEffect(page) {
        slot.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        run.animateTo(1f, tween(1100, easing = LinearEasing))
        slot.animateTo(0f, tween(160))
        onDone()
    }
    val strips = 16
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = run.value
        // The real page is hidden behind this; its picture is what moves.
        drawRect(p.bg)
        if (t == 0f) drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, IntSize(w.toInt(), h.toInt()))
        drawRect(Color.Black.copy(alpha = 0.35f * slot.value))
        val slotY = h * 0.62f
        val pageH = w * image.height / image.width.coerceAtLeast(1)
        // Starts where it is and slides down into the slot, smoothly speeding up then slowing.
        val ease = t * t * (3 - 2 * t)
        val top = ease * (pageH + h - slotY)
        val src = IntSize(image.width, image.height)
        if (t > 0f) {
            // Above the slot: the page itself, still whole.
            clipRect(0f, 0f, w, slotY) {
                drawImage(image, IntOffset.Zero, src, IntOffset(0, top.toInt()), IntSize(w.toInt(), pageH.toInt()))
            }
            // Below it: the same page in strips that fan out from the slot and fall at different speeds.
            val sw = w / strips
            val fan = (t * 1.6f).coerceAtMost(1f)
            for (i in 0 until strips) {
                val x0 = i * sw
                val centre = x0 + sw / 2
                clipRect(0f, slotY + 3f, w, h) {
                    translate(0f, top * 0.12f * (i % 3)) {
                        rotate((i - strips / 2f + 0.5f) * 3.2f * fan, Offset(centre, slotY)) {
                            drawImage(
                                image,
                                IntOffset(image.width * i / strips, 0),
                                IntSize(image.width / strips, image.height),
                                IntOffset((x0 + 1).toInt(), top.toInt()),
                                IntSize((sw - 2).toInt().coerceAtLeast(1), pageH.toInt()),
                                alpha = (1f - fan * 0.55f).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
        }
        // The shredder's slot: a dark bar with a soft accent glow.
        val bar = 14f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, p.accent.copy(alpha = 0.45f * slot.value), Color.Transparent), slotY - 40f, slotY + 40f),
            Offset(0f, slotY - 40f),
            Size(w, 80f),
        )
        drawRoundRect(
            Color(0xFF0B0B0D).copy(alpha = slot.value),
            Offset(w * 0.04f, slotY - bar / 2),
            Size(w * 0.92f, bar),
            CornerRadius(bar / 2),
        )
    }
}
