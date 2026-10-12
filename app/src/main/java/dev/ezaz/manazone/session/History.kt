package dev.ezaz.manazone.session

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One local day of finished sessions. [sessions] lists every session that overlaps the day. */
data class DayRecord(
    val date: LocalDate,
    val focusedMillis: Long,
    val completedCount: Int,
    val sessions: List<FocusSession>,
)

enum class StatsRange(val label: String) {
    WEEK("Last 7 days"),
    MONTH("Last 30 days"),
    LAST_MONTH("Last month"),
    ALL("All time"),
}

/** One chart bar: a single day, or a whole month when [monthly]. */
data class Bar(val start: LocalDate, val focusedMillis: Long, val monthly: Boolean)

data class RangeStats(
    val range: StatsRange,
    val bars: List<Bar>,
    val totalMillis: Long,
    val completedCount: Int,
    val activeDays: Int,
    val averageMillisPerDay: Long,
    val bestDay: DayRecord?,
    /** Days with activity inside the range, newest first. */
    val days: List<DayRecord>,
) {
    companion object {
        val EMPTY = RangeStats(StatsRange.WEEK, emptyList(), 0, 0, 0, 0, null, emptyList())
    }
}

object History {
    /** Days after which "All time" switches from daily to monthly bars. */
    private const val DAILY_BARS_MAX_SPAN_DAYS = 60

    /**
     * Groups finished sessions by local day with the same accounting as [DailyStats]: a session's
     * focused interval is split across the days it overlaps, and a completion counts on the day it ended.
     */
    fun byDay(sessions: List<FocusSession>, zone: ZoneId): Map<LocalDate, DayRecord> {
        val focused = HashMap<LocalDate, Long>()
        val completed = HashMap<LocalDate, Int>()
        val listed = HashMap<LocalDate, MutableList<FocusSession>>()
        fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        for (s in sessions) {
            if (s.status == SessionStatus.ACTIVE || s.focusedMillis <= 0) continue
            val start = s.startWallMillis
            val end = start + s.focusedMillis
            var day = dateOf(start)
            val lastDay = dateOf(end - 1)
            while (!day.isAfter(lastDay)) {
                val (dayStart, dayEnd) = DailyStats.dayBounds(day, zone)
                val overlap = minOf(end, dayEnd) - maxOf(start, dayStart)
                if (overlap > 0) {
                    focused.merge(day, overlap, Long::plus)
                    listed.getOrPut(day) { ArrayList() }.add(s)
                }
                day = day.plusDays(1)
            }
            if (s.status == SessionStatus.COMPLETED) completed.merge(dateOf(end), 1, Int::plus)
        }
        val dates = (focused.keys + completed.keys).toSortedSet()
        return dates.associateWith { d ->
            DayRecord(d, focused[d] ?: 0, completed[d] ?: 0, listed[d].orEmpty().sortedBy { it.startWallMillis })
        }
    }

    fun compute(sessions: List<FocusSession>, range: StatsRange, today: LocalDate, zone: ZoneId): RangeStats =
        compute(byDay(sessions, zone), range, today)

    fun compute(records: Map<LocalDate, DayRecord>, range: StatsRange, today: LocalDate): RangeStats {
        val firstActive = records.keys.minOrNull()
        val (from, to) = when (range) {
            StatsRange.WEEK -> today.minusDays(6) to today
            StatsRange.MONTH -> today.minusDays(29) to today
            StatsRange.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            StatsRange.ALL -> (firstActive ?: today) to today
        }
        val inRange = records.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val total = inRange.sumOf { it.focusedMillis }
        val spanDays = ChronoUnit.DAYS.between(from, to).toInt() + 1

        val monthly = range == StatsRange.ALL && spanDays > DAILY_BARS_MAX_SPAN_DAYS
        val bars = if (monthly) {
            val byMonth = inRange.groupBy { YearMonth.from(it.date) }.mapValues { (_, v) -> v.sumOf { it.focusedMillis } }
            generateSequence(YearMonth.from(from)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(YearMonth.from(to)) }
                .map { Bar(it.atDay(1), byMonth[it] ?: 0, monthly = true) }
                .toList()
        } else {
            (0 until spanDays).map { i ->
                val d = from.plusDays(i.toLong())
                Bar(d, records[d]?.focusedMillis ?: 0, monthly = false)
            }
        }
        return RangeStats(
            range = range,
            bars = if (range == StatsRange.ALL && firstActive == null) emptyList() else bars,
            totalMillis = total,
            completedCount = inRange.sumOf { it.completedCount },
            activeDays = inRange.count { it.focusedMillis > 0 },
            averageMillisPerDay = if (spanDays > 0) total / spanDays else 0,
            bestDay = inRange.filter { it.focusedMillis > 0 }.maxByOrNull { it.focusedMillis },
            days = inRange.sortedByDescending { it.date },
        )
    }
}
