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
    implementation("com.github.lapcevichme:ambientlight:1.0.0")
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

## 🎛️ Settings Bottom Sheet

You can show the authentic YouTube-styled settings sheet with one line:

```kotlin
var showSettings by remember { mutableStateOf(false) }

if (showSettings) {
    AmbientSettingsBottomSheet(
        isEnabled = ambientState.isEnabled,
        onToggleEnabled = { ambientState.isEnabled = it },
        blurRadius = ambientState.blurRadius,
        onBlurRadiusChange = { ambientState.blurRadius = it },
        brightness = ambientState.brightness,
        onBrightnessChange = { ambientState.brightness = it },
        // ... (all other parameters supported)
        onDismissRequest = { showSettings = false }
    )
}
```

---

## 📄 License

Apache License 2.0. See [LICENSE](LICENSE) for details.
Portions derived from [youtube-ambient-light](https://github.com/WesselKroos/youtube-ambient-light) by Wessel Kroos.
