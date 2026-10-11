package de.run.app.presentation.settings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminWatchGeometryTest {
    @Test
    fun `ring center follows fitted watch image at multiple sizes`() {
        val containers = listOf(
            DpSize(180.dp, 250.dp),
            DpSize(240.dp, 250.dp),
            DpSize(320.dp, 180.dp)
        )

        containers.forEach { container ->
            val geometry = calculateWatchOverlayGeometry(container, imageAspectRatio = 1f)
            val ringCenterX = geometry.ringTopLeft.x + geometry.ringSize / 2
            val ringCenterY = geometry.ringTopLeft.y + geometry.ringSize / 2

            assertEquals(geometry.displayCenter.x.value, ringCenterX.value, 0.001f)
            assertEquals(geometry.displayCenter.y.value, ringCenterY.value, 0.001f)
            assertTrue(geometry.ringTopLeft.x >= geometry.imageTopLeft.x)
            assertTrue(geometry.ringTopLeft.y >= geometry.imageTopLeft.y)
            assertTrue(geometry.ringTopLeft.x + geometry.ringSize <= geometry.imageTopLeft.x + geometry.imageSize.width)
            assertTrue(geometry.ringTopLeft.y + geometry.ringSize <= geometry.imageTopLeft.y + geometry.imageSize.height)
        }
    }
}
