package com.verisonder.sondercircle

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/**
 * Only takes screenshots, when you open SonderCircle. It listens to no events and can't see
 * window content (see res/xml/accessibility.xml).
 */
class CircleAccessibility : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Settings › Advanced › Delay: waits [ms] with nothing of SonderCircle's on screen (so you can
     * open a menu or scroll first), then takes the screenshot and opens the circle on it.
     */
    fun captureLater(ms: Long) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({
            capture { bmp ->
                if (bmp == null) {
                    android.widget.Toast.makeText(this, "Couldn't take a screenshot. Try again.", android.widget.Toast.LENGTH_SHORT).show()
                    return@capture
                }
                Thread {
                    val open = runCatching { Handoff.prepare(this, bmp) }.getOrNull()
                    main.post {
                        if (open == null) {
                            android.widget.Toast.makeText(this, "Install SonderWeb first: SonderCircle opens in it", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            // An app whose accessibility service is on may open its screen from here.
                            runCatching { startActivity(open) }
                                .onFailure { android.widget.Toast.makeText(this, "Update SonderWeb to use SonderCircle", android.widget.Toast.LENGTH_LONG).show() }
                        }
                    }
                }.start()
            }
        }, ms)
    }

    /** Takes the screenshot; [done] gets it (or null) on the main thread. */
    fun capture(done: (Bitmap?) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bmp = runCatching {
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    }.getOrNull()
                    buffer.close()
                    done(bmp)
                }

                override fun onFailure(errorCode: Int) {
                    done(null)
                }
            },
        )
    }

    companion object {
        @Volatile
        var instance: CircleAccessibility? = null
            private set

        /** Whether you've switched it on in Android's accessibility settings. */
        fun enabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, CircleAccessibility::class.java).flattenToString()
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
