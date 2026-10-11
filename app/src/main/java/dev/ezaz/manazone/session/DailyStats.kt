package dev.ezaz.manazone.session

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyStats(val focusedMillis: Long, val completedCount: Int) {
    companion object {
        val EMPTY = DailyStats(0, 0)

        /**
         * Totals for the local day [dayStartMillis, dayEndMillis). A session's focused interval is
         * `[startWallMillis, startWallMillis + focusedMillis]`; only the part overlapping the day
         * counts, so a session crossing midnight is split between both days. A completed session is
         * counted on the day it ended. Cancelled sessions contribute time but never a completion.
         */
        fun compute(sessions: List<FocusSession>, dayStartMillis: Long, dayEndMillis: Long): DailyStats {
            var focused = 0L
            var completed = 0
            for (s in sessions) {
                if (s.status == SessionStatus.ACTIVE) continue
                val start = s.startWallMillis
                val end = s.startWallMillis + s.focusedMillis
                val overlap = minOf(end, dayEndMillis) - maxOf(start, dayStartMillis)
                if (overlap > 0) focused += overlap
                if (s.status == SessionStatus.COMPLETED && end in dayStartMillis until dayEndMillis) completed++
            }
            return DailyStats(focused, completed)
        }

        fun dayBounds(wallMillis: Long, zone: ZoneId): Pair<Long, Long> =
            dayBounds(Instant.ofEpochMilli(wallMillis).atZone(zone).toLocalDate(), zone)

        fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
            date.atStartOfDay(zone).toInstant().toEpochMilli() to
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
