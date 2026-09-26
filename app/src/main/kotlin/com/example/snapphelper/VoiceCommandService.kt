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
        private const val LISTEN_TOKEN = "listen"

        @Volatile private var instance: VoiceCommandService? = null
        @Volatile private var pauseUntil = 0L

        fun pauseRecognitionForTts(milliseconds: Long) {
            pauseUntil = maxOf(
                pauseUntil,
                System.currentTimeMillis() + milliseconds
            )
            instance?.pauseForTts()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var lastAcceptCommandAt = 0L
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            createChannel()
            startAsForeground()

            // SpeechRecognizer is created after the foreground service starts.
            // This prevents a recognition-provider problem from crashing the app.
            handler.postDelayed(
                { setupRecognizerSafely() },
                500L
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun startAsForeground() {
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SnappBox Voice Helper")
                .setContentText(
                    "گوش دادن به فرمان «قبول کن» فعال است"
                )
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            e.printStackTrace()

            // Fallback for devices that reject the typed foreground call.
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ignored: Throwable) {
                ignored.printStackTrace()
            }
        }
    }

    private fun setupRecognizerSafely() {
        if (destroyed) return

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                scheduleListen(3000L)
                return
            }

            recognizer?.cancel()
            recognizer?.destroy()

            val newRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            newRecognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(
                        params: Bundle?
                    ) {
                        listening = true
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        listening = false
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) = Unit

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) = Unit

                    override fun onError(error: Int) {
                        listening = false

                        if (!destroyed) {
                            scheduleListen(
                                if (
                                    error ==
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                                ) 1500L else 800L
                            )
                        }
                    }

                    override fun onResults(
                        results: Bundle?
                    ) {
                        listening = false

                        try {
                            val matches =
                                results
                                    ?.getStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION
                                    )
                                    .orEmpty()

                            if (matches.any(::isExplicitAcceptCommand)) {
                                tryAccept()
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }

                        if (!destroyed) {
                            scheduleListen(450L)
                        }
                    }
                }
            )

            recognizer = newRecognizer
            scheduleListen(250L)

        } catch (e: Throwable) {
            e.printStackTrace()
            recognizer = null

            if (!destroyed) {
                scheduleListen(2000L)
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (destroyed) return START_NOT_STICKY

        try {
            if (recognizer == null) {
                setupRecognizerSafely()
            } else {
                scheduleListen(250L)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return START_NOT_STICKY
    }

    private fun scheduleListen(delayMs: Long) {
        if (destroyed) return

        handler.removeCallbacksAndMessages(LISTEN_TOKEN)

        handler.postAtTime(
            {
                if (!destroyed) {
                    startListeningIfAllowed()
                }
            },
            LISTEN_TOKEN,
            System.currentTimeMillis() +
                delayMs.coerceAtLeast(100L)
        )
    }

    private fun startListeningIfAllowed() {
        if (destroyed) return

        try {
            val remaining =
                pauseUntil - System.currentTimeMillis()

            if (remaining > 0L) {
                scheduleListen(
                    remaining.coerceAtLeast(350L)
                )
                return
            }

            if (listening) return

            val r = recognizer ?: run {
                setupRecognizerSafely()
                return
            }

            val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        "fa-IR"
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        false
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS,
                        5
                    )
                }

            r.startListening(intent)

        } catch (e: Throwable) {
            e.printStackTrace()
            listening = false

            if (!destroyed) {
                scheduleListen(1500L)
            }
        }
    }

    private fun isExplicitAcceptCommand(
        raw: String
    ): Boolean {
        val n = normalizeCommand(raw)

        return n in setOf(
            "قبولکن",
            "قبولشکن",
            "سفارشروقبولکن",
            "درخواستروقبولکن",
            "اینوسفارشروبگیر",
            "اینسفارشروربگیر",
            "بگیرش"
        )
    }

    private fun normalizeCommand(value: String): String =
        value
            .trim()
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("ۀ", "ه")
            .replace("\u200c", "")
            .replace(Regex("[\\s،,.!?؟؛:\\-]+"), "")

    private fun tryAccept() {
        try {
            val now = System.currentTimeMillis()

            if (
                now - lastAcceptCommandAt <
                AppConfig.ACCEPT_COOLDOWN_MS
            ) {
                return
            }

            val service =
                SnappBoxAccessibilityService.instance
                    ?: return

            if (service.acceptCurrentOrder()) {
                lastAcceptCommandAt = now
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun pauseForTts() {
        if (destroyed) return

        handler.post {
            try {
                recognizer?.cancel()
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            listening = false

            val remaining =
                pauseUntil - System.currentTimeMillis()

            scheduleListen(
                remaining.coerceAtLeast(350L)
            )
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SnappBox Voice Helper",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        destroyed = true
        instance = null

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }

        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }

        recognizer = null
        listening = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
