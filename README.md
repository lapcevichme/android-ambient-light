# AmbientLight for Android

[![JitPack](https://jitpack.io/v/lapcevichme/android-ambient-light.svg)](https://jitpack.io/#lapcevichme/android-ambient-light)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An ambient light glow effect library for Android video players. Powered by **AGSL Runtime Shaders** (Android 13+) and **Skia Hardware RenderEffect Blur** (Android 12+), ported from [Wessel Kroos's ambient light engine](https://github.com/WesselKroos/youtube-ambilight).

https://github.com/user-attachments/assets/c653d53c-8770-4490-96cf-07afa5669ffc

<sub>Demo video: <a href="https://www.youtube.com/watch?v=S6_pO9xj4iM">The Larping Tombstone - "Looping the Rooms (Larping the Rooms)" (Remix)</a></sub>

## Features

* **Perimeter Ray-Casting**: Samples video borders to project ambient light and rays outward from the edges of the player.

* **Zero-Allocation Frame Loop**: Reuses a single preallocated `128x72` bitmap buffer for frame capture to avoid garbage collection churn during playback.

* **Letterbox & Pillarbox Detection**: Automatically detects and crops out black bars (21:9, 4:3, etc.) in real time so the glow originates from actual video frames.

* **OLED Cutoff Mode**: Suppresses low-luminance glow on dark scenes to preserve pure black pixels.

* **Directional Masks**: Independent toggles for Top, Bottom, Left, and Right light projection.

* **HSV Vibrance & Contrast**: Non-linear saturation boosting and contrast adjustment ported from Wessel Kroos's WebGL shaders.

* **API 24+ Fallbacks**: Uses AGSL ray-casting on Android 13+, RenderEffect hardware blur on Android 12, and a hardware-filtered Canvas fallback on Android 7.0-11.

* **Compose & Views Interop**: First-class support for Jetpack Compose (`AmbientLightContainer`) and traditional XML layouts (`AmbientBackdropView`).

* **Prebuilt Settings Sheet**: Optional YouTube-styled dark bottom sheet for tweaking shader parameters at runtime.

## Installation

Add JitPack to your root `settings.gradle.kts`:

```
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```
dependencies {
    implementation("com.github.lapcevichme:android-ambient-light:1.0.0")
}
```

## Quick Start

### 1. Jetpack Compose

```
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

```
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
// Automatically links frame extraction and synchronizes layout coordinates
ambientBackdrop.attachTextureView(videoTexture)

// Attach to ExoPlayer
exoPlayer.setVideoTextureView(videoTexture)
```


## AmbientLightState & Custom Controls

`AmbientLightContainer` is driven by `AmbientLightState`, an observable state holder backed by Compose snapshot state. Updating any property immediately updates the shader uniforms on the next frame.

You can build your own configuration UI by binding controls directly to `state`:

```
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
| ----- | ----- | ----- | ----- | 
| `state.isEnabled` | `Boolean` | `true` | Master toggle to enable or disable ambient lighting | 
| `state.brightness` | `Float` | `1.0f` | Light brightness multiplier (`0.5f` .. `2.5f`) | 
| `state.blurRadius` | `Float` | `50f` | Hardware blur radius (`10f` .. `100f`) | 
| `state.vibrance` | `Float` | `0.0f` | HSV saturation boost curve (`0.0f` .. `1.0f`) | 
| `state.contrast` | `Float` | `1.0f` | Ray contrast factor (`0.5f` .. `2.0f`) | 
| `state.spreadLimit` | `Float` | `0.40f` | Ray reach and distance falloff from video edges (`0.1f` .. `0.8f`) | 
| `state.edgeSize` | `Float` | `0.12f` | Border sample depth for ray-casting (`0.05f` .. `0.25f`) | 
| `state.flickerReduction` | `Int` | `10` | Temporal smoothing percentage (`0` .. `100`) | 
| `state.isOledMode` | `Boolean` | `false` | Deep black cutoff to eliminate weak glow on OLED panels | 
| `state.detectLetterbox` | `Boolean` | `true` | Real-time automatic black bar detection (21:9, 4:3) | 
| `state.directionTop` | `Boolean` | `true` | Independent toggle for top glow | 
| `state.directionBottom` | `Boolean` | `true` | Independent toggle for bottom glow | 
| `state.directionLeft` | `Boolean` | `true` | Independent toggle for left glow | 
| `state.directionRight` | `Boolean` | `true` | Independent toggle for right glow | 
| `state.resetToDefaults()` | Method | - | Resets all parameters to default values | 

## Settings Bottom Sheet

If you want a ready-made configuration UI, you can display the built-in bottom sheet:

```
var showSettings by remember { mutableStateOf(false) }

if (showSettings) {
    AmbientSettingsBottomSheet(
        state = ambientState,
        onDismissRequest = { showSettings = false }
    )
}
```

An explicit overload is also available if you are managing parameters outside of `AmbientLightState`.

### Customizing Theme & Colors (Material 3 / Material You)

By default, the settings sheet uses a dark monochromatic palette (`AmbientSheetColors.Default`). You can override individual tokens or map them to your app's `MaterialTheme.colorScheme`:

```
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

## Architecture & Limitations

* **`TextureView` Requirement**: Because `SurfaceView` renders directly to a hardware compositor layer, reading back frame pixels on every update (via `PixelCopy`) introduces IPC overhead and frame drops. This library uses `InterceptingTextureView` to downsample frames directly into a tiny `128x72` `Bitmap` on the UI thread with minimal latency.

* **DRM / Protected Content**: Secure video playback (Widevine L1) requires a protected hardware surface (`SurfaceView` with `FLAG_SECURE`), which blocks CPU/GPU pixel readback at the OS level. Attempting to capture frames from DRM-protected streams will yield blank black frames, causing the ambient glow to remain off.

## Credits & Acknowledgements

* **Ambient Light Engine**: Ported from [youtube-ambilight](https://github.com/WesselKroos/youtube-ambilight) by Wessel Kroos.
* **Demo Video Footage**: [The Larping Tombstone - "Looping the Rooms (Larping the Rooms)" (Remix)](https://www.youtube.com/watch?v=S6_pO9xj4iM).

## License

MIT License. See [LICENSE](LICENSE) for details.
