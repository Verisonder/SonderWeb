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
import kotlin.math.sin

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
        // Behind it all, the page dims away.
        drawRect(Color.Black.copy(alpha = 0.55f * slot.value))
        val slotY = h * 0.62f
        val t = run.value
        // The page (scaled to the screen) moves down through the slot.
        val pageH = w * image.height / image.width.coerceAtLeast(1)
        val top = slotY - pageH * 0.92f + t * (pageH + h * 0.5f)
        val src = IntSize(image.width, image.height)
        // Above the slot: the page itself, still whole.
        clipRect(0f, 0f, w, slotY) {
            drawImage(image, IntOffset.Zero, src, IntOffset(0, top.toInt()), IntSize(w.toInt(), pageH.toInt()))
        }
        // Below the slot: the same page cut into strips that spread, curl and fall.
        val sw = w / strips
        for (i in 0 until strips) {
            val phase = i * 0.7f
            val x0 = i * sw
            val srcX = (image.width * i / strips)
            val srcW = image.width / strips
            clipRect(0f, slotY, w, h) {
                // How far this strip has come out of the slot.
                val out = (top + pageH - slotY).coerceAtLeast(0f)
                val spread = (out / h).coerceIn(0f, 1f)
                val dx = (i - strips / 2f + 0.5f) * sw * 0.35f * spread
                val sway = sin(out / 90f + phase) * 10f * spread
                translate(dx + sway, out * 0.08f * (1 + (i % 3))) {
                    rotate((i - strips / 2f) * 1.6f * spread, Offset(x0 + sw / 2, slotY)) {
                        drawImage(
                            image,
                            IntOffset(srcX, 0),
                            IntSize(srcW, image.height),
                            IntOffset((x0 + 1).toInt(), top.toInt()),
                            IntSize((sw - 2).toInt().coerceAtLeast(1), pageH.toInt()),
                            alpha = (1f - spread * 0.8f).coerceIn(0f, 1f),
                        )
                    }
                }
            }
        }
        // The shredder's slot: a dark bar with a soft accent glow.
        val bar = 14f
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Transparent, p.accent.copy(alpha = 0.45f * slot.value), Color.Transparent), slotY - 40f, slotY + 40f),
            Offset(-20f, slotY - 40f),
            Size(w + 40f, 80f),
        )
        drawRoundRect(
            Color(0xFF0B0B0D).copy(alpha = slot.value),
            Offset(w * 0.04f, slotY - bar / 2),
            Size(w * 0.92f, bar),
            CornerRadius(bar / 2),
        )
    }
}
