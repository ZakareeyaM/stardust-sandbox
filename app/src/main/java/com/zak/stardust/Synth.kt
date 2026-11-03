package com.zak.stardust

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sin

/** Tiny synthesizer: generates a short plucked sine note and plays it. No audio files needed. */
class Synth {

    @Volatile
    var enabled = true

    private var lastPlay = 0L

    fun play(freq: Double, ms: Int = 220, volume: Double = 0.5) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlay < 90) return
        lastPlay = now

        Thread {
            try {
                val sampleRate = 22050
                val n = sampleRate * ms / 1000
                val data = ShortArray(n)
                for (i in 0 until n) {
                    val t = i.toDouble() / sampleRate
                    val decay = 1.0 - i.toDouble() / n
                    val env = decay * decay
                    val v = (sin(2.0 * PI * freq * t) + 0.4 * sin(4.0 * PI * freq * t)) * env * volume
                    data[i] = (v * 20000.0).toInt().coerceIn(-32768, 32767).toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, n)
                track.play()
                Thread.sleep(ms + 80L)
                track.release()
            } catch (e: Exception) {
                // Audio is optional: ignore any failure.
            }
        }.start()
    }
}
