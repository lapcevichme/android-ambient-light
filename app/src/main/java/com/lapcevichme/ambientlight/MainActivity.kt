package com.lapcevichme.ambientlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lapcevichme.ambientlight.player.AmbientPlayerContainer
import com.lapcevichme.ambientlight.ui.theme.AmbientLightTheme

import android.net.Uri
import androidx.media3.common.PlaybackException

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
    var spread by remember { mutableFloatStateOf(1.35f) }
    var vibrance by remember { mutableFloatStateOf(0.35f) }
    var blurRadius by remember { mutableFloatStateOf(80f) }
    var flickerReduction by remember { mutableIntStateOf(50) }
    var currentVideoIndex by remember { mutableIntStateOf(0) }

    // Playback state
    var playbackStatus by remember { mutableStateOf("Загрузка...") }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Dynamic dominant ambient color (for UI outline shadow tinting)
    var dominantColor by remember { mutableStateOf(Color(0xFF222222)) }

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
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambient Light Sandbox (AGSL)", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
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
            // Player Container with Ambient Backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        AmbientPlayerContainer(ctx).apply {
                            attachPlayer(exoPlayer)
                            ambientBackdropView.onDominantColorListener = { rgb ->
                                dominantColor = Color(rgb)
                            }
                        }
                    },
                    update = { container ->
                        container.ambientBackdropView.isAmbientEnabled = isEnabled
                        container.ambientBackdropView.spread = spread
                        container.ambientBackdropView.vibrance = vibrance
                        container.ambientBackdropView.blurRadius = blurRadius
                        container.ambientBackdropView.flickerReduction = flickerReduction
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Playback status or error indicator
            if (playbackError != null) {
                Surface(
                    color = Color(0xFF441111),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
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
                ambientShadowColor = dominantColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sandbox Controls Panel
            AmbientControlPanel(
                isEnabled = isEnabled,
                onEnabledChange = { isEnabled = it },
                spread = spread,
                onSpreadChange = { spread = it },
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
        }
    }
}

@Composable
fun MockYouTubeContent(
    title: String,
    statusText: String = "Воспроизведение",
    ambientShadowColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Статус: $statusText • 1.2 млн просмотров",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Channel Info Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCC0000)),
                contentAlignment = Alignment.Center
            ) {
                Text("YT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Open Source Media", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("580 тыс. подписчиков", color = Color.Gray, fontSize = 11.sp)
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

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons Row with Dynamic Ambient Outline Shadow
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("124 тыс.", "Поделиться", "Ремикс", "Скачать").forEach { label ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF272727),
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = ambientShadowColor,
                            spotColor = ambientShadowColor
                        )
                        .height(34.dp)
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

        // Comments Preview Card with Dynamic Ambient Shadow
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = ambientShadowColor,
                    spotColor = ambientShadowColor
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Комментарии • 3,4 тыс.", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Подсветка плавно адаптируется под границы кадра и отбрасывает объемную тень под карточки!",
                    color = Color.LightGray,
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
    spread: Float,
    onSpreadChange: (Float) -> Unit,
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Настройки эффекта (Wessel Kroos Port)",
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

            // Spread Slider
            Text("Размах проекции (Spread): ${"%.2f".format(spread)}", color = Color.White, fontSize = 12.sp)
            Slider(
                value = spread,
                onValueChange = onSpreadChange,
                valueRange = 1.0f..2.5f,
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

            // Blur Radius Slider
            Text("Радиус размытия (Blur): ${blurRadius.toInt()} dp", color = Color.White, fontSize = 12.sp)
            Slider(
                value = blurRadius,
                onValueChange = onBlurRadiusChange,
                valueRange = 5f..150f,
                enabled = isEnabled
            )

            // Flicker Reduction Slider
            Text("Сглаживание мерцания (Flicker Reduction): $flickerReduction%", color = Color.White, fontSize = 12.sp)
            Slider(
                value = flickerReduction.toFloat(),
                onValueChange = { onFlickerReductionChange(it.toInt()) },
                valueRange = 0f..100f,
                enabled = isEnabled
            )
        }
    }
}