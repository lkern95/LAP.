package de.run.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.domain.model.ScheduledRun
import de.run.app.domain.model.TrainingWeek
import de.run.app.presentation.theme.RunTheme
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodayWorkoutCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailsButtonOpensTodaysRunUsingItsPlanWeekKey() {
        var openedWeek: LocalDate? = null
        var openedMinutes = 0
        val (week, run) = sampleWeekAndRun()
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                TodayWorkoutCard(
                    week = week,
                    onOpenTrainingDetailsForTime = { weekStart, _, minutes ->
                        openedWeek = weekStart
                        openedMinutes = minutes
                    },
                    completedRuns = 0,
                    defaultAvailableMinutes = 60,
                    maximumAvailableMinutes = 90,
                    canStartTraining = true,
                    nextScheduledRun = ScheduledRun(DayOfWeek.TUESDAY, run, week.startsOn.plusDays(1))
                )
            }
        }

        compose.onNodeWithContentDescription("Trainingsdetails ansehen").assertIsDisplayed().performClick()

        assertEquals(week.startsOn, openedWeek)
        assertEquals(60, openedMinutes)
    }

    @Test fun selectedAvailableTimeIsPassedWithThePlanWeekKey() {
        var openedWeek: LocalDate? = null
        var openedMinutes = 0
        val (week, run) = sampleWeekAndRun()
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                TodayWorkoutCard(
                    week = week,
                    onOpenTrainingDetailsForTime = { weekStart, _, minutes ->
                        openedWeek = weekStart
                        openedMinutes = minutes
                    },
                    completedRuns = 0,
                    defaultAvailableMinutes = 30,
                    maximumAvailableMinutes = 45,
                    canStartTraining = true,
                    nextScheduledRun = ScheduledRun(DayOfWeek.TUESDAY, run, week.startsOn.plusDays(1))
                )
            }
        }

        compose.onNodeWithContentDescription("Weniger oder mehr Zeit?").performClick()
        compose.onNodeWithTag("available-time-slider").performSemanticsAction(SemanticsActions.SetProgress) {
            assertTrue(it(67f))
        }
        compose.onNodeWithText("70 Minuten").assertIsDisplayed()
        compose.onNodeWithText("Einheit ansehen").performClick()

        assertEquals(week.startsOn, openedWeek)
        assertEquals(70, openedMinutes)
    }

    @Test fun availableTimeSliderCanBeDraggedToIncreaseDuration() {
        var openedMinutes = 0
        val (week, run) = sampleWeekAndRun()
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                TodayWorkoutCard(
                    week = week,
                    onOpenTrainingDetailsForTime = { _, _, minutes -> openedMinutes = minutes },
                    completedRuns = 0,
                    defaultAvailableMinutes = 20,
                    maximumAvailableMinutes = 45,
                    canStartTraining = true,
                    nextScheduledRun = ScheduledRun(DayOfWeek.TUESDAY, run, week.startsOn.plusDays(1))
                )
            }
        }

        compose.onNodeWithContentDescription("Weniger oder mehr Zeit?").performClick()
        compose.onNodeWithTag("available-time-slider").performTouchInput {
            down(center)
            moveTo(Offset(center.x * 1.9f, center.y), delayMillis = 600)
            up()
        }
        compose.onNodeWithText("Einheit ansehen").performClick()

        assertTrue("Dragging the slider must increase today's available time", openedMinutes > 20)
        assertEquals("The selected time must use ten-minute increments", 0, openedMinutes % 10)
        assertTrue("The slider must stop at four hours", openedMinutes <= 240)
    }

    @Test fun availableTimeSliderOffers20To240Minutes() {
        var openedMinutes = 0
        val (week, run) = sampleWeekAndRun()
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                TodayWorkoutCard(
                    week = week,
                    onOpenTrainingDetailsForTime = { _, _, minutes -> openedMinutes = minutes },
                    completedRuns = 0,
                    defaultAvailableMinutes = 30,
                    maximumAvailableMinutes = 45,
                    canStartTraining = true,
                    nextScheduledRun = ScheduledRun(DayOfWeek.TUESDAY, run, week.startsOn.plusDays(1))
                )
            }
        }

        compose.onNodeWithContentDescription("Weniger oder mehr Zeit?").performClick()
        val slider = compose.onNodeWithTag("available-time-slider")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(240f)) }
        compose.onNodeWithText("240 Minuten").assertIsDisplayed()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(20f)) }
        compose.onNodeWithText("20 Minuten").assertIsDisplayed()
        compose.onNodeWithText("Einheit ansehen").performClick()

        assertEquals(20, openedMinutes)
    }

    private fun sampleWeekAndRun(): Pair<TrainingWeek, PlannedRun> {
        val monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val run = PlannedRun(
            title = "Lockerer Lauf",
            optional = false,
            steps = listOf(RunStep("Einlaufen", 10), RunStep("Laufen", 40), RunStep("Auslaufen", 10)),
            scheduledDate = monday.plusDays(1)
        )
        val week = TrainingWeek(1, monday, 60, 0, listOf(run), false)
        return week to run
    }
}
