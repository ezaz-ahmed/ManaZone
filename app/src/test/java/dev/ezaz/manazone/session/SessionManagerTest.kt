package dev.ezaz.manazone.session

import dev.ezaz.manazone.core.UNKNOWN_BOOT_COUNT
import dev.ezaz.manazone.dnd.DndOwnership
import dev.ezaz.manazone.dnd.InterruptionFilter
import dev.ezaz.manazone.presets.DEFAULT_PRESETS
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {
    private val time = FakeTime()
    private val store = FakeSessionStore()
    private val effects = RecordingEffects()
    private val guard = RecordingGuard()

    private fun manager() = SessionManager(store, time, effects, guard, log = { _, _ -> })

    private val min = 60_000L

    @Test
    fun `every default duration starts a session with the exact planned length`() = runTest {
        for (minutes in DEFAULT_PRESETS) {
            val m = manager()
            val result = m.start(minutes)
            assertTrue(result is StartResult.Started)
            val session = result.session
            assertEquals(minutes * min, session.plannedMillis)
            assertEquals(minutes * min, SessionTiming.remainingMillis(session, time.now()))
            m.cancel()
        }
        assertEquals(DEFAULT_PRESETS.size, store.sessions.value.size)
    }

    @Test
    fun `countdown is derived from timestamps`() = runTest {
        val s = manager().start(25).session
        time.advance(4 * min + 30_000)
        assertEquals(20 * min + 30_000, SessionTiming.remainingMillis(s, time.now()))
        time.advanceMinutes(30)
        assertEquals(0, SessionTiming.remainingMillis(s, time.now()))
        assertEquals(1f, SessionTiming.progress(s, time.now()))
    }

    @Test
    fun `stop after 9 minutes of 20 records 9 minutes as cancelled`() = runTest {
        val m = manager()
        val id = m.start(20).session.id
        time.advanceMinutes(9)
        val cancelled = m.cancel()

        assertNotNull(cancelled)
        val stored = store.byId(id)
        assertEquals(SessionStatus.CANCELLED, stored.status)
        assertEquals(9 * min, stored.focusedMillis)
        assertEquals(stored.startWallMillis + 9 * min, stored.endWallMillis)
        assertTrue(stored.completionSeen)
        assertEquals(0, effects.feedbackCount)
        assertNull(store.active())
    }

    @Test
    fun `time after cancellation is never counted`() = runTest {
        val m = manager()
        val id = m.start(20).session.id
        time.advanceMinutes(9)
        m.cancel()
        time.advanceMinutes(60)
        m.reconcile()
        m.cancel()
        assertEquals(9 * min, store.byId(id).focusedMillis)
        assertEquals(SessionStatus.CANCELLED, store.byId(id).status)
        assertEquals(1, effects.finished.size)
    }

    @Test
    fun `completion finalizes exactly once and plays feedback once`() = runTest {
        val m = manager()
        val id = m.start(5).session.id
        time.advanceMinutes(5)
        val done = m.reconcile()
        repeat(5) { m.reconcile() }
        m.cancel()

        assertEquals(SessionStatus.COMPLETED, done?.status)
        assertEquals(5 * min, store.byId(id).focusedMillis)
        assertFalse(store.byId(id).completionSeen)
        assertEquals(1, effects.finished.size)
        assertEquals(1, effects.feedbackCount)
    }

    @Test
    fun `concurrent finalizers from UI and alarm complete once`() = runTest {
        val m = manager()
        m.start(5)
        time.advanceMinutes(6)
        (1..10).map { async { m.reconcile() } }.awaitAll()
        assertEquals(1, effects.finished.size)
        assertEquals(1, effects.feedbackCount)
    }

    @Test
    fun `a second start returns the active session instead of creating one`() = runTest {
        val m = manager()
        val first = m.start(25)
        val second = m.start(60)
        assertTrue(second is StartResult.AlreadyActive)
        assertEquals(first.session.id, second.session.id)
        assertEquals(1, store.sessions.value.size)
    }

    @Test
    fun `concurrent widget taps create a single session`() = runTest {
        val m = manager()
        val results = (1..8).map { async { m.start(25) } }.awaitAll()
        assertEquals(1, store.sessions.value.size)
        assertEquals(1, results.count { it is StartResult.Started })
    }

    @Test
    fun `starting after a finished session creates a new one`() = runTest {
        val m = manager()
        m.start(5)
        time.advanceMinutes(10)
        val next = m.start(20)
        assertTrue(next is StartResult.Started)
        assertEquals(SessionStatus.COMPLETED, store.sessions.value.first().status)
        assertEquals(2, store.sessions.value.size)
    }

    @Test
    fun `remaining time is correct after process recreation`() = runTest {
        val id = manager().start(25).session.id
        time.advanceMinutes(10)
        // A brand new manager instance stands in for a recreated process.
        val restored = manager()
        val active = restored.reconcile()
        assertEquals(id, active?.id)
        assertEquals(15 * min, SessionTiming.remainingMillis(active!!, time.now()))
    }

    @Test
    fun `session that elapsed while the process was dead is finalized once without a late cue`() = runTest {
        val id = manager().start(25).session.id
        time.advanceMinutes(90)
        val restored = manager()
        restored.reconcile()
        restored.reconcile()
        manager().reconcile()

        assertEquals(SessionStatus.COMPLETED, store.byId(id).status)
        assertEquals(25 * min, store.byId(id).focusedMillis)
        assertEquals(1, effects.finished.size)
        assertEquals(0, effects.feedbackCount) // too late to ding; completion state is shown instead
    }

    @Test
    fun `finalization within the grace window still plays the cue`() = runTest {
        manager().start(25)
        time.advance(25 * min + 60_000)
        manager().reconcile()
        assertEquals(1, effects.feedbackCount)
    }

    @Test
    fun `cancelled and completed sessions are never resurrected`() = runTest {
        val m = manager()
        m.start(20)
        time.advanceMinutes(3)
        m.cancel()
        m.start(5)
        time.advanceMinutes(5)
        m.reconcile()

        time.reboot(downtimeMillis = 60_000)
        assertNull(manager().reconcile())
        assertNull(store.active())
        assertEquals(listOf(SessionStatus.CANCELLED, SessionStatus.COMPLETED), store.sessions.value.map { it.status })
    }

    @Test
    fun `wall clock changes do not affect a session on the same boot`() = runTest {
        val m = manager()
        val s = m.start(30).session
        time.advanceMinutes(10)
        time.wall += 5 * 60 * min // user sets the clock 5 hours ahead
        assertEquals(20 * min, SessionTiming.remainingMillis(s, time.now()))
        assertEquals(SessionStatus.ACTIVE, m.reconcile()?.status)
        time.wall -= 10 * 60 * min // and then 10 hours back
        assertEquals(20 * min, SessionTiming.remainingMillis(s, time.now()))
    }

    @Test
    fun `reboot mid-session keeps the remaining time and re-anchors to the new boot`() = runTest {
        val m = manager()
        val id = m.start(60).session.id
        time.advanceMinutes(10)
        time.reboot(downtimeMillis = 5 * min, uptimeMillis = min) // 16 minutes elapsed on the wall clock

        val restored = manager().reconcile()!!
        assertEquals(id, restored.id)
        assertEquals(time.boot, restored.anchorBootCount)
        assertEquals(44 * min, SessionTiming.remainingMillis(restored, time.now()))
        assertEquals(restored, effects.activated.last()) // alarm re-armed

        // After re-anchoring, the monotonic clock is used again: wall changes no longer matter.
        time.advanceMinutes(4)
        time.wall += 3 * 60 * min
        assertEquals(40 * min, SessionTiming.remainingMillis(store.byId(id), time.now()))
    }

    @Test
    fun `reboot after the session would have ended finalizes it as completed`() = runTest {
        val id = manager().start(20).session.id
        time.advanceMinutes(5)
        time.reboot(downtimeMillis = 60 * min)
        manager().reconcile()
        assertEquals(SessionStatus.COMPLETED, store.byId(id).status)
        assertEquals(20 * min, store.byId(id).focusedMillis)
        assertEquals(0, effects.feedbackCount)
    }

    @Test
    fun `reboot with clock set backwards never produces negative time`() = runTest {
        val id = manager().start(20).session.id
        time.reboot(downtimeMillis = 0)
        time.wall -= 3 * 60 * min
        val restored = manager().reconcile()!!
        assertEquals(20 * min, SessionTiming.remainingMillis(restored, time.now()))
        manager().cancel()
        assertEquals(0, store.byId(id).focusedMillis)
    }

    @Test
    fun `reboot is detected without a boot counter when the monotonic clock goes backwards`() = runTest {
        time.boot = UNKNOWN_BOOT_COUNT
        manager().start(30)
        time.advanceMinutes(10)
        time.wall += 5 * min
        time.elapsed = 1_000 // rebooted; boot count still unknown
        val restored = manager().reconcile()!!
        assertEquals(15 * min, SessionTiming.remainingMillis(restored, time.now()))
    }

    @Test
    fun `stop pressed after the time ran out completes instead of cancelling`() = runTest {
        val m = manager()
        val id = m.start(5).session.id
        time.advance(5 * min + 200)
        m.cancel()
        assertEquals(SessionStatus.COMPLETED, store.byId(id).status)
        assertEquals(5 * min, store.byId(id).focusedMillis)
    }

    @Test
    fun `cancelled sessions are not counted as completed`() = runTest {
        val m = manager()
        m.start(20)
        time.advanceMinutes(9)
        m.cancel()
        m.start(5)
        time.advanceMinutes(5)
        m.reconcile()

        val (start, end) = time.wall - 60 * min to time.wall + 60 * min
        val stats = DailyStats.compute(store.sessions.value, start, end)
        assertEquals(1, stats.completedCount)
        assertEquals(14 * min, stats.focusedMillis)
    }

    @Test
    fun `dnd is engaged on start and released exactly once on finish`() = runTest {
        val ownership = DndOwnership(InterruptionFilter.ALL, InterruptionFilter.PRIORITY)
        guard.ownership = ownership
        val m = manager()
        val id = m.start(25).session.id
        assertEquals(ownership, store.byId(id).dnd)
        time.advanceMinutes(2)
        m.cancel()
        m.cancel()
        assertEquals(listOf(ownership), guard.released)
    }

    @Test
    fun `dnd state survives process death and is released by the new process`() = runTest {
        val ownership = DndOwnership(InterruptionFilter.ALL, InterruptionFilter.PRIORITY)
        guard.ownership = ownership
        manager().start(5)
        time.advanceMinutes(6)
        manager().reconcile()
        assertEquals(listOf(ownership), guard.released)
    }

    @Test
    fun `dnd ownership is cleared after release so it is never released twice`() = runTest {
        val ownership = DndOwnership(InterruptionFilter.ALL, InterruptionFilter.PRIORITY)
        guard.ownership = ownership
        val m = manager()
        val id = m.start(25).session.id
        m.cancel()
        assertNull(store.byId(id).dnd)
        m.releaseOrphanedDnd()
        assertEquals(listOf(ownership), guard.released)
    }

    @Test
    fun `a release interrupted by process death is completed at next start`() = runTest {
        val ownership = DndOwnership(InterruptionFilter.ALL, InterruptionFilter.PRIORITY)
        val id = store.insertIfNoActive(
            FocusSession(0, 5 * min, time.wall, time.elapsed, time.boot, SessionStatus.ACTIVE, dnd = ownership)
        )!!
        store.finish(id, SessionStatus.COMPLETED, 5 * min, time.wall, completionSeen = true)

        manager().releaseOrphanedDnd()

        assertEquals(listOf(ownership), guard.released)
        assertNull(store.byId(id).dnd)
    }

    @Test
    fun `a failed release keeps ownership so it is retried`() = runTest {
        val ownership = DndOwnership(InterruptionFilter.ALL, InterruptionFilter.PRIORITY)
        val failing = object : DistractionGuard {
            override suspend fun engage(): DndOwnership = ownership
            override suspend fun release(ownership: DndOwnership) = error("revoked")
        }
        val m = SessionManager(store, time, effects, failing, log = { _, _ -> })
        val id = m.start(25).session.id
        m.cancel()
        assertEquals(ownership, store.byId(id).dnd)
    }

    @Test
    fun `failing dnd does not block focusing`() = runTest {
        val failing = object : DistractionGuard {
            override suspend fun engage(): DndOwnership = error("revoked")
            override suspend fun release(ownership: DndOwnership) = error("revoked")
        }
        val m = SessionManager(store, time, effects, failing, log = { _, _ -> })
        assertTrue(m.start(25) is StartResult.Started)
        assertNotNull(m.cancel())
    }

    @Test
    fun `new session dismisses an unseen completion`() = runTest {
        val m = manager()
        val first = m.start(5).session.id
        time.advanceMinutes(5)
        m.reconcile()
        assertFalse(store.byId(first).completionSeen)
        m.start(20)
        assertTrue(store.byId(first).completionSeen)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `durations outside 1 minute to 24 hours are rejected`() = runTest {
        manager().start(24 * 60 + 1)
    }
}
