package dev.ezaz.manazone.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ezaz.manazone.R
import dev.ezaz.manazone.presets.compactDuration
import dev.ezaz.manazone.presets.formatDuration
import dev.ezaz.manazone.session.DailyStats
import dev.ezaz.manazone.session.FocusSession
import dev.ezaz.manazone.session.SessionTiming
import dev.ezaz.manazone.ui.MainViewModel
import dev.ezaz.manazone.ui.Route
import dev.ezaz.manazone.ui.focus.rememberTicker
import dev.ezaz.manazone.ui.formatCountdown
import dev.ezaz.manazone.ui.formatFocused
import dev.ezaz.manazone.ui.theme.DigitStyle
import dev.ezaz.manazone.ui.theme.Mz

@Composable
fun HomeScreen(vm: MainViewModel) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    val stats by vm.todayStats.collectAsStateWithLifecycle()
    val active by vm.activeSession.collectAsStateWithLifecycle()
    val completion by vm.unseenCompletion.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight && maxWidth > 560.dp
        val banner: @Composable () -> Unit = {
            SessionBanner(
                active = active,
                completion = completion,
                vm = vm,
            )
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    Header(onStats = { vm.navigate(Route.Stats) }, onSettings = { vm.navigate(Route.Settings) })
                    Spacer(Modifier.weight(1f))
                    Stats(stats)
                    Spacer(Modifier.height(20.dp))
                    banner()
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(32.dp))
                Box(Modifier.weight(1.2f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Presets(presets, dimmed = active != null, onStart = vm::startFocus)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                Header(onStats = { vm.navigate(Route.Stats) }, onSettings = { vm.navigate(Route.Settings) })
                Spacer(Modifier.height(56.dp))
                Stats(stats)
                Spacer(Modifier.height(24.dp))
                banner()
                Spacer(Modifier.height(40.dp))
                Presets(presets, dimmed = active != null, onStart = vm::startFocus)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(onStats: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Wordmark(Modifier.weight(1f))
        IconButton(onClick = onStats, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_stats), contentDescription = "Stats", tint = Mz.TextSecondary)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_tune), contentDescription = "Settings", tint = Mz.TextSecondary)
        }
    }
}

/** App title: a focus-ring mark (a timer ring with its last quarter still to run) and a two-weight name. */
@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { heading(); contentDescription = "Manazone" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawCircle(Mz.Hairline, radius = arcSize.width / 2, style = Stroke(stroke))
            drawArc(
                color = Mz.Emerald,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawCircle(Mz.Emerald, radius = 2.5.dp.toPx())
        }
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Mz.TextPrimary)) { append("mana") }
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraLight, color = Mz.Emerald)) { append("zone") }
            },
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 27.sp, letterSpacing = (-0.8).sp),
        )
    }
}

@Composable
private fun Stats(stats: DailyStats) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text("TODAY", style = MaterialTheme.typography.labelMedium, color = Mz.TextTertiary)
        Spacer(Modifier.height(10.dp))
        Text(
            formatFocused(stats.focusedMillis),
            style = MaterialTheme.typography.displaySmall,
            color = Mz.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (stats.completedCount) {
                1 -> "1 session completed"
                else -> "${stats.completedCount} sessions completed"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Mz.TextSecondary,
        )
    }
}

@Composable
private fun SessionBanner(active: FocusSession?, completion: FocusSession?, vm: MainViewModel) {
    AnimatedVisibility(visible = active != null || completion != null, enter = fadeIn(), exit = fadeOut()) {
        val now = rememberTicker(vm::timeNow, enabled = active != null)
        val (title, action, onClick) = when {
            active != null -> Triple(
                "Focusing · ${formatCountdown(SessionTiming.remainingMillis(active, now))} left",
                "Return",
                { vm.navigate(Route.Focus) },
            )
            completion != null -> Triple(
                "Session complete · ${formatDuration(completion.plannedMinutes)}",
                "Done",
                { vm.finishCompletion() },
            )
            else -> Triple("", "", {})
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = Mz.EmeraldFaint,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Mz.TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(action, color = Mz.Emerald, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Presets(presets: List<Int>, dimmed: Boolean, onStart: (Int) -> Unit) {
    Column {
        Text("FOCUS", style = MaterialTheme.typography.labelMedium, color = Mz.TextTertiary)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.alpha(if (dimmed) 0.45f else 1f),
        ) {
            presets.forEach { minutes -> PresetButton(minutes, onClick = { onStart(minutes) }) }
        }
        if (dimmed) {
            Spacer(Modifier.height(12.dp))
            Text(
                "A session is running. Tapping a duration returns to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mz.TextTertiary,
            )
        }
    }
}

@Composable
private fun PresetButton(minutes: Int, onClick: () -> Unit) {
    val (value, unit) = compactDuration(minutes)
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(CircleShape)
            .border(BorderStroke(1.dp, Mz.Hairline), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Start ${formatDuration(minutes)} focus" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = DigitStyle.copy(fontWeight = FontWeight.Light, fontSize = if (value.length > 3) 24.sp else 30.sp),
                textAlign = TextAlign.Center,
            )
            Text(unit, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = Mz.TextSecondary)
        }
    }
}
