package de.run.app.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.run.app.domain.ForecastConfidence
import de.run.app.domain.GoalOutlook
import de.run.app.domain.HalfMarathonForecast
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Rule
import org.junit.Test

class WorkoutGoalProjectionCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailCardShowsSharedBroadForecastWhenPerformanceEvidenceIsLimited() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutGoalProjectionCard(forecast())
            }
        }

        compose.onNodeWithText("Halbmarathon-Prognose").assertIsDisplayed()
        compose.onNodeWithText("Grobe Trainingsorientierung: 2:05 h–2:45 h").assertIsDisplayed()
        compose.onNodeWithText("Große Unsicherheit; Trainingspace ist keine Wettkampfpace.").assertIsDisplayed()
    }

    private fun forecast() = HalfMarathonForecast(
        estimatedMinutes = 125..165,
        evidenceRuns = 1,
        weeksRemaining = 12,
        recentRunsPerWeek = 2,
        longestRunKilometers = 10.0,
        targetOutlook = GoalOutlook.NOT_ENOUGH_DATA,
        explanation = "Grobe Orientierung.",
        recommendation = "Als Nächstes den langen Lauf umsetzen.",
        recentDistanceKilometers = 25.0,
        previousDistanceKilometers = 20.0,
        recentRunCount = 6,
        previousRunCount = 5,
        recentLongestRunKilometers = 10.0,
        dataQuality = "Grobe Orientierung",
        confidence = ForecastConfidence.TRAINING_ORIENTATION,
        previousLongestRunKilometers = 8.0,
        outlookSummary = "Der Ausdaueraufbau ist auf Kurs."
    )
}
