package de.run.app.presentation.history

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.run.app.MainActivity
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityHistoryFiltersTest {
    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<MainActivity>()

    @Test
    fun showsAvailableYearAndMonthFiltersWithoutSportFilter() {
        var chosenYear = 0
        var chosenMonth = 0
        composeRule.activity.setContent {
            RunTheme(AppTheme.OLIVE) {
                ActivityHistoryFilters(
                    years = listOf(2026, 2025),
                    months = listOf(10, 9),
                    selectedYear = 2026,
                    selectedMonth = 10,
                    monthName = { if (it == 10) "Oktober" else "September" },
                    onYearSelected = { chosenYear = it },
                    onMonthSelected = { chosenMonth = it }
                )
            }
        }

        composeRule.onNodeWithText("Jahr").assertIsDisplayed()
        composeRule.onNodeWithText("Monat").assertIsDisplayed()
        composeRule.onNodeWithText("Oktober").assertIsDisplayed()
        composeRule.onNodeWithText("2026").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sportart").assertCountEquals(0)
        composeRule.onAllNodesWithText("Alle").assertCountEquals(0)
        composeRule.onAllNodesWithTag("history-year-popup").assertCountEquals(0)
        composeRule.onAllNodesWithTag("history-month-popup").assertCountEquals(0)

        composeRule.onNodeWithTag("history-year-anchor").performClick()
        composeRule.onNodeWithTag("history-year-popup").assertIsDisplayed()
        assertDropdownMatchesAnchor("history-year")
        composeRule.onNodeWithText("2025").performClick()
        composeRule.runOnIdle { assert(chosenYear == 2025) }
        composeRule.onAllNodesWithTag("history-year-popup").assertCountEquals(0)

        composeRule.onNodeWithTag("history-month-anchor").performClick()
        composeRule.onNodeWithTag("history-month-popup").assertIsDisplayed()
        assertDropdownMatchesAnchor("history-month")
        composeRule.onNodeWithText("September").performClick()
        composeRule.runOnIdle { assert(chosenMonth == 9) }
        composeRule.onAllNodesWithTag("history-month-popup").assertCountEquals(0)
    }

    private fun assertDropdownMatchesAnchor(tag: String) {
        val anchor = composeRule.onNodeWithTag("$tag-anchor").fetchSemanticsNode().boundsInRoot.width
        val popup = composeRule.onNodeWithTag("$tag-popup").fetchSemanticsNode().boundsInRoot.width
        assert(kotlin.math.abs(anchor - popup) <= 1f)
    }
}
