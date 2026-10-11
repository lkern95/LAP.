package de.run.app.presentation.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Rule
import org.junit.Test

class AppPopupTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedPopupOpensAndClosesAroundItsBackgroundContent() {
        composeRule.setContent {
            RunTheme(AppTheme.OLIVE) {
                PopupFixture()
            }
        }

        composeRule.onAllNodesWithText("Laufdetails").assertCountEquals(0)
        composeRule.onNodeWithText("Öffnen").performClick()
        composeRule.onNodeWithText("Laufdetails").assertIsDisplayed()
        composeRule.onNodeWithText("Dauer").assertIsDisplayed()
        composeRule.onNodeWithText("Schließen").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Laufdetails").fetchSemanticsNodes().isEmpty()
        }
    }

    @Composable
    private fun PopupFixture() {
        var visible by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
            Button(onClick = { visible = true }) { Text("Öffnen") }
            AppPopup(
                visible = visible,
                title = "Laufdetails",
                onDismiss = { visible = false }
            ) {
                Text("Dauer")
                Button(onClick = { visible = false }) { Text("Schließen") }
            }
        }
    }
}
