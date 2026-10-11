package dev.ezaz.manazone.ui.focus

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ezaz.manazone.core.TimeSnapshot
import dev.ezaz.manazone.presets.formatDuration
import dev.ezaz.manazone.session.FocusSession
import dev.ezaz.manazone.session.SessionTiming
import dev.ezaz.manazone.ui.MainViewModel
import dev.ezaz.manazone.ui.QuietButton
import dev.ezaz.manazone.ui.Route
import dev.ezaz.manazone.ui.formatCountdown
import dev.ezaz.manazone.ui.formatFocused
import dev.ezaz.manazone.ui.theme.DigitStyle
import dev.ezaz.manazone.ui.theme.Mz
import kotlinx.coroutines.delay

/**
 * Re-reads the clock once per second, aligned to the second boundary. This only drives redraws;
 * the displayed value is always computed from persisted timestamps, so pausing or losing this loop
 * (background, call, rotation, process death) never affects accuracy.
 */
@Composable
fun rememberTicker(now: () -> TimeSnapshot, enabled: Boolean = true): TimeSnapshot {
    val state by produceState(now(), enabled) {
        while (enabled) {
            value = now()
            delay(1000 - value.elapsedRealtimeMillis % 1000 + 5)
        }
        value = now()
    }
    return state
}

@Composable
fun FocusScreen(vm: MainViewModel) {
    val active by vm.activeSession.collectAsStateWithLifecycle()
    val completion by vm.unseenCompletion.collectAsStateWithLifecycle()

    BackHandler { vm.leaveFocus() }

    // Neither an active session nor a completion to show (e.g. finished elsewhere): go home.
    val nothingToShow = active == null && completion == null
    LaunchedEffect(nothingToShow) {
        if (nothingToShow) {
            delay(800) // let Room's separate flows settle across the active -> completed transition
            vm.navigate(Route.Home)
        }
    }

    Box(Modifier.fillMaxSize().background(Mz.Black)) {
        AnimatedContent(
            targetState = active?.let { FocusContent.Running(it) } ?: completion?.let { FocusContent.Done(it) },
            contentKey = { it?.javaClass },
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) },
            label = "focus",
        ) { content ->
            when (content) {
                is FocusContent.Running -> Countdown(content.session, vm)
                is FocusContent.Done -> Completed(content.session, onDone = vm::finishCompletion)
                null -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

private sealed interface FocusContent {
    data class Running(val session: FocusSession) : FocusContent
    data class Done(val session: FocusSession) : FocusContent
}

@Composable
private fun Countdown(session: FocusSession, vm: MainViewModel) {
    KeepScreenOn()
    ImmersiveSystemBars()

    val now = rememberTicker(vm::timeNow)
    val remaining = SessionTiming.remainingMillis(session, now)
    val progress = SessionTiming.progress(session, now)
    val elapsed = remaining == 0L
    LaunchedEffect(session.id, elapsed) {
        if (elapsed) vm.onCountdownElapsed()
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight
        val text = formatCountdown(remaining)
        // Size digits from the space available: wide enough for the text, and never taller than
        // about half (portrait) or 60% (landscape) of the screen.
        val charWidthEm = 0.56f
        val byWidth = maxWidth * 0.88f / (text.length * charWidthEm)
        val byHeight = maxHeight * (if (landscape) 0.55f else 0.28f)
        val digitSize: Dp = min(byWidth, byHeight)
        val fontSize = with(LocalDensity.current) { digitSize.toSp() }

        // Shift the static digits by a few dp each minute to spread OLED wear.
        val minute = (SessionTiming.rawElapsedMillis(session, now) / 60_000).toInt()
        val shiftX = (((minute * 7) % 9) - 4).dp
        val shiftY = (((minute * 5) % 9) - 4).dp

        Column(
            Modifier.fillMaxSize().padding(vertical = if (landscape) 12.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${formatDuration(session.plannedMinutes)} focus".uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Mz.TextTertiary,
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.offset(shiftX, shiftY),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text,
                    style = DigitStyle.copy(fontSize = fontSize, lineHeight = fontSize),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("countdown")
                        .semantics { contentDescription = "${formatCountdown(remaining)} remaining" },
                )
                Spacer(Modifier.height(if (landscape) 12.dp else 20.dp))
                ProgressHairline(progress, Modifier.width(digitSize * text.length * charWidthEm * 0.8f))
            }
            Spacer(Modifier.weight(1f))
            QuietButton("Stop", onClick = vm::stop)
        }
    }
}

@Composable
private fun ProgressHairline(progress: Float, modifier: Modifier) {
    Box(modifier.height(2.dp).background(Mz.Hairline)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Mz.EmeraldDim)
        )
    }
}

@Composable
private fun Completed(session: FocusSession, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("COMPLETE", style = MaterialTheme.typography.labelMedium, color = Mz.Emerald)
        Spacer(Modifier.height(20.dp))
        Text(
            formatFocused(session.focusedMillis),
            style = DigitStyle.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize * 1.6f, color = Mz.TextPrimary),
        )
        Spacer(Modifier.height(8.dp))
        Text("focused", style = MaterialTheme.typography.bodyLarge, color = Mz.TextSecondary)
        Spacer(Modifier.height(48.dp))
        QuietButton("Done", onClick = onDone, accent = Mz.Emerald)
    }
}

/** Keeps the display awake only while this composable (the running countdown) is on screen. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Hides system bars during focus; a swipe from the edge reveals them temporarily. */
@Composable
private fun ImmersiveSystemBars() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
