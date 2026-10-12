package dev.ezaz.manazone.ui.focus

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ezaz.manazone.core.TimeSnapshot
import dev.ezaz.manazone.R
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
    // Ticks only while the screen is visible; on return it re-reads the clock immediately.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by produceState(now(), enabled, lifecycle) {
        if (enabled) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = now()
                    delay(1000 - value.elapsedRealtimeMillis % 1000 + 5)
                }
            }
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
    RotateWithDevice()

    val settings by vm.settings.collectAsStateWithLifecycle()
    val now = rememberTicker(vm::timeNow)
    val remaining = SessionTiming.remainingMillis(session, now)
    val elapsed = remaining == 0L
    LaunchedEffect(session.id, elapsed) {
        if (elapsed) vm.onCountdownElapsed()
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val landscape = maxWidth > maxHeight
        val text = formatCountdown(remaining)

        // On OLED screens, drift the static digits to a new spot once a minute (slowly, so it isn't
        // distracting) to spread pixel wear; on other screens the clock stays centered. Horizontal range
        // stays inside the margin left by the 88% width cap. The offset is read at layout time, so the
        // glide re-lays-out the clock without recomposing it every frame.
        val step = if (settings.oledProtection) (SessionTiming.rawElapsedMillis(session, now) / 60_000).toInt() else 0
        val maxX = if (settings.oledProtection) ((maxWidth * 0.06f) - 2.dp).coerceAtLeast(0.dp) else 0.dp
        val maxY = if (!settings.oledProtection) 0.dp else if (landscape) 24.dp else 56.dp
        val shiftX = animateDpAsState(maxX * ((((step * 37) % 21) - 10) / 10f), tween(3000), label = "shiftX")
        val shiftY = animateDpAsState(maxY * ((((step * 23) % 21) - 10) / 10f), tween(3000), label = "shiftY")

        // Digits are sized from the space available: wide enough for the text, and never taller than
        // about 28% (portrait) or 55% (landscape) of the screen.
        ClockDisplay(
            text = text,
            style = ClockStyle.from(settings.clockStyle),
            color = Color(settings.clockColor),
            weight = settings.clockWeight,
            heightFraction = if (landscape) 0.55f else 0.28f,
            contentModifier = Modifier
                .offset { IntOffset(shiftX.value.roundToPx(), shiftY.value.roundToPx()) }
                .testTag("countdown")
                .semantics { contentDescription = "${formatCountdown(remaining)} remaining" },
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
        Text(stringResource(R.string.focus_complete), style = MaterialTheme.typography.labelMedium, color = Mz.Emerald)
        Spacer(Modifier.height(20.dp))
        Text(
            formatFocused(session.focusedMillis),
            style = DigitStyle.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize * 1.6f, color = Mz.TextPrimary),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.focus_focused), style = MaterialTheme.typography.bodyLarge, color = Mz.TextSecondary)
        Spacer(Modifier.height(48.dp))
        QuietButton(stringResource(R.string.action_done), onClick = onDone, accent = Mz.Emerald)
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

/** The app is portrait-locked (manifest); only the running clock follows the device's rotation. */
@Composable
private fun RotateWithDevice() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
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
