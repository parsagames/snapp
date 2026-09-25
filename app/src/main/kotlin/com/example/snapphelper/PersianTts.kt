package com.example.snapphelper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.Executors

class PersianTts(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var initialized = false
    @Volatile private var tts: OfflineTts? = null

    fun initialize() {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            try {
                val dir = "tts/vits-piper-fa_IR-amir-medium"

                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = "$dir/fa_IR-amir-medium.onnx",
                            tokens = "$dir/tokens.txt",
                            dataDir = "$dir/espeak-ng-data"
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1
                )

                // Use AssetManager: the model lives inside the APK assets.
                tts = OfflineTts(context.assets, config)
                initialized = true
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun speak(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return

        if (!initialized) initialize()
        val engine = tts ?: return

        executor.execute {
            try {
                val audio = engine.generateWithConfig(
                    text = message,
                    config = GenerationConfig(
                        sid = 0,
                        speed = 1.0f,
                        silenceScale = 0.2f
                    )
                )
                playAudio(audio.samples, audio.sampleRate)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty() || sampleRate <= 0) return

        val pcm = ShortArray(samples.size) { i ->
            (samples[i] * 32767f)
                .coerceIn(-32768f, 32767f)
                .toInt()
                .toShort()
        }

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                val n = track.write(pcm, offset, pcm.size - offset)
                if (n <= 0) break
                offset += n
            }
            track.stop()
        } finally {
            track.release()
        }
    }

    fun shutdown() {
        executor.shutdownNow()
        synchronized(this) {
            try { tts?.release() } catch (_: Throwable) {}
            tts = null
            initialized = false
        }
    }
}
