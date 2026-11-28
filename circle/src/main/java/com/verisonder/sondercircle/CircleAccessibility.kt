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
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

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
