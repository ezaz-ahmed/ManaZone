package dev.ezaz.manazone.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ezaz.manazone.AppContainer
import dev.ezaz.manazone.ManazoneApp
import dev.ezaz.manazone.presets.PresetEdit
import dev.ezaz.manazone.presets.UserSettings
import dev.ezaz.manazone.session.DailyStats
import dev.ezaz.manazone.session.FocusSession
import dev.ezaz.manazone.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class Route { Home, Focus, Settings }

class MainViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val manager: SessionManager = container.sessionManager

    private val _route = MutableStateFlow(savedState.get<String>(KEY_ROUTE)?.let(Route::valueOf) ?: Route.Home)
    val route: StateFlow<Route> = _route.asStateFlow()

    /** False until the initial route has been decided, so the app never flashes the wrong screen. */
    private val _ready = MutableStateFlow(savedState.contains(KEY_ROUTE))
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private var routeChosenExplicitly = savedState.contains(KEY_ROUTE)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _dndAccess = MutableStateFlow(container.dndController.hasAccess())
    val dndAccess: StateFlow<Boolean> = _dndAccess.asStateFlow()
    private var enableDndWhenGranted = false

    val activeSession: StateFlow<FocusSession?> =
        manager.activeSession.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val unseenCompletion: StateFlow<FocusSession?> =
        manager.unseenCompletion.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val presets: StateFlow<List<Int>> =
        container.preferences.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<UserSettings> =
        container.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /** Today's totals; the day window is re-evaluated periodically so midnight rolls over. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayStats: StateFlow<DailyStats> = flow {
        while (true) {
            emit(DailyStats.dayBounds(manager.now().wallMillis, ZoneId.systemDefault()))
            delay(30_000)
        }
    }.distinctUntilChanged().flatMapLatest { (start, end) ->
        container.sessionStore.observeFinishedEndingAfter(start).map { DailyStats.compute(it, start, end) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyStats.EMPTY)

    fun timeNow() = manager.now()

    init {
        if (!routeChosenExplicitly) {
            viewModelScope.launch {
                safely {
                    manager.reconcile()
                    val resume = container.sessionStore.active() != null ||
                        manager.unseenCompletion.first() != null
                    if (!routeChosenExplicitly && resume) navigate(Route.Focus)
                }
                _ready.value = true
            }
        }
    }

    fun navigate(route: Route) {
        routeChosenExplicitly = true
        savedState[KEY_ROUTE] = route.name
        _route.value = route
    }

    /** Starts a session, or returns to the one already running. Never creates a second one. */
    fun startFocus(minutes: Int) {
        routeChosenExplicitly = true
        viewModelScope.launch {
            safely {
                manager.start(minutes)
                navigate(Route.Focus)
            }
            _ready.value = true
        }
    }

    /** Stop/Exit: immediate, no confirmation. */
    fun stop() {
        navigate(Route.Home)
        container.appScope.launch { safely { manager.cancel() } }
    }

    fun finishCompletion() {
        navigate(Route.Home)
        viewModelScope.launch { safely { manager.acknowledgeCompletion() } }
    }

    /** Leaving the focus screen without Stop keeps the session running. */
    fun leaveFocus() {
        if (activeSession.value == null && unseenCompletion.value != null) finishCompletion()
        else navigate(Route.Home)
    }

    /** Called by the countdown when it reaches zero; finalization is idempotent. */
    fun onCountdownElapsed() {
        container.appScope.launch { safely { manager.reconcile() } }
    }

    fun onForeground() {
        container.appScope.launch { safely { manager.reconcile() } }
        refreshDndAccess()
    }

    fun updatePresets(edit: PresetEdit): String? = when (edit) {
        is PresetEdit.Rejected -> edit.message
        is PresetEdit.Ok -> {
            viewModelScope.launch { safely { container.preferences.setPresets(edit.presets) } }
            null
        }
    }

    fun setVibration(enabled: Boolean) = viewModelScope.launch { safely { container.preferences.setVibration(enabled) } }
    fun setSound(enabled: Boolean) = viewModelScope.launch { safely { container.preferences.setSound(enabled) } }

    /** Returns true if the caller must first explain and open the system access screen. */
    fun requestDnd(enabled: Boolean): Boolean {
        if (enabled && !container.dndController.hasAccess()) {
            enableDndWhenGranted = true
            return true
        }
        enableDndWhenGranted = false
        viewModelScope.launch { safely { container.preferences.setDnd(enabled) } }
        return false
    }

    fun cancelDndRequest() {
        enableDndWhenGranted = false
    }

    private fun refreshDndAccess() {
        val granted = container.dndController.hasAccess()
        _dndAccess.value = granted
        if (granted && enableDndWhenGranted) {
            enableDndWhenGranted = false
            viewModelScope.launch { safely { container.preferences.setDnd(true) } }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed", e)
            _message.value = "Something went wrong. Please try again."
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val KEY_ROUTE = "route"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ManazoneApp
                MainViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}
