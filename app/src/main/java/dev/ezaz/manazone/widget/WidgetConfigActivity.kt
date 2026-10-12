package dev.ezaz.manazone.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ezaz.manazone.ManazoneApp
import dev.ezaz.manazone.R
import dev.ezaz.manazone.presets.formatDuration
import dev.ezaz.manazone.ui.theme.ManazoneTheme
import dev.ezaz.manazone.ui.theme.Mz
import kotlinx.coroutines.launch

/**
 * Lets the user pick a duration when a widget is added, and again later on launchers that support
 * reconfiguration. Backing out leaves RESULT_CANCELED, which makes the launcher discard a new
 * widget and keeps an existing widget's duration unchanged.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultData = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultData)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val preferences = (application as ManazoneApp).container.preferences
        setContent {
            ManazoneTheme {
                val presets by preferences.presets.collectAsStateWithLifecycle(emptyList())
                var current by remember { mutableStateOf<Int?>(null) }
                var saving by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    current = FocusWidget.getMinutes(this@WidgetConfigActivity, appWidgetId)
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Mz.Black)
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall, color = Mz.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.widget_config_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mz.TextSecondary,
                    )
                    Spacer(Modifier.height(24.dp))
                    presets.forEach { minutes ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .clickable(enabled = !saving, role = Role.Button) {
                                    saving = true
                                    scope.launch {
                                        try {
                                            FocusWidget.setMinutes(this@WidgetConfigActivity, appWidgetId, minutes)
                                            setResult(RESULT_OK, resultData)
                                        } catch (e: Exception) {
                                            Toast.makeText(this@WidgetConfigActivity, getString(R.string.widget_config_save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                        finish()
                                    }
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                formatDuration(minutes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (minutes == current) Mz.Emerald else Mz.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (minutes == current) Text("Current", color = Mz.Emerald, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(color = Mz.Hairline)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.widget_config_footer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mz.TextTertiary,
                    )
                }
            }
        }
    }
}
