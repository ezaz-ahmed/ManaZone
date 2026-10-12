package dev.ezaz.manazone

import android.app.Application
import android.content.Context
import android.util.Log
import dev.ezaz.manazone.alarm.CompletionAlarmScheduler
import dev.ezaz.manazone.alarm.SessionNotification
import dev.ezaz.manazone.core.SystemTimeSource
import dev.ezaz.manazone.core.TimeSource
import dev.ezaz.manazone.data.ManazoneDatabase
import dev.ezaz.manazone.data.RoomSessionStore
import dev.ezaz.manazone.dnd.AndroidDndController
import dev.ezaz.manazone.dnd.DndController
import dev.ezaz.manazone.dnd.DndOwnership
import dev.ezaz.manazone.dnd.DndPolicy
import dev.ezaz.manazone.feedback.CompletionFeedback
import dev.ezaz.manazone.presets.PreferencesRepository
import dev.ezaz.manazone.session.DistractionGuard
import dev.ezaz.manazone.session.FocusSession
import dev.ezaz.manazone.session.SessionEffects
import dev.ezaz.manazone.session.SessionManager
import dev.ezaz.manazone.session.SessionStatus
import dev.ezaz.manazone.widget.FocusWidget
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ManazoneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onAppStart()
    }
}

/** Manual dependency wiring. One instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t -> Log.e(TAG, "Uncaught", t) }
    )
    val time: TimeSource = SystemTimeSource(appContext.contentResolver)
    val database = ManazoneDatabase.create(appContext)
    val sessionStore = RoomSessionStore(database.sessionDao())
    val preferences = PreferencesRepository(appContext)
    val dndController: DndController = AndroidDndController(appContext)
    private val alarms = CompletionAlarmScheduler(appContext)
    private val feedback = CompletionFeedback(appContext)
    private val notification = SessionNotification(appContext)

    private val effects = object : SessionEffects {
        override suspend fun onActive(session: FocusSession) {
            runCatching { alarms.schedule(session) }.onFailure { Log.w(TAG, "Alarm scheduling failed", it) }
            runCatching { notification.show(session) }.onFailure { Log.w(TAG, "Notification failed", it) }
            refreshWidgets()
        }

        override suspend fun onFinished(session: FocusSession, playCompletionFeedback: Boolean) {
            alarms.cancel()
            runCatching { notification.cancel() }
            refreshWidgets()
            if (playCompletionFeedback && session.status == SessionStatus.COMPLETED) {
                val settings = preferences.currentSettings()
                appScope.launch { feedback.play(settings.vibrationEnabled, settings.soundEnabled) }
            }
        }
    }

    private val guard = object : DistractionGuard {
        override suspend fun engage(): DndOwnership? =
            DndPolicy.engage(preferences.currentSettings().dndEnabled, dndController)

        override suspend fun release(ownership: DndOwnership) {
            DndPolicy.release(ownership, dndController)
        }
    }

    val sessionManager = SessionManager(sessionStore, time, effects, guard)

    fun refreshWidgets() {
        appScope.launch {
            runCatching { FocusWidget.refreshAll(appContext) }.onFailure { Log.w(TAG, "Widget update failed", it) }
        }
    }

    fun onAppStart() {
        appScope.launch {
            // Recover after process death or reboot: finalize elapsed sessions, re-anchor live ones,
            // and re-arm the completion alarm (alarms do not survive reboot or force-stop).
            val active = sessionManager.reconcile()
            if (active != null && active.status == SessionStatus.ACTIVE) {
                runCatching { alarms.schedule(active) }
            }
        }
        appScope.launch {
            // Preset edits change what widgets show; keep them in sync.
            preferences.presets.distinctUntilChanged().drop(1).collect { refreshWidgets() }
        }
    }

    private companion object {
        const val TAG = "Manazone"
    }
}
