package dev.ezaz.manazone.core

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings

/** A single reading of every clock the session logic depends on. */
data class TimeSnapshot(
    /** Wall-clock time (epoch millis). Can jump when the user or network changes the clock. */
    val wallMillis: Long,
    /** Monotonic time since boot, including deep sleep. Never jumps; resets on reboot. */
    val elapsedRealtimeMillis: Long,
    /** Number of device boots, or [UNKNOWN_BOOT_COUNT] if the platform does not expose it. */
    val bootCount: Int,
)

const val UNKNOWN_BOOT_COUNT = -1

/** Injectable clock so timer logic stays deterministic under test. */
fun interface TimeSource {
    fun now(): TimeSnapshot
}

class SystemTimeSource(private val contentResolver: ContentResolver) : TimeSource {
    override fun now() = TimeSnapshot(
        wallMillis = System.currentTimeMillis(),
        elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT_COUNT),
    )
}
