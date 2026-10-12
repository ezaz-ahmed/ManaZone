package dev.ezaz.manazone.session

import dev.ezaz.manazone.core.TimeSnapshot
import dev.ezaz.manazone.core.TimeSource
import dev.ezaz.manazone.dnd.DndOwnership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Controllable clocks. [advance] moves both clocks as real time would. */
class FakeTime(
    var wall: Long = 1_700_000_000_000L,
    var elapsed: Long = 10_000_000L,
    var boot: Int = 7,
) : TimeSource {
    override fun now() = TimeSnapshot(wall, elapsed, boot)

    fun advance(millis: Long) {
        wall += millis
        elapsed += millis
    }

    fun advanceMinutes(minutes: Long) = advance(minutes * 60_000)

    /** Simulates a reboot after [downtimeMillis] powered off, landing [uptimeMillis] into the new boot. */
    fun reboot(downtimeMillis: Long, uptimeMillis: Long = 30_000) {
        wall += downtimeMillis + uptimeMillis
        elapsed = uptimeMillis
        boot += 1
    }
}

/** In-memory SessionStore with the same conditional semantics as the Room DAO. */
class FakeSessionStore : SessionStore {
    val sessions = MutableStateFlow<List<FocusSession>>(emptyList())
    private var nextId = 1L

    override fun observeActive(): Flow<FocusSession?> =
        sessions.map { list -> list.lastOrNull { it.status == SessionStatus.ACTIVE } }

    override fun observeUnseenCompletion(): Flow<FocusSession?> =
        sessions.map { list -> list.lastOrNull { it.status == SessionStatus.COMPLETED && !it.completionSeen } }

    override fun observeFinishedEndingAfter(wallMillis: Long): Flow<List<FocusSession>> =
        sessions.map { list -> list.filter { it.status != SessionStatus.ACTIVE && (it.endWallMillis ?: 0) > wallMillis } }

    override suspend fun active() = sessions.value.lastOrNull { it.status == SessionStatus.ACTIVE }

    override suspend fun insertIfNoActive(session: FocusSession): Long? {
        if (active() != null) return null
        val id = nextId++
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }

    override suspend fun finish(
        id: Long,
        status: SessionStatus,
        focusedMillis: Long,
        endWallMillis: Long,
        completionSeen: Boolean,
    ): Boolean {
        val current = sessions.value.firstOrNull { it.id == id } ?: return false
        if (current.status != SessionStatus.ACTIVE) return false
        update(id) { it.copy(status = status, focusedMillis = focusedMillis, endWallMillis = endWallMillis, completionSeen = completionSeen) }
        return true
    }

    override suspend fun rebase(id: Long, anchorElapsedMillis: Long, anchorBootCount: Int) =
        update(id) { if (it.status == SessionStatus.ACTIVE) it.copy(anchorElapsedMillis = anchorElapsedMillis, anchorBootCount = anchorBootCount) else it }

    override suspend fun setDnd(id: Long, dnd: DndOwnership) = update(id) { it.copy(dnd = dnd) }

    override suspend fun clearDnd(id: Long) = update(id) { it.copy(dnd = null) }

    override suspend fun finishedWithDnd() =
        sessions.value.filter { it.status != SessionStatus.ACTIVE && it.dnd != null }

    override suspend fun clearFinishedDnd() {
        sessions.value = sessions.value.map { if (it.status != SessionStatus.ACTIVE) it.copy(dnd = null) else it }
    }

    override suspend fun markCompletionsSeen() {
        sessions.value = sessions.value.map { it.copy(completionSeen = true) }
    }

    fun byId(id: Long) = sessions.value.first { it.id == id }

    private fun update(id: Long, block: (FocusSession) -> FocusSession) {
        sessions.value = sessions.value.map { if (it.id == id) block(it) else it }
    }
}

class RecordingEffects : SessionEffects {
    val activated = mutableListOf<FocusSession>()
    val finished = mutableListOf<Pair<FocusSession, Boolean>>()
    val feedbackCount get() = finished.count { it.second }

    override suspend fun onActive(session: FocusSession) {
        activated += session
    }

    override suspend fun onFinished(session: FocusSession, playCompletionFeedback: Boolean) {
        finished += session to playCompletionFeedback
    }
}

class RecordingGuard(var ownership: DndOwnership? = null) : DistractionGuard {
    var engaged = 0
    val released = mutableListOf<DndOwnership>()

    override suspend fun engage(): DndOwnership? {
        engaged++
        return ownership
    }

    override suspend fun release(ownership: DndOwnership) {
        released += ownership
    }
}
