package com.lapcevichme.ambientlight.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.lapcevichme.ambientlight.ambient.AmbientSettingsBottomSheet
import com.lapcevichme.ambientlight.app.ui.theme.AmbientLightTheme
import com.lapcevichme.ambientlight.compose.rememberAmbientLightState
import com.lapcevichme.ambientlight.compose.rememberAmbientVideoConnector
import com.lapcevichme.ambientlight.player.InterceptingTextureView
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

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

private fun getOfflineDemoUri(packageName: String): Uri =
    "android.resource://$packageName/${R.raw.sample_ambient}".toUri()

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

@SuppressLint("SourceLockedOrientationActivity")
private fun setPlayerOrientation(activity: Activity, isFullscreen: Boolean) {
    activity.requestedOrientation = if (isFullscreen) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

@Composable
fun AmbientSandboxScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Ambient settings state
    val ambientState = rememberAmbientLightState(
        initialSpreadLimit = 0.55f,
        initialFlickerReduction = 50
    )

    var customVideoUri by remember { mutableStateOf<Uri?>(null) }
    var customVideoName by remember { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    // Playback state
    var playbackStatusRes by remember { mutableIntStateOf(R.string.demo_status_loading) }
    var playbackErrorCode by remember { mutableStateOf<String?>(null) }
    var framesCount by remember { mutableLongStateOf(0L) }

    // ExoPlayer lifecycle management
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }

    val defaultDemoTitle = stringResource(R.string.demo_sample_offline)
    val defaultGalleryName = stringResource(R.string.gallery_video_default_name)
    val activeVideoTitle = customVideoName ?: defaultDemoTitle
    val playbackStatus = stringResource(playbackStatusRes)
    val playbackError = playbackErrorCode?.let { stringResource(R.string.demo_playback_error, it) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: defaultGalleryName
            customVideoName = name
            if (customVideoUri == uri) {
                playbackErrorCode = null
                exoPlayer.seekTo(0)
                exoPlayer.play()
            } else {
                customVideoUri = uri
            }
        }
    }

    // Shared Views attached to the player stage
    val backdropView = remember { AmbientBackdropView(context) }
    val textureView = remember { InterceptingTextureView(context) }

    // Handle Orientation and System Bars for Fullscreen
    DisposableEffect(isFullscreen) {
        activity?.let { act ->
            val windowInsetsController =
                WindowCompat.getInsetsController(act.window, act.window.decorView)
            setPlayerOrientation(act, isFullscreen)
            if (isFullscreen) {
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackStatusRes = when (playbackState) {
                    Player.STATE_IDLE -> R.string.demo_status_stopped
                    Player.STATE_BUFFERING -> R.string.demo_status_buffering
                    Player.STATE_READY -> R.string.demo_status_playing
                    Player.STATE_ENDED -> R.string.demo_status_ended
                    else -> R.string.demo_status_stopped
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackErrorCode = error.errorCodeName
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(customVideoUri) {
        playbackErrorCode = null
        backdropView.reset()
        val mediaUri = customVideoUri ?: getOfflineDemoUri(context.packageName)
        exoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    rememberAmbientVideoConnector(backdropView, textureView)

    DisposableEffect(Unit) {
        exoPlayer.setVideoTextureView(textureView)

        backdropView.onDominantColorListener = { _ ->
            framesCount = backdropView.framesProcessed
        }

        onDispose {
            exoPlayer.release()
        }
    }

    // Sync ambient backdrop parameters
    ambientState.applyTo(backdropView)

    if (isFullscreen) {
        FullscreenLandscapePlayer(
            backdropView = backdropView,
            textureView = textureView,
            exoPlayer = exoPlayer,
            videoTitle = activeVideoTitle,
            isEnabled = ambientState.isEnabled,
            onToggleEnabled = { ambientState.isEnabled = it },
            onExitFullscreen = { isFullscreen = false },
            onOpenSettings = { showSettingsSheet = true }
        )
    } else {
        PortraitSandboxView(
            backdropView = backdropView,
            textureView = textureView,
            exoPlayer = exoPlayer,
            activeVideoTitle = activeVideoTitle,
            onEnterFullscreen = { isFullscreen = true },
            onOpenSettings = { showSettingsSheet = true },
            playbackStatus = playbackStatus,
            playbackError = playbackError,
            framesCount = framesCount,
            isCustomVideo = customVideoUri != null,
            onPickGalleryVideo = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onResetToSample = {
                customVideoUri = null
                customVideoName = null
            }
        )
    }

    if (showSettingsSheet) {
        AmbientSettingsBottomSheet(
            state = ambientState,
            onDismissRequest = { showSettingsSheet = false }
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
    onEnterFullscreen: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
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
            delay(3500.milliseconds)
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
            delay(200.milliseconds)
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
                // Top-Right: Settings Gear Button (opens Ambient Settings Sheet)
                if (onOpenSettings != null) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(36.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x66000000),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.ambient_settings_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

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
                    val displayCurrent =
                        if (isScrubbing) scrubPositionMs.toLong() else currentPositionMs
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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(
                                        0x66000000
                                    )
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("⛶", fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }

                    val maxSlider = durationMs.coerceAtLeast(1L).toFloat()
                    val sliderVal =
                        if (isScrubbing) scrubPositionMs else currentPositionMs.toFloat()
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
    onExitFullscreen: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
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
            factory = {
                backdropView.apply {
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        view.post { updateVideoCoordinates(textureView, backdropView) }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 1: Centered 16:9 Video (sitting seamlessly flush on the ambient backdrop)
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = {
                    textureView.apply {
                        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                            view.post { updateVideoCoordinates(view, backdropView) }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlaid controls with Scrubber and Play/Pause
            VideoPlayerControls(
                exoPlayer = exoPlayer,
                onEnterFullscreen = null,
                onOpenSettings = onOpenSettings
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
                    Text(
                        stringResource(R.string.demo_exit_fullscreen),
                        color = Color.White,
                        fontSize = 13.sp
                    )
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
                        Text(
                            stringResource(R.string.demo_ambient_label) + " ",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1E1E1E),
                                checkedTrackColor = Color.White,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = Color(0xFFAAAAAA),
                                uncheckedTrackColor = Color(0x28FFFFFF),
                                uncheckedBorderColor = Color(0x33FFFFFF)
                            )
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
                        stringResource(R.string.demo_fullscreen_badge, videoTitle),
                        color = Color(0xFFEEEEEE),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PortraitSandboxView(
    backdropView: AmbientBackdropView,
    textureView: InterceptingTextureView,
    exoPlayer: ExoPlayer,
    activeVideoTitle: String,
    onEnterFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    playbackStatus: String,
    playbackError: String?,
    framesCount: Long,
    isCustomVideo: Boolean,
    onPickGalleryVideo: () -> Unit,
    onResetToSample: () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
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
                    factory = {
                        backdropView.apply {
                            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                view.post { updateVideoCoordinates(textureView, backdropView) }
                            }
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )

                // LAYER 1: Active Video and Metadata UI sitting directly over the ambient glow
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Generous headroom extending behind status bar so upward ambient glow rays illuminate behind clock and battery icons
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Video Player (sharp square corners with padding, ambient light shining from all 4 sides)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(Color.Black)
                            .aspectRatio(16f / 9f)
                    ) {
                        AndroidView(
                            factory = {
                                textureView.apply {
                                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                                        view.post { updateVideoCoordinates(view, backdropView) }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlaid controls with Scrubber, Play/Pause, Fullscreen toggle, and Settings gear
                        VideoPlayerControls(
                            exoPlayer = exoPlayer,
                            onEnterFullscreen = onEnterFullscreen,
                            onOpenSettings = onOpenSettings
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
                                text = stringResource(
                                    R.string.demo_playback_error_hint,
                                    playbackError
                                ),
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

            // Suggested Videos Feed (Kurzgesagt, DECO*27 matching YouTube screenshot)
            SuggestedVideosFeed()

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Settings Access Bar at bottom of feed
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x18FFFFFF),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable { onOpenSettings() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.demo_quick_settings),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = stringResource(R.string.demo_quick_settings_action),
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Video Source & Demo Controls Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x18FFFFFF),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.demo_video_source_title),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Button(
                        onClick = onPickGalleryVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F0F0F)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isCustomVideo) stringResource(R.string.demo_pick_gallery_another)
                            else stringResource(R.string.demo_pick_gallery),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (isCustomVideo) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onResetToSample,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.demo_return_sample),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun updateVideoCoordinates(videoView: View, backdropView: AmbientBackdropView) {
    if (videoView.width <= 0 || videoView.height <= 0 || backdropView.width <= 0 || backdropView.height <= 0) {
        videoView.post { updateVideoCoordinates(videoView, backdropView) }
        return
    }
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
    statusText: String = stringResource(R.string.demo_status_playing),
    framesCount: Long = 0L
) {
    val offlineSampleTitle = stringResource(R.string.demo_sample_offline)
    val displayTitle = if (title.startsWith("sample_") || title == offlineSampleTitle) {
        "The Most Controversial Idea In Physics"
    } else {
        title
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Video Title (matching YouTube screenshot)
        Text(
            text = displayTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Metadata line: @lapcevichme 52K likes 2.3M views more
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val channelHandle = stringResource(R.string.mock_channel_handle)
            val statsText = stringResource(R.string.mock_video_stats)
            val moreText = stringResource(R.string.mock_more)
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFFDDDDDD),
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append("$channelHandle  ")
                    }
                    withStyle(SpanStyle(color = Color(0xFFAAAAAA))) {
                        append(statsText)
                    }
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                        append(moreText)
                    }
                },
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live pipeline status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF4CAF50), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.demo_frames_processed, statusText, framesCount),
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E),
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Channel Info & Actions Row: Avatar + Subscribe pill + Action icon buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF8D6E63), Color(0xFF4E342E))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.mock_channel_initials),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Subscribe Button
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = stringResource(R.string.mock_subscribe),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Icons Row: Like, Dislike, Share, Save, More
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = stringResource(R.string.mock_like),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbDown,
                        contentDescription = stringResource(R.string.mock_dislike),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.mock_share),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.mock_save),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = stringResource(R.string.mock_more_options),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Comments Preview Card (Translucent frosted card, physically lit by ambient light)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.mock_comments_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.mock_comments_count),
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDDDDDD))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color(0x66FFFFFF))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB8860B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💻", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.mock_comment_text),
                        color = Color(0xFFEEEEEE),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestedVideosFeed(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        // --- Kurzgesagt Video Card ---
        // 16:9 Thumbnail: Peaceful cosmic science theme
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF0C101A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Deep space gradient background
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF090D1E),
                            Color(0xFF131A3D),
                            Color(0xFF1C1335),
                            Color(0xFF0A0E24)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                )

                // 2. Soft pastel nebula glow clouds
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x4438BDF8), Color(0x180284C7), Color.Transparent),
                        center = Offset(w * 0.72f, h * 0.5f),
                        radius = w * 0.42f
                    ),
                    radius = w * 0.42f,
                    center = Offset(w * 0.72f, h * 0.5f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x38818CF8), Color(0x146366F1), Color.Transparent),
                        center = Offset(w * 0.35f, h * 0.65f),
                        radius = w * 0.35f
                    ),
                    radius = w * 0.35f,
                    center = Offset(w * 0.35f, h * 0.65f)
                )

                // 3. Delicate twinkling stars (fixed deterministic coordinates)
                val stars = listOf(
                    0.08f to 0.15f, 0.18f to 0.12f, 0.32f to 0.22f, 0.48f to 0.14f,
                    0.65f to 0.12f, 0.82f to 0.18f, 0.92f to 0.28f, 0.12f to 0.48f,
                    0.22f to 0.75f, 0.38f to 0.85f, 0.78f to 0.88f, 0.90f to 0.72f,
                    0.52f to 0.32f, 0.85f to 0.42f, 0.05f to 0.85f, 0.42f to 0.45f
                )
                for ((sx, sy) in stars) {
                    drawCircle(
                        color = Color(0xEEFFFFFF),
                        radius = 2.dp.toPx(),
                        center = Offset(w * sx, h * sy)
                    )
                }

                // 4. Stylized glowing ringed planet
                val planetCenter = Offset(w * 0.62f, h * 0.52f)
                val planetRadius = w * 0.16f

                // Atmospheric outer halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x5538BDF8), Color(0x180284C7), Color.Transparent),
                        center = planetCenter,
                        radius = planetRadius * 1.4f
                    ),
                    radius = planetRadius * 1.4f,
                    center = planetCenter
                )

                // Back half of planetary rings (behind the sphere)
                rotate(degrees = -24f, pivot = planetCenter) {
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x88FDE047),
                                Color(0x77F59E0B),
                                Color(0x33F59E0B)
                            ),
                            start = Offset(planetCenter.x - planetRadius * 2.2f, planetCenter.y),
                            end = Offset(planetCenter.x + planetRadius * 2.2f, planetCenter.y)
                        ),
                        topLeft = Offset(
                            planetCenter.x - planetRadius * 2.1f,
                            planetCenter.y - planetRadius * 0.32f
                        ),
                        size = Size(planetRadius * 4.2f, planetRadius * 0.64f),
                        style = Stroke(width = 8.dp.toPx())
                    )
                }

                // Planet sphere with 3D spherical gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0B192C)),
                        center = Offset(
                            planetCenter.x - planetRadius * 0.3f,
                            planetCenter.y - planetRadius * 0.3f
                        ),
                        radius = planetRadius * 1.2f
                    ),
                    radius = planetRadius,
                    center = planetCenter
                )

                // Front arc of planetary rings (in front of the sphere)
                rotate(degrees = -24f, pivot = planetCenter) {
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xEEFDE047),
                                Color(0xDDF59E0B),
                                Color(0x88F59E0B)
                            ),
                            start = Offset(planetCenter.x - planetRadius * 2.2f, planetCenter.y),
                            end = Offset(planetCenter.x + planetRadius * 2.2f, planetCenter.y)
                        ),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(
                            planetCenter.x - planetRadius * 2.1f,
                            planetCenter.y - planetRadius * 0.32f
                        ),
                        size = Size(planetRadius * 4.2f, planetRadius * 0.64f),
                        style = Stroke(width = 8.dp.toPx())
                    )
                }

                // Small orbiting moon
                val moonCenter = Offset(
                    planetCenter.x - planetRadius * 1.35f,
                    planetCenter.y - planetRadius * 0.8f
                )
                drawCircle(
                    color = Color(0xFFE2E8F0),
                    radius = 5.dp.toPx(),
                    center = moonCenter
                )
            }

            // Headline "WHAT IF?" badge across top-left of thumbnail
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0x99000000),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.feed_kurzgesagt_thumb_headline),
                    color = Color(0xFFFFEB3B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Duration badge in bottom right ("11:24")
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xDD000000),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "11:24",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Kurzgesagt Channel info & title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Kurzgesagt Earth avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D47A1)),
                contentAlignment = Alignment.Center
            ) {
                Text("🌍", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feed_kurzgesagt_title),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.feed_kurzgesagt_meta),
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.feed_more_options),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DECO*27 Card Snippet ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF212121))
                    .border(BorderStroke(1.dp, Color(0xFF444444)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("27", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DECO*27",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("♪", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.feed_deco_meta),
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "かわいいデコミクたちのPOP UP PARADEが...",
                    color = Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.feed_more_options),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}