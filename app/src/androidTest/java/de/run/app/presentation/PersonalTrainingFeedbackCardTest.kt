package de.run.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import de.run.app.domain.TrainingFeedback
import de.run.app.domain.WorkoutPlanStatus
import de.run.app.domain.WeeklyPlanProgress
import de.run.app.domain.WeeklyTrainingComparison
import de.run.app.domain.TrainingWeekStats
import de.run.app.domain.model.AppTheme
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.RunStep
import de.run.app.presentation.components.PersonalTrainingFeedbackCard
import de.run.app.presentation.theme.RunTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class PersonalTrainingFeedbackCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun separatesTrainingQualityFromWeeklyPlanAndKeepsEvidenceCollapsedUntilRequested() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                PersonalTrainingFeedbackCard(
                    TrainingFeedback(
                        shortSummary = "Die geplante Dauer wurde ungefähr getroffen.",
                        status = WorkoutPlanStatus.WELL_MATCHED,
                        goalSummary = "Dein Halbmarathonziel ist noch nicht seriös einschätzbar.",
                        reasons = listOf("Ist-Dauer: 40 Min."),
                        nextAction = "Als Nächstes: den langen Lauf für 60 Minuten umsetzen.",
                        weeklyFocus = "Nächste Woche die geplanten drei Läufe gleichmäßig verteilen.",
                        weeklyPlanProgress = WeeklyPlanProgress(LocalDate.parse("2026-10-19"), 1, 3, 1),
                        weeklyComparison = comparison(),
                        plannedRun = PlannedRun("Lockerer Lauf", false, listOf(
                            RunStep("Einlaufen", 10),
                            RunStep("Locker laufen", 30, 2, 4)
                        ))
                    )
                )
            }
        }

        compose.onNodeWithText("War das Training gut?").assertIsDisplayed()
        compose.onNodeWithText("Dauer passend · Intensität nur mit RPE beurteilbar").assertIsDisplayed()
        compose.onNodeWithText("Dein Wochenplan").assertIsDisplayed()
        compose.onNodeWithText("Diese Woche: 1 von 3 Läufen erledigt · 2 noch offen.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Dein Halbmarathonziel ist noch nicht seriös einschätzbar.").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Geplant: Lockerer Lauf · 40 Min. · locker · RPE 2–4").assertIsDisplayed()
        compose.onNodeWithText("Als Nächstes: den langen Lauf für 60 Minuten umsetzen.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Fokus nächste Woche:", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Abschnitte und Vorgaben").performClick()
        compose.onNodeWithText("10 Min. Einlaufen").assertIsDisplayed()
        compose.onNodeWithText("Warum diese Bewertung?").performClick()
        compose.onNodeWithText("• Ist-Dauer: 40 Min.").assertIsDisplayed()
    }

    @Test
    fun showsChangedNextUnitAsTheSingleNextAction() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                PersonalTrainingFeedbackCard(
                    TrainingFeedback(
                        shortSummary = "Geplant 45 Min., gemessen 100 Min.: +55 Min.",
                        status = WorkoutPlanStatus.HIGHER_LOAD,
                        goalSummary = "Noch nicht einschätzbar.",
                        reasons = emptyList(),
                        nextAction = "Als Nächstes: Erholungslauf für 15 Minuten.",
                        weeklyFocus = "",
                        planChangeSummary = "Dein Lauf dauerte 55 Min. länger als geplant. Darum wurde die nächste Einheit „Erholungslauf“ auf 15 Min. verkürzt."
                    )
                )
            }
        }

        compose.onNodeWithText(
            "Als Nächstes: Dein Lauf dauerte 55 Min. länger als geplant. Darum wurde die nächste Einheit „Erholungslauf“ auf 15 Min. verkürzt."
        ).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Als Nächstes: Erholungslauf für 15 Minuten.").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun extraRunReportsUnknownQualityAndClearWeeklyCompletionWithoutCallingItWellDone() {
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                PersonalTrainingFeedbackCard(
                    TrainingFeedback(
                        shortSummary = "25 Min. zusätzlich gelaufen. Belastung ohne RPE und Beschwerderückmeldung nicht beurteilbar.",
                        status = WorkoutPlanStatus.EXTRA_RUN,
                        goalSummary = "",
                        reasons = emptyList(),
                        nextAction = "Als Nächstes: Erholungslauf für 25 Minuten laut Wochenplan.",
                        weeklyFocus = "",
                        weeklyPlanProgress = WeeklyPlanProgress(LocalDate.parse("2026-10-19"), 1, 2, 1),
                        qualityHeadline = "Belastung offen"
                    )
                )
            }
        }

        compose.onNodeWithText("Belastung offen").assertIsDisplayed()
        compose.onNodeWithText(
            "25 Min. zusätzlich gelaufen. Belastung ohne RPE und Beschwerderückmeldung nicht beurteilbar."
        ).assertIsDisplayed()
        compose.onNodeWithText("Diese Woche: 1 von 2 Läufen erledigt · 1 noch offen.").assertIsDisplayed()
        compose.onNodeWithText("Als Nächstes: Erholungslauf für 25 Minuten laut Wochenplan.").assertIsDisplayed()
    }

    private fun comparison() = WeeklyTrainingComparison(
        current = TrainingWeekStats(LocalDate.parse("2026-10-12"), 2, 10_000.0, 7_200, 360, 140, 2, 2),
        previous = TrainingWeekStats(LocalDate.parse("2026-10-05"), 1, 5_000.0, 3_600, 370, 142, 1, 1),
        distanceChangePercent = 100,
        feedback = "Der Umfang stieg."
    )
}
