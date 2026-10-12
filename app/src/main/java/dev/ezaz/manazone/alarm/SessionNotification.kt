package dev.ezaz.manazone.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.ezaz.manazone.MainActivity
import dev.ezaz.manazone.ManazoneApp
import dev.ezaz.manazone.R
import dev.ezaz.manazone.session.FocusSession
import kotlinx.coroutines.launch

/**
 * Ongoing notification with a system-drawn countdown (no service or per-second updates needed)
 * and a Stop action. Shown while a session is active, so it is there whenever the app is minimized.
 */
class SessionNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(session: FocusSession) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        val remaining = session.anchorElapsedMillis + session.plannedMillis - SystemClock.elapsedRealtime()
        val endWall = System.currentTimeMillis() + remaining.coerceAtLeast(0)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, StopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.widget_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(endWall)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.action_stop), stop)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "focus_session"
        const val NOTIFICATION_ID = 1
    }
}

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ManazoneApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.sessionManager.cancel()
            } finally {
                pending.finish()
            }
        }
    }
}
