package de.run.app.presentation

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.run.app.R
import kotlinx.coroutines.delay

private val InterVariable = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.ExtraLight),
    Font(R.font.inter_variable, weight = FontWeight.Black)
)

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    var titleVisible by remember { mutableStateOf(!animationsEnabled) }
    var taglineVisible by remember { mutableStateOf(!animationsEnabled) }

    LaunchedEffect(animationsEnabled) {
        if (animationsEnabled) {
            delay(180)
            titleVisible = true
            delay(350)
            taglineVisible = true
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF3F2EF)))
        LoopingBackgroundVideo(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = titleVisible,
                enter = if (animationsEnabled) fadeIn() + slideInVertically { it / 3 } else EnterTransition.None
            ) {
                Text(
                    text = "LAP.",
                    color = Color.White,
                    fontFamily = InterVariable,
                    fontWeight = FontWeight.Black,
                    fontSize = 62.sp,
                    letterSpacing = 1.5.sp,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.32f),
                            offset = Offset(0f, 2f),
                            blurRadius = 5f
                        )
                    )
                )
            }
            AnimatedVisibility(
                visible = taglineVisible,
                enter = if (animationsEnabled) fadeIn() + slideInVertically { it / 4 } else EnterTransition.None
            ) {
                Text(
                    text = "your running PAL.",
                    color = Color.White.copy(alpha = 0.88f),
                    fontFamily = InterVariable,
                    fontWeight = FontWeight.ExtraLight,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.36f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
        ) {
            Text("Los geht's", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LoopingBackgroundVideo(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val textureView = remember(context) { TextureView(context).apply { isOpaque = false } }

    DisposableEffect(textureView, lifecycleOwner) {
        var player: MediaPlayer? = null
        var surface: Surface? = null
        var isPrepared = false

        fun updateCrop(width: Int, height: Int) {
            val video = player ?: return
            val videoWidth = video.videoWidth
            val videoHeight = video.videoHeight
            if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
            val videoAspectRatio = videoWidth / videoHeight.toFloat()
            val viewAspectRatio = width / height.toFloat()
            val scaleX = minOf(videoAspectRatio / viewAspectRatio, 1f)
            val scaleY = minOf(viewAspectRatio / videoAspectRatio, 1f)
            val transform = Matrix().apply {
                setScale(scaleX, scaleY, width / 2f, height / 2f)
            }
            textureView.setTransform(transform)
        }

        val surfaceListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                val videoSurface = Surface(texture)
                val mediaPlayer = MediaPlayer()
                player = mediaPlayer
                surface = videoSurface
                try {
                    mediaPlayer.setDataSource(
                        context,
                        "android.resource://${context.packageName}/${R.raw.welcome}".toUri()
                    )
                    mediaPlayer.setSurface(videoSurface)
                    mediaPlayer.setVolume(0f, 0f)
                    mediaPlayer.isLooping = true
                    mediaPlayer.setOnVideoSizeChangedListener { _, _, _ ->
                        updateCrop(textureView.width, textureView.height)
                    }
                    mediaPlayer.setOnPreparedListener { preparedPlayer ->
                        isPrepared = true
                        updateCrop(textureView.width, textureView.height)
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            preparedPlayer.start()
                        }
                    }
                    mediaPlayer.setOnErrorListener { _, _, _ ->
                        true
                    }
                    mediaPlayer.prepareAsync()
                } catch (_: Exception) {
                    mediaPlayer.release()
                    player = null
                    videoSurface.release()
                    surface = null
                }
            }

            override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                updateCrop(width, height)
            }

            override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean = true

            override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
        }

        textureView.surfaceTextureListener = surfaceListener
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            val currentPlayer = player ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> if (currentPlayer.isPlaying) currentPlayer.pause()
                Lifecycle.Event.ON_START -> if (isPrepared && !currentPlayer.isPlaying) currentPlayer.start()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            textureView.surfaceTextureListener = null
            player?.release()
            surface?.release()
        }
    }

    AndroidView(factory = { textureView }, modifier = modifier)
}
