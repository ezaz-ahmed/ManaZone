package dev.ezaz.manazone.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The one-shot completion cue: a short double vibration and/or a soft synthesized "ding-dong".
 * Callers are responsible for invoking it once per session (see SessionManager).
 */
class CompletionFeedback(private val context: Context) {

    /** Plays the enabled cues and suspends until the sound (if any) has finished. */
    suspend fun play(vibrate: Boolean, sound: Boolean) {
        if (vibrate) runCatching { vibrate() }.onFailure { Log.w(TAG, "Vibration failed", it) }
        if (sound) runCatching { playDingDong() }.onFailure { Log.w(TAG, "Sound failed", it) }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 140, 160, 220), intArrayOf(0, 160, 0, 200), -1)
        // Alarm usage lets the cue through when the session ends while Manazone is in the background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private suspend fun playDingDong() {
        val pcm = synthesizeDingDong()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                // Notification-event usage respects silent mode and the user's own DND rules.
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(pcm.size * 1000L / SAMPLE_RATE + 100)
        } finally {
            track.release()
        }
    }

    companion object {
        private const val TAG = "CompletionFeedback"
        private const val SAMPLE_RATE = 22_050

        /** Two soft bell tones (E5 then C5) with exponential decay. */
        fun synthesizeDingDong(): ShortArray {
            val total = (SAMPLE_RATE * 1.9).toInt()
            val out = ShortArray(total)
            fun bell(freq: Double, startSec: Double, lengthSec: Double) {
                val start = (startSec * SAMPLE_RATE).toInt()
                val length = (lengthSec * SAMPLE_RATE).toInt()
                for (i in 0 until length) {
                    val idx = start + i
                    if (idx >= total) break
                    val t = i.toDouble() / SAMPLE_RATE
                    val attack = (t / 0.008).coerceAtMost(1.0)
                    val env = attack * exp(-t * 3.2)
                    val tone = sin(2 * PI * freq * t) + 0.25 * sin(2 * PI * freq * 2.0 * t)
                    val mixed = out[idx] + (tone * env * 0.28 * Short.MAX_VALUE / 1.25)
                    out[idx] = mixed.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            bell(659.25, 0.0, 1.2)
            bell(523.25, 0.55, 1.35)
            return out
        }
    }
}
