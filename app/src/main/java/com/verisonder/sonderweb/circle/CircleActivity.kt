package com.verisonder.sonderweb.circle

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asImageBitmap
import com.verisonder.sonderweb.browser.Browser

/**
 * Circle to search, opened by SonderCircle with a screenshot. It runs inside SonderWeb so the
 * results come with your own sign-ins (Google, and everything else you use in SonderWeb).
 */
class CircleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val shot = intent?.data?.let { read(it) }
        if (shot == null) {
            finishQuietly()
            return
        }
        // The engine, with your sign-ins and uBlock Origin (already running if SonderWeb is open).
        Browser.start(this)
        val image = shot.asImageBitmap()
        // SonderCircle's settings: which engines to search with.
        val textEngine = intent?.getStringExtra("text_engine").orEmpty()
        val imageEngine = intent?.getStringExtra("image_engine") ?: "google"
        // How long the opening glow runs (an older SonderCircle doesn't say: as before).
        val introMs = (intent?.getIntExtra("intro_ms", 1100) ?: 1100).coerceIn(0, 3000)
        setContent {
            CircleScreen(shot = shot, image = image, textEngine = textEngine, imageEngine = imageEngine, introMs = introMs, onClose = ::finishQuietly)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A new screenshot while already open: start over on it.
        setIntent(intent)
        recreate()
    }

    /** The screenshot SonderCircle shared (it lets this app read it once). */
    private fun read(uri: Uri): Bitmap? = runCatching {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
