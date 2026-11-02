package com.verisonder.sonderweb.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

/** How long the whole thing takes. */
private const val SHRED_MS = 1400f

/**
 * The page gets clawed, like Brave's shred icon: three slashes rip across it one after another,
 * then it falls apart along them, the pieces tumbling away. [onDone] runs at the end.
 */
@Composable
fun ShredAnimation(page: Bitmap, onDone: () -> Unit) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val image = remember(page) { page.asImageBitmap() }
    val run = remember { Animatable(0f) }
    LaunchedEffect(page) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        run.animateTo(1f, tween(SHRED_MS.toInt(), easing = LinearEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ms = run.value * SHRED_MS
        fun clamp(v: Float) = v.coerceIn(0f, 1f)
        // Sizes below are for a 412 dp wide screen, scaled to this one.
        val u = w / 412f

        // Three claw lines, from top right to bottom left: (x at the top, x at the bottom).
        val lines = listOf(0.65f to -0.35f, 0.90f to -0.10f, 1.15f to 0.15f).map { (a, b) -> (w * a) to (w * b) }
        val y0 = -40f * u
        val y1 = h + 40f * u
        fun xAt(line: Pair<Float, Float>, y: Float) = line.first + (line.second - line.first) * (y - y0) / (y1 - y0)

        drawRect(p.bg)
        // The pieces between the lines, falling apart.
        val edges = listOf(-4000f to -4000f) + lines + listOf(6000f to 6000f)
        val fall = clamp((ms - 380f) / 900f)
        val f = fall * fall
        for (k in 0 until 4) {
            val a = edges[k]
            val b = edges[k + 1]
            val dir = k - 1.5f
            val piece = Path().apply {
                moveTo(maxOf(-50f, xAt(a, y0)), y0)
                lineTo(minOf(w + 50f, xAt(b, y0)), y0)
                lineTo(minOf(w + 50f, xAt(b, y1)), y1)
                lineTo(maxOf(-50f, xAt(a, y1)), y1)
                close()
            }
            translate(dir * 60f * u * f, f * h * (0.9f + 0.15f * k)) {
                rotate(dir * 9f * f, Offset(w / 2, h / 2)) {
                    clipPath(piece) {
                        drawImage(
                            image,
                            IntOffset.Zero,
                            IntSize(image.width, image.height),
                            IntOffset.Zero,
                            IntSize(w.toInt(), h.toInt()),
                            alpha = 1f - clamp((fall - 0.6f) / 0.4f),
                        )
                    }
                }
            }
        }
        // The claw marks: tapered gashes ripping in one after another, gone once it falls apart.
        val claw = ms / 330f
        val fade = 1f - clamp((fall - 0.2f) / 0.4f)
        lines.forEachIndexed { i, line ->
            val pr = clamp(claw * 1.6f - i * 0.3f)
            if (pr <= 0f || fade <= 0f) return@forEachIndexed
            val sx = line.first
            val ex = line.first + (line.second - line.first) * pr
            val ey = y0 + (y1 - y0) * pr
            val nx = y1 - y0
            val ny = -(line.second - line.first)
            val len = hypot(nx, ny)
            val width = 11f * u
            val mx = (sx + ex) / 2
            val my = (y0 + ey) / 2
            val gash = Path().apply {
                moveTo(sx, y0)
                quadraticTo(mx + nx / len * width, my + ny / len * width, ex, ey)
                quadraticTo(mx - nx / len * width, my - ny / len * width, sx, y0)
                close()
            }
            drawPath(gash, Color(0xFF141416).copy(alpha = fade))
        }
    }
}
