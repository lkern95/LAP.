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
                "Dein nächstes Training",
                "Deine nächste Einheit",
                "Heute Zeit zum Trainieren?"
            ).any { label ->
                composeRule.onAllNodesWithText(label, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun todayKeepsTrainingStatusAndPlanWeekClearAndNonDuplicated() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Planwoche").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Planwoche").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Laufumfang diese Woche").assertIsDisplayed()
        composeRule.onNodeWithText("Halbmarathon").assertIsDisplayed()
        composeRule.onNodeWithText("Diese Woche").performScrollTo().assertIsDisplayed()
        val hasUnclearStatus = composeRule.onAllNodesWithText("Laufdaten noch nicht verfügbar")
            .fetchSemanticsNodes().isNotEmpty()
        val hasStoredRun = composeRule.onAllNodesWithText("Bin ich auf Kurs?")
            .fetchSemanticsNodes().isNotEmpty()
        if (hasStoredRun) {
            composeRule.onNodeWithText("Bin ich auf Kurs?").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("vorgesehen", substring = true).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Läufe erfasst", substring = true).performScrollTo().assertIsDisplayed()
            assert(composeRule.onAllNodesWithText("Laufdaten noch nicht verfügbar").fetchSemanticsNodes().isEmpty())
            assert(composeRule.onAllNodesWithText("— /", substring = true).fetchSemanticsNodes().isEmpty())
        } else {
            assert(composeRule.onAllNodesWithText("Für eine Zeitprognose fehlen aktuelle, aussagekräftige Läufe.")
                .fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithText("Laufumfang diese Woche").assertIsDisplayed()
            composeRule.onNodeWithText("Laufdaten nicht verfügbar", substring = true)
                .performScrollTo().assertIsDisplayed()
        }
        if (hasUnclearStatus && !hasStoredRun) {
            composeRule.onNodeWithText("LAP benötigt Zugriff auf deine Laufdaten, um dein Training auszuwerten.")
                .assertIsDisplayed()
            assert(composeRule.onAllNodesWithText("Freigabe fehlt").fetchSemanticsNodes().isEmpty())
            assert(composeRule.onAllNodesWithText("Läufe nicht freigegeben").fetchSemanticsNodes().isEmpty())
        }

        val asksToGrantAccess = composeRule.onAllNodesWithText("Laufdaten freigeben")
            .fetchSemanticsNodes().isNotEmpty()
        if (asksToGrantAccess) {
            assert(composeRule.onAllNodesWithText("Training starten").fetchSemanticsNodes().isEmpty())
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
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Farbstil").performScrollTo().performClick()
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
        composeRule.onNodeWithText("Profil").performClick()

        composeRule.onNodeWithText("Sicherung").performScrollTo().performClick()
        composeRule.onNodeWithText("Sicherung").assertIsDisplayed()
        composeRule.onNodeWithText("Sicherung erstellen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sicherung wiederherstellen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileCanPreviewEvaluationReport() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Sicherung").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Auswertungsprotokoll anzeigen")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Auswertungsprotokoll anzeigen")
            .performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("# LAP-Auswertungsprotokoll", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("# LAP-Auswertungsprotokoll", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Schließen").performClick()
    }

    @Test
    fun trainingSettingsAreDirectlyAvailableUnderProfile() {
        composeRule.onNodeWithText("Plan").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            listOf("Dein Halbmarathon", "Dein Trainingsplan", "Wochenplan").any { label ->
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            }
        }
        listOf("Ziel bearbeiten", "Kraftübungen ansehen", "Verfügbarkeit ändern")
            .forEach { label -> assert(composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) }

        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("TRAINING").performScrollTo().assertIsDisplayed()
        assert(composeRule.onAllNodesWithText("Ziel bearbeiten").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("TRAINING").performClick()
        listOf("Ziel bearbeiten", "Kraftübungen ansehen", "Verfügbarkeit ändern")
            .forEach { label -> composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText("TRAINING").performClick()
        assert(composeRule.onAllNodesWithText("Ziel bearbeiten").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Farbstil").performScrollTo().assertIsDisplayed()
        assert(composeRule.onAllNodesWithText("Olive").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Farbstil").performClick()
        composeRule.onNodeWithText("Olive").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun strengthLibraryCanBeOpenedBeforeSettingUpPlan() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("TRAINING").performScrollTo().performClick()
        composeRule.onNodeWithText("Kraftübungen ansehen").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Kraft für deinen Lauf")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Kraft für deinen Lauf").get(0).assertIsDisplayed()
        composeRule.onNodeWithText("Knie & Hüftstabilität").assertIsDisplayed()
        assertTrainingActionIsDisplayed()
    }

    @Test
    fun strengthSessionShowsJsonExerciseAndKeepsProgressWhenReturningFromDetail() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("TRAINING").performScrollTo().performClick()
        composeRule.onNodeWithText("Kraftübungen ansehen").performScrollTo().performClick()
        performTrainingAction()
        composeRule.onNodeWithText("Kniebeuge mit Miniband").assertIsDisplayed()
        composeRule.onNodeWithText("Satz 1 starten").performClick()
        composeRule.onNodeWithText("Satz 1 abschließen").assertIsDisplayed()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.onNodeWithText("Training fortsetzen", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Training fortsetzen", substring = true).performClick()
        composeRule.onNodeWithText("Kniebeuge mit Miniband").assertIsDisplayed()
        composeRule.onNodeWithText("Satz 1 abschließen").assertIsDisplayed()
    }

    @Test
    fun garminScreenShowsWatchAndImportSteps() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()

        composeRule.onNodeWithText("Garmin").assertIsDisplayed()
        composeRule.onNodeWithText("Aktivitäten").assertIsDisplayed()
        composeRule.onNodeWithText("Gesundheitsdaten").assertIsDisplayed()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Schwarze Garmin-Uhr").assertIsDisplayed()
    }

    private fun assertTrainingActionIsDisplayed() {
        val hasStart = composeRule.onAllNodesWithText("Training starten", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        val hasResume = composeRule.onAllNodesWithText("Training fortsetzen", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        assert(hasStart || hasResume)
    }

    private fun performTrainingAction() {
        val hasResume = composeRule.onAllNodesWithText("Training fortsetzen", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        val label = if (hasResume) "Training fortsetzen" else "Training starten"
        composeRule.onNodeWithText(label, substring = true).performClick()
    }

    @Test
    fun garminBackArrowReturnsToProfileAndKeepsTheme() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Farbstil").performScrollTo().performClick()
        composeRule.onNodeWithText("Olive").performScrollTo().performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()

        composeRule.onNodeWithContentDescription("Zurück zum Profil").performClick()
        composeRule.onNodeWithText("Garmin Connect").assertIsDisplayed()
        composeRule.onNodeWithText("Olive").performScrollTo().assertIsSelected()
    }

    @Test
    fun profileTabReturnsFromGarminTrainingPlansWithoutLosingProfileState() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Farbstil").performScrollTo().performClick()
        composeRule.onNodeWithText("Olive").performScrollTo().performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().performClick()
        composeRule.onNodeWithText("Plan").assertIsSelected()

        composeRule.onNodeWithText("Profil").performClick()

        composeRule.onNodeWithText("Garmin Connect").assertIsDisplayed()
        composeRule.onNodeWithText("Olive").performScrollTo().assertIsSelected()
        composeRule.onAllNodesWithText("Profil").get(1).assertIsSelected()
    }

    @Test
    fun androidBackGestureReturnsFromTrainingPlanThroughGarminToProfile() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()
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
            composeRule.onAllNodesWithText("Garmin Connect")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Profil").get(1).assertIsSelected()
    }

    @Test
    fun androidBackDispatcherReturnsFromTrainingPlanThroughGarminToProfile() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().performClick()
        composeRule.onNodeWithText("Plan").assertIsSelected()

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Schwarze Garmin-Uhr")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Garmin Connect")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Profil").get(1).assertIsSelected()
    }

    @Test
    fun garminActivityRowOpensRunHistory() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()
        composeRule.onNodeWithText("Aktivitäten").performScrollTo().performClick()

        composeRule.onNodeWithText("Dein Fortschritt").assertIsDisplayed()
    }

    @Test
    fun garminPlanRowOpensTrainingPlan() {
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()
        composeRule.onNodeWithText("Trainingspläne").performScrollTo().performClick()

        composeRule.onNodeWithText("Plan").assertIsSelected()
    }

    @Test
    fun readinessDetailsCanShowDebugOnlyGarminDemoData() {
        composeRule.onNodeWithText("Deine Erholungsdaten").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Erholung").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Emulator-Test").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Demodaten einschalten").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Demodaten").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Demodaten").get(0).assertIsDisplayed()
        composeRule.onNodeWithText("7 Std. 48 Min.", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ausgeglichen · 48 ms letzte Nacht", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Stress Ø 24/100", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("7.420 Schritte", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Letzte 7 Tage: 3 Läufe", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Datenbasis: 6 von 6 Bereichen verfügbar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Modellwert · keine medizinische Messung der körperlichen Erholung").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Deine letzten Trainingswochen").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("3 absolvierte Läufe", substring = true)[0].performScrollTo().assertIsDisplayed()
        val personalFeedbackTitle = "War das Training gut?"
        if (composeRule.onAllNodesWithText(personalFeedbackTitle).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(personalFeedbackTitle).performScrollTo().assertIsDisplayed()
            assert(composeRule.onAllNodesWithText("Bin ich auf Kurs?").fetchSemanticsNodes().isEmpty())
        } else {
            composeRule.onNodeWithText("Dein Feedback").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Alles passt").performClick()
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.onNodeWithText("Garmin Connect").performClick()
        composeRule.onNodeWithText("Demoverbindung mit Testdaten").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Gesundheitsdaten").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Erholung").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Demodaten ausschalten").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Demodaten einschalten").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("Demodaten ausschalten").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Demodaten ausschalten").performScrollTo().performClick()
        } else {
            composeRule.onNodeWithText("Demodaten einschalten").performScrollTo().assertIsDisplayed()
        }
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
