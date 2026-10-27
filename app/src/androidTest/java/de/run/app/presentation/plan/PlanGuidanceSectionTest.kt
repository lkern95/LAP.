package de.run.app.presentation.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanGuidanceSectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun planGuidanceStartsCollapsedAndCanBeExpanded() {
        val explanation = "LAP berechnet den Plan nach aufgezeichneten Läufen neu."
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                PlanGuidanceSection(listOf(explanation))
            }
        }

        compose.onNodeWithText("Wie dein Plan weiterwächst").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText(explanation).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithContentDescription("Wie dein Plan weiterwächst anzeigen").performClick()
        compose.onNodeWithText(explanation).assertIsDisplayed()
    }
}
