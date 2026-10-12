package com.lapcevichme.ambientlight.player

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lapcevichme.ambientlight.ambient.AmbientBackdropView

/**
 * Container emulating the player layer structure of YouTube's watch_while_layout.
 *
 * Index 0: AmbientBackdropView (expands across the full container beneath the player).
 * Index 1: InterceptingTextureView (presents the active ExoPlayer video stream with frame interception).
 */
class AmbientPlayerContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val ambientBackdropView: AmbientBackdropView = AmbientBackdropView(context)
    val textureView: InterceptingTextureView = InterceptingTextureView(context)

    // Preallocated downsampled frame buffer (32x18) to avoid per-frame GC allocations
    private val frameBufferBitmap: Bitmap = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)

    private var player: ExoPlayer? = null

    init {
        // Backdrop fills container to illuminate behind the player and under overlapping UI
        val backdropParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        addView(ambientBackdropView, 0, backdropParams)

        // Center video texture with padding so the ambient light aura is visible on all sides
        val horizontalMarginPx = dpToPx(16f)
        val videoParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = horizontalMarginPx
            rightMargin = horizontalMarginPx
        }
        addView(textureView, 1, videoParams)

        setupFrameInterceptor()
    }

    private fun setupFrameInterceptor() {
        textureView.onFrameUpdatedListener = { view ->
            // Hardware downsample directly into the preallocated low-res buffer
            view.getBitmap(frameBufferBitmap)
            ambientBackdropView.processFrame(frameBufferBitmap)
        }
    }

    @OptIn(UnstableApi::class)
    fun attachPlayer(exoPlayer: ExoPlayer) {
        this.player = exoPlayer
        exoPlayer.setVideoTextureView(textureView)
    }

    fun detachPlayer() {
        player?.clearVideoTextureView(textureView)
        player = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = MeasureSpec.getSize(widthMeasureSpec)
        val videoWidth = (totalWidth - dpToPx(32f)).coerceAtLeast(0)
        // Standard 16:9 video height for the TextureView
        val videoHeight = (videoWidth * 9) / 16

        val textureLp = textureView.layoutParams
        if (textureLp.height != videoHeight) {
            textureLp.height = videoHeight
            textureView.layoutParams = textureLp
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
