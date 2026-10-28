package com.lapcevichme.ambientlight.ambient

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.set
import org.intellij.lang.annotations.Language

/**
 * Interface defining operations for ambient light shader renderers.
 * Decouples views from API 33-specific [RuntimeShader] implementations.
 */
internal interface AmbientShaderDelegate {
    val shader: Shader
    fun updateResolution(width: Float, height: Float)
    fun updateVideoRect(left: Float, top: Float, right: Float, bottom: Float)
    fun updateTexture(bitmap: Bitmap)
    fun updateCrop(bounds: CropBounds)
    fun updateSpreadLimit(limit: Float)
    fun updateEdgeSize(edgeSize: Float)
    fun updateVibrance(vibrance: Float)
    fun updateBrightness(brightness: Float)
    fun updateContrast(contrast: Float)
    fun updateDirections(top: Boolean, right: Boolean, bottom: Boolean, left: Boolean)
    fun updateFadeParams(fadeStart: Float, fadeCurve: Float)
    fun updateOledMode(enabled: Boolean)
}

/**
 * Manages the AGSL runtime shader for localized ambient light radiating from behind the player.
 *
 * Implements:
 * 1. Perimeter sampling: Kroos-style stacked projector copies grown by equal pixel steps (edge size).
 * 2. Letterbox/pillarbox crop compensation.
 * 3. Vibrance boosting in HSV color space.
 * 4. Center attenuation: dims pixels directly underneath the player so no sharp duplicate video is rendered.
 * 5. Radial distance falloff: smoothly dissolves light into the page background.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AmbientShader : AmbientShaderDelegate {

    @Language("AGSL")
    private val shaderSource = """
        uniform shader videoTexture;
        uniform float2 uResolution;
        uniform float2 uTextureResolution;
        uniform float4 uVideoRect; // Normalized (left, top, right, bottom) of video in backdrop space [0, 1]
        uniform float2 uCropOffset;
        uniform float2 uCropScale;
        uniform float uSpreadLimit;
        uniform float uEdgeSize;
        uniform float uVibrance;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float4 uDirections; // (top, right, bottom, left) [1.0 = enabled, 0.0 = disabled]
        uniform float uFadeStart;
        uniform float uFadeCurve;
        uniform float uOledMode;

        // Converts RGB color to HSV representation
        vec3 rgb2hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        // Converts HSV back to standard RGB
        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        // Non-linear saturation response curve from Wessel Kroos' projector-webgl.js.
        float saturateValue(float c, float v) {
            float x = c;
            if (v < 0.0) {
                x = 1.0 - c;
            }
            float a = 1.0 + 5.0 * (1.0 - abs(v));
            float y = a * a - x * ((a * a - 1.0) / (a * a)) - (a - x / a) * (a - x / a);
            float d = y - x;
            y = min(1.0, x + d * 5.0);
            return (v >= 0.0) ? y : (1.0 - y);
        }

        // Samples the video frame at normalized video UV with letterbox crop compensation
        half4 sampleVideo(float2 normVideoUV) {
            float2 croppedUV = clamp(uCropOffset + (normVideoUV * uCropScale), float2(0.0), float2(1.0));
            return videoTexture.eval(croppedUV * uTextureResolution);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;

            // Video center and half-size in backdrop coordinates [0, 1]
            float2 videoCenter = (uVideoRect.xy + uVideoRect.zw) * 0.5;
            float2 videoHalfSize = max((uVideoRect.zw - uVideoRect.xy) * 0.5, float2(0.001));
            float2 videoHalfSizePx = videoHalfSize * uResolution;

            // Fragment vector from video center normalized by video half-size
            // At video perimeter, max(abs(p.x), abs(p.y)) == 1.0
            float2 p = (uv - videoCenter) / videoHalfSize;
            float s = max(abs(p.x), abs(p.y));

            // Maximum expansion scale
            float spread = max(uSpreadLimit, 0.01);

            half4 color;
            float falloff = 0.0;

            if (s <= 1.0) {
                // Inside video bounds: solid full brightness so blur does not create dark dips near borders
                color = sampleVideo(p * 0.5 + 0.5);
                falloff = 1.0;
            } else {
                // Directional masking (Wessel Kroos directions filter):
                float dirMask = 1.0;
                if (p.y < -1.0 && uDirections.x < 0.5) dirMask = 0.0; // Top disabled
                if (p.x > 1.0 && uDirections.y < 0.5) dirMask = 0.0;  // Right disabled
                if (p.y > 1.0 && uDirections.z < 0.5) dirMask = 0.0;  // Bottom disabled
                if (p.x < -1.0 && uDirections.w < 0.5) dirMask = 0.0; // Left disabled

                // Wessel Kroos' projector (projector-webgl.js multiTexture): stacked copies of the frame,
                // each grown by the same number of pixels (uEdgeSize of the larger video side) on every
                // side. A fragment samples the smallest copy containing it, so rays fan out relative to
                // the video width/height rather than radially from the center.
                float2 offsetPx = p * videoHalfSizePx;
                float growPx = max(abs(offsetPx.x) - videoHalfSizePx.x, abs(offsetPx.y) - videoHalfSizePx.y);
                float stepPx = max(uEdgeSize * max(videoHalfSizePx.x, videoHalfSizePx.y), 1.0);
                // Continuous level instead of Kroos' ceil(): discrete levels show up as a staircase
                // in narrow glow areas. +0.5 keeps his average sampling depth (half a step inside).
                float level = growPx / stepPx + 0.5;
                float2 copyHalfPx = videoHalfSizePx + level * stepPx;
                color = sampleVideo(clamp(offsetPx / copyHalfPx, -1.0, 1.0) * 0.5 + 0.5);

                // Isotropic distance falloff in physical pixels (rounded around corners),
                // reach is relative to video width as in Wessel Kroos' projector-shadow.js
                float2 outsidePx = max(abs(p) - 1.0, float2(0.0)) * videoHalfSizePx;
                float maxDistPx = max(spread * 2.2 * videoHalfSizePx.x, 1.0);
                float dist = length(outsidePx) / maxDistPx;

                if (dist < 1.0 && dirMask > 0.0) {
                    float fadeStart = uFadeStart;
                    float easing = 16.0 / (max(uFadeCurve, 1.0) * 0.64);
                    float fadeProgress = clamp((dist - fadeStart) / max(1.0 - fadeStart, 0.001), 0.0, 1.0);
                    float shadowAlpha = pow(fadeProgress, easing);
                    falloff = 1.0 - shadowAlpha;
                }
            }

            // Contrast adjustment (around midpoint 0.5)
            if (uContrast != 1.0) {
                color.rgb = clamp(((color.rgb - 0.5) * uContrast) + 0.5, 0.0, 1.0);
            }

            // Apply vibrance boost in HSV color space
            if (uVibrance != 0.0) {
                vec3 hsv = rgb2hsv(color.rgb);
                hsv.y = saturateValue(hsv.y, uVibrance);
                color.rgb = half3(hsv2rgb(hsv));
            }

            // OLED pure blacks suppression: gently fade out weak glow on dark pixels
            if (uOledMode > 0.5) {
                float lum = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
                if (lum < 0.04) {
                    falloff *= smoothstep(0.0, 0.04, lum);
                }
            }

            float intensity = falloff * uBrightness;

            // Output premultiplied alpha color for proper Skia blending
            return half4(color.rgb * intensity, intensity);
        }
    """.trimIndent()

    val runtimeShader = RuntimeShader(shaderSource)
    override val shader: Shader get() = runtimeShader

    init {
        val dummy = createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            this[0, 0] = Color.DKGRAY
        }
        updateTexture(dummy)
        updateResolution(100f, 100f)
        updateVideoRect(0.1f, 0.1f, 0.9f, 0.5f)
        updateCrop(CropBounds())
        updateSpreadLimit(0.4f)
        updateEdgeSize(0.12f)
        updateVibrance(0.0f)
        updateBrightness(1.0f)
        updateContrast(1.0f)
        updateDirections(top = true, right = true, bottom = true, left = true)
        updateFadeParams(fadeStart = 0.15f, fadeCurve = 35f)
        updateOledMode(false)
    }

    override fun updateResolution(width: Float, height: Float) {
        runtimeShader.setFloatUniform("uResolution", width, height)
    }

    override fun updateVideoRect(left: Float, top: Float, right: Float, bottom: Float) {
        runtimeShader.setFloatUniform("uVideoRect", left, top, right, bottom)
    }

    override fun updateTexture(bitmap: Bitmap) {
        runtimeShader.setFloatUniform("uTextureResolution", bitmap.width.toFloat(), bitmap.height.toFloat())
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        runtimeShader.setInputShader("videoTexture", bitmapShader)
    }

    override fun updateCrop(bounds: CropBounds) {
        val cropOffsetX = bounds.left
        val cropOffsetY = bounds.top
        val cropScaleX = 1f - (bounds.left + bounds.right)
        val cropScaleY = 1f - (bounds.top + bounds.bottom)

        runtimeShader.setFloatUniform("uCropOffset", cropOffsetX, cropOffsetY)
        runtimeShader.setFloatUniform("uCropScale", cropScaleX.coerceAtLeast(0.01f), cropScaleY.coerceAtLeast(0.01f))
    }

    override fun updateSpreadLimit(limit: Float) {
        runtimeShader.setFloatUniform("uSpreadLimit", limit)
    }

    override fun updateEdgeSize(edgeSize: Float) {
        runtimeShader.setFloatUniform("uEdgeSize", edgeSize)
    }

    override fun updateVibrance(vibrance: Float) {
        // saturateValue() is only defined for [-1, 1]; beyond that the curve divides by zero (NaN colors)
        runtimeShader.setFloatUniform("uVibrance", vibrance.coerceIn(-1f, 1f))
    }

    override fun updateBrightness(brightness: Float) {
        runtimeShader.setFloatUniform("uBrightness", brightness)
    }

    override fun updateContrast(contrast: Float) {
        runtimeShader.setFloatUniform("uContrast", contrast)
    }

    override fun updateDirections(top: Boolean, right: Boolean, bottom: Boolean, left: Boolean) {
        runtimeShader.setFloatUniform(
            "uDirections",
            if (top) 1f else 0f,
            if (right) 1f else 0f,
            if (bottom) 1f else 0f,
            if (left) 1f else 0f
        )
    }

    override fun updateFadeParams(fadeStart: Float, fadeCurve: Float) {
        runtimeShader.setFloatUniform("uFadeStart", fadeStart)
        runtimeShader.setFloatUniform("uFadeCurve", fadeCurve)
    }

    override fun updateOledMode(enabled: Boolean) {
        runtimeShader.setFloatUniform("uOledMode", if (enabled) 1f else 0f)
    }
}
