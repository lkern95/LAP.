package de.run.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import de.run.app.domain.model.AppTheme
import de.run.app.presentation.theme.RunTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GarminWatchAlignmentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ringCenterDoesNotMoveAcrossImageSizesOrAnimationFrames() {
        val displaySizes = listOf(
            DpSize(180.dp, 250.dp),
            DpSize(240.dp, 250.dp),
            DpSize(320.dp, 180.dp)
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    displaySizes.forEach { displaySize ->
                        GarminWatch(
                            loading = true,
                            connected = false,
                            failed = false,
                            modifier = Modifier.size(displaySize.width, displaySize.height)
                        )
                    }
                }
            }
        }

        val expectedCenters: List<Pair<Float, Float>> = displaySizes.mapIndexed { index, displaySize ->
            val expected = calculateWatchOverlayGeometry(displaySize, imageAspectRatio = 1f).displayCenter
            val precedingHeight = displaySizes.take(index).fold(0f) { total, preceding ->
                total + preceding.height.value
            }
            expected.x.value to (expected.y.value + precedingHeight)
        }
        listOf(0L, 225L, 450L, 675L).forEach { frameTime ->
            composeRule.mainClock.advanceTimeBy(frameTime)
            val ringBounds = composeRule.onAllNodesWithContentDescription("Laufdaten werden gesucht")
                .fetchSemanticsNodes()
            assertEquals(displaySizes.size, ringBounds.size)
            ringBounds.forEachIndexed { index, node ->
                assertEquals(expectedCenters[index].first, node.boundsInRoot.center.x, 1f)
                assertEquals(expectedCenters[index].second, node.boundsInRoot.center.y, 1f)
            }
        }
    }

    @Test
    fun successAndFailureExposeTheirCenterStatus() {
        val connected = mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                RunTheme(AppTheme.OLIVE) {
                    GarminWatch(
                        loading = false,
                        connected = connected.value,
                        failed = !connected.value,
                        modifier = Modifier.size(240.dp, 250.dp)
                    )
                }
            }
        }
        assertStatusDescription("Import der Laufdaten fehlgeschlagen")
        composeRule.mainClock.advanceTimeBy(400)
        assertPixelColor(red = true)
        composeRule.runOnIdle { connected.value = true }
        assertStatusDescription("Health-Connect-Prüfung erfolgreich")
        composeRule.mainClock.advanceTimeBy(800)
        assertPixelColor(red = false)
    }

    @Test
    fun tappingTheWatchRequestsAnotherHealthDataCheck() {
        var retryCount = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                RunTheme(AppTheme.OLIVE) {
                    GarminWatch(
                        loading = false,
                        connected = false,
                        failed = false,
                        modifier = Modifier.size(240.dp, 250.dp),
                        onRetrySync = { retryCount++ }
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Garmin-Laufdaten jetzt prüfen").performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun pressingTheWatchDoesNotFlashAGrayRectangle() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                RunTheme(AppTheme.OLIVE) {
                    GarminWatch(
                        loading = false,
                        connected = false,
                        failed = false,
                        modifier = Modifier.size(240.dp, 250.dp),
                        onRetrySync = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val watch = composeRule.onNodeWithContentDescription("Garmin-Laufdaten jetzt prüfen")
        val before = watch.captureToImage().toPixelMap()[2, 2]

        watch.performTouchInput {
            down(center)
            advanceEventTime(150)
        }
        val whilePressed = watch.captureToImage().toPixelMap()[2, 2]
        watch.performTouchInput { up() }

        assertEquals(before, whilePressed)
    }

    private fun assertPixelColor(red: Boolean) {
        val geometry = calculateWatchOverlayGeometry(DpSize(240.dp, 250.dp), imageAspectRatio = 1f)
        val sampleX = if (red) 0.50f else 0.59f
        val sampleY = if (red) 0.50f else 0.51f
        val x = (geometry.ringTopLeft.x.value + geometry.ringSize.value * sampleX).toInt()
        val y = (geometry.ringTopLeft.y.value + geometry.ringSize.value * sampleY).toInt()
        val pixel = composeRule.onNodeWithContentDescription("Schwarze Garmin-Uhr")
            .captureToImage().toPixelMap()[x, y]
        if (red) assertTrue(pixel.red > pixel.green) else assertTrue(pixel.green > pixel.red)
    }

    private fun assertStatusDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
