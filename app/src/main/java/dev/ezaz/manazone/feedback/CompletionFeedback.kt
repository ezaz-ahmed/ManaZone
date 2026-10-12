package dev.ezaz.manazone.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The one-shot completion cue: a short double vibration and/or a soft synthesized "ding-dong".
 * Callers are responsible for invoking it once per session (see SessionManager).
 */
class CompletionFeedback(private val context: Context, private val customSounds: CustomSoundStore) {

    /** Plays the enabled cues and suspends until the sound (if any) has finished. */
    suspend fun play(vibrate: Boolean, sound: Boolean, soundId: String, volume: Float) {
        if (vibrate) runCatching { vibrate() }.onFailure { Log.w(TAG, "Vibration failed", it) }
        if (sound) preview(soundId, volume)
    }

    /** Plays one sound at the given volume (0..1); used for completion and for previews in Settings. */
    suspend fun preview(soundId: String, volume: Float) {
        runCatching {
            val custom = customSounds.fileFor(soundId)
            // A custom sound whose file is gone falls back to the default rather than staying silent.
            if (custom != null) playFile(custom, volume) else playPcm(CompletionSound.byId(soundId).render(volume))
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else Log.w(TAG, "Sound failed", it) }
    }

    private suspend fun playFile(file: File, volume: Float) {
        val player = MediaPlayer()
        try {
            withTimeoutOrNull((CustomSoundStore.MAX_SECONDS + 5) * 1000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    player.setDataSource(file.absolutePath)
                    val v = volume.coerceIn(0f, 1f)
                    player.setVolume(v, v)
                    player.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                    player.setOnErrorListener { _, _, _ ->
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("MediaPlayer error"))
                        true
                    }
                    player.setOnPreparedListener { it.start() }
                    player.prepareAsync()
                }
            }
        } finally {
            player.release()
        }
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

    private suspend fun playPcm(pcm: ShortArray) {
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
        private const val SAMPLE_RATE = CompletionSound.SAMPLE_RATE
    }
}
