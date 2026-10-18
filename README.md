# AmbientLight for Android 🌟

A high-performance ambient light glow effect library for Android video players (YouTube-style Ambient Mode). Powered by **AGSL Runtime Shaders** (Android 13+) and **Skia Hardware RenderEffect Blur** (Android 12+), ported from [Wessel Kroos's ambient light engine](https://github.com/WesselKroos/youtube-ambient-light).

---

## ✨ Features

- **🌈 True Perimeter Ray-Casting**: Ambient light and rays emanate directly from the actual borders of the video outward.
- **⚡ Zero Memory Overhead**: Single preallocated low-res buffer (GC pauses: 0 ms).
- **🎬 Letterbox & Pillarbox Detection**: Automatically detects and trims black bars (21:9, 4:3, etc.) so glow originates from true video content.
- **🖤 OLED Pure Blacks Mode**: Suppresses weak glow on dark scenes to save battery and preserve deep blacks.
- **🧭 Directional Light Masks**: Independent toggles for Top, Bottom, Left, and Right glow directions.
- **🎨 HSV Vibrance & Contrast**: Non-linear saturation boosting and contrast adjustment based on Wessel Kroos's WebGL shaders.
- **📱 Broad Compatibility (Android 7.0+ / API 24+)**: Runs everywhere with automatic graceful degradation (high-precision AGSL ray-casting on Android 13+, hardware-filtered Canvas fallback on Android 7.0–12).
- **🧩 Jetpack Compose & Classic Views Support**: First-class support for Compose (`AmbientLightContainer`) and XML Views (`AmbientBackdropView`).
- **🎛️ Built-in Settings Sheet**: Ready-to-use YouTube-themed dark monochrome bottom sheet for real-time user customization.

---

## 📦 Installation

Add JitPack to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.lapcevichme:android-ambient-light:1.0.0")
}
```

---

## 🚀 Quick Start

### 1. Jetpack Compose

```kotlin
@Composable
fun VideoPlayerScreen(exoPlayer: ExoPlayer) {
    val ambientState = rememberAmbientLightState()
    val textureView = remember { InterceptingTextureView(LocalContext.current) }

    // Connect player and texture view
    DisposableEffect(Unit) {
        exoPlayer.setVideoTextureView(textureView)
        onDispose { exoPlayer.clearVideoTextureView(textureView) }
    }

    AmbientLightContainer(
        state = ambientState,
        modifier = Modifier.fillMaxWidth().height(320.dp)
    ) { backdropView ->
        // Connect frame capture loop and auto-sync coordinates
        rememberAmbientVideoConnector(backdropView, textureView)

        // Player with padding to give ambient light room to shine
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(16f / 9f)
        ) {
            AndroidView(
                factory = { textureView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
```

### 2. Classic Android Views / XML

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="320dp">

    <!-- Layer 0: Ambient light backdrop beneath the player -->
    <com.lapcevichme.ambientlight.ambient.AmbientBackdropView
        android:id="@+id/ambientBackdrop"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Layer 1: Foreground video player -->
    <com.lapcevichme.ambientlight.player.InterceptingTextureView
        android:id="@+id/videoTexture"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:layout_marginStart="16dp"
        android:layout_marginEnd="16dp" />
</FrameLayout>
```

In your Activity / Fragment:

```kotlin
val frameBuffer = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888)

videoTexture.onFrameUpdatedListener = { view ->
    view.getBitmap(frameBuffer)
    ambientBackdrop.processFrame(frameBuffer)
}

videoTexture.doOnLayout {
    ambientBackdrop.updateVideoPosition(
        videoTexture.left.toFloat(),
        videoTexture.top.toFloat(),
        videoTexture.right.toFloat(),
        videoTexture.bottom.toFloat()
    )
}

exoPlayer.setVideoTextureView(videoTexture)
```

---

## ⚙️ AmbientLightState & Custom Controls

`AmbientLightContainer` is driven by `AmbientLightState`, an observable state holder where all parameters are reactive Compose snapshot states. Any property update immediately reflects in the shader in real time.

You can build your own custom UI (dialogs, sidebars, or settings screens) simply by binding controls to `state`:

```kotlin
@Composable
fun CustomAmbientControls(state: AmbientLightState) {
    Column(modifier = Modifier.padding(16.dp)) {
        Switch(
            checked = state.isEnabled,
            onCheckedChange = { state.isEnabled = it }
        )
        Slider(
            value = state.brightness,
            onValueChange = { state.brightness = it },
            valueRange = 0.5f..2.5f
        )
        Slider(
            value = state.blurRadius,
            onValueChange = { state.blurRadius = it },
            valueRange = 10f..100f
        )
        Button(onClick = { state.resetToDefaults() }) {
            Text("Reset to defaults")
        }
    }
}
```

### State Properties Reference

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `state.isEnabled` | `Boolean` | `true` | Master toggle to enable or disable ambient lighting |
| `state.brightness` | `Float` | `1.0f` | Light brightness multiplier (`0.5f` .. `2.5f`) |
| `state.blurRadius` | `Float` | `50f` | Hardware blur radius (`10f` .. `100f`) |
| `state.vibrance` | `Float` | `0.0f` | HSV saturation boost curve (`0.0f` .. `1.0f`) |
| `state.contrast` | `Float` | `1.0f` | Ray contrast factor (`0.5f` .. `2.0f`) |
| `state.spreadLimit` | `Float` | `0.40f` | Ray reach and distance falloff from video edges (`0.1f` .. `0.8f`) |
| `state.edgeSize` | `Float` | `0.12f` | Border sample depth for ray-casting (`0.05f` .. `0.25f`) |
| `state.flickerReduction` | `Int` | `10` | Wessel Kroos temporal smoothing percentage (`0` .. `100`) |
| `state.isOledMode` | `Boolean` | `false` | Deep black cutoff to save battery and eliminate weak glow on OLEDs |
| `state.detectLetterbox` | `Boolean` | `true` | Real-time automatic black bar detection (21:9, 4:3) |
| `state.directionTop` | `Boolean` | `true` | Independent toggle for top glow |
| `state.directionBottom` | `Boolean` | `true` | Independent toggle for bottom glow |
| `state.directionLeft` | `Boolean` | `true` | Independent toggle for left glow |
| `state.directionRight` | `Boolean` | `true` | Independent toggle for right glow |
| `state.resetToDefaults()`| Method | — | Resets all parameters to recommended default values |

---

## 🎛️ Settings Bottom Sheet

You can show the authentic YouTube-styled settings sheet with one line:

```kotlin
var showSettings by remember { mutableStateOf(false) }

if (showSettings) {
    AmbientSettingsBottomSheet(
        state = ambientState,
        onDismissRequest = { showSettings = false }
    )
}
```

Or configure individual parameters with the explicit overload if you don't use `AmbientLightState`.

### Customizing Theme & Colors (Material 3 / Material You)

By default, the settings sheet uses YouTube's dark monochromatic style (`AmbientSheetColors.Default`). You can customize any color or connect it directly to your app's `MaterialTheme.colorScheme`:

```kotlin
val materialColors = AmbientSheetColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    accentColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    cardBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
    cardBorderColor = MaterialTheme.colorScheme.outlineVariant,
    switchCheckedTrackColor = MaterialTheme.colorScheme.primary,
    switchCheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
    statusActiveColor = MaterialTheme.colorScheme.primary // or default pastel green Color(0xFF81C784)
)

AmbientSettingsBottomSheet(
    state = ambientState,
    onDismissRequest = { showSettings = false },
    colors = materialColors
)
```

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
Portions derived from [youtube-ambient-light](https://github.com/WesselKroos/youtube-ambient-light) by Wessel Kroos.
