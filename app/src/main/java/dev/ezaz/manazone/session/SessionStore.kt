package dev.ezaz.manazone.session

import dev.ezaz.manazone.dnd.DndOwnership
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for sessions. Room implements it in the app; tests use an in-memory fake. */
interface SessionStore {
    fun observeActive(): Flow<FocusSession?>
    fun observeUnseenCompletion(): Flow<FocusSession?>

    /** Finalized sessions whose end lies after [wallMillis]; used for daily totals. */
    fun observeFinishedEndingAfter(wallMillis: Long): Flow<List<FocusSession>>

    suspend fun active(): FocusSession?

    /** Inserts [session] atomically only if no session is active. Returns the new id, or null. */
    suspend fun insertIfNoActive(session: FocusSession): Long?

    /**
     * Moves an ACTIVE session to a final state. Returns true only for the call that actually
     * performed the transition, which is what makes finalization exactly-once.
     */
    suspend fun finish(
        id: Long,
        status: SessionStatus,
        focusedMillis: Long,
        endWallMillis: Long,
        completionSeen: Boolean,
    ): Boolean

    suspend fun rebase(id: Long, anchorElapsedMillis: Long, anchorBootCount: Int)
    suspend fun setDnd(id: Long, dnd: DndOwnership)
    suspend fun clearDnd(id: Long)

    /** Finished sessions whose DND ownership was never cleared, i.e. whose release may not have happened. */
    suspend fun finishedWithDnd(): List<FocusSession>

    suspend fun clearFinishedDnd()
    suspend fun markCompletionsSeen()
}
