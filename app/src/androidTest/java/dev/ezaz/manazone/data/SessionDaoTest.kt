package dev.ezaz.manazone.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionDaoTest {
    private lateinit var db: ManazoneDatabase
    private lateinit var store: RoomSessionStore

    private fun entity(planned: Long = 1_500_000) = SessionEntity(
        plannedMillis = planned,
        startWallMillis = 1_000,
        anchorElapsedMillis = 500,
        anchorBootCount = 3,
        status = "ACTIVE",
        endWallMillis = null,
        focusedMillis = 0,
        completionSeen = false,
        dndPreviousFilter = null,
        dndAppliedFilter = null,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ManazoneDatabase::class.java).build()
        store = RoomSessionStore(db.sessionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun onlyOneActiveSessionCanBeInserted() = runTest {
        val id = db.sessionDao().insertIfNoActive(entity())
        assertNotNull(id)
        assertNull(db.sessionDao().insertIfNoActive(entity()))
        assertEquals(1, db.sessionDao().all().size)
    }

    @Test
    fun finishIsConditionalOnActiveStatus() = runTest {
        val id = db.sessionDao().insertIfNoActive(entity())!!
        assertTrue(store.finish(id, dev.ezaz.manazone.session.SessionStatus.CANCELLED, 540_000, 541_000, true))
        assertFalse(store.finish(id, dev.ezaz.manazone.session.SessionStatus.COMPLETED, 1_500_000, 1_501_000, false))
        val stored = db.sessionDao().all().single()
        assertEquals("CANCELLED", stored.status)
        assertEquals(540_000, stored.focusedMillis)
        assertNull(store.active())
    }

    @Test
    fun flowsReflectActiveCompletionAndDailyQueries() = runTest {
        val id = db.sessionDao().insertIfNoActive(entity())!!
        assertEquals(id, store.observeActive().first()?.id)
        store.finish(id, dev.ezaz.manazone.session.SessionStatus.COMPLETED, 1_500_000, 1_501_000, false)
        assertNull(store.observeActive().first())
        assertEquals(id, store.observeUnseenCompletion().first()?.id)
        assertEquals(1, store.observeFinishedEndingAfter(1_000).first().size)
        assertEquals(0, store.observeFinishedEndingAfter(1_501_000).first().size)
        store.markCompletionsSeen()
        assertNull(store.observeUnseenCompletion().first())
    }

    @Test
    fun dndOwnershipRoundTrips() = runTest {
        val id = db.sessionDao().insertIfNoActive(entity())!!
        store.setDnd(id, dev.ezaz.manazone.dnd.DndOwnership(1, 2))
        assertEquals(dev.ezaz.manazone.dnd.DndOwnership(1, 2), store.active()?.dnd)
    }
}
