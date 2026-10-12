package com.lapcevichme.ambientlight

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lapcevichme.ambientlight.ambient.AmbientBackdropView
import com.lapcevichme.ambientlight.player.InterceptingTextureView
import com.lapcevichme.ambientlight.ui.theme.AmbientLightTheme

import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmbientLightTheme(darkTheme = true) {
                AmbientSandboxScreen()
            }
        }
    }
}

data class SampleVideo(val title: String, val uriString: (packageName: String) -> String)

private val SAMPLE_VIDEOS = listOf(
    SampleVideo("Оффлайн демо (Цвета + Letterbox)", { pkg -> "android.resource://$pkg/${R.raw.sample_ambient}" }),
    SampleVideo("Big Buck Bunny (Онлайн)", { _ -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }),
    SampleVideo("Tears of Steel (Онлайн)", { _ -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4" }),
    SampleVideo("Elephants Dream (Онлайн)", { _ -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" })
)

private fun queryDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Throwable) {
            // Fall back to URI segment
        }
    }
    return uri.lastPathSegment
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSandboxScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Ambient settings state
    var isEnabled by remember { mutableStateOf(true) }
    var spreadLimit by remember { mutableFloatStateOf(0.55f) }
    var brightness by remember { mutableFloatStateOf(1.0f) }
    var vibrance by remember { mutableFloatStateOf(0.40f) }
    var blurRadius by remember { mutableFloatStateOf(90f) }
    var flickerReduction by remember { mutableIntStateOf(50) }
    var currentVideoIndex by remember { mutableIntStateOf(0) }
    var customVideoUri by remember { mutableStateOf<Uri?>(null) }
    var customVideoName by remember { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // Playback state
    var playbackStatus by remember { mutableStateOf("Загрузка...") }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var framesCount by remember { mutableStateOf(0L) }

    // ExoPlayer lifecycle management
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }

    val activeVideoTitle = customVideoName ?: SAMPLE_VIDEOS[currentVideoIndex].title

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "Видео из галереи"
            customVideoName = name
            if (customVideoUri == uri) {
                playbackError = null
                exoPlayer.seekTo(0)
                exoPlayer.play()
            } else {
                customVideoUri = uri
            }
        }
    }

    // Shared Views & Buffers attached to the player stage
    val backdropView = remember { AmbientBackdropView(context) }
    val textureView = remember { InterceptingTextureView(context) }
    val frameBufferBitmap = remember { Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888) }

    // Handle Orientation and System Bars for Fullscreen
    DisposableEffect(isFullscreen) {
        activity?.let { act ->
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackStatus = when (playbackState) {
                    Player.STATE_IDLE -> "Остановлен"
                    Player.STATE_BUFFERING -> "Буферизация..."
                    Player.STATE_READY -> "Воспроизведение"
                    Player.STATE_ENDED -> "Завершено"
                    else -> "Неизвестно"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Сбой воспроизведения: ${error.errorCodeName}"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(currentVideoIndex, customVideoUri) {
        playbackError = null
        backdropView.reset()
        val mediaUri = customVideoUri ?: Uri.parse(SAMPLE_VIDEOS[currentVideoIndex].uriString(context.packageName))
        exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    DisposableEffect(Unit) {
        textureView.onFrameUpdatedListener = { view ->
            view.getBitmap(frameBufferBitmap)
            backdropView.processFrame(frameBufferBitmap)
        }
        exoPlayer.setVideoTextureView(textureView)

        backdropView.onDominantColorListener = { _ ->
            framesCount = backdropView.framesProcessed
        }

        onDispose {
            exoPlayer.release()
        }
    }

    // Sync ambient backdrop parameters
    backdropView.isAmbientEnabled = isEnabled
    backdropView.spreadLimit = spreadLimit
    backdropView.brightness = brightness
    backdropView.vibrance = vibrance
    backdropView.blurRadius = blurRadius
    backdropView.flickerReduction = flickerReduction

    if (isFullscreen) {
        FullscreenLandscapePlayer(
            backdropView = backdropView,
            textureView = textureView,
            exoPlayer = exoPlayer,
            videoTitle = activeVideoTitle,
            isEnabled = isEnabled,
            onToggleEnabled = { isEnabled = it },
            onExitFullscreen = { isFullscreen = false }
        )
    } else {
        PortraitSandboxView(
            backdropView = backdropView,
            textureView = textureView,
            exoPlayer = exoPlayer,
            activeVideoTitle = activeVideoTitle,
            isEnabled = isEnabled,
            onEnabledChange = { isEnabled = it },
            spreadLimit = spreadLimit,
            onSpreadLimitChange = { spreadLimit = it },
            brightness = brightness,
            onBrightnessChange = { brightness = it },
            vibrance = vibrance,
            onVibranceChange = { vibrance = it },
            blurRadius = blurRadius,
            onBlurRadiusChange = { blurRadius = it },
            flickerReduction = flickerReduction,
            onFlickerReductionChange = { flickerReduction = it },
            currentVideoIndex = currentVideoIndex,
            isCustomVideo = customVideoUri != null,
            onSwitchVideo = {
                if (customVideoUri != null) {
                    customVideoUri = null
                    customVideoName = null
                } else {
                    currentVideoIndex = (currentVideoIndex + 1) % SAMPLE_VIDEOS.size
                }
            },
            onPickGalleryVideo = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onEnterFullscreen = { isFullscreen = true },
            playbackStatus = playbackStatus,
            playbackError = playbackError,
            framesCount = framesCount
        )
    }
}

private fun formatTimeMs(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
fun VideoPlayerControls(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    onEnterFullscreen: (() -> Unit)? = null
) {
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }

    // Auto-hide controls after 3.5 seconds of playback
    LaunchedEffect(showControls, isPlaying, isScrubbing) {
        if (showControls && isPlaying && !isScrubbing) {
            delay(3500)
            showControls = false
        }
    }

    // Continual position tracker
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            isPlaying = exoPlayer.isPlaying
            delay(200)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { showControls = !showControls }
    ) {
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000))
            ) {
                // Center: Play / Pause Button
                Surface(
                    shape = CircleShape,
                    color = Color(0x99000000),
                    modifier = Modifier
                        .size(52.dp)
                        .align(Alignment.Center)
                        .clickable {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlaying = true
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom: Time labels + Scrubber slider + Fullscreen toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val displayCurrent = if (isScrubbing) scrubPositionMs.toLong() else currentPositionMs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTimeMs(displayCurrent)} / ${formatTimeMs(durationMs)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (onEnterFullscreen != null) {
                            Button(
                                onClick = onEnterFullscreen,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("⛶", fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }

                    val maxSlider = durationMs.coerceAtLeast(1L).toFloat()
                    val sliderVal = if (isScrubbing) scrubPositionMs else currentPositionMs.toFloat()
                    Slider(
                        value = sliderVal.coerceIn(0f, maxSlider),
                        onValueChange = {
                            isScrubbing = true
                            scrubPositionMs = it
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(scrubPositionMs.toLong())
                            isScrubbing = false
                        },
                        valueRange = 0f..maxSlider,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF0000), // YouTube red accent
                            activeTrackColor = Color(0xFFFF0000),
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FullscreenLandscapePlayer(
    backdropView: AmbientBackdropView,
    textureView: InterceptingTextureView,
    exoPlayer: ExoPlayer,
    videoTitle: String,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onExitFullscreen: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }

    BackHandler {
        onExitFullscreen()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        // LAYER 0: Ambient Backdrop fills the full landscape display (including letterbox/pillarbox bars)
        AndroidView(
            factory = { backdropView },
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 1: Centered 16:9 Video
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
                .shadow(16.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = {
                    textureView.apply {
                        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                            updateVideoCoordinates(view, backdropView)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlaid controls with Scrubber and Play/Pause
            VideoPlayerControls(
                exoPlayer = exoPlayer,
                onEnterFullscreen = null
            )
        }

        // Overlay Navigation Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top-Left: Exit button
                Button(
                    onClick = onExitFullscreen,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x88000000)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text("← Выход из полноэкранного", color = Color.White, fontSize = 13.sp)
                }

                // Top-Right: Ambient Light toggle
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x88000000),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Подсветка: ", color = Color.White, fontSize = 12.sp)
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = onToggleEnabled
                        )
                    }
                }

                // Bottom: Info badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x77000000),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        "$videoTitle • Полноэкранный режим с подсветкой",
                        color = Color(0xFFEEEEEE),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitSandboxView(
    backdropView: AmbientBackdropView,
    textureView: InterceptingTextureView,
    exoPlayer: ExoPlayer,
    activeVideoTitle: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    spreadLimit: Float,
    onSpreadLimitChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    vibrance: Float,
    onVibranceChange: (Float) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    flickerReduction: Int,
    onFlickerReductionChange: (Int) -> Unit,
    currentVideoIndex: Int,
    isCustomVideo: Boolean,
    onSwitchVideo: () -> Unit,
    onPickGalleryVideo: () -> Unit,
    onEnterFullscreen: () -> Unit,
    playbackStatus: String,
    playbackError: String?,
    framesCount: Long
) {
    Scaffold(
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // STAGE CONTAINER: Houses the Ambient Backdrop and Player + Video metadata.
            // Scrolls naturally with the page content; no duplicated ghost player.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                // LAYER 0: Ambient Backdrop View matching the video + metadata stage
                AndroidView(
                    factory = { backdropView },
                    modifier = Modifier.matchParentSize()
                )

                // LAYER 1: Active Video and Metadata UI sitting directly over the ambient glow
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Generous headroom above player so upward ambient glow rays are clearly visible
                    Spacer(modifier = Modifier.height(28.dp))

                    // Video Player
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .shadow(12.dp, shape = RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .aspectRatio(16f / 9f)
                    ) {
                        AndroidView(
                            factory = {
                                textureView.apply {
                                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                        updateVideoCoordinates(view, backdropView)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlaid controls with Scrubber, Play/Pause, and Fullscreen toggle
                        VideoPlayerControls(
                            exoPlayer = exoPlayer,
                            onEnterFullscreen = onEnterFullscreen
                        )
                    }

                    // Playback status or error indicator
                    if (playbackError != null) {
                        Surface(
                            color = Color(0xCC441111),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$playbackError (включите 'Оффлайн демо')",
                                color = Color(0xFFFF8888),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Mock YouTube Interface with Translucent Frosted Glass UI (Physically lit by AGSL backdrop)
                    MockYouTubeContent(
                        title = activeVideoTitle,
                        statusText = playbackStatus,
                        framesCount = framesCount
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sandbox Controls Panel (Clearly separated below the player stage)
            AmbientControlPanel(
                isEnabled = isEnabled,
                onEnabledChange = onEnabledChange,
                spreadLimit = spreadLimit,
                onSpreadLimitChange = onSpreadLimitChange,
                brightness = brightness,
                onBrightnessChange = onBrightnessChange,
                vibrance = vibrance,
                onVibranceChange = onVibranceChange,
                blurRadius = blurRadius,
                onBlurRadiusChange = onBlurRadiusChange,
                flickerReduction = flickerReduction,
                onFlickerReductionChange = onFlickerReductionChange,
                currentVideoIndex = currentVideoIndex,
                isCustomVideo = isCustomVideo,
                onSwitchVideo = onSwitchVideo,
                onPickGalleryVideo = onPickGalleryVideo,
                onEnterFullscreen = onEnterFullscreen
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun updateVideoCoordinates(videoView: View, backdropView: AmbientBackdropView) {
    val videoLoc = IntArray(2)
    videoView.getLocationOnScreen(videoLoc)

    val backdropLoc = IntArray(2)
    backdropView.getLocationOnScreen(backdropLoc)

    val left = (videoLoc[0] - backdropLoc[0]).toFloat()
    val top = (videoLoc[1] - backdropLoc[1]).toFloat()
    val right = left + videoView.width.toFloat()
    val bottom = top + videoView.height.toFloat()

    backdropView.updateVideoPosition(left, top, right, bottom)
}

@Composable
fun MockYouTubeContent(
    title: String,
    statusText: String = "Воспроизведение",
    framesCount: Long = 0L
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Статус: $statusText • Обработано кадров: $framesCount",
            fontSize = 12.sp,
            color = Color(0xFFCCCCCC)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Channel Info Row with Frosted Background
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0x18FFFFFF),
            border = BorderStroke(1.dp, Color(0x1EFFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("YT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ambient Project Studio", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("1.42 млн подписчиков", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Подписаться", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons Row with Frosted Glass Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("142 тыс.", "Поделиться", "Ремикс", "Скачать").forEach { label ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x14FFFFFF),
                    border = BorderStroke(1.dp, Color(0x1EFFFFFF)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Comments Preview Card with Frosted Glass Look
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
            border = BorderStroke(1.dp, Color(0x1EFFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Комментарии • 4,8 тыс.", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Свечение исходит строго из-под кромок плеера, освещая интерфейс вокруг и скроллясь вместе с ним!",
                    color = Color(0xFFDDDDDD),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun AmbientControlPanel(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    spreadLimit: Float,
    onSpreadLimitChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    vibrance: Float,
    onVibranceChange: (Float) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    flickerReduction: Int,
    onFlickerReductionChange: (Int) -> Unit,
    currentVideoIndex: Int,
    isCustomVideo: Boolean,
    onSwitchVideo: () -> Unit,
    onPickGalleryVideo: () -> Unit,
    onEnterFullscreen: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x55181818)),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Настройки подсветки (Wessel Kroos Port)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Gallery Video Picker Button
            Button(
                onClick = onPickGalleryVideo,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📁 Выбрать видео из галереи")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Switch Video Button
            Button(
                onClick = onSwitchVideo,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isCustomVideo) {
                        "Вернуться к тестовым видео (${currentVideoIndex + 1}/${SAMPLE_VIDEOS.size})"
                    } else {
                        "Сменить тестовое видео (${currentVideoIndex + 1}/${SAMPLE_VIDEOS.size})"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fullscreen Button
            Button(
                onClick = onEnterFullscreen,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⛶ Открыть горизонтальный Fullscreen (20:9)")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Ambient Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Подсветка активна", color = Color.White, fontSize = 13.sp)
                Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Spread Limit Slider (emanates outwards from player perimeter)
            Text("Дальность лучей (Spread): ${(spreadLimit * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
            Slider(
                value = spreadLimit,
                onValueChange = onSpreadLimitChange,
                valueRange = 0.1f..1.0f,
                enabled = isEnabled
            )

            // Blur Radius Slider
            Text("Мягкость размытия (Blur): ${blurRadius.toInt()} dp", color = Color.White, fontSize = 12.sp)
            Slider(
                value = blurRadius,
                onValueChange = onBlurRadiusChange,
                valueRange = 15f..160f,
                enabled = isEnabled
            )

            // Brightness Slider
            Text("Яркость свечения: ${(brightness * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.2f..1.8f,
                enabled = isEnabled
            )

            // Vibrance Slider
            Text("Насыщенность (Vibrance): ${"%.2f".format(vibrance)}", color = Color.White, fontSize = 12.sp)
            Slider(
                value = vibrance,
                onValueChange = onVibranceChange,
                valueRange = -1.0f..1.0f,
                enabled = isEnabled
            )

            // Flicker Reduction Slider
            Text("Сглаживание мерцания (Flicker): $flickerReduction%", color = Color.White, fontSize = 12.sp)
            Slider(
                value = flickerReduction.toFloat(),
                onValueChange = { onFlickerReductionChange(it.toInt()) },
                valueRange = 0f..100f,
                enabled = isEnabled
            )
        }
    }
}