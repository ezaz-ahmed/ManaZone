package dev.ezaz.manazone.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.ezaz.manazone.MainActivity
import dev.ezaz.manazone.ManazoneApp
import dev.ezaz.manazone.R
import dev.ezaz.manazone.presets.compactDuration
import dev.ezaz.manazone.presets.formatDuration
import dev.ezaz.manazone.session.SessionTiming
import kotlinx.coroutines.launch

/**
 * A 1×1 launcher widget bound to one duration, drawn with plain RemoteViews (no Glance, no
 * WorkManager) so it costs no background machinery. Each widget instance stores its own duration, so
 * several widgets with different durations can coexist. The widget never runs a timer itself;
 * tapping it hands off to [MainActivity], which asks the SessionManager to start (or return to) the
 * single active session.
 */
object FocusWidget {
    private const val PREFS = "focus_widgets"
    private const val EMERALD = 0xFF34C48B.toInt()
    private const val FOCUSING_TEXT = 0xFFE8F5EE.toInt()
    private const val MUTED = 0xFF8A8F8C.toInt()

    fun launchIntent(context: Context, minutes: Int?, token: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            if (minutes != null) {
                action = MainActivity.ACTION_START_FOCUS
                putExtra(MainActivity.EXTRA_MINUTES, minutes)
                putExtra(MainActivity.EXTRA_TOKEN, token)
                // Unique data keeps PendingIntents for different durations distinct.
                data = "manazone://focus/$minutes".toUri()
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    fun getMinutes(context: Context, appWidgetId: Int): Int? =
        prefs(context).takeIf { it.contains(key(appWidgetId)) }?.getInt(key(appWidgetId), 0)

    suspend fun setMinutes(context: Context, appWidgetId: Int, minutes: Int) {
        prefs(context).edit(commit = true) { putInt(key(appWidgetId), minutes) }
        update(context, intArrayOf(appWidgetId))
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit { appWidgetIds.forEach { remove(key(it)) } }
    }

    suspend fun refreshAll(context: Context) {
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, FocusWidgetReceiver::class.java))
        if (ids.isNotEmpty()) update(context, ids)
    }

    suspend fun update(context: Context, appWidgetIds: IntArray) {
        val container = (context.applicationContext as ManazoneApp).container
        val token = container.preferences.startToken()
        val focusing = runCatching {
            container.sessionStore.active()?.let { !SessionTiming.isElapsed(it, container.time.now()) } ?: false
        }.getOrDefault(false)
        val manager = AppWidgetManager.getInstance(context)
        for (id in appWidgetIds) manager.updateAppWidget(id, views(context, id, getMinutes(context, id), focusing, token))
    }

    private fun views(context: Context, id: Int, minutes: Int?, focusing: Boolean, token: String): RemoteViews {
        val (value, unit) = minutes?.let(::compactDuration) ?: (context.getString(R.string.widget_unset_value) to context.getString(R.string.widget_unset_unit))
        val description = when {
            minutes == null -> context.getString(R.string.widget_unconfigured)
            focusing -> context.getString(R.string.widget_focusing_description)
            else -> context.getString(R.string.widget_start_description, formatDuration(minutes))
        }
        val tap = PendingIntent.getActivity(
            context, id, launchIntent(context, minutes, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteViews(context.packageName, R.layout.widget_view).apply {
            setInt(R.id.widget_root, "setBackgroundResource", if (focusing) R.drawable.widget_background_active else R.drawable.widget_background)
            setTextViewText(R.id.widget_value, value)
            setTextViewTextSize(R.id.widget_value, TypedValue.COMPLEX_UNIT_SP, if (value.length > 3) 18f else 24f)
            setTextColor(R.id.widget_value, if (focusing) FOCUSING_TEXT else EMERALD)
            setTextViewText(R.id.widget_unit, unit)
            setTextColor(R.id.widget_unit, if (focusing) EMERALD else MUTED)
            setContentDescription(R.id.widget_root, description)
            setOnClickPendingIntent(R.id.widget_root, tap)
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(appWidgetId: Int) = "minutes_$appWidgetId"
}

class FocusWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        (context.applicationContext as ManazoneApp).container.appScope.launch {
            try {
                FocusWidget.update(context, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = FocusWidget.forget(context, appWidgetIds)
}
