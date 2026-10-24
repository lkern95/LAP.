package de.run.app.presentation.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import de.run.app.R
import de.run.app.domain.model.AppTheme

@Immutable
data class CoachArtworkStyle(
    @param:DrawableRes val artworkResource: Int,
    val artworkAspectRatio: Float,
    val titleColor: Color,
    val messageColor: Color,
    val contentStartFraction: Float = 0.32f,
    val contentEndFraction: Float = 0.065f,
    val contentTopFraction: Float = 0.37f,
    val contentBottomFraction: Float = 0.20f
)

val LocalCoachArtworkStyle = staticCompositionLocalOf {
    coachArtworkStyle(AppTheme.OLIVE)
}

fun coachArtworkStyle(theme: AppTheme): CoachArtworkStyle = when (theme) {
    AppTheme.OLIVE -> CoachArtworkStyle(
        artworkResource = R.drawable.coach_card_empty,
        artworkAspectRatio = 1448f / 1086f,
        titleColor = Color(0xFF1A1C18),
        messageColor = Color(0xFF1A1C18)
    )
    AppTheme.DARK -> CoachArtworkStyle(
        artworkResource = R.drawable.coach_card_empty,
        artworkAspectRatio = 1448f / 1086f,
        titleColor = Color(0xFF1A1C18),
        messageColor = Color(0xFF1A1C18),
        contentTopFraction = 0.365f
    )
    AppTheme.LIGHT -> CoachArtworkStyle(
        artworkResource = R.drawable.coach_card_empty,
        artworkAspectRatio = 1448f / 1086f,
        titleColor = Color(0xFF1A1C18),
        messageColor = Color(0xFF1A1C18),
        contentTopFraction = 0.365f
    )
}
