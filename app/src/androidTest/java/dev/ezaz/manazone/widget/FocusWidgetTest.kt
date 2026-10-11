package dev.ezaz.manazone.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ezaz.manazone.MainActivity
import dev.ezaz.manazone.allSessions
import dev.ezaz.manazone.app
import dev.ezaz.manazone.resetAppState
import dev.ezaz.manazone.shell
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Binds real widget instances through AppWidgetHost (the app acts as its own host after
 * `appwidget grantbind`), drives the configuration activity, and fires the exact intent a widget
 * tap sends. Rendering on a specific launcher still needs a manual check on a device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FocusWidgetTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val host by lazy { AppWidgetHost(app, HOST_ID) }
    private val manager by lazy { AppWidgetManager.getInstance(app) }
    private val allocated = mutableListOf<Int>()

    @Before
    fun setUp() {
        resetAppState()
        val user = shell("am get-current-user").trim()
        shell("appwidget grantbind --package ${app.packageName} --user $user")
    }

    @After
    fun tearDown() {
        allocated.forEach { host.deleteAppWidgetId(it) }
        resetAppState()
    }

    private fun bindWidget(): Int {
        val id = host.allocateAppWidgetId().also { allocated += it }
        assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(app, FocusWidgetReceiver::class.java)))
        return id
    }

    private fun configIntent(id: Int) =
        Intent(app, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    @Test
    fun configuringWidgetsStoresIndependentDurations() {
        val first = bindWidget()
        val second = bindWidget()

        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configIntent(first)).use { s ->
            compose.waitUntilAtLeastOneExists(hasText("25 min"), 5_000)
            compose.onNodeWithText("25 min").performClick()
            assertEquals(Activity.RESULT_OK, s.result.resultCode)
            assertEquals(first, s.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        }
        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configIntent(second)).use { s ->
            compose.waitUntilAtLeastOneExists(hasText("1 h"), 5_000)
            compose.onNodeWithText("1 h").performClick()
            assertEquals(Activity.RESULT_OK, s.result.resultCode)
        }
        runBlocking {
            assertEquals(25, FocusWidget.getMinutes(app, first))
            assertEquals(60, FocusWidget.getMinutes(app, second))
        }

        // Reconfiguring changes only that widget.
        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configIntent(first)).use { s ->
            compose.waitUntilAtLeastOneExists(hasText("Current"), 5_000)
            compose.onNodeWithText("30 min").performClick()
            assertEquals(Activity.RESULT_OK, s.result.resultCode)
        }
        runBlocking {
            assertEquals(30, FocusWidget.getMinutes(app, first))
            assertEquals(60, FocusWidget.getMinutes(app, second))
        }
    }

    @Test
    fun cancellingConfigurationReturnsCanceledAndStoresNothing() {
        val id = bindWidget()
        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configIntent(id)).use { s ->
            compose.waitUntilAtLeastOneExists(hasText("25 min"), 5_000)
            Espresso.pressBackUnconditionally()
            assertEquals(Activity.RESULT_CANCELED, s.result.resultCode)
        }
        runBlocking { assertNull(FocusWidget.getMinutes(app, id)) }
    }

    @Test
    fun widgetTapStartsTheSessionAndRepeatedTapsDoNotDuplicate() {
        ActivityScenario.launch<MainActivity>(FocusWidget.launchIntent(app, 25)).use {
            compose.waitUntilAtLeastOneExists(hasTestTag("countdown"), 5_000)
            compose.onNodeWithText("25 MIN FOCUS").assertExists()

            // A second widget (different duration) tapped while the session runs.
            app.startActivity(FocusWidget.launchIntent(app, 60))
            Thread.sleep(1_500)
            compose.waitUntilAtLeastOneExists(hasTestTag("countdown"), 5_000)
            compose.onNodeWithText("25 MIN FOCUS").assertExists()
            app.startActivity(FocusWidget.launchIntent(app, 25))
            Thread.sleep(1_500)

            val sessions = allSessions()
            assertEquals(1, sessions.size)
            assertEquals(25 * 60_000L, sessions.single().plannedMillis)
            assertEquals("ACTIVE", sessions.single().status)
        }
    }

    private companion object {
        const val HOST_ID = 4242
    }
}
