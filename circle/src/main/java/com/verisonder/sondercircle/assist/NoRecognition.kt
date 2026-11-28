package com.verisonder.sondercircle.assist

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Android requires an assistant to name a speech service. SonderCircle has none of its own
 * (the microphone button uses the phone's), so this one just says it's not available.
 */
class NoRecognition : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
