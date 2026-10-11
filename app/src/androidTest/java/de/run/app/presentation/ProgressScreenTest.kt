package de.run.app.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.run.app.MainActivity
import de.run.app.domain.ProgressDistanceBucket
import de.run.app.domain.ProgressPeriod
import de.run.app.domain.TrainingProgressDashboard
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.history.ProgressVolumeCard
import de.run.app.presentation.theme.RunTheme
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun continuePastWelcome() {
        composeRule.finishFirstRunIfNeeded()
    }

    @Test
    fun progressPeriodSelectionChangesTheSelectedRange() {
        composeRule.onNodeWithText("Fortschritt").performClick()
        composeRule.onNodeWithText("Dein Fortschritt").assertIsDisplayed()
        composeRule.onNodeWithText("Monat").assertIsSelected()
        composeRule.onNodeWithText("Woche").assertIsNotSelected().performClick().assertIsSelected()
        composeRule.onNodeWithText("Gesamt").performClick().assertIsSelected()
        composeRule.onNodeWithTag("workout-history-list")
            .performScrollToNode(hasText("Trainingsumfang"))
        composeRule.onNodeWithText("Trainingsumfang").assertIsDisplayed()
    }

    @Test
    fun monthChartShowsSeveralAlignedDatesAcrossTheWholeMonth() {
        composeRule.activity.setContent {
            RunTheme(AppTheme.OLIVE) {
                ProgressVolumeCard(monthDashboard(), loading = false)
            }
        }
        (1..31 step 2).map { "$it." }.forEach { day ->
            composeRule.onNodeWithText(day).assertIsDisplayed()
        }
    }

    private fun monthDashboard() = TrainingProgressDashboard(
        period = ProgressPeriod.MONTH,
        distanceMeters = 21_100.0,
        runCount = 3,
        previousDistanceMeters = null,
        chartBuckets = (1..31).map { day ->
            ProgressDistanceBucket(
                startsOn = LocalDate.of(2026, 10, day),
                label = "$day.",
                distanceMeters = if (day in setOf(5, 6, 9)) 5_000.0 else 0.0,
                runCount = if (day in setOf(5, 6, 9)) 1 else 0
            )
        },
        longestRecentRunMeters = 9_800.0,
        averagePaceSecondsPerKilometer = 449L,
        weeklyDistanceTrendMeters = emptyList(),
        weeklyLongestRunMeters = emptyList(),
        weeklyAveragePaceSecondsPerKilometer = emptyList()
    )
}
