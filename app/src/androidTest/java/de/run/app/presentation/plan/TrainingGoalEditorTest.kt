package de.run.app.presentation.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.presentation.theme.RunTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrainingGoalEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editingGoalShowsExistingDetailsAndSavesThemSeparately() {
        val existing = TrainingGoal(
            id = "existing",
            raceDate = LocalDate.now().plusMonths(5),
            raceDistanceMeters = 21_100,
            raceGoal = RaceGoal.RUN_THROUGH
        )
        var saved: TrainingGoal? = null

        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingGoalEditor(
                    existing = existing,
                    isSaving = false,
                    saveFailed = false,
                    onSave = { saved = it },
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("Ziel bearbeiten").assertIsDisplayed()
        composeRule.onNodeWithText("Durchlaufen").performClick()
        composeRule.onNodeWithText("Ziel speichern").performScrollTo().assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(existing.raceDate, saved?.raceDate)
            assertEquals(existing.raceDistanceMeters, saved?.raceDistanceMeters)
            assertEquals(RaceGoal.RUN_THROUGH, saved?.raceGoal)
        }
    }
}
