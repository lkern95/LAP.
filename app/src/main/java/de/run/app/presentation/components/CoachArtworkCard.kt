package de.run.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import de.run.app.presentation.theme.CoachArtworkStyle
import de.run.app.presentation.theme.LocalCoachArtworkStyle

/** Draws the supplied empty card art at its natural proportions; coach text stays native and dynamic. */
@Composable
fun CoachArtworkCard(
    modifier: Modifier = Modifier,
    style: CoachArtworkStyle = LocalCoachArtworkStyle.current,
    consumeClicks: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(if (consumeClicks) Modifier.clickable(onClick = {}) else Modifier)
            .testTag(COACH_ARTWORK_CARD_TAG)
    ) {
        val cardWidth = maxWidth
        val artworkHeight = cardWidth / style.artworkAspectRatio
        Box(Modifier.fillMaxWidth().aspectRatio(style.artworkAspectRatio)) {
            Spacer(Modifier.matchParentSize())
            Image(
                painter = painterResource(style.artworkResource),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
                    .testTag(COACH_ARTWORK_TAG)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = cardWidth * style.contentStartFraction,
                        top = artworkHeight * style.contentTopFraction,
                        end = cardWidth * style.contentEndFraction,
                        bottom = artworkHeight * style.contentBottomFraction
                    ),
                content = content
            )
        }
    }
}

const val COACH_ARTWORK_TAG = "coach-artwork"
const val COACH_ARTWORK_CARD_TAG = "coach-artwork-card"
