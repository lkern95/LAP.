package de.run.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.run.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeBrandingAndSkipActionAreVisible() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("your running PAL.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("LAP.").assertIsDisplayed()
        composeRule.onNodeWithText("your running PAL.").assertIsDisplayed()
        composeRule.onNodeWithText("Los geht's").assertIsDisplayed()
    }
}
