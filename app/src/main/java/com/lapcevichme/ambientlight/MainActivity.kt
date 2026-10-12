package com.lapcevichme.ambientlight

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lapcevichme.ambientlight.ambient.AmbientBackdropView
import com.lapcevichme.ambientlight.player.InterceptingTextureView
import com.lapcevichme.ambientlight.ui.theme.AmbientLightTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSandboxScreen() {
    val context = LocalContext.current

    // Ambient settings state
    var isEnabled by remember { mutableStateOf(true) }
    var spreadLimit by remember { mutableFloatStateOf(0.55f) }
    var brightness by remember { mutableFloatStateOf(1.0f) }
    var vibrance by remember { mutableFloatStateOf(0.40f) }
    var blurRadius by remember { mutableFloatStateOf(90f) }
    var flickerReduction by remember { mutableIntStateOf(50) }
    var currentVideoIndex by remember { mutableIntStateOf(0) }

    // Playback state
    var playbackStatus by remember { mutableStateOf("Загрузка...") }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var framesCount by remember { mutableStateOf(0L) }

    // Dynamic dominant ambient color (for UI outline shadow tinting)
    var dominantColor by remember { mutableStateOf(Color(0xFF333333)) }

    // Shared Views & Buffers attached to the player stage
    val backdropView = remember { AmbientBackdropView(context) }
    val textureView = remember { InterceptingTextureView(context) }
    val frameBufferBitmap = remember { Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888) }

    // ExoPlayer lifecycle management
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            val uriStr = SAMPLE_VIDEOS[currentVideoIndex].uriString(context.packageName)
            setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            prepare()
            playWhenReady = true
        }
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
                playbackError = "Сбой сети/видео: ${error.errorCodeName}"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(currentVideoIndex) {
        playbackError = null
        val uriStr = SAMPLE_VIDEOS[currentVideoIndex].uriString(context.packageName)
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
        exoPlayer.prepare()
        exoPlayer.play()
        onDispose { }
    }

    DisposableEffect(Unit) {
        // Connect frame interceptor to the backdrop view
        textureView.onFrameUpdatedListener = { view ->
            view.getBitmap(frameBufferBitmap)
            backdropView.processFrame(frameBufferBitmap)
        }
        exoPlayer.setVideoTextureView(textureView)

        backdropView.onDominantColorListener = { rgb ->
            dominantColor = Color(rgb)
            framesCount = backdropView.framesProcessed
        }

        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ambient Light for YouTube",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0F0F),
                    titleContentColor = Color.White
                )
            )
        },
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
                    update = { view ->
                        view.isAmbientEnabled = isEnabled
                        view.spreadLimit = spreadLimit
                        view.brightness = brightness
                        view.vibrance = vibrance
                        view.blurRadius = blurRadius
                        view.flickerReduction = flickerReduction
                    },
                    modifier = Modifier.matchParentSize()
                )

                // LAYER 1: Active Video and Metadata UI sitting directly over the ambient glow
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Video Player
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .aspectRatio(16f / 9f)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = dominantColor,
                                spotColor = dominantColor
                            )
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

                    // Mock YouTube Interface with Dynamic Ambient Shadows
                    MockYouTubeContent(
                        title = SAMPLE_VIDEOS[currentVideoIndex].title,
                        statusText = playbackStatus,
                        framesCount = framesCount,
                        ambientShadowColor = dominantColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sandbox Controls Panel (Clearly separated below the player stage)
            AmbientControlPanel(
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
                onSwitchVideo = {
                    currentVideoIndex = (currentVideoIndex + 1) % SAMPLE_VIDEOS.size
                }
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
    framesCount: Long = 0L,
    ambientShadowColor: Color
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
            color = Color(0x33222222),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = ambientShadowColor,
                    spotColor = ambientShadowColor
                )
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

        // Action Buttons Row with Frosted Glass Chips & Dynamic Ambient Shadows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("142 тыс.", "Поделиться", "Ремикс", "Скачать").forEach { label ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x33282828),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = ambientShadowColor,
                            spotColor = ambientShadowColor
                        )
                        .height(36.dp)
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
            colors = CardDefaults.cardColors(containerColor = Color(0x33202020)),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = ambientShadowColor,
                    spotColor = ambientShadowColor
                )
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
    onSwitchVideo: () -> Unit
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

            // Switch Video Button
            Button(
                onClick = onSwitchVideo,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сменить тестовое видео (${currentVideoIndex + 1}/${SAMPLE_VIDEOS.size})")
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