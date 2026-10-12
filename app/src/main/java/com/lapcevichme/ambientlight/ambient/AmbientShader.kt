package com.lapcevichme.ambientlight.ambient

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import org.intellij.lang.annotations.Language

/**
 * Manages the AGSL (Android Graphics Shading Language) runtime shader for ambient lighting.
 *
 * Implements:
 * 1. Geometric outward expansion (Spread): samples UV outward from video rect center.
 * 2. Letterbox/pillarbox compensation: crops inactive border margins.
 * 3. Vibrance boosting: non-linear saturation curve ported from Wessel Kroos' projector-webgl.js.
 * 4. Quadratic edge falloff: softly tapers ambient glow near screen extremities.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AmbientShader {

    @Language("AGSL")
    private val shaderSource = """
        uniform shader videoTexture;
        uniform float2 uResolution;
        uniform float2 uTextureResolution;
        uniform float2 uCropOffset;
        uniform float2 uCropScale;
        uniform float uSpread;
        uniform float uVibrance;

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
        // Provides vibrant tones without crushing highlight details or blowing out near-white pixels.
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
            float2 normUV = fragCoord / uResolution;

            // Outward projection: scale relative to center (0.5, 0.5)
            float2 centered = (normUV - 0.5) / max(uSpread, 0.001) + 0.5;

            // Apply letterbox crop compensation
            float2 croppedUV = uCropOffset + (centered * uCropScale);
            croppedUV = clamp(croppedUV, float2(0.0), float2(1.0));

            // Bilinear texture sample evaluated in downsampled pixel coordinates
            half4 color = videoTexture.eval(croppedUV * uTextureResolution);

            // Apply vibrance boost in HSV color space
            if (uVibrance != 0.0) {
                vec3 hsv = rgb2hsv(color.rgb);
                hsv.y = saturateValue(hsv.y, uVibrance);
                color.rgb = half3(hsv2rgb(hsv));
            }

            // Soft vignette falloff towards outer edges
            float2 dist = abs(normUV - 0.5) * 2.0;
            float falloff = 1.0 - smoothstep(0.65, 1.0, max(dist.x, dist.y));

            // Return with premultiplied alpha for proper Skia blending
            return half4(color.rgb * falloff, color.a * falloff);
        }
    """.trimIndent()

    val runtimeShader = RuntimeShader(shaderSource)

    init {
        // Initialize default safe uniforms to avoid driver crashes before first frame arrives
        val dummy = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, android.graphics.Color.DKGRAY)
        }
        updateTexture(dummy)
        updateResolution(100f, 100f)
        updateCrop(CropBounds())
        updateSpread(1.35f)
        updateVibrance(0.25f)
    }

    fun updateResolution(width: Float, height: Float) {
        runtimeShader.setFloatUniform("uResolution", width, height)
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

    fun updateSpread(spread: Float) {
        runtimeShader.setFloatUniform("uSpread", spread)
    }

    fun updateVibrance(vibrance: Float) {
        // Uniform range: -1.0 to 1.0 (0.0 = original saturation)
        runtimeShader.setFloatUniform("uVibrance", vibrance)
    }
}
