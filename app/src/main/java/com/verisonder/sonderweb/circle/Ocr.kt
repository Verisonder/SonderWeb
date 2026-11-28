package com.verisonder.sonderweb.circle

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The words on the screenshot, read on the phone itself (nothing is sent anywhere), so you can
 * pick text out of anything, pictures and videos included.
 */
object Ocr {
    /** One word, where it sits on the screenshot, and its place in reading order. */
    data class Word(val text: String, val box: Rect, val line: Int, val order: Int)

    suspend fun read(bitmap: Bitmap): List<Word> = suspendCancellableCoroutine { cont ->
        val recognizer = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull()
        if (recognizer == null) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val words = ArrayList<Word>()
                runCatching {
                    var line = 0
                    for (block in result.textBlocks) {
                        for (l in block.lines) {
                            for (e in l.elements) {
                                val box = e.boundingBox ?: continue
                                words += Word(e.text, box, line, words.size)
                            }
                            line++
                        }
                    }
                }
                recognizer.close()
                if (cont.isActive) cont.resume(words)
            }
            .addOnFailureListener {
                recognizer.close()
                if (cont.isActive) cont.resume(emptyList())
            }
    }

    /** The words of [words] between [a] and [b] (in reading order), joined as text. */
    fun join(words: List<Word>): String {
        val out = StringBuilder()
        var lastLine = -1
        for (w in words.sortedBy { it.order }) {
            if (out.isNotEmpty()) out.append(if (w.line != lastLine) "\n" else " ")
            out.append(w.text)
            lastLine = w.line
        }
        return out.toString()
    }
}
