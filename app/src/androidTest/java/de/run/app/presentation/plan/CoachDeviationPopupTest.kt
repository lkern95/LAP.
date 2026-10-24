package de.run.app.presentation.plan

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class CoachDeviationPopupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun warningShowsTheSharedTextAndDismissesAfterAboutFourSeconds() {
        val message = "Du bist schneller als der geplante Tempobereich. Laufe etwas ruhiger."
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                var notice by remember { mutableStateOf<CoachDeviationNotice?>(CoachDeviationNotice(1, message)) }
                CoachDeviationPopup(notice) { id -> if (notice?.id == id) notice = null }
            }
        }

        compose.onNodeWithText(message).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4_500)
        compose.waitUntil(1_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty() }
        assertTrue(compose.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty())
    }
}
