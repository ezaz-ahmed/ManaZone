package dev.ezaz.manazone.ui

import android.app.NotificationManager
import androidx.compose.ui.test.ExperimentalTestApi
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
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ezaz.manazone.MainActivity
import dev.ezaz.manazone.app
import dev.ezaz.manazone.resetAppState
import dev.ezaz.manazone.shell
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises granted, denied and revoked DND access using the `cmd notification` shell commands. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DndSettingsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val pkg get() = app.packageName
    private val notificationManager get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        resetAppState()
        shell("cmd notification set_dnd off")
    }

    @After
    fun tearDown() {
        scenario?.close()
        shell("cmd notification set_dnd off")
        shell("cmd notification disallow_dnd $pkg")
        resetAppState()
    }

    private fun openSettings() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Settings"), 5_000)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("dndToggle"), 5_000)
        compose.onNodeWithTag("dndToggle").performScrollTo()
    }

    @Test
    fun deniedAccessExplainsBeforeOpeningSettingsAndStaysOff() {
        shell("cmd notification disallow_dnd $pkg")
        openSettings()
        compose.onNodeWithTag("dndToggle").assertIsOff().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Do Not Disturb access"), 3_000)
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithTag("dndToggle").assertIsOff()
        compose.onNodeWithText("Off. Manazone works without this.").assertExists()
    }

    @Test
    fun grantedAccessSilencesDuringSessionAndRestoresAfterStop() {
        shell("cmd notification allow_dnd $pkg")
        openSettings()
        compose.onNodeWithTag("dndToggle").performClick()
        compose.waitUntilAtLeastOneExists(hasText("On. Access granted."), 3_000)
        compose.onNodeWithTag("dndToggle").assertIsOn()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Start 25 min focus"), 3_000)
        compose.onNodeWithContentDescription("Start 25 min focus").performClick()
        compose.waitUntil(5_000) {
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        compose.onNodeWithText("Stop").performClick()
        compose.waitUntil(5_000) {
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }

    @Test
    fun revokedAccessIsReportedAndSessionsStillWork() {
        shell("cmd notification allow_dnd $pkg")
        openSettings()
        compose.onNodeWithTag("dndToggle").performClick()
        compose.waitUntilAtLeastOneExists(hasText("On. Access granted."), 3_000)

        shell("cmd notification disallow_dnd $pkg")
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntilAtLeastOneExists(hasText("Access was revoked", substring = true), 5_000)
        compose.onNodeWithTag("dndToggle").assertIsOff()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Start 5 min focus"), 3_000)
        compose.onNodeWithContentDescription("Start 5 min focus").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("countdown"), 5_000)
        compose.onNodeWithText("Stop").performClick()
        compose.waitUntilAtLeastOneExists(hasText("TODAY"), 5_000)
    }
}
