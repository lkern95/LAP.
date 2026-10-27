package de.run.app.presentation.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.DistanceSpikeAssessment
import de.run.app.domain.model.DistanceSpikeLevel
import de.run.app.presentation.theme.RunTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainingDetailsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsThePlannedSectionsAndWatchOnlyStartGuidance() {
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingDetailsScreen(
                    run = sampleRun(),
                    onBack = {},
                    onOpenPlan = {}
                )
            }
        }

        composeRule.onNodeWithText("Trainingsdetails").assertIsDisplayed()
        composeRule.onNodeWithText("Lockerer Lauf").assertIsDisplayed()
        composeRule.onNodeWithText("Dauer").assertIsDisplayed()
        composeRule.onNodeWithText("RPE-Skala: 1 = sehr leicht, 10 = maximal.").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Sprechtempo", substring = true)[0].performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("RPE 2–4 von 10", substring = true)[0].performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Einlaufen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hauptteil").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Auslaufen").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Live-Begleitung starten").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Live-Coach im Emulator testen").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Starte die Aufzeichnung auf deiner Garmin-Uhr. LAP begleitet den Lauf hier.").assertIsDisplayed()
        composeRule.onNodeWithText("So startest du den Lauf auf der Uhr").performClick()
        composeRule.onNodeWithText("LAP startet keine Aufzeichnung auf der Uhr. Wähle dort Aktivitäten → Laufen und öffne LAP Live als Datenseite.")
            .assertIsDisplayed()
    }

    @Test
    fun liveButtonNavigatesToGarminReception() {
        var started = false
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingDetailsScreen(
                    run = sampleRun(),
                    onBack = {},
                    onOpenPlan = {},
                    onStartGarminLive = { started = true }
                )
            }
        }

        composeRule.onNodeWithText("Live-Begleitung starten").performClick()
        assertTrue(started)
    }

    @Test
    fun unavailableRunCanReturnToThePlan() {
        composeRule.setContent {
            RunTheme(AppTheme.DARK) {
                TrainingDetailsScreen(run = null, onBack = {}, onOpenPlan = {})
            }
        }
        composeRule.onNodeWithText("Diese Trainingseinheit ist nicht mehr verfügbar.").assertIsDisplayed()
        composeRule.onNodeWithText("Zum Wochenplan").assertIsDisplayed()
    }

    @Test
    fun showsMeasuredDistanceIncreaseAsAWarningWithoutBlockingTheRun() {
        val run = sampleRun().copy(
            distanceSpikeAssessment = DistanceSpikeAssessment(
                level = DistanceSpikeLevel.WARNING,
                referenceDistanceMeters = 8_000.0,
                referenceDate = java.time.LocalDate.of(2026, 10, 10),
                estimatedDistanceMinimumMeters = 10_000.0,
                estimatedDistanceMaximumMeters = 12_000.0,
                increaseMinimumPercent = 25,
                increaseMaximumPercent = 50,
                isEstimate = true
            )
        )
        composeRule.setContent {
            RunTheme(AppTheme.DARK) {
                TrainingDetailsScreen(run = run, onBack = {}, onOpenPlan = {})
            }
        }

        composeRule.onNodeWithText("Deutlicher Distanzsprung", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Gehabschnitte sind nicht eingerechnet", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("keine Trainingssperre", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Live-Begleitung starten").assertExists()
    }

    private fun sampleRun() = PlannedRun(
        title = "Lockerer Lauf",
        optional = false,
        steps = listOf(
            RunStep("Einlaufen", 10),
            RunStep("Hauptteil", 30, 2, 4, "Sprechtempo"),
            RunStep("Auslaufen", 10)
        )
    )
}
