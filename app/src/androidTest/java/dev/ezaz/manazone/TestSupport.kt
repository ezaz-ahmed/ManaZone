package dev.ezaz.manazone

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dev.ezaz.manazone.data.toEntity
import dev.ezaz.manazone.session.FocusSession
import dev.ezaz.manazone.session.SessionStatus
import kotlinx.coroutines.runBlocking

val app: ManazoneApp
    get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ManazoneApp

val container: AppContainer get() = app.container

/** Clears sessions and preferences so each test starts from a fresh install state. */
fun resetAppState(): Unit = runBlocking {
    container.database.clearAllTables()
    container.preferences.clear()
    Unit
}

fun shell(command: String): String {
    val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
}

/** Inserts an active session that started [elapsedMinutes] ago, as if the app had been killed. */
fun insertActiveSessionStartedAgo(plannedMinutes: Int, elapsedMinutes: Int): Long = runBlocking {
    val now = container.time.now()
    val ago = elapsedMinutes * 60_000L
    val session = FocusSession(
        id = 0,
        plannedMillis = plannedMinutes * 60_000L,
        startWallMillis = now.wallMillis - ago,
        anchorElapsedMillis = SystemClock.elapsedRealtime() - ago,
        anchorBootCount = now.bootCount,
        status = SessionStatus.ACTIVE,
    )
    checkNotNull(container.database.sessionDao().insertIfNoActive(session.toEntity()))
}

fun allSessions() = runBlocking { container.database.sessionDao().all() }
