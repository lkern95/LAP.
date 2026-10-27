package de.run.app.presentation.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.run.app.domain.TrainingPlanBuilder
import de.run.app.domain.model.RaceGoal
import de.run.app.domain.model.TrainingGoal
import de.run.app.domain.model.TrainingProfile
import de.run.app.domain.model.AvailabilityMode
import de.run.app.presentation.theme.RunTheme
import de.run.app.domain.model.AppTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class PlanWeekScheduleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun raceWeekShowsRaceAsItsOwnDatedEntry() {
        val today = LocalDate.of(2026, 10, 5)
        val raceDate = today.plusDays(8)
        val goal = TrainingGoal("race-ui", raceDate, 21_100, RaceGoal.RUN_THROUGH)
        val profile = TrainingProfile(45, false, 0)
        val raceWeek = TrainingPlanBuilder().build(profile, goal, today).weeks
            .first { raceDate in it.startsOn..it.startsOn.plusDays(6) }

        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                PlanWeekSchedule(
                    week = raceWeek,
                    availabilityMode = AvailabilityMode.RECURRING,
                    onOpenStrength = {},
                    onOpenTrainingDetails = {},
                    raceDate = raceDate,
                    raceTitle = "Halbmarathon"
                )
            }
        }

        composeRule.onNodeWithText("Wettkampf").assertIsDisplayed()
        composeRule.onNodeWithText("Halbmarathon").assertIsDisplayed()
    }
}
