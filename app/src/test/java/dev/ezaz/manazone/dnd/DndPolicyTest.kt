package dev.ezaz.manazone.dnd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DndPolicyTest {
    private class FakeDnd(var access: Boolean = true, var state: Int = InterruptionFilter.ALL) : DndController {
        var sets = 0
        override fun hasAccess() = access
        override fun currentFilter() = state
        override fun setFilter(filter: Int) {
            check(access) { "SecurityException" }
            sets++
            state = filter
        }
    }

    @Test
    fun `granted access turns priority mode on and restores it`() {
        val dnd = FakeDnd()
        val ownership = DndPolicy.engage(enabled = true, controller = dnd)
        assertEquals(DndOwnership(InterruptionFilter.ALL, InterruptionFilter.PRIORITY), ownership)
        assertEquals(InterruptionFilter.PRIORITY, dnd.state)
        assertTrue(DndPolicy.release(ownership!!, dnd))
        assertEquals(InterruptionFilter.ALL, dnd.state)
    }

    @Test
    fun `feature disabled or access denied changes nothing`() {
        val dnd = FakeDnd(access = false)
        assertNull(DndPolicy.engage(enabled = true, controller = dnd))
        assertNull(DndPolicy.engage(enabled = false, controller = FakeDnd()))
        assertEquals(0, dnd.sets)
    }

    @Test
    fun `dnd already on is left under the user's control`() {
        val dnd = FakeDnd(state = InterruptionFilter.NONE)
        assertNull(DndPolicy.engage(enabled = true, controller = dnd))
        assertEquals(InterruptionFilter.NONE, dnd.state)
        assertEquals(0, dnd.sets)
    }

    @Test
    fun `a later change by the user is not overwritten`() {
        val dnd = FakeDnd()
        val ownership = DndPolicy.engage(true, dnd)!!
        dnd.state = InterruptionFilter.ALARMS // user or another app changed it mid-session
        assertFalse(DndPolicy.release(ownership, dnd))
        assertEquals(InterruptionFilter.ALARMS, dnd.state)
    }

    @Test
    fun `revoked access during a session fails safely`() {
        val dnd = FakeDnd()
        val ownership = DndPolicy.engage(true, dnd)!!
        dnd.access = false
        assertFalse(DndPolicy.release(ownership, dnd))
        assertEquals(InterruptionFilter.PRIORITY, dnd.state)
    }
}
