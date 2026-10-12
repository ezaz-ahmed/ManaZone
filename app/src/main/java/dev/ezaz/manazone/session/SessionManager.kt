package dev.ezaz.manazone.session

import android.util.Log
import dev.ezaz.manazone.core.TimeSnapshot
import dev.ezaz.manazone.core.TimeSource
import dev.ezaz.manazone.dnd.DndOwnership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface StartResult {
    val session: FocusSession

    data class Started(override val session: FocusSession) : StartResult
    data class AlreadyActive(override val session: FocusSession) : StartResult
}

/**
 * Single source of truth for the session lifecycle. Every transition goes through here, guarded by
 * a mutex in-process and by conditional updates in storage, so concurrent triggers (UI, widget,
 * alarm) cannot create a second session or finalize one twice.
 */
class SessionManager(
    private val store: SessionStore,
    private val time: TimeSource,
    private val effects: SessionEffects,
    private val guard: DistractionGuard,
    /** Completion feedback only plays if finalization happens this soon after the planned end. */
    private val feedbackGraceMillis: Long = DEFAULT_FEEDBACK_GRACE_MILLIS,
    private val log: (String, Throwable?) -> Unit = { msg, t -> Log.w(TAG, msg, t) },
) {
    private val mutex = Mutex()

    val activeSession: Flow<FocusSession?> = store.observeActive()
    val unseenCompletion: Flow<FocusSession?> = store.observeUnseenCompletion()

    fun now(): TimeSnapshot = time.now()

    suspend fun start(minutes: Int): StartResult = mutex.withLock {
        require(minutes in MIN_MINUTES..MAX_MINUTES) { "Duration out of range: $minutes" }
        reconcileLocked()
        store.active()?.let { return StartResult.AlreadyActive(it) }

        val now = time.now()
        val draft = FocusSession(
            id = 0,
            plannedMillis = minutes * 60_000L,
            startWallMillis = now.wallMillis,
            anchorElapsedMillis = now.elapsedRealtimeMillis,
            anchorBootCount = now.bootCount,
            status = SessionStatus.ACTIVE,
        )
        val id = store.insertIfNoActive(draft)
            ?: return StartResult.AlreadyActive(checkNotNull(store.active()))
        // A new session supersedes any completion screen the user never dismissed.
        store.markCompletionsSeen()

        var session = draft.copy(id = id)
        val dnd = runCatching { guard.engage() }.onFailure { log("DND engage failed", it) }.getOrNull()
        if (dnd != null) {
            store.setDnd(id, dnd)
            session = session.copy(dnd = dnd)
        }
        runCatching { effects.onActive(session) }.onFailure { log("onActive failed", it) }
        StartResult.Started(session)
    }

    /**
     * Immediate, unconfirmed cancellation that records only the time actually focused. If the
     * planned time had already passed (Stop tapped at the last instant), the session completes.
     */
    suspend fun cancel(): FocusSession? = mutex.withLock {
        val session = store.active() ?: return null
        val now = time.now()
        if (SessionTiming.isElapsed(session, now)) {
            finalizeCompletedLocked(session, now)
        } else {
            finishLocked(
                session = session,
                status = SessionStatus.CANCELLED,
                focusedMillis = SessionTiming.focusedMillis(session, now),
                playFeedback = false,
            )
        }
    }

    /**
     * Brings persisted state in line with the clocks: completes an elapsed session exactly once and
     * re-anchors a session that survived a reboot. Safe to call any number of times.
     */
    suspend fun reconcile(): FocusSession? = mutex.withLock { reconcileLocked() }

    suspend fun acknowledgeCompletion() = store.markCompletionsSeen()

    /**
     * Releases DND for finished sessions whose release never completed (e.g. the process died
     * between finalizing and releasing). Safe to call any number of times.
     */
    suspend fun releaseOrphanedDnd() = mutex.withLock {
        store.finishedWithDnd().forEach { s -> s.dnd?.let { releaseDnd(s.id, it) } }
    }

    /** Ownership is cleared only after a successful release, so a failed one is retried at next start. */
    private suspend fun releaseDnd(id: Long, dnd: DndOwnership) {
        runCatching {
            guard.release(dnd)
            store.clearDnd(id)
        }.onFailure { log("DND release failed", it) }
    }

    private suspend fun reconcileLocked(): FocusSession? {
        val session = store.active() ?: return null
        val now = time.now()
        if (SessionTiming.isElapsed(session, now)) return finalizeCompletedLocked(session, now)
        if (!SessionTiming.isSameBoot(session, now)) {
            // Rebooted mid-session: keep the elapsed time measured by the wall clock and continue
            // on the new boot's monotonic clock from there.
            val elapsed = SessionTiming.rawElapsedMillis(session, now)
            val rebased = session.copy(
                anchorElapsedMillis = now.elapsedRealtimeMillis - elapsed,
                anchorBootCount = now.bootCount,
            )
            store.rebase(session.id, rebased.anchorElapsedMillis, rebased.anchorBootCount)
            runCatching { effects.onActive(rebased) }.onFailure { log("onActive failed", it) }
            return rebased
        }
        return session
    }

    private suspend fun finalizeCompletedLocked(session: FocusSession, now: TimeSnapshot): FocusSession? {
        val lateness = SessionTiming.rawElapsedMillis(session, now) - session.plannedMillis
        return finishLocked(
            session = session,
            status = SessionStatus.COMPLETED,
            focusedMillis = session.plannedMillis,
            playFeedback = lateness <= feedbackGraceMillis,
        )
    }

    private suspend fun finishLocked(
        session: FocusSession,
        status: SessionStatus,
        focusedMillis: Long,
        playFeedback: Boolean,
    ): FocusSession? {
        val end = session.startWallMillis + focusedMillis
        val completionSeen = status != SessionStatus.COMPLETED
        if (!store.finish(session.id, status, focusedMillis, end, completionSeen)) return null
        val finished = session.copy(
            status = status,
            focusedMillis = focusedMillis,
            endWallMillis = end,
            completionSeen = completionSeen,
        )
        // Restore DND before any sound so the user's own settings decide whether it is audible.
        session.dnd?.let { releaseDnd(session.id, it) }
        runCatching { effects.onFinished(finished, playFeedback) }.onFailure { log("onFinished failed", it) }
        return finished
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 24 * 60
        const val DEFAULT_FEEDBACK_GRACE_MILLIS = 2 * 60_000L
        private const val TAG = "SessionManager"
    }
}
