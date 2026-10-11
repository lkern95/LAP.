package de.run.app.presentation.plan

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.run.app.MainActivity
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingOnboardingScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun recurringAvailabilityAllowsAllSevenDaysWithoutSettingSevenRuns() {
        composeRule.activity.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingOnboardingScreen(
                    initial = null,
                    initialGoal = null,
                    initialStep = 2,
                    isSaving = false,
                    saveFailed = false,
                    onSave = { _, _ -> },
                    onCancel = {},
                    onConnectGarmin = {},
                    garminSharingSupported = true
                )
            }
        }
        composeRule.onNodeWithText("Meist feste Tage").performClick()
        listOf("Montag", "Mittwoch", "Freitag", "Samstag").forEach { day ->
            composeRule.onNodeWithText(day, substring = true).performScrollTo().performClick()
        }

        composeRule.onNodeWithText("Montag", substring = true).performScrollTo().assertIsDisplayed()
        assert(composeRule.onAllNodesWithText("Verfügbar").fetchSemanticsNodes().size >= 7)
        composeRule.onNodeWithText("Wie viele Einheiten passen, entscheidet LAP passend zu deinem Ziel und deiner Erholung.", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun flexibleAvailabilityAsksOnlyForTimeLimitsAndNoWeekDates() {
        composeRule.activity.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingOnboardingScreen(
                    initial = null,
                    initialGoal = null,
                    initialStep = 2,
                    isSaving = false,
                    saveFailed = false,
                    onSave = { _, _ -> },
                    onCancel = {},
                    onConnectGarmin = {},
                    garminSharingSupported = true
                )
            }
        }

        composeRule.onNodeWithText("Wie passt Training in deinen Alltag?").assertIsDisplayed()
        composeRule.onNodeWithText("Bis zu 45 Min.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Noch unklar").performScrollTo().assertIsDisplayed()
        assert(composeRule.onAllNodesWithText("Trage nur ein, was du schon weißt.").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun garminIndicatorAppearsAfterConnectTapAndUsesThemeIndependentStatusColors() {
        composeRule.activity.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingOnboardingScreen(
                    initial = null,
                    initialGoal = null,
                    initialStep = 1,
                    isSaving = false,
                    saveFailed = false,
                    onSave = { _, _ -> },
                    onCancel = {},
                    onConnectGarmin = {}
                )
            }
        }
        assert(composeRule.onAllNodesWithContentDescription("Import der Laufdaten fehlgeschlagen").fetchSemanticsNodes().isEmpty())
        val connectLabel = if (Build.VERSION.SDK_INT >= 34) "Garmin verbinden" else "Health Connect freigeben"
        composeRule.onNodeWithText(connectLabel).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Import der Laufdaten fehlgeschlagen").assertIsDisplayed()

        composeRule.activity.setContent {
            RunTheme(AppTheme.DARK) {
                TrainingOnboardingScreen(
                    initial = null,
                    initialGoal = null,
                    initialStep = 1,
                    isSaving = false,
                    saveFailed = false,
                    onSave = { _, _ -> },
                    onCancel = {},
                    onConnectGarmin = {},
                    garminConnected = true,
                    garminSharingSupported = true
                )
            }
        }
        assert(composeRule.onAllNodesWithContentDescription("Import über Health Connect erfolgreich").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Garmin verbinden").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Import über Health Connect erfolgreich").assertIsDisplayed()
    }

    @Test
    fun olderAndroidCanContinueWithoutPromisingGarminSync() {
        composeRule.activity.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingOnboardingScreen(
                    initial = null,
                    initialGoal = null,
                    initialStep = 1,
                    isSaving = false,
                    saveFailed = false,
                    onSave = { _, _ -> },
                    onCancel = {},
                    onConnectGarmin = { error("Garmin sharing must not be requested on older Android") },
                    garminSharingSupported = false
                )
            }
        }

        composeRule.onNodeWithText("Garmin Connect gibt Läufe auf diesem Android nicht direkt an Health Connect weiter.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Du kannst Health Sync als lokale Brücke zu Health Connect nutzen oder FIT-Dateien später unter Fortschritt importieren.", substring = true)
            .assertIsDisplayed()
        assert(composeRule.onAllNodesWithText("Garmin verbinden").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun raceDistanceCanBeChangedToMarathon() {
        composeRule.activity.setContent {
            RunTheme(AppTheme.OLIVE) {
                TrainingOnboardingScreen(
                    initial = null,
                    initialGoal = null,
                    initialStep = 0,
                    isSaving = false,
                    saveFailed = false,
                    onSave = { _, _ -> },
                    onCancel = {},
                    onConnectGarmin = {}
                )
            }
        }
        composeRule.onNodeWithText("Wettkampfstrecke in km").performClick()
        composeRule.onNodeWithText("Wettkampfstrecke in km").performTextClearance()
        composeRule.onNodeWithText("Wettkampfstrecke in km").performTextInput("42,2")
        composeRule.onNodeWithText("Marathon").assertIsDisplayed()
    }
}
