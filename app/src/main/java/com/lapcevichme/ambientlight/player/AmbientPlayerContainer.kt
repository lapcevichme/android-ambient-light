package com.lapcevichme.ambientlight.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lapcevichme.ambientlight.ambient.AmbientBackdropView

/**
 * Container emulating the player layer structure of YouTube's watch_while_layout.
 *
 * Index 0: AmbientBackdropView (expands across the container beneath all elements).
 * Index 1: TextureView (presents the active ExoPlayer video stream).
 */
class AmbientPlayerContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val ambientBackdropView: AmbientBackdropView = AmbientBackdropView(context)
    val textureView: TextureView = TextureView(context)

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

        // Video texture positioned with standard 16:9 aspect ratio sizing
        val videoParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        addView(textureView, 1, videoParams)

        setupTextureListener()
    }

    private fun setupTextureListener() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                player?.setVideoTextureView(textureView)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                player?.clearVideoTextureView(textureView)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // Hardware downsample directly into the preallocated low-res buffer
                textureView.getBitmap(frameBufferBitmap)
                ambientBackdropView.processFrame(frameBufferBitmap)
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun attachPlayer(exoPlayer: ExoPlayer) {
        this.player = exoPlayer
        if (textureView.isAvailable) {
            exoPlayer.setVideoTextureView(textureView)
        }
    }

    fun detachPlayer() {
        player?.clearVideoTextureView(textureView)
        player = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Set standard 16:9 video height for the TextureView
        val videoHeight = (width * 9) / 16

        val textureLp = textureView.layoutParams
        if (textureLp.height != videoHeight) {
            textureLp.height = videoHeight
            textureView.layoutParams = textureLp
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
