package dev.ezaz.manazone.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ezaz.manazone.ui.focus.FocusScreen
import dev.ezaz.manazone.ui.home.HomeScreen
import dev.ezaz.manazone.ui.settings.SettingsScreen
import dev.ezaz.manazone.ui.stats.StatsScreen
import dev.ezaz.manazone.ui.theme.Mz

@Composable
fun ManazoneRoot(vm: MainViewModel) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Returning to the app (after a call, another app, or the lock screen) re-syncs with the clocks.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(Mz.Black)) {
        if (ready) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
                label = "route",
            ) { target ->
                when (target) {
                    Route.Home -> HomeScreen(vm)
                    Route.Focus -> FocusScreen(vm)
                    Route.Settings -> SettingsScreen(vm)
                    Route.Stats -> StatsScreen(vm)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            Snackbar(it, containerColor = Mz.Surface, contentColor = Mz.TextPrimary)
        }
    }
}
