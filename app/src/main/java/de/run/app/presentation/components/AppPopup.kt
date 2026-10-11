package de.run.app.presentation.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

/** Shared full-screen popup with the app's common motion, dimming, and platform blur. */
@Composable
fun AppPopup(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    var dialogShown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) dialogShown = true
        else if (dialogShown) {
            delay(POPUP_EXIT_MILLIS)
            dialogShown = false
        }
    }
    if (dialogShown) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val view = LocalView.current
            SideEffect {
                val window = (view.parent as? DialogWindowProvider)?.window
                if (window != null) {
                    window.setDimAmount(0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.attributes = window.attributes.apply { blurBehindRadius = POPUP_BLUR_RADIUS }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = POPUP_SCRIM_ALPHA))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "Popup schließen" },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + scaleIn(initialScale = 0.96f),
                    exit = fadeOut() + scaleOut(targetScale = 0.96f)
                ) {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .clickable(onClick = {}),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = onDismiss) { Text("×", style = MaterialTheme.typography.titleLarge) }
                            }
                            content()
                        }
                    }
                }
            }
        }
    }
}

private const val POPUP_EXIT_MILLIS = 220L
private const val POPUP_BLUR_RADIUS = 18
private const val POPUP_SCRIM_ALPHA = 0.22f
