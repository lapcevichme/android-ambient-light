package com.lapcevichme.ambientlight.compose

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.lapcevichme.ambientlight.ambient.AmbientBackdropView
import com.lapcevichme.ambientlight.player.InterceptingTextureView
import androidx.core.graphics.createBitmap

/**
 * State holder controlling all parameters of the ambient light glow and rays.
 */
@Stable
class AmbientLightState(
    initialEnabled: Boolean = true,
    initialBlurRadius: Float = 50f,
    initialBrightness: Float = 1.0f,
    initialVibrance: Float = 0.0f,
    initialSpreadLimit: Float = 0.40f,
    initialEdgeSize: Float = 0.12f,
    initialFlickerReduction: Int = 10,
    initialContrast: Float = 1.0f,
    initialIsOledMode: Boolean = false,
    initialDetectLetterbox: Boolean = true,
    initialFadeStart: Float = 0.15f,
    initialFadeCurve: Float = 35f,
    initialDirectionTop: Boolean = true,
    initialDirectionBottom: Boolean = true,
    initialDirectionLeft: Boolean = true,
    initialDirectionRight: Boolean = true
) {
    var isEnabled by mutableStateOf(initialEnabled)
    var blurRadius by mutableFloatStateOf(initialBlurRadius)
    var brightness by mutableFloatStateOf(initialBrightness)
    var vibrance by mutableFloatStateOf(initialVibrance)
    var spreadLimit by mutableFloatStateOf(initialSpreadLimit)
    var edgeSize by mutableFloatStateOf(initialEdgeSize)
    var flickerReduction by mutableIntStateOf(initialFlickerReduction)
    var contrast by mutableFloatStateOf(initialContrast)
    var isOledMode by mutableStateOf(initialIsOledMode)
    var detectLetterbox by mutableStateOf(initialDetectLetterbox)
    var fadeStart by mutableFloatStateOf(initialFadeStart)
    var fadeCurve by mutableFloatStateOf(initialFadeCurve)
    var directionTop by mutableStateOf(initialDirectionTop)
    var directionBottom by mutableStateOf(initialDirectionBottom)
    var directionLeft by mutableStateOf(initialDirectionLeft)
    var directionRight by mutableStateOf(initialDirectionRight)

    /**
     * Resets all parameters to their optimal default settings.
     */
    fun resetToDefaults() {
        isEnabled = true
        blurRadius = 50f
        brightness = 1.0f
        vibrance = 0.0f
        spreadLimit = 0.40f
        edgeSize = 0.12f
        flickerReduction = 10
        contrast = 1.0f
        isOledMode = false
        detectLetterbox = true
        fadeStart = 0.15f
        fadeCurve = 35f
        directionTop = true
        directionBottom = true
        directionLeft = true
        directionRight = true
    }

    /**
     * Synchronizes this state's properties into the backing [AmbientBackdropView].
     */
    fun applyTo(backdropView: AmbientBackdropView) {
        backdropView.isAmbientEnabled = isEnabled
        backdropView.blurRadius = blurRadius
        backdropView.brightness = brightness
        backdropView.vibrance = vibrance
        backdropView.spreadLimit = spreadLimit
        backdropView.edgeSize = edgeSize
        backdropView.flickerReduction = flickerReduction
        backdropView.contrast = contrast
        backdropView.isOledMode = isOledMode
        backdropView.isLetterboxDetectionEnabled = detectLetterbox
        backdropView.fadeStart = fadeStart
        backdropView.fadeCurve = fadeCurve
        backdropView.directionTop = directionTop
        backdropView.directionBottom = directionBottom
        backdropView.directionLeft = directionLeft
        backdropView.directionRight = directionRight
    }
}

/**
 * Creates and remembers an [AmbientLightState].
 */
