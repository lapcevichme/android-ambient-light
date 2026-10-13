package com.lapcevichme.ambientlight.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView

/**
 * TextureView that safely intercepts onSurfaceTextureUpdated frames even when
 * ExoPlayer overwrites the surfaceTextureListener via setVideoTextureView().
 *
 * ExoPlayer's internal ComponentListener discards onSurfaceTextureUpdated.
 * This class wraps any assigned listener to ensure ambient light captures every frame.
 */
class InterceptingTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {

    private var exoListener: SurfaceTextureListener? = null
    var onFrameUpdatedListener: ((TextureView) -> Unit)? = null

    init {
        // Initialize with default wrapper
        super.setSurfaceTextureListener(createWrapper(null))
    }

    override fun setSurfaceTextureListener(listener: SurfaceTextureListener?) {
        // HACK: ExoPlayer calls setSurfaceTextureListener() on attach, which normally
        // wipes out external listeners. We intercept it and forward lifecycle events.
        exoListener = listener
        super.setSurfaceTextureListener(createWrapper(listener))
    }

    override fun getSurfaceTextureListener(): SurfaceTextureListener? {
        return exoListener
    }

    private fun createWrapper(delegate: SurfaceTextureListener?): SurfaceTextureListener {
        return object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                delegate?.onSurfaceTextureAvailable(surface, width, height)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                delegate?.onSurfaceTextureSizeChanged(surface, width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return delegate?.onSurfaceTextureDestroyed(surface) ?: true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                delegate?.onSurfaceTextureUpdated(surface)
                onFrameUpdatedListener?.invoke(this@InterceptingTextureView)
            }
        }
    }
}
