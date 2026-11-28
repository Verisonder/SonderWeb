package com.verisonder.sondercircle

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Opened by the app icon and the Quick Settings tile. Shows nothing: waits for the panel or the
 * sidebar you tapped to get out of the way, takes the screenshot, and opens the circle on it.
 */
class CaptureActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        val service = CircleAccessibility.instance
        if (service == null) {
            // Not set up yet, or switched on but not running (Android stops it after a crash
            // until it's switched off and on again): the settings page says which.
            startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_NEEDS_SCREENSHOT, true))
            finish()
            return
        }
        // Long enough for the Quick Settings panel (or a sidebar) to finish closing.
        val settle = if (intent?.getBooleanExtra(EXTRA_FROM_TILE, false) == true) TILE_DELAY else ICON_DELAY
        val delay = Handoff.delay(this)
        if (delay > 0) {
            // A delay of your own: this screen goes away at once (so the phone can be used
            // meanwhile) and the screenshot is taken later.
            service.captureLater(settle + delay * 100L)
            done()
            return
        }
        main.postDelayed({
            service.capture { bmp ->
                if (bmp == null) {
                    Toast.makeText(this, "Couldn't take a screenshot. Try again.", Toast.LENGTH_SHORT).show()
                    done()
                    return@capture
                }
                // Saved off the main thread, then SonderWeb opens the circle on it.
                Thread {
                    val open = runCatching { Handoff.prepare(this, bmp) }.getOrNull()
                    main.post {
                        if (open == null) {
                            Toast.makeText(this, "Install SonderWeb first: SonderCircle opens in it", Toast.LENGTH_LONG).show()
                        } else {
                            runCatching { startActivity(open) }
                                .onFailure { Toast.makeText(this, "Update SonderWeb to use SonderCircle", Toast.LENGTH_LONG).show() }
                        }
                        done()
                    }
                }.start()
            }
        }, settle)
    }

    private fun done() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FROM_TILE = "from_tile"
        private const val TILE_DELAY = 450L
        private const val ICON_DELAY = 220L
    }
}
