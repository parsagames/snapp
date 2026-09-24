package com.example.snapphelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class VoiceCommandService : Service() {
    companion object {
        private const val CHANNEL_ID = "voice_helper_channel"
        private const val NOTIFICATION_ID = 1001
        @Volatile private var instance: VoiceCommandService? = null
        @Volatile private var pauseUntil = 0L
        private const val LISTEN_TOKEN = "listen"

        fun pauseRecognitionForTts(milliseconds: Long) {
            pauseUntil = maxOf(pauseUntil, System.currentTimeMillis() + milliseconds)
            instance?.pauseForTts()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var lastAcceptCommandAt = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startAsForeground()
        setupRecognizer()
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SnappBox Voice Helper")
            .setContentText("گوش دادن به فرمان «قبول کن» فعال است")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                listening = false
                scheduleListen(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 500)
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (matches.any(::isExplicitAcceptCommand)) tryAccept()
                scheduleListen(450)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleListen(250)
        return START_NOT_STICKY
    }

    private fun scheduleListen(delayMs: Long) {
        handler.removeCallbacksAndMessages(LISTEN_TOKEN)
        handler.postAtTime({ startListeningIfAllowed() }, LISTEN_TOKEN, System.currentTimeMillis() + delayMs)
    }

    private fun startListeningIfAllowed() {
        if (System.currentTimeMillis() < pauseUntil) {
            scheduleListen((pauseUntil - System.currentTimeMillis()).coerceAtLeast(250))
            return
        }
        if (listening) return
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try { r.startListening(intent) } catch (_: Throwable) { scheduleListen(1200) }
    }

    private fun isExplicitAcceptCommand(raw: String): Boolean {
        val n = normalizeCommand(raw)
        return n in setOf(
            "قبولکن", "قبولشکن", "سفارشروقبولکن", "درخواستروقبولکن",
            "اینوسفارشروبگیر", "اینسفارشروربگیر", "بگیرش"
        )
    }

    private fun normalizeCommand(value: String): String = value
        .trim()
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("ۀ", "ه")
        .replace("‌", "")
        .replace(Regex("[\\s،,.!?؟؛:؛\\-]+"), "")

    private fun tryAccept() {
        val now = System.currentTimeMillis()
        if (now - lastAcceptCommandAt < AppConfig.ACCEPT_COOLDOWN_MS) return
        val service = SnappBoxAccessibilityService.instance ?: return
        if (service.acceptCurrentOrder()) lastAcceptCommandAt = now
    }

    private fun pauseForTts() {
        handler.post {
            recognizer?.cancel()
            listening = false
            scheduleListen((pauseUntil - System.currentTimeMillis()).coerceAtLeast(350))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "SnappBox Voice Helper", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

}
