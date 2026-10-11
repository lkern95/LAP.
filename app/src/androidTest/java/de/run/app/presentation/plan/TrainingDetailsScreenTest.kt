package de.run.app.presentation.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.presentation.theme.RunTheme
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
        composeRule.onNodeWithText("Nicht vorgegeben").assertIsDisplayed()
        composeRule.onNodeWithText("Einlaufen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hauptteil").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Auslaufen").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Auf der Uhr starten  →").performClick()
        composeRule.onNodeWithText("Wähle auf der Uhr Aktivitäten → Laufen. Nach der Synchronisierung findest du den Lauf hier.")
            .assertIsDisplayed()
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

    private fun sampleRun() = PlannedRun(
        title = "Lockerer Lauf",
        optional = false,
        steps = listOf(
            RunStep("Einlaufen", 10),
            RunStep("Hauptteil", 30),
            RunStep("Auslaufen", 10)
        )
    )
}
