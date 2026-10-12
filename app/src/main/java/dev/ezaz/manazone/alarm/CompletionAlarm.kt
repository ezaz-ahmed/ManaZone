package dev.ezaz.manazone.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.ezaz.manazone.ManazoneApp
import dev.ezaz.manazone.session.FocusSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wakes the app at the planned end so a session that finishes in the background is finalized and
 * its cue plays on time. The alarm is only a trigger: correctness comes from persisted timestamps,
 * so a late, lost (reboot) or duplicate alarm is harmless.
 */
class CompletionAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(session: FocusSession) {
        // Elapsed-realtime alarms are immune to wall-clock changes.
        val triggerAt = session.anchorElapsedMillis + session.plannedMillis
        val pending = pendingIntent()
        // Exact alarms need no special access before Android 12.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        } else {
            // Without exact-alarm access the system may deliver this a little late; the UI still
            // finalizes on time while visible, and timestamps keep the record exact either way.
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel() = alarmManager.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CompletionReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class CompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ManazoneApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.sessionManager.reconcile()
                // Keep the process alive until the completion cue ends (bounded by goAsync's ~10s limit).
                withTimeoutOrNull(8_000) { container.awaitFeedback() }
            } finally {
                pending.finish()
            }
        }
    }
}
