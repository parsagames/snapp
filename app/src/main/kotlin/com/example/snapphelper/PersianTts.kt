package com.example.snapphelper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors

class PersianTts(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var initialized = false

    private var tts: OfflineTts? = null

    fun initialize() {
        if (initialized) return

        try {
            val modelDir = File(context.filesDir, "tts")
            copyAssetsRecursively("tts", modelDir)

            val model = File(modelDir, "fa_IR-amir-medium.onnx")
            val tokens = File(modelDir, "tokens.txt")
            val lexicon = File(modelDir, "lexicon.txt")

            val config = OfflineTtsConfig(
                model = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    tokens = tokens.absolutePath,
                    lexicon = lexicon.absolutePath
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            tts = OfflineTts(config)
            initialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String) {
        if (!initialized) {
            initialize()
        }

        val engine = tts ?: return

        executor.execute {
            try {
                val audio = engine.generate(
                    text = text,
                    sid = 0,
                    speed = 1.0f
                )

                playAudio(audio.samples, audio.sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playAudio(samples: FloatArray, sampleRate: Int) {
        val pcm = ShortArray(samples.size)

        for (i in samples.indices) {
            val value = (samples[i] * 32767f)
                .coerceIn(-32768f, 32767f)

            pcm[i] = value.toInt().toShort()
        }

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(
            minBuffer,
            pcm.size * 2
        )

        val audioTrack = AudioTrack.Builder()
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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(
            pcm,
            0,
            pcm.size
        )

        audioTrack.play()

        Thread.sleep(
            ((pcm.size.toLong() * 1000L) / sampleRate) + 200L
        )

        audioTrack.stop()
        audioTrack.release()
    }

    private fun copyAssetsRecursively(
        assetPath: String,
        destination: File
    ) {
        if (!destination.exists()) {
            destination.mkdirs()
        }

        val children = context.assets.list(assetPath)

        if (children.isNullOrEmpty()) {
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childDestination = File(destination, child)

            copyAssetsRecursively(
                childAssetPath,
                childDestination
            )
        }
    }

    fun shutdown() {
        executor.shutdownNow()
        tts?.release()
        tts = null
    }
}
