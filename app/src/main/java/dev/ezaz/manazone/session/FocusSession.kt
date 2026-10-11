package dev.ezaz.manazone.session

import dev.ezaz.manazone.core.TimeSnapshot
import dev.ezaz.manazone.core.UNKNOWN_BOOT_COUNT
import dev.ezaz.manazone.dnd.DndOwnership

enum class SessionStatus { ACTIVE, COMPLETED, CANCELLED }

/**
 * One focus session. Timing is derived from persisted anchors, never from a live counter:
 *
 * - [anchorElapsedMillis] is the `elapsedRealtime` value that corresponds to the session's start on
 *   boot [anchorBootCount]. While the device stays on that boot, elapsed time is measured with the
 *   monotonic clock, so wall-clock changes cannot shorten or extend a session.
 * - After a reboot the monotonic clock resets, so elapsed time falls back to the wall clock
 *   ([startWallMillis]) and the session is re-anchored to the new boot (see [SessionManager]).
 */
data class FocusSession(
    val id: Long,
    val plannedMillis: Long,
    val startWallMillis: Long,
    val anchorElapsedMillis: Long,
    val anchorBootCount: Int,
    val status: SessionStatus,
    /** Wall-clock end used for daily accounting: `startWallMillis + focusedMillis`. Null while active. */
    val endWallMillis: Long? = null,
    /** Actual focused time. Zero while active; fixed once finalized. */
    val focusedMillis: Long = 0,
    /** Whether the user has seen the completion state. Cancelled sessions are always "seen". */
    val completionSeen: Boolean = false,
    /** Do Not Disturb change made by Manazone for this session, if any. */
    val dnd: DndOwnership? = null,
) {
    val plannedMinutes: Int get() = (plannedMillis / 60_000).toInt()
}

object SessionTiming {

    /** True when the session's monotonic anchor belongs to the current boot. */
    fun isSameBoot(session: FocusSession, now: TimeSnapshot): Boolean =
        if (session.anchorBootCount != UNKNOWN_BOOT_COUNT && now.bootCount != UNKNOWN_BOOT_COUNT) {
            session.anchorBootCount == now.bootCount
        } else {
            // Without a boot counter, a monotonic clock that went backwards means a reboot happened.
            now.elapsedRealtimeMillis >= session.anchorElapsedMillis
        }

    /** Elapsed time since start, not clamped to the plan. May exceed the planned duration. */
    fun rawElapsedMillis(session: FocusSession, now: TimeSnapshot): Long {
        val elapsed = if (isSameBoot(session, now)) {
            now.elapsedRealtimeMillis - session.anchorElapsedMillis
        } else {
            now.wallMillis - session.startWallMillis
        }
        return elapsed.coerceAtLeast(0)
    }

    /** Focused time that counts toward totals: elapsed time, capped at the planned duration. */
    fun focusedMillis(session: FocusSession, now: TimeSnapshot): Long =
        rawElapsedMillis(session, now).coerceAtMost(session.plannedMillis)

    fun remainingMillis(session: FocusSession, now: TimeSnapshot): Long =
        (session.plannedMillis - rawElapsedMillis(session, now)).coerceAtLeast(0)

    fun isElapsed(session: FocusSession, now: TimeSnapshot): Boolean =
        rawElapsedMillis(session, now) >= session.plannedMillis

    /** Fraction of the planned duration that has passed, in 0..1. */
    fun progress(session: FocusSession, now: TimeSnapshot): Float =
        if (session.plannedMillis <= 0) 1f
        else focusedMillis(session, now).toFloat() / session.plannedMillis
}
