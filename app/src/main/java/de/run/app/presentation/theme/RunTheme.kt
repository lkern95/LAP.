package de.run.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import de.run.app.domain.model.AppTheme

val LocalCautionColor = staticCompositionLocalOf { Color(0xFF856000) }

private val OliveScheme = lightColorScheme(
    primary = Color(0xFF344535),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE8D8),
    onPrimaryContainer = Color(0xFF1E2C20),
    secondary = Color(0xFF667664),
    secondaryContainer = Color(0xFFDDE8D8),
    onSecondaryContainer = Color(0xFF1E2C20),
    tertiary = Color(0xFFC63F6C),
    background = Color(0xFFF8F6F0),
    surface = Color(0xFFFFFDF8),
    surfaceVariant = Color(0xFFECEBE4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F5EF),
    surfaceContainer = Color(0xFFF1EFE8),
    surfaceContainerHigh = Color(0xFFECEAE2),
    surfaceContainerHighest = Color(0xFFE6E4DC),
    surfaceDim = Color(0xFFE4E2DA),
    surfaceBright = Color(0xFFFFFDF8),
    outline = Color(0xFF73786D),
    outlineVariant = Color(0xFFC7C9BE),
    onSurface = Color(0xFF1A1C18),
    onSurfaceVariant = Color(0xFF484A43),
    error = Color(0xFFBA1A1A)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB6C9AD),
    onPrimary = Color(0xFF203021),
    primaryContainer = Color(0xFF354A36),
    onPrimaryContainer = Color(0xFFD2E6C8),
    secondary = Color(0xFFBECAB9),
    secondaryContainer = Color(0xFF3E4A3C),
    onSecondaryContainer = Color(0xFFE0E8DB),
    tertiary = Color(0xFFFFB1C8),
    background = Color(0xFF111410),
    surface = Color(0xFF191D17),
    surfaceVariant = Color(0xFF41463D),
    surfaceContainerLowest = Color(0xFF0C100B),
    surfaceContainerLow = Color(0xFF1D221B),
    surfaceContainer = Color(0xFF22271F),
    surfaceContainerHigh = Color(0xFF282D25),
    surfaceContainerHighest = Color(0xFF33382F),
    surfaceDim = Color(0xFF111410),
    surfaceBright = Color(0xFF393F35),
    outline = Color(0xFF899185),
    outlineVariant = Color(0xFF41483F),
    onSurface = Color(0xFFE3E4DB),
    onSurfaceVariant = Color(0xFFC2C7BB),
    error = Color(0xFFFFB4AB)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF344535),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5EAE2),
    onPrimaryContainer = Color(0xFF1D2B1F),
    secondary = Color(0xFF58645A),
    secondaryContainer = Color(0xFFE5EAE2),
    onSecondaryContainer = Color(0xFF1D2B1F),
    tertiary = Color(0xFFC63F6C),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F1EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F8F5),
    surfaceContainer = Color(0xFFF3F3EF),
    surfaceContainerHigh = Color(0xFFEEEEEA),
    surfaceContainerHighest = Color(0xFFE8E8E3),
    surfaceDim = Color(0xFFE1E2DC),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFF73766F),
    outlineVariant = Color(0xFFC8CAC3),
    onSurface = Color(0xFF1A1C18),
    onSurfaceVariant = Color(0xFF454942),
    error = Color(0xFFBA1A1A)
)

@Composable
fun RunTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors = remember(theme) {
        when (theme) {
            AppTheme.OLIVE -> OliveScheme
            AppTheme.DARK -> DarkScheme
            AppTheme.LIGHT -> LightScheme
        }
    }
    val cautionColor = when (theme) {
        AppTheme.OLIVE -> Color(0xFF856000)
        AppTheme.DARK -> Color(0xFFE4C15A)
        AppTheme.LIGHT -> Color(0xFF7A5700)
    }
    CompositionLocalProvider(
        LocalCoachArtworkStyle provides coachArtworkStyle(theme),
        LocalCautionColor provides cautionColor
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
