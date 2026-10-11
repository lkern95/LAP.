package de.run.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.run.app.MainActivity
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun continuePastWelcome() {
        composeRule.finishFirstRunIfNeeded()
    }

    @Test
    fun homeShowsTodayAndARelevantTrainingState() {
        composeRule.onAllNodesWithText("Heute").fetchSemanticsNodes()
            .also { nodes -> assert(nodes.isNotEmpty()) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listOf(
                "Plan einrichten",
                "Dein Training",
                "Deine nächste Einheit",
                "Heute Zeit zum Trainieren?"
            ).any { label ->
                composeRule.onAllNodesWithText(label, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun todayCalendarActionOpensTheTrainingPlan() {
        composeRule.onNodeWithContentDescription("Wochenplan öffnen").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            listOf("Dein Halbmarathon", "Dein Trainingsplan", "Wochenplan").any { label ->
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun todayTabReturnsFromPlanAndProgress() {
        composeRule.onNodeWithText("Plan").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listOf("Dein Halbmarathon", "Dein Trainingsplan", "Wochenplan").any { label ->
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.onAllNodesWithText("Heute").get(0).performClick()
        composeRule.onAllNodesWithText("Heute").get(1).assertIsSelected()

        composeRule.onNodeWithText("Fortschritt").performClick()
        composeRule.onNodeWithText("Dein Fortschritt").assertIsDisplayed()
        composeRule.onAllNodesWithText("Heute").get(0).performClick()
        composeRule.onAllNodesWithText("Heute").get(1).assertIsSelected()
    }

    @Test
    fun planSetupDoesNotAskForSymptomInformation() {
        composeRule.onNodeWithText("Plan").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listOf("Plan erstellen", "Dein Trainingsplan", "Wochenplan").any { label ->
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
        }

        assert(composeRule.onAllNodesWithText("Ich habe wiederkehrende Taubheit, Kribbeln oder ausstrahlende Beschwerden")
            .fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun historyShowsFriendlyEmptyOrAccessState() {
        composeRule.onNodeWithText("Fortschritt").performClick()

        composeRule.onNodeWithText("Dein Fortschritt").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listOf(
                "Deine Läufe werden geladen.",
                "Noch keine Läufe gefunden.",
                "Noch keine Läufe in diesem Zeitraum.",
                "Freigabe fehlt",
                "Laufdaten gerade nicht verfügbar",
                "Läufe konnten nicht aktualisiert werden"
            ).any { message ->
                composeRule.onAllNodesWithText(message, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun themeCanBeChangedFromProfile() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Farbstil").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Olive").performScrollTo().performClick()
        composeRule.onNodeWithText("Olive").assertIsSelected()

        composeRule.onNodeWithText("Dunkel").performScrollTo().performClick()

        composeRule.onNodeWithText("Dunkel").assertIsSelected()
        composeRule.onNodeWithText("Hell").performScrollTo().performClick()
        composeRule.onNodeWithText("Hell").assertIsSelected()
        composeRule.onNodeWithText("Olive").performScrollTo().performClick()
        composeRule.onNodeWithText("Olive").assertIsSelected()
    }

    @Test
    fun profileShowsLocalBackupAndRestoreActions() {
        composeRule.onNodeWithText("Ich").performClick()

        composeRule.onNodeWithText("Sicherung").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sicherung erstellen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sicherung wiederherstellen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun strengthLibraryCanBeOpenedBeforeSettingUpPlan() {
        composeRule.onNodeWithText("Plan").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Kraftübungen ansehen")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kraftübungen ansehen").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Kraft für deinen Lauf")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kraft für deinen Lauf").assertIsDisplayed()
    }

    @Test
    fun garminScreenShowsWatchAndImportSteps() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()

        composeRule.onNodeWithText("Garmin").assertIsDisplayed()
        composeRule.onNodeWithText("Aktivitäten").assertIsDisplayed()
        composeRule.onNodeWithText("Gesundheitsdaten").assertIsDisplayed()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Schwarze Garmin-Uhr").assertIsDisplayed()
    }

    @Test
    fun garminBackArrowReturnsToProfileAndKeepsTheme() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Olive").performScrollTo().performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()

        composeRule.onNodeWithContentDescription("Zurück zum Profil").performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").assertIsDisplayed()
        composeRule.onNodeWithText("Olive").performScrollTo().assertIsSelected()
    }

    @Test
    fun profileTabReturnsFromGarminTrainingPlansWithoutLosingProfileState() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Olive").performScrollTo().performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().performClick()
        composeRule.onNodeWithText("Plan").assertIsSelected()

        composeRule.onNodeWithText("Ich").performClick()

        composeRule.onNodeWithText("Läufe und Garmin Connect").assertIsDisplayed()
        composeRule.onNodeWithText("Olive").performScrollTo().assertIsSelected()
        composeRule.onAllNodesWithText("Ich").get(1).assertIsSelected()
    }

    @Test
    fun androidBackGestureReturnsFromTrainingPlanThroughGarminToProfile() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().performClick()
        composeRule.onNodeWithText("Plan").assertIsSelected()

        performBackGestureFromEdge(fromRight = true)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Schwarze Garmin-Uhr")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Schwarze Garmin-Uhr").assertIsDisplayed()
        performBackGestureFromEdge(fromRight = false)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Läufe und Garmin Connect")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Ich").get(1).assertIsSelected()
    }

    @Test
    fun androidBackDispatcherReturnsFromTrainingPlanThroughGarminToProfile() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().performClick()
        composeRule.onNodeWithText("Plan").assertIsSelected()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Schwarze Garmin-Uhr")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Läufe und Garmin Connect")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Ich").get(1).assertIsSelected()
    }

    @Test
    fun garminActivityRowOpensRunHistory() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()
        composeRule.onNodeWithText("Aktivitäten").performScrollTo().performClick()

        composeRule.onNodeWithText("Dein Fortschritt").assertIsDisplayed()
    }

    @Test
    fun garminPlanRowOpensTrainingPlan() {
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().performClick()

        composeRule.onNodeWithText("Plan").assertIsSelected()
    }

    @Test
    fun readinessDetailsCanShowDebugOnlyGarminDemoData() {
        composeRule.onNodeWithText("Deine Trainingswerte").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Erholung").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Emulator-Test").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Demodaten einschalten").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Demodaten").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Demodaten").get(0).assertIsDisplayed()
        composeRule.onNodeWithText("7 Std. 48 Min.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3 Läufe · 21,1 km").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Dein Feedback").assertIsDisplayed()
        composeRule.onNodeWithText("Alles passt").performClick()
        composeRule.onNodeWithText("Ich").performClick()
        composeRule.onNodeWithText("Läufe und Garmin Connect").performClick()
        composeRule.onNodeWithText("Demoverbindung mit Testdaten").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Heute").performClick()
        composeRule.onNodeWithText("Deine Trainingswerte").performClick()
        composeRule.onNodeWithText("Demodaten ausschalten").performClick()
    }

    private fun performBackGestureFromEdge(fromRight: Boolean) {
        val metrics = composeRule.activity.resources.displayMetrics
        val centerY = metrics.heightPixels / 2
        val inset = (130 * metrics.density).toInt()
        val startX = if (fromRight) metrics.widthPixels - 1 else 1
        val endX = if (fromRight) metrics.widthPixels - inset else inset
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input swipe $startX $centerY $endX $centerY 250")
            .close()
        composeRule.waitForIdle()
    }
}
