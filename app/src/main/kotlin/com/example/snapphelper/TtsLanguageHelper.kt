package com.example.snapphelper

import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The app was completely silent because both TextToSpeech instances forced
 * Locale("fa", "IR") and never checked whether the device's TTS engine
 * actually has Persian voice data installed. Most engines (including the
 * default Google Speech Services on many devices) do NOT ship Persian, so
 * setLanguage() was returning LANG_MISSING_DATA / LANG_NOT_SUPPORTED and the
 * code just set ttsReady = false forever - nothing was ever spoken, silently.
 *
 * This helper checks language availability first and falls back to English
 * automatically when Persian isn't available, so the assistant always speaks
 * something instead of failing silently.
 */
object TtsLanguageHelper {

    /**
     * Picks the best available language on this TTS engine.
     * Returns true if Persian was selected, false if it fell back to English.
     */
    fun applyBestLanguage(engine: TextToSpeech): Boolean {
        val farsi = Locale("fa", "IR")
        val farsiResult = engine.isLanguageAvailable(farsi)
        return if (farsiResult >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = farsi
            true
        } else {
            // Fall back to English so the assistant is never mute.
            val english = Locale.US
            val englishResult = engine.isLanguageAvailable(english)
            engine.language = if (englishResult >= TextToSpeech.LANG_AVAILABLE) english else Locale.getDefault()
            false
        }
    }
}
