package com.lapcevichme.ambientlight.ambient

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

/**
 * Manages the AGSL runtime shader for localized ambient light radiating from behind the player.
 *
 * Implements:
 * 1. Perimeter sampling: rays and glow emanate from the actual video borders outward.
 * 2. Letterbox/pillarbox crop compensation.
 * 3. Vibrance boosting in HSV color space.
 * 4. Center attenuation: dims pixels directly underneath the player so no sharp duplicate video is rendered.
 * 5. Radial distance falloff: smoothly dissolves light into the page background.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AmbientShader {

    @Language("AGSL")
    private val shaderSource = """
        uniform shader videoTexture;
        uniform float2 uResolution;
        uniform float2 uTextureResolution;
        uniform float4 uVideoRect; // Normalized (left, top, right, bottom) of video in backdrop space [0, 1]
        uniform float2 uCropOffset;
        uniform float2 uCropScale;
        uniform float uSpreadLimit;
        uniform float uVibrance;
        uniform float uBrightness;

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

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;

            // Clamp coordinate to the video perimeter to expand border colors outward
            float2 clampedUV = clamp(uv, uVideoRect.xy, uVideoRect.zw);

            // Normalized coordinates inside the video [0, 1]
            float2 videoSize = max(uVideoRect.zw - uVideoRect.xy, float2(0.001));
            float2 normVideoUV = (clampedUV - uVideoRect.xy) / videoSize;

            // Apply letterbox crop compensation
            float2 croppedUV = uCropOffset + (normVideoUV * uCropScale);
            croppedUV = clamp(croppedUV, float2(0.0), float2(1.0));

            // Bilinear texture sample evaluated in downsampled pixel coordinates
            half4 color = videoTexture.eval(croppedUV * uTextureResolution);

            // Apply vibrance boost in HSV color space
            if (uVibrance != 0.0) {
                vec3 hsv = rgb2hsv(color.rgb);
                hsv.y = saturateValue(hsv.y, uVibrance);
                color.rgb = half3(hsv2rgb(hsv));
            }

            // Aspect-ratio normalized Euclidean distance from video bounds
            float2 d = max(float2(0.0), max(uVideoRect.xy - uv, uv - uVideoRect.zw));
            float aspect = uResolution.x / max(uResolution.y, 1.0);
            float dist = length(float2(d.x, d.y / aspect));

            // Radial falloff: light emanates from perimeter and softly dissolves outward
            float spread = max(uSpreadLimit, 0.01);
            float falloff = 1.0 - smoothstep(0.0, spread, dist);

            // Dim center under the video to prevent duplicate player rendering
            float centerAttenuation = smoothstep(0.0, 0.05, dist);
            float edgeFactor = mix(0.25, 1.0, centerAttenuation);

            float intensity = falloff * edgeFactor * uBrightness;

            // Output premultiplied alpha color for proper Skia blending
            return half4(color.rgb * intensity, intensity);
        }
    """.trimIndent()

    val runtimeShader = RuntimeShader(shaderSource)

    init {
        val dummy = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.DKGRAY)
        }
        updateTexture(dummy)
        updateResolution(100f, 100f)
        updateVideoRect(0.1f, 0.1f, 0.9f, 0.5f)
        updateCrop(CropBounds())
        updateSpreadLimit(0.4f)
        updateVibrance(0.4f)
        updateBrightness(1.0f)
    }

    fun updateResolution(width: Float, height: Float) {
        runtimeShader.setFloatUniform("uResolution", width, height)
    }

    fun updateVideoRect(left: Float, top: Float, right: Float, bottom: Float) {
        runtimeShader.setFloatUniform("uVideoRect", left, top, right, bottom)
    }

    fun updateTexture(bitmap: Bitmap) {
        runtimeShader.setFloatUniform("uTextureResolution", bitmap.width.toFloat(), bitmap.height.toFloat())
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        runtimeShader.setInputShader("videoTexture", bitmapShader)
    }

    fun updateCrop(bounds: CropBounds) {
        val cropOffsetX = bounds.left
        val cropOffsetY = bounds.top
        val cropScaleX = 1f - (bounds.left + bounds.right)
        val cropScaleY = 1f - (bounds.top + bounds.bottom)

        runtimeShader.setFloatUniform("uCropOffset", cropOffsetX, cropOffsetY)
        runtimeShader.setFloatUniform("uCropScale", cropScaleX.coerceAtLeast(0.01f), cropScaleY.coerceAtLeast(0.01f))
    }

    fun updateSpreadLimit(limit: Float) {
        runtimeShader.setFloatUniform("uSpreadLimit", limit)
    }

    fun updateVibrance(vibrance: Float) {
        runtimeShader.setFloatUniform("uVibrance", vibrance)
    }

    fun updateBrightness(brightness: Float) {
        runtimeShader.setFloatUniform("uBrightness", brightness)
    }
}
