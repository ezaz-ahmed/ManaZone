package dev.ezaz.manazone.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class HistoryTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val min = 60_000L
    private val today = LocalDate.of(2026, 10, 12)

    private fun at(d: LocalDate, h: Int, mi: Int) =
        LocalDateTime.of(d, LocalTime.of(h, mi)).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        d: LocalDate,
        h: Int,
        focusedMin: Long,
        status: SessionStatus = SessionStatus.COMPLETED,
        mi: Int = 0,
    ): FocusSession {
        val start = at(d, h, mi)
        return FocusSession(
            id = start, plannedMillis = focusedMin * min, startWallMillis = start, anchorElapsedMillis = 0,
            anchorBootCount = 1, status = status, endWallMillis = start + focusedMin * min, focusedMillis = focusedMin * min,
        )
    }

    @Test
    fun `groups sessions by day and counts completions`() {
        val days = History.byDay(
            listOf(
                session(today, 9, 25),
                session(today, 11, 10, SessionStatus.CANCELLED),
                session(today.minusDays(1), 8, 50),
            ),
            zone,
        )
        assertEquals(35 * min, days.getValue(today).focusedMillis)
        assertEquals(1, days.getValue(today).completedCount)
        assertEquals(2, days.getValue(today).sessions.size)
        assertEquals(50 * min, days.getValue(today.minusDays(1)).focusedMillis)
    }

    @Test
    fun `a session across midnight is split and listed on both days`() {
        val s = session(today.minusDays(1), 23, 60, mi = 30)
        val days = History.byDay(listOf(s), zone)
        assertEquals(30 * min, days.getValue(today.minusDays(1)).focusedMillis)
        assertEquals(30 * min, days.getValue(today).focusedMillis)
        assertEquals(0, days.getValue(today.minusDays(1)).completedCount)
        assertEquals(1, days.getValue(today).completedCount)
    }

    @Test
    fun `ignores active and zero length sessions`() {
        val active = session(today, 9, 25, SessionStatus.ACTIVE)
        val zero = session(today, 10, 0, SessionStatus.CANCELLED)
        assertTrue(History.byDay(listOf(active, zero), zone).isEmpty())
    }

    @Test
    fun `week range has seven daily bars and totals only that window`() {
        val stats = History.compute(
            listOf(session(today, 9, 30), session(today.minusDays(6), 9, 60), session(today.minusDays(7), 9, 120)),
            StatsRange.WEEK, today, zone,
        )
        assertEquals(7, stats.bars.size)
        assertEquals(90 * min, stats.totalMillis)
        assertEquals(2, stats.activeDays)
        assertEquals(today.minusDays(6), stats.bestDay?.date)
        assertEquals(listOf(today, today.minusDays(6)), stats.days.map { it.date })
        assertEquals(90 * min / 7, stats.averageMillisPerDay)
    }

    @Test
    fun `month range has thirty bars`() {
        assertEquals(30, History.compute(emptyList(), StatsRange.MONTH, today, zone).bars.size)
    }

    @Test
    fun `last month covers the previous calendar month`() {
        val stats = History.compute(
            listOf(session(LocalDate.of(2026, 9, 30), 9, 40), session(LocalDate.of(2026, 10, 1), 9, 99)),
            StatsRange.LAST_MONTH, today, zone,
        )
        assertEquals(30, stats.bars.size)
        assertEquals(LocalDate.of(2026, 9, 1), stats.bars.first().start)
        assertEquals(40 * min, stats.totalMillis)
    }

    @Test
    fun `all time uses daily bars for a short history and monthly bars for a long one`() {
        val short = History.compute(listOf(session(today.minusDays(3), 9, 20)), StatsRange.ALL, today, zone)
        assertEquals(4, short.bars.size)
        assertFalse(short.bars.first().monthly)

        val long = History.compute(
            listOf(session(LocalDate.of(2026, 5, 20), 9, 20), session(today, 9, 30)),
            StatsRange.ALL, today, zone,
        )
        assertTrue(long.bars.all { it.monthly })
        assertEquals(6, long.bars.size) // May..Oct
        assertEquals(50 * min, long.totalMillis)
        assertEquals(20 * min, long.bars.first().focusedMillis)
    }

    @Test
    fun `all time with no sessions is empty`() {
        val stats = History.compute(emptyList(), StatsRange.ALL, today, zone)
        assertTrue(stats.bars.isEmpty())
        assertEquals(0, stats.totalMillis)
    }
}
