package dev.ezaz.manazone.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ezaz.manazone.MainActivity
import dev.ezaz.manazone.allSessions
import dev.ezaz.manazone.insertActiveSessionStartedAgo
import dev.ezaz.manazone.presets.DEFAULT_PRESETS
import dev.ezaz.manazone.presets.formatDuration
import dev.ezaz.manazone.resetAppState
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ManazoneUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = resetAppState()

    @After
    fun tearDown() {
        scenario?.close()
        resetAppState()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntilAtLeastOneExists(matcher, 5_000)

    private fun startPreset(minutes: Int) {
        val label = "Start ${formatDuration(minutes)} focus"
        waitFor(hasContentDescription(label))
        compose.onNodeWithContentDescription(label).performClick()
        waitFor(hasTestTag("countdown"))
    }

    private fun waitForHome() = waitFor(hasText("TODAY"))

    @Test
    fun homeShowsBrandingStatsPresetsAndSettings() {
        launch()
        waitForHome()
        compose.onNodeWithText("manazone").assertIsDisplayed()
        compose.onNodeWithText("0 min").assertIsDisplayed()
        compose.onNodeWithText("0 sessions completed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        DEFAULT_PRESETS.forEach {
            compose.onNodeWithContentDescription("Start ${formatDuration(it)} focus").assertExists()
        }
    }

    @Test
    fun eachDefaultDurationStartsAndStopsImmediately() {
        launch()
        for (minutes in DEFAULT_PRESETS) {
            startPreset(minutes)
            val expected = if (minutes >= 60) "1:00:00" else "%02d:00".format(minutes)
            val previous = if (minutes >= 60) "59:59" else "%02d:59".format(minutes - 1)
            compose.waitUntil(3_000) {
                compose.onAllNodes(hasText(expected) or hasText(previous)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Stop").performClick()
            // No confirmation dialog: the home screen comes straight back.
            waitForHome()
            compose.onNodeWithText("Stop").assertDoesNotExist()
        }
        compose.waitUntil(3_000) { allSessions().all { it.status == "CANCELLED" } }
        assertEquals(DEFAULT_PRESETS.size, allSessions().size)
        assertEquals(DEFAULT_PRESETS.map { it * 60_000L }, allSessions().map { it.plannedMillis })
    }

    @Test
    fun stopAfterNineMinutesRecordsNineMinutes() {
        insertActiveSessionStartedAgo(plannedMinutes = 20, elapsedMinutes = 9)
        launch()
        // Relaunching with a live session goes straight to the countdown.
        waitFor(hasTestTag("countdown"))
        compose.onNodeWithText("Stop").performClick()
        waitForHome()
        waitFor(hasText("9 min"))
        compose.onNodeWithText("0 sessions completed").assertIsDisplayed()
        val session = allSessions().single()
        assertEquals("CANCELLED", session.status)
        assertEquals(9L, session.focusedMillis / 60_000)
    }

    @Test
    fun sessionThatEndedWhileAwayShowsCompletionOnce() {
        insertActiveSessionStartedAgo(plannedMinutes = 25, elapsedMinutes = 26)
        launch()
        waitFor(hasText("COMPLETE"))
        compose.onNodeWithText("25 min").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        waitForHome()
        waitFor(hasText("1 session completed"))
        compose.onNodeWithText("25 min").assertIsDisplayed()
        val session = allSessions().single()
        assertEquals("COMPLETED", session.status)
        assertEquals(25 * 60_000L, session.focusedMillis)

        // Recreating the activity must not bring the completion back.
        scenario!!.recreate()
        waitForHome()
        compose.onNodeWithText("COMPLETE").assertDoesNotExist()
    }

    @Test
    fun backgroundInterruptionDoesNotCancelOrPause() {
        launch()
        startPreset(25)
        val id = allSessions().single().id
        // Simulates a call or app switch covering Manazone.
        scenario!!.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(2_500)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        waitFor(hasTestTag("countdown"))
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("24:5", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val session = allSessions().single()
        assertEquals(id, session.id)
        assertEquals("ACTIVE", session.status)
    }

    @Test
    fun recreationAndRotationKeepTheSameSession() {
        launch()
        startPreset(30)
        scenario!!.recreate()
        waitFor(hasTestTag("countdown"))
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(1_500)
        waitFor(hasTestTag("countdown"))
        compose.onNodeWithTag("countdown").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertIsDisplayed()
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        Thread.sleep(1_500)
        waitFor(hasTestTag("countdown"))
        assertEquals(1, allSessions().size)
        assertEquals("ACTIVE", allSessions().single().status)
    }

    @Test
    fun backLeavesSessionRunningAndHomeReturnsToIt() {
        launch()
        startPreset(20)
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor(hasText("Return"))
        assertEquals("ACTIVE", allSessions().single().status)
        // Tapping any preset returns to the running session instead of starting another.
        compose.onNodeWithContentDescription("Start 5 min focus").performClick()
        waitFor(hasTestTag("countdown"))
        compose.onNodeWithText("20 MIN FOCUS").assertIsDisplayed()
        assertEquals(1, allSessions().size)
    }

    @Test
    fun addEditReorderAndRemovePresets() {
        launch()
        waitForHome()
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor(hasText("Add duration"))

        compose.onNodeWithText("Add duration").performClick()
        compose.onNodeWithTag("durationInput").performTextReplacement("25")
        compose.onNodeWithText("Save").performClick()
        waitFor(hasText("25 min is already a preset"))
        compose.onNodeWithTag("durationInput").performTextReplacement("1441")
        compose.onNodeWithText("Save").performClick()
        waitFor(hasText("Duration can be at most 24 hours"))
        compose.onNodeWithTag("durationInput").performTextReplacement("45")
        compose.onNodeWithText("Save").performClick()
        waitFor(hasText("45 min"))

        compose.onNodeWithContentDescription("Edit 45 min").performClick()
        compose.onNodeWithTag("durationInput").performTextReplacement("1h 30m")
        compose.onNodeWithText("Save").performClick()
        waitFor(hasText("1 h 30 min"))

        compose.onNodeWithContentDescription("Move 1 h 30 min up").performClick()
        compose.onNodeWithContentDescription("Remove 5 min").performClick()
        compose.waitUntil(3_000) {
            kotlinx.coroutines.runBlocking {
                dev.ezaz.manazone.container.preferences.presets.first()
            } == listOf(20, 25, 30, 90, 60)
        }

        // Presets persist and appear on the home screen.
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasContentDescription("Start 1 h 30 min focus"))
        compose.onNodeWithContentDescription("Start 5 min focus").assertDoesNotExist()
    }
}

