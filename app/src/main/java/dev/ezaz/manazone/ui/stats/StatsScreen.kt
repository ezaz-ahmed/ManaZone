package dev.ezaz.manazone.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ezaz.manazone.R
import dev.ezaz.manazone.session.Bar
import dev.ezaz.manazone.session.DayRecord
import dev.ezaz.manazone.session.FocusSession
import dev.ezaz.manazone.session.SessionStatus
import dev.ezaz.manazone.session.StatsRange
import dev.ezaz.manazone.ui.MainViewModel
import dev.ezaz.manazone.ui.Route
import dev.ezaz.manazone.ui.formatFocused
import dev.ezaz.manazone.ui.theme.Mz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil

@Composable
fun StatsScreen(vm: MainViewModel) {
    val range by vm.statsRange.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(listOf<Long>()) }

    BackHandler { vm.navigate(Route.Home) }

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.navigate(Route.Home) }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Mz.TextSecondary)
                }
                Text("Stats", style = MaterialTheme.typography.headlineSmall, color = Mz.TextPrimary)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsRange.entries.forEach { option ->
                    val selected = option == range
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .border(if (selected) 2.dp else 1.dp, if (selected) Mz.Emerald else Mz.Hairline, RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton, onClickLabel = "Show ${option.label}") { vm.selectStatsRange(option) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option.label, style = MaterialTheme.typography.bodyMedium, color = if (selected) Mz.Emerald else Mz.TextSecondary)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 8.dp).semantics(mergeDescendants = true) {}) {
                Text(formatFocused(stats.totalMillis), style = MaterialTheme.typography.displaySmall, color = Mz.TextPrimary)
                Text("focused · ${range.label.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = Mz.TextSecondary)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Metric("Sessions", stats.completedCount.toString(), Modifier.weight(1f))
                Metric("Active days", stats.activeDays.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Metric("Daily average", formatFocused(stats.averageMillisPerDay), Modifier.weight(1f))
                Metric(
                    "Best day",
                    stats.bestDay?.let { "${formatFocused(it.focusedMillis)} · ${shortDate(it.date)}" } ?: "–",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            if (stats.bars.isEmpty()) {
                Text("Nothing to chart yet. Finish a session and it shows up here.", style = MaterialTheme.typography.bodyMedium, color = Mz.TextTertiary, modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                BarChart(stats.bars, stats.range, Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "DAYS",
                style = MaterialTheme.typography.labelMedium,
                color = Mz.TextTertiary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            if (stats.days.isEmpty()) {
                Text("No sessions in this period.", style = MaterialTheme.typography.bodyMedium, color = Mz.TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        items(stats.days, key = { it.date.toEpochDay() }) { day ->
            val open = day.date.toEpochDay() in expanded
            DayRow(
                day = day,
                open = open,
                onToggle = {
                    val id = day.date.toEpochDay()
                    expanded = if (open) expanded - id else expanded + id
                },
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Mz.TextTertiary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Mz.TextPrimary)
    }
}

@Composable
private fun DayRow(day: DayRecord, open: Boolean, onToggle: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val label = dayLabel(day.date)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide sessions for $label" else "Show sessions for $label", onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = Mz.TextPrimary)
                Text(
                    "${day.sessions.size} ${if (day.sessions.size == 1) "session" else "sessions"}" +
                        if (day.completedCount > 0) " · ${day.completedCount} completed" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mz.TextSecondary,
                )
            }
            Text(formatFocused(day.focusedMillis), style = MaterialTheme.typography.bodyLarge, color = Mz.Emerald)
        }
        if (open) {
            Column(Modifier.padding(start = 20.dp, end = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                day.sessions.forEach { SessionLine(it, zone) }
            }
        }
        HorizontalDivider(color = Mz.Hairline, thickness = 1.dp)
    }
}

@Composable
private fun SessionLine(session: FocusSession, zone: ZoneId) {
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val start = Instant.ofEpochMilli(session.startWallMillis).atZone(zone)
    val end = Instant.ofEpochMilli(session.startWallMillis + session.focusedMillis).atZone(zone)
    val outcome = when (session.status) {
        SessionStatus.COMPLETED -> "Completed · ${formatFocused(session.plannedMillis)}"
        else -> "Stopped · ${formatFocused(session.focusedMillis)} of ${formatFocused(session.plannedMillis)}"
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${timeFormat.format(start)} – ${timeFormat.format(end)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Mz.TextPrimary,
            modifier = Modifier.width(150.dp),
        )
        Text(outcome, style = MaterialTheme.typography.bodyMedium, color = Mz.TextSecondary)
    }
}

/** Bars scale to the tallest value. Tap a bar to read its exact value; by default the peak is shown. */
@Composable
private fun BarChart(bars: List<Bar>, range: StatsRange, modifier: Modifier = Modifier) {
    var selectedStart by rememberSaveable(range) { mutableStateOf<Long?>(null) }
    val peak = bars.maxOf { it.focusedMillis }
    val selected = bars.indexOfFirst { it.start.toEpochDay() == selectedStart }.takeIf { it >= 0 }
        ?: bars.indexOfFirst { it.focusedMillis == peak }
    val readout = bars[selected]
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Mz.TextTertiary)
    val stride = ceil(bars.size / 7f).toInt().coerceAtLeast(1)

    Column(modifier) {
        Text(
            "${barCaption(readout)} · ${formatFocused(readout.focusedMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Mz.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .semantics { contentDescription = "Bar chart of focused time. Busiest: ${barCaption(bars[bars.indexOfFirst { it.focusedMillis == peak }])}, ${formatFocused(peak)}." }
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val i = (offset.x / (size.width / bars.size)).toInt().coerceIn(0, bars.lastIndex)
                        selectedStart = bars[i].start.toEpochDay()
                    }
                },
        ) {
            val labelHeight = 18.dp.toPx()
            val chartHeight = size.height - labelHeight
            val slot = size.width / bars.size
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val corner = CornerRadius(minOf(barWidth / 2, 6.dp.toPx()))
            drawLine(Mz.Hairline, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val x = slot * i + (slot - barWidth) / 2
                val h = if (peak > 0) (chartHeight - 4.dp.toPx()) * bar.focusedMillis / peak else 0f
                val color: Color = when {
                    bar.focusedMillis == 0L -> Mz.Hairline
                    i == selected -> Mz.Emerald
                    else -> Mz.EmeraldDim
                }
                val barHeight = h.coerceAtLeast(3.dp.toPx())
                drawRoundRect(color, Offset(x, chartHeight - barHeight), Size(barWidth, barHeight), corner)
                if (i % stride == 0) {
                    val text = measurer.measure(axisLabel(bar, range), labelStyle)
                    drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, chartHeight + 4.dp.toPx()))
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")
private val shortFormat = DateTimeFormatter.ofPattern("d MMM")
private val weekdayFormat = DateTimeFormatter.ofPattern("EEE")
private val monthFormat = DateTimeFormatter.ofPattern("MMM")
private val monthYearFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

private fun shortDate(date: LocalDate): String = shortFormat.format(date)

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFormat.format(date)
    }
}

private fun barCaption(bar: Bar): String = if (bar.monthly) monthYearFormat.format(bar.start) else dayFormat.format(bar.start)

private fun axisLabel(bar: Bar, range: StatsRange): String = when {
    bar.monthly -> monthFormat.format(bar.start)
    range == StatsRange.WEEK -> weekdayFormat.format(bar.start)
    else -> bar.start.dayOfMonth.toString()
}
