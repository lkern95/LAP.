package de.run.app.presentation.plan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import de.run.app.presentation.components.AppPopup
import de.run.app.presentation.theme.LocalCoachArtworkStyle
import kotlinx.coroutines.delay

internal data class CoachDeviationNotice(val id: Long, val message: String)

@Composable
internal fun CoachDeviationPopup(
    notice: CoachDeviationNotice?,
    onDismiss: (Long) -> Unit
) {
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        delay(4_000)
        onDismiss(current.id)
    }
    AppPopup(
        visible = notice != null,
        title = "DEIN COACH",
        onDismiss = { notice?.let { onDismiss(it.id) } },
        coachArtwork = true
    ) {
        val messageSize = when (notice?.message.orEmpty().length) {
            in 0..56 -> 18.sp
            in 57..100 -> 16.sp
            else -> 14.sp
        }
        Text(
            notice?.message.orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = messageSize,
                lineHeight = messageSize * 1.25f
            ),
            color = LocalCoachArtworkStyle.current.messageColor,
            modifier = Modifier.testTag("coach-deviation-notice")
        )
    }
}
