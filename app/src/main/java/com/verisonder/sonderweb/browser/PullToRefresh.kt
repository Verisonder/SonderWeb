package com.verisonder.sonderweb.browser

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController

/** Pull down from the top of a page to reload it. */
object PullToRefresh {
    /** How far the indicator has come down, in pixels (already damped). */
    var pull by mutableFloatStateOf(0f)

    /** Released past the line: the page is reloading. */
    var refreshing by mutableStateOf(false)

    /** Pull distance (px) that reloads; set by the screen from its density. */
    var threshold = 160f
}

/**
 * The engine view, watching touches: when a page is already at its top and doesn't use the
 * gesture itself, dragging down pulls the reload indicator instead.
 */
class PullGeckoView(context: Context) : GeckoView(context) {
    private var startY = 0f
    private var atTop = false
    private var pulling = false

    // Sign-in fields: the phone's password manager is asked to show its suggestions.
    override fun setSession(session: GeckoSession) {
        super.setSession(session)
        val own = session.autofillDelegate
        if (own != null && own !is LoginFill) session.autofillDelegate = LoginFill(this, own)
    }

    override fun releaseSession(): GeckoSession? {
        // Hand back the engine view's own hookup so it can unhook it from this session.
        session?.let { s -> (s.autofillDelegate as? LoginFill)?.let { s.autofillDelegate = it.inner } }
        return super.releaseSession()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                atTop = false
                pulling = false
                // Ask the engine whether the page is at its top and left the touch to us.
                onTouchEventForDetailResult(event).accept({ d ->
                    if (d != null) {
                        atTop = d.handledResult() != PanZoomController.INPUT_RESULT_HANDLED_CONTENT &&
                            (d.scrollableDirections() and PanZoomController.SCROLLABLE_FLAG_TOP) == 0
                    }
                }, { _ -> })
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    release(cancel = true)
                } else if (atTop && !PullToRefresh.refreshing) {
                    val dy = event.y - startY
                    if (dy > 0 || pulling) {
                        pulling = true
                        // Resistance: the indicator moves at about half the finger's speed, slowing further on.
                        val damped = (dy.coerceAtLeast(0f) * 0.5f).let { it / (1f + it / (PullToRefresh.threshold * 2.5f)) }
                        PullToRefresh.pull = damped
                    }
                }
            }
            MotionEvent.ACTION_UP -> release(cancel = false)
            MotionEvent.ACTION_CANCEL -> release(cancel = true)
        }
        return super.onTouchEvent(event)
    }

    private fun release(cancel: Boolean) {
        if (pulling && !cancel && PullToRefresh.pull >= PullToRefresh.threshold) {
            PullToRefresh.refreshing = true
            Browser.current?.session?.reload()
        }
        pulling = false
        atTop = false
        PullToRefresh.pull = 0f
    }
}
