package de.run.app.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoachArtworkCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longDynamicTextStaysReadableWithoutChangingImageProportions() {
        val longMessage = "Du bist schneller als geplant. Laufe beim nächsten Abschnitt ruhiger und achte auf ein angenehmes Gefühl. Das Tempo soll sich weiterhin ruhig anfühlen."
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                Column {
                    CoachArtworkCard(Modifier.testTag("short-coach-card")) {
                        Text("Kurz", Modifier.testTag("short-coach-message"))
                    }
                    CoachArtworkCard(Modifier.testTag("long-coach-card")) {
                        Text(
                            longMessage,
                            Modifier.testTag("long-coach-message"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp)
                        )
                    }
                }
            }
        }

        compose.onAllNodesWithTag("coach-artwork").assertCountEquals(2)
        compose.onNodeWithTag("short-coach-message").assertIsDisplayed()
        compose.onNodeWithText(longMessage).assertIsDisplayed()
        val longBounds = compose.onNodeWithTag("long-coach-card").fetchSemanticsNode().boundsInRoot
        assertTrue("The card keeps the supplied image proportions", kotlin.math.abs(longBounds.width / longBounds.height - 1448f / 1086f) < 0.02f)
        assertTrue("The long text stays within the artwork card", compose.onNodeWithTag("long-coach-message").fetchSemanticsNode().boundsInRoot.bottom < longBounds.bottom)
    }

    @Test fun allAppThemesRenderTheSameCoachCardWithTheirOwnArtworkStyle() {
        compose.setContent {
            Column {
                AppTheme.entries.forEach { theme ->
                    RunTheme(theme) {
                        CoachArtworkCard(Modifier.testTag("coach-card-${theme.name}")) {
                            Text(theme.name)
                        }
                    }
                }
            }
        }

        compose.onAllNodesWithTag("coach-artwork").assertCountEquals(AppTheme.entries.size)
        AppTheme.entries.forEach { theme -> compose.onNodeWithTag("coach-card-${theme.name}").assertIsDisplayed() }
    }

    @Test fun dynamicTextStaysInsideTheBlankFieldAtNaturalImageSize() {
        val message = "Du bist schneller als der geplante Tempobereich. Laufe beim nächsten Abschnitt etwas ruhiger."
        compose.setContent {
            RunTheme(AppTheme.OLIVE) {
                CoachArtworkCard(Modifier.testTag("coach-card")) {
                    Text("DEIN COACH", style = MaterialTheme.typography.labelLarge)
                    Text(
                        message,
                        Modifier.testTag("coach-message"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp)
                    )
                }
            }
        }

        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithTag("coach-artwork").assertIsDisplayed()
        val cardBounds = compose.onNodeWithTag("coach-card").fetchSemanticsNode().boundsInRoot
        val messageBounds = compose.onNodeWithTag("coach-message").fetchSemanticsNode().boundsInRoot
        assertTrue("Text begins inside the illustration's blank card area", messageBounds.left > cardBounds.left + cardBounds.width * 0.28f)
        assertTrue("Text remains inside the card's right edge", messageBounds.right < cardBounds.right)
        assertTrue("Text remains below the illustration's top decoration", messageBounds.top > cardBounds.top + cardBounds.height * 0.30f)
        assertTrue("Text stays within the illustration's open field", messageBounds.bottom < cardBounds.top + cardBounds.height * 0.80f)
    }
}
