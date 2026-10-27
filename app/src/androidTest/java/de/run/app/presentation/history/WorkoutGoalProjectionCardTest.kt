package de.run.app.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test fun detailCardDoesNotPresentSharedEnduranceDataAsThisRunsTimeForecast() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutGoalProjectionCard(forecast())
            }
        }

        compose.onNodeWithText("Deine Ausdauerbasis").assertIsDisplayed()
        compose.onNodeWithText("3 lange Läufe zeigen deine Ausdauer. Für eine Zielzeit fehlt noch ein Test- oder Wettkampflauf.").assertIsDisplayed()
        assert(compose.onAllNodesWithText("Grobe Trainingsorientierung: 2:05 h–2:45 h").fetchSemanticsNodes().isEmpty())
    }

    @Test fun detailCardExplainsWhyAShortRunCannotProduceATimeRange() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutGoalProjectionCard(forecast().copy(estimatedMinutes = null, confidence = null))
            }
        }

        compose.onNodeWithText("Für eine Zielzeit fehlt ein Lauf über 5 km, den du als sehr anstrengend oder als Test/Wettkampf erfasst hast.").assertIsDisplayed()
    }

    @Test fun performanceRangeNamesItsSharedEvidenceInsteadOfImplyingThisRunWasTheSource() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                WorkoutGoalProjectionCard(forecast().copy(
                    estimatedMinutes = 125..145,
                    evidenceRuns = 2,
                    confidence = ForecastConfidence.ROUGH
                ))
            }
        }

        compose.onNodeWithText("Halbmarathon-Zeitspanne").assertIsDisplayed()
        compose.onNodeWithText("Grobe Zeitspanne: 2:05 h–2:25 h").assertIsDisplayed()
        compose.onNodeWithText("Aus 2 aktuellen Test-, Wettkampf- oder anstrengenden Läufen. Keine genaue Vorhersage.").assertIsDisplayed()
    }

    private fun forecast() = HalfMarathonForecast(
        estimatedMinutes = null,
        evidenceRuns = 3,
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