@Composable
fun rememberAmbientLightState(
    initialEnabled: Boolean = true,
    initialBlurRadius: Float = 50f,
    initialBrightness: Float = 1.0f,
    initialVibrance: Float = 0.0f,
    initialSpreadLimit: Float = 0.40f,
    initialEdgeSize: Float = 0.12f,
    initialFlickerReduction: Int = 10,
    initialContrast: Float = 1.0f,
    initialIsOledMode: Boolean = false,
    initialDetectLetterbox: Boolean = true,
    initialFadeStart: Float = 0.15f,
    initialFadeCurve: Float = 35f,
    initialDirectionTop: Boolean = true,
    initialDirectionBottom: Boolean = true,
    initialDirectionLeft: Boolean = true,
    initialDirectionRight: Boolean = true
): AmbientLightState {
    return rememberSaveable(
        saver = Saver(
            save = { state ->
                listOf(
                    state.isEnabled,
                    state.blurRadius,
                    state.brightness,
                    state.vibrance,
                    state.spreadLimit,
                    state.edgeSize,
                    state.flickerReduction,
                    state.contrast,
                    state.isOledMode,
                    state.detectLetterbox,
                    state.fadeStart,
                    state.fadeCurve,
                    state.directionTop,
                    state.directionBottom,
                    state.directionLeft,
                    state.directionRight
                )
            },
            restore = { list ->
                AmbientLightState(
                    initialEnabled = list[0] as Boolean,
                    initialBlurRadius = (list[1] as Number).toFloat(),
                    initialBrightness = (list[2] as Number).toFloat(),
                    initialVibrance = (list[3] as Number).toFloat(),
                    initialSpreadLimit = (list[4] as Number).toFloat(),
                    initialEdgeSize = (list[5] as Number).toFloat(),
                    initialFlickerReduction = (list[6] as Number).toInt(),
                    initialContrast = (list[7] as Number).toFloat(),
                    initialIsOledMode = list[8] as Boolean,
                    initialDetectLetterbox = list[9] as Boolean,
                    initialFadeStart = (list[10] as Number).toFloat(),
                    initialFadeCurve = (list[11] as Number).toFloat(),
                    initialDirectionTop = list[12] as Boolean,
                    initialDirectionBottom = list[13] as Boolean,
                    initialDirectionLeft = list[14] as Boolean,
                    initialDirectionRight = list[15] as Boolean
                )
            }
        )
    ) {
        AmbientLightState(
            initialEnabled, initialBlurRadius, initialBrightness, initialVibrance,
            initialSpreadLimit, initialEdgeSize, initialFlickerReduction, initialContrast,
            initialIsOledMode, initialDetectLetterbox, initialFadeStart, initialFadeCurve,
            initialDirectionTop, initialDirectionBottom, initialDirectionLeft, initialDirectionRight
        )
    }
}

/**
 * Top-level Composable container placing [AmbientBackdropView] behind user content
 * and automatically keeping light parameters synchronized.
 */
@Composable
fun AmbientLightContainer(
    state: AmbientLightState,
    modifier: Modifier = Modifier,
    backdropView: AmbientBackdropView = rememberAmbientBackdropView(),
    content: @Composable BoxScope.(backdropView: AmbientBackdropView) -> Unit
) {
    // Keep parameters in sync with state
    state.applyTo(backdropView)

    Box(modifier = modifier) {
        // LAYER 0: Ambient Backdrop View matching container bounds
        AndroidView(
            factory = { backdropView },
            modifier = Modifier.matchParentSize()
        )

        // LAYER 1: Foreground video content and controls
        content(backdropView)
    }
}

/**
 * Helper to remember an [AmbientBackdropView] instance bound to the local context.
 */
@Composable
fun rememberAmbientBackdropView(): AmbientBackdropView {
    val context = LocalContext.current
    return remember { AmbientBackdropView(context) }
}

/**
 * Helper to link an [InterceptingTextureView] with an [AmbientBackdropView],
 * creating a zero-allocation frame capture loop and automatic position synchronization.
 */
@SuppressLint("ComposableNaming")
@Composable
fun rememberAmbientVideoConnector(
    backdropView: AmbientBackdropView,
    textureView: InterceptingTextureView,
    downsampleWidth: Int = 128,
    downsampleHeight: Int = 72
) {
    val frameBuffer = remember(downsampleWidth, downsampleHeight) {
        createBitmap(downsampleWidth, downsampleHeight)
    }

    DisposableEffect(backdropView, textureView) {
        textureView.onFrameUpdatedListener = { view ->
            view.getBitmap(frameBuffer)
            backdropView.processFrame(frameBuffer)
        }

        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateVideoCoordinates(textureView, backdropView)
        }
        textureView.addOnLayoutChangeListener(layoutListener)
        backdropView.addOnLayoutChangeListener(layoutListener)

        onDispose {
            textureView.removeOnLayoutChangeListener(layoutListener)
            backdropView.removeOnLayoutChangeListener(layoutListener)
            textureView.onFrameUpdatedListener = null
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

    val relLeft = (videoLoc[0] - backdropLoc[0]).toFloat()
    val relTop = (videoLoc[1] - backdropLoc[1]).toFloat()
    val relRight = relLeft + videoView.width.toFloat()
    val relBottom = relTop + videoView.height.toFloat()

    backdropView.updateVideoPosition(relLeft, relTop, relRight, relBottom)
}
