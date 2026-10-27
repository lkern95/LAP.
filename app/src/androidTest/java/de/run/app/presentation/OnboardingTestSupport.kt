package de.run.app.presentation

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import de.run.app.MainActivity

internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.finishFirstRunIfNeeded() {
    waitForIdle()
    onAllNodesWithText("Los geht's").fetchSemanticsNodes().firstOrNull()?.let {
        onNodeWithText("Los geht's").performClick()
    }
    waitUntil(timeoutMillis = 8_000) {
        onAllNodesWithText("Dein Ziel").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("Heute").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("Deine Erholungsdaten").fetchSemanticsNodes().isNotEmpty()
    }
    if (onAllNodesWithText("Dein Ziel").fetchSemanticsNodes().isNotEmpty()) {
        repeat(3) { onNodeWithContentDescription("Einrichtung fortsetzen").performClick() }
        onNodeWithContentDescription("Plan erstellen").performClick()
        waitUntil(timeoutMillis = 8_000) {
            onAllNodesWithText("Deine Erholungsdaten").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
