package de.run.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
        compose.onNodeWithText("Auf Kurs: 6 Läufe in vier Wochen, längster Lauf 12,0 km.").assertIsDisplayed()
        assert(compose.onAllNodesWithText("6 Läufe, 40,0 km in 4 Wochen; längster Lauf 12,0 km. Davor keine Vergleichswerte.").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Bin ich auf Kurs? anzeigen").performClick()
        compose.onNodeWithText("6 Läufe, 40,0 km in 4 Wochen; längster Lauf 12,0 km. Davor keine Vergleichswerte.").assertIsDisplayed()
        compose.onNodeWithText("Nächster Schritt: den lockeren Lauf umsetzen.").assertIsDisplayed()
        compose.onNodeWithText("3 lange Läufe zeigen deine Ausdauer. Für eine Zielzeit fehlt noch ein Test- oder Wettkampflauf.").assertIsDisplayed()
        assert(compose.onAllNodesWithText("Grobe Trainingsorientierung: 2:05–2:45 Std.").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("In den letzten vier Wochen kamen 40,0 km zusammen.").assertDoesNotExist()

        compose.onNodeWithText("Warum diese Einschätzung?").performClick()
        compose.onNodeWithText("In den letzten vier Wochen kamen 40,0 km zusammen; davor liegen keine vergleichbaren Läufe.").assertIsDisplayed()
    }

    private fun forecast() = HalfMarathonForecast(
        estimatedMinutes = null,
        evidenceRuns = 3,
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
        outlookSummary = "Auf Kurs: 6 Läufe in vier Wochen, längster Lauf 12,0 km."
    )
}
