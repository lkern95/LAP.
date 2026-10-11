package de.run.app.presentation.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize

internal data class GarminWatchOverlayGeometry(
    val imageTopLeft: DpOffset,
    val imageSize: DpSize,
    val displayCenter: DpOffset,
    val ringTopLeft: DpOffset,
    val ringSize: Dp
)

/** Display center measured in the original 1254 × 1254 Garmin image. */
internal const val GARMIN_DISPLAY_CENTER_X = 0.501f
internal const val GARMIN_DISPLAY_CENTER_Y = 0.471f
internal const val GARMIN_RING_SIZE_FRACTION = 0.304f

internal fun calculateWatchOverlayGeometry(
    containerSize: DpSize,
    imageAspectRatio: Float
): GarminWatchOverlayGeometry {
    require(containerSize.width.value > 0f && containerSize.height.value > 0f)
    require(imageAspectRatio > 0f)

    val containerRatio = containerSize.width.value / containerSize.height.value
    val imageSize = if (containerRatio > imageAspectRatio) {
        DpSize(containerSize.height * imageAspectRatio, containerSize.height)
    } else {
        DpSize(containerSize.width, containerSize.width / imageAspectRatio)
    }
    val imageTopLeft = DpOffset(
        (containerSize.width - imageSize.width) / 2,
        (containerSize.height - imageSize.height) / 2
    )
    val displayCenter = DpOffset(
        imageTopLeft.x + imageSize.width * GARMIN_DISPLAY_CENTER_X,
        imageTopLeft.y + imageSize.height * GARMIN_DISPLAY_CENTER_Y
    )
    val ringSize = imageSize.height * GARMIN_RING_SIZE_FRACTION

    return GarminWatchOverlayGeometry(
        imageTopLeft = imageTopLeft,
        imageSize = imageSize,
        displayCenter = displayCenter,
        ringTopLeft = DpOffset(displayCenter.x - ringSize / 2, displayCenter.y - ringSize / 2),
        ringSize = ringSize
    )
}
