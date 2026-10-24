package de.run.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.run.app.domain.ForecastConfidence
import de.run.app.domain.GoalOutlook
import de.run.app.domain.HalfMarathonForecast
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Rule
import org.junit.Test

class HalfMarathonForecastCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun todayKeepsTheCourseCheckShortAndPutsItsEvidenceBehindWhy() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                HalfMarathonForecastCard(forecast())
            }
        }

        compose.onNodeWithText("Bin ich auf Kurs?").assertIsDisplayed()
        compose.onNodeWithText("Der Ausdaueraufbau fürs Durchlaufen ist auf Kurs.").assertIsDisplayed()
        compose.onNodeWithText("6 Läufe in vier Wochen · längster Lauf 12,0 km").assertIsDisplayed()
        compose.onNodeWithText("Grobe Trainingsorientierung: 2:05–2:45 Std.").assertIsDisplayed()
        compose.onNodeWithText("In den letzten vier Wochen kamen 40,0 km zusammen.").assertDoesNotExist()

        compose.onNodeWithText("Warum diese Einschätzung?").performClick()
        compose.onNodeWithText("In den letzten vier Wochen kamen 40,0 km zusammen; davor liegen keine vergleichbaren Läufe.").assertIsDisplayed()
    }

    private fun forecast() = HalfMarathonForecast(
        estimatedMinutes = 125..165,
        evidenceRuns = 1,
        weeksRemaining = 19,
        recentRunsPerWeek = 2,
        longestRunKilometers = 12.0,
        targetOutlook = GoalOutlook.NOT_ENOUGH_DATA,
        explanation = "In den letzten vier Wochen kamen 40,0 km zusammen; davor liegen keine vergleichbaren Läufe.",
        recommendation = "Als Nächstes den lockeren Lauf umsetzen.",
        recentDistanceKilometers = 40.0,
        previousDistanceKilometers = 0.0,
        recentRunCount = 6,
        previousRunCount = 0,
        recentLongestRunKilometers = 12.0,
        dataQuality = "Grobe Orientierung",
        confidence = ForecastConfidence.TRAINING_ORIENTATION,
        previousLongestRunKilometers = 0.0,
        outlookSummary = "Der Ausdaueraufbau fürs Durchlaufen ist auf Kurs."
    )
}
