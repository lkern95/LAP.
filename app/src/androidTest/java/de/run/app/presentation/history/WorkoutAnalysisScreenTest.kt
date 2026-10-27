package de.run.app.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.Workout
import de.run.app.domain.model.RunningSymptomFeedback
import de.run.app.domain.TrainingFeedback
import de.run.app.domain.WorkoutPlanStatus
import de.run.app.presentation.theme.RunTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutAnalysisScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsActualSyncedValuesAndExplainsMissingRoute() {
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutAnalysisScreen(
                    workout = sampleWorkout(distanceMeters = 8_000.0, averageHeartRateBpm = 151),
                    onContinue = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Deine Laufwerte").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Stark gemacht!").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("workout-analysis-list")
            .performScrollToNode(hasText("Mit Garmin Connect synchronisiert"))
        composeRule.onNodeWithText("Mit Garmin Connect synchronisiert").assertIsDisplayed()
        composeRule.onNodeWithText("8,0 km").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("49:56").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("6:15").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("151").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("bpm").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("workout-analysis-list")
            .performScrollToNode(hasContentDescription("Details zur Synchronisierung anzeigen"))
        composeRule.onNodeWithContentDescription("Details zur Synchronisierung anzeigen").performClick()
        composeRule.onNodeWithText("Eine Streckenkarte wurde für diesen Lauf nicht mitgesendet.")
            .assertIsDisplayed()
    }

    @Test
    fun identifiesLocallyImportedFitWithoutClaimingGarminSync() {
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutAnalysisScreen(
                    workout = sampleWorkout(distanceMeters = 8_000.0, averageHeartRateBpm = 151)
                        .copy(sourceName = "FIT-Datei"),
                    onContinue = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("FIT-Aktivität importiert").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Lauf synchronisiert").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Mit Garmin Connect synchronisiert").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun presentsIndividualExtraRunFeedbackWithoutPerformanceGrade() {
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutAnalysisScreen(
                    workout = sampleWorkout(distanceMeters = 2_500.0, averageHeartRateBpm = 120)
                        .copy(sourceName = "FIT-Datei"),
                    onContinue = {},
                    onBack = {},
                    trainingFeedback = TrainingFeedback(
                        shortSummary = "25 Min. zusätzlich gelaufen. Anstrengung und Beschwerden sind noch offen.",
                        status = WorkoutPlanStatus.EXTRA_RUN,
                        goalSummary = "Noch nicht einschätzbar.",
                        reasons = emptyList(),
                        nextAction = "Als Nächstes: Erholungslauf für 25 Minuten laut Wochenplan.",
                        weeklyFocus = "",
                        weeklyPlanProgress = de.run.app.domain.WeeklyPlanProgress(
                            java.time.LocalDate.parse("2026-11-09"), 1, 2, 1
                        ),
                        qualityHeadline = "Anstrengung offen"
                    )
                )
            }
        }

        composeRule.onNodeWithText("War das Training gut?").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("War das Training gut? anzeigen").performClick()
        composeRule.onNodeWithText(
            "25 Min. zusätzlich gelaufen. Anstrengung und Beschwerden sind noch offen."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Diese Woche: 1 von 2 Läufen erledigt · 1 noch offen.").assertIsDisplayed()
        composeRule.onNodeWithText("Als Nächstes: Erholungslauf für 25 Minuten laut Wochenplan.").assertIsDisplayed()
    }

    @Test
    fun marksUnavailableMeasurementsInsteadOfInventingThem() {
        composeRule.setContent {
            RunTheme(AppTheme.DARK) {
                WorkoutAnalysisScreen(sampleWorkout(distanceMeters = null, averageHeartRateBpm = null), {}, {})
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Nicht erfasst").fetchSemanticsNodes().size >= 2)
        composeRule.onNodeWithTag("workout-analysis-list")
            .performScrollToNode(hasContentDescription("Details zur Synchronisierung anzeigen"))
        composeRule.onNodeWithContentDescription("Details zur Synchronisierung anzeigen").performClick()
        composeRule.onNodeWithText("Eine Streckenkarte wurde für diesen Lauf nicht mitgesendet.")
            .assertIsDisplayed()
    }

    @Test
    fun continueReturnsThroughProvidedAction() {
        var continued = false
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutAnalysisScreen(sampleWorkout(5_000.0, 145), { continued = true }, {})
            }
        }

        composeRule.onNodeWithText("Weiter").performClick()
        assertTrue(continued)
    }

    @Test
    fun feedbackStartsUnansweredAndSavesTheChosenValues() {
        var savedEffort: Int? = null
        var savedSymptoms: RunningSymptomFeedback? = null
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutAnalysisScreen(
                    sampleWorkout(5_000.0, 145), {}, {},
                    onSaveFeedback = { effort, symptoms -> savedEffort = effort; savedSymptoms = symptoms }
                )
            }
        }

        val list = composeRule.onNodeWithTag("workout-analysis-list")
        list.performScrollToNode(hasText("Wie war der Lauf für dich?"))
        composeRule.onNodeWithText("Wie war der Lauf für dich?").assertIsDisplayed()
        list.performScrollToNode(hasText("Rückmeldung speichern"))
        composeRule.onNodeWithText("Rückmeldung speichern").assertIsNotEnabled()
        list.performScrollToNode(hasText("8"))
        composeRule.onNodeWithText("8").performClick()
        list.performScrollToNode(hasText("Unsicher"))
        composeRule.onNodeWithText("Unsicher").performClick()
        list.performScrollToNode(hasText("Rückmeldung speichern"))
        composeRule.onNodeWithText("Rückmeldung speichern").performClick()

        composeRule.runOnIdle {
            assertTrue(savedEffort == 8)
            assertTrue(savedSymptoms == RunningSymptomFeedback.UNSURE)
        }
    }

    @Test
    fun feedbackCanBeSkippedWithoutSelectingAnAnswer() {
        var skipped = false
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutAnalysisScreen(sampleWorkout(5_000.0, 145), {}, {}, onSkipFeedback = { skipped = true })
            }
        }

        composeRule.onNodeWithTag("workout-analysis-list").performScrollToNode(hasText("Überspringen"))
        composeRule.onNodeWithText("Überspringen").assertIsDisplayed()
        composeRule.onNodeWithText("Überspringen").performClick()

        composeRule.runOnIdle { assertTrue(skipped) }
    }

    private fun sampleWorkout(distanceMeters: Double?, averageHeartRateBpm: Int?) = Workout(
        sourceId = "garmin:workout-1",
        sourceName = "Garmin Connect",
        title = "Lauf",
        startedAtMillis = 1_700_000_000_000,
        endedAtMillis = 1_700_002_996_000,
        distanceMeters = distanceMeters,
        averageHeartRateBpm = averageHeartRateBpm
    )
}
