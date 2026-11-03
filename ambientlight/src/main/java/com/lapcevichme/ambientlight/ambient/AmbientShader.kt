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
 * 2. Letterbox/pillarbox crop: per axis, the projector shrinks to the content for glow crossing the bars.
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

        float glowFalloff(float distPx, float maxDistPx) {
            float dist = distPx / maxDistPx;
            if (dist >= 1.0) return 0.0;
            float easing = 16.0 / (max(uFadeCurve, 1.0) * 0.64);
            float fadeProgress = clamp((dist - uFadeStart) / max(1.0 - uFadeStart, 0.001), 0.0, 1.0);
            return 1.0 - pow(fadeProgress, easing);
        }

        // Wessel Kroos' projector (projector-webgl.js multiTexture) for a projector rect given as a crop of
        // the player: stacked copies of the frame, each grown by the same number of pixels on every side.
        // A fragment samples the smallest copy containing it, so rays fan out relative to the projector
        // size rather than radially from the center. Returns the color in rgb and the falloff in alpha.
        half4 project(float2 fragPx, float2 playerMinPx, float2 playerSizePx, float2 cropOffset, float2 cropScale,
                      float stepPx, float maxDistPx) {
            float2 halfPx = max(cropScale * playerSizePx * 0.5, float2(0.5));
            float2 offsetPx = fragPx - (playerMinPx + cropOffset * playerSizePx + halfPx);
            float growPx = max(abs(offsetPx.x) - halfPx.x, abs(offsetPx.y) - halfPx.y);

            float2 q;
            float falloff = 1.0;
            if (growPx <= 0.0) {
                q = offsetPx / halfPx;
            } else {
                // Continuous level instead of Kroos' ceil(): discrete levels show up as a staircase
                // in narrow glow areas. +0.5 keeps his average sampling depth (half a step inside).
                float level = growPx / stepPx + 0.5;
                q = offsetPx / (halfPx + level * stepPx);
                // Isotropic distance falloff in physical pixels (rounded around corners)
                falloff = glowFalloff(length(max(abs(offsetPx) - halfPx, float2(0.0))), maxDistPx);
            }
            float2 textureUV = clamp(cropOffset + (clamp(q, -1.0, 1.0) * 0.5 + 0.5) * cropScale, float2(0.0), float2(1.0));
            return half4(videoTexture.eval(textureUV * uTextureResolution).rgb, falloff);
        }

        half4 main(float2 fragCoord) {
            float2 playerMinPx = uVideoRect.xy * uResolution;
            float2 playerSizePx = max((uVideoRect.zw - uVideoRect.xy) * uResolution, float2(1.0));
            float2 playerHalfPx = playerSizePx * 0.5;

            // Step size and reach stay relative to the full player (Kroos' ratio)
            float stepPx = max(uEdgeSize * max(playerHalfPx.x, playerHalfPx.y), 1.0);
            float maxDistPx = max(max(uSpreadLimit, 0.01) * 2.2 * playerHalfPx.x, 1.0);

            // Detected bars only matter for glow that has to cross them: letterbox (top/bottom) crop
            // shrinks the projector for the glow above/below the player, pillarbox (left/right) crop for
            // the glow at its sides. Otherwise a narrow pillarboxed column would light the whole width
            // above the player through its side edges, unlike youtube-ambilight, which keeps the full player.
            half4 vertical = project(fragCoord, playerMinPx, playerSizePx,
                float2(0.0, uCropOffset.y), float2(1.0, uCropScale.y), stepPx, maxDistPx);
            half4 horizontal = project(fragCoord, playerMinPx, playerSizePx,
                float2(uCropOffset.x, 0.0), float2(uCropScale.x, 1.0), stepPx, maxDistPx);

            // Blend across the player's diagonals (Kroos' direction triangles) to avoid a seam in the corners
            float2 edgePx = abs(fragCoord - (playerMinPx + playerHalfPx)) - playerHalfPx;
            // (narrow band: a wide one leaks side light into the outer corners)
            float blendPx = max(stepPx * 0.25, 1.0);
            float verticalWeight = smoothstep(-blendPx, blendPx, edgePx.y - edgePx.x);
            half4 projected = mix(horizontal, vertical, verticalWeight);
            half4 color = half4(projected.rgb, 1.0);
            float falloff = projected.a;

            // Directional masking (Wessel Kroos directions filter), relative to the player
            float2 p = (fragCoord - (playerMinPx + playerHalfPx)) / playerHalfPx;
            if ((p.y < -1.0 && uDirections.x < 0.5) || (p.x > 1.0 && uDirections.y < 0.5) ||
                (p.y > 1.0 && uDirections.z < 0.5) || (p.x < -1.0 && uDirections.w < 0.5)) {
                falloff = 0.0;
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
