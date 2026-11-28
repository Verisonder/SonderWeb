package com.verisonder.sondercircle.assist

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.verisonder.sondercircle.Handoff

/**
 * Hold home (or power, where the phone uses it for the assistant): Android hands over a
 * screenshot of what's on screen, and the circle opens on it. Nothing of its own is shown.
 */
class CircleSession(context: Context) : VoiceInteractionSession(context) {
    private var opened = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        opened = false
        // Settings › Advanced › Delay: the screenshot comes later, from SonderCircle's own
        // screenshot switch (Android's picture is taken right now).
        val delay = Handoff.delay(context)
        val service = com.verisonder.sondercircle.CircleAccessibility.instance
        if (delay > 0 && service != null) {
            opened = true
            hide()
            service.captureLater(300L + delay * 100L)
            return
        }
        if (showFlags and SHOW_WITH_SCREENSHOT == 0) {
            // The phone's "Use screenshot" switch for the assistant is off.
            Toast.makeText(context, "Turn on \"Use screenshot\" in the assistant settings", Toast.LENGTH_LONG).show()
            hide()
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        if (opened) return
        opened = true
        if (screenshot == null) {
            Toast.makeText(context, "Couldn't get the screen", Toast.LENGTH_SHORT).show()
            hide()
            return
        }
        val shot = if (screenshot.config == Bitmap.Config.HARDWARE) screenshot.copy(Bitmap.Config.ARGB_8888, false) else screenshot
        val main = Handler(Looper.getMainLooper())
        Thread {
            val open = runCatching { Handoff.prepare(context, shot) }.getOrNull()
            main.post {
                if (open == null) {
                    Toast.makeText(context, "Install SonderWeb first: SonderCircle opens in it", Toast.LENGTH_LONG).show()
                } else {
                    runCatching { startAssistantActivity(open) }
                }
                hide()
            }
        }.start()
    }
}
