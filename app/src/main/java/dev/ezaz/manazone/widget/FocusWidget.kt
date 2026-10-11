package dev.ezaz.manazone.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.ezaz.manazone.MainActivity
import dev.ezaz.manazone.ManazoneApp
import dev.ezaz.manazone.R
import dev.ezaz.manazone.presets.compactDuration
import dev.ezaz.manazone.presets.formatDuration
import dev.ezaz.manazone.session.SessionTiming

/**
 * A 1×1 launcher widget bound to one duration. Each widget instance stores its own duration in
 * Glance per-widget state, so several widgets with different durations can coexist. The widget
 * never runs a timer itself; tapping it hands off to [MainActivity], which asks the
 * SessionManager to start (or return to) the single active session.
 */
class FocusWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ManazoneApp).container
        val focusing = runCatching {
            container.sessionStore.active()?.let { !SessionTiming.isElapsed(it, container.time.now()) } ?: false
        }.getOrDefault(false)
        provideContent {
            val minutes = currentState<Preferences>()[MINUTES_KEY]
            WidgetContent(context, minutes, focusing)
        }
    }

    @Composable
    private fun WidgetContent(context: Context, minutes: Int?, focusing: Boolean) {
        val background = if (focusing) R.drawable.widget_background_active else R.drawable.widget_background
        val (value, unit) = minutes?.let(::compactDuration) ?: ("–" to "set")
        val description = when {
            minutes == null -> context.getString(R.string.widget_unconfigured)
            focusing -> context.getString(R.string.widget_focusing_description)
            else -> context.getString(R.string.widget_start_description, formatDuration(minutes))
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(background))
                .padding(4.dp)
                .clickable(actionStartActivity(launchIntent(context, minutes)))
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(if (focusing) Color(0xFFE8F5EE) else EMERALD),
                    fontSize = if (value.length > 3) 18.sp else 24.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
            Text(
                text = unit,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(if (focusing) EMERALD else MUTED), fontSize = 11.sp),
            )
        }
    }

    companion object {
        val MINUTES_KEY = intPreferencesKey("minutes")
        private val EMERALD = Color(0xFF34C48B)
        private val MUTED = Color(0xFF8A8F8C)

        fun launchIntent(context: Context, minutes: Int?): Intent =
            Intent(context, MainActivity::class.java).apply {
                if (minutes != null) {
                    action = MainActivity.ACTION_START_FOCUS
                    putExtra(MainActivity.EXTRA_MINUTES, minutes)
                    // Unique data keeps PendingIntents for different durations distinct.
                    data = "manazone://focus/$minutes".toUri()
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        suspend fun refreshAll(context: Context) = FocusWidget().updateAll(context)

        suspend fun setMinutes(context: Context, appWidgetId: Int, minutes: Int) {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[MINUTES_KEY] = minutes }
            }
            FocusWidget().update(context, glanceId)
        }

        suspend fun getMinutes(context: Context, appWidgetId: Int): Int? = runCatching {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            androidx.glance.appwidget.state.getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[MINUTES_KEY]
        }.getOrNull()
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}
