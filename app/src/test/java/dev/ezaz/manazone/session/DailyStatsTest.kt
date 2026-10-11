package dev.ezaz.manazone.session

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DailyStatsTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val min = 60_000L

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun session(start: Long, focusedMin: Long, status: SessionStatus, planned: Long = focusedMin) = FocusSession(
        id = start,
        plannedMillis = planned * min,
        startWallMillis = start,
        anchorElapsedMillis = 0,
        anchorBootCount = 1,
        status = status,
        endWallMillis = start + focusedMin * min,
        focusedMillis = focusedMin * min,
    )

    @Test
    fun `sums actual focused time and counts completed sessions`() {
        val (s, e) = DailyStats.dayBounds(LocalDate.of(2026, 10, 12), zone)
        val stats = DailyStats.compute(
            listOf(
                session(at(2026, 10, 12, 9, 0), 25, SessionStatus.COMPLETED),
                session(at(2026, 10, 12, 10, 0), 9, SessionStatus.CANCELLED, planned = 20),
                session(at(2026, 10, 12, 11, 0), 60, SessionStatus.COMPLETED),
            ),
            s, e,
        )
        assertEquals(94 * min, stats.focusedMillis)
        assertEquals(2, stats.completedCount)
    }

    @Test
    fun `a session crossing midnight is split between both days`() {
        val crossing = session(at(2026, 10, 11, 23, 40), 60, SessionStatus.COMPLETED)
        val (s11, e11) = DailyStats.dayBounds(LocalDate.of(2026, 10, 11), zone)
        val (s12, e12) = DailyStats.dayBounds(LocalDate.of(2026, 10, 12), zone)

        val day11 = DailyStats.compute(listOf(crossing), s11, e11)
        val day12 = DailyStats.compute(listOf(crossing), s12, e12)

        assertEquals(20 * min, day11.focusedMillis)
        assertEquals(0, day11.completedCount)
        assertEquals(40 * min, day12.focusedMillis)
        assertEquals(1, day12.completedCount) // counted on the day it ended
    }

    @Test
    fun `sessions from other days and active sessions are ignored`() {
        val (s, e) = DailyStats.dayBounds(LocalDate.of(2026, 10, 12), zone)
        val active = session(at(2026, 10, 12, 8, 0), 0, SessionStatus.ACTIVE, planned = 25)
            .copy(endWallMillis = null)
        val yesterday = session(at(2026, 10, 11, 9, 0), 25, SessionStatus.COMPLETED)
        assertEquals(DailyStats.EMPTY, DailyStats.compute(listOf(active, yesterday), s, e))
    }

    @Test
    fun `day bounds follow the local calendar including DST days`() {
        val ny = ZoneId.of("America/New_York")
        val (start, end) = DailyStats.dayBounds(LocalDate.of(2026, 11, 1), ny) // DST ends: 25-hour day
        assertEquals(25 * 60 * min, end - start)
        val (s2, e2) = DailyStats.dayBounds(start + 3 * 60 * min, ny)
        assertEquals(start to end, s2 to e2)
    }
}
