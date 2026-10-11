package dev.ezaz.manazone.session

import dev.ezaz.manazone.dnd.DndOwnership

/** Side effects of session transitions (alarms, widgets, feedback). Fake-able in tests. */
interface SessionEffects {
    /** Called after a session starts or is re-anchored after a reboot. */
    suspend fun onActive(session: FocusSession)

    /** Called exactly once per session, after it has been finalized in storage. */
    suspend fun onFinished(session: FocusSession, playCompletionFeedback: Boolean)
}

/** Optional Do Not Disturb handling. Failures must never prevent focusing. */
interface DistractionGuard {
    /** Applies DND if enabled and permitted. Returns what was changed, or null if nothing was. */
    suspend fun engage(): DndOwnership?

    /** Restores the prior state if Manazone's change is still in effect. */
    suspend fun release(ownership: DndOwnership)
}
