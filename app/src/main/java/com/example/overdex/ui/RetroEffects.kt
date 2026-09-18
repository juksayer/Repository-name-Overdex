package com.example.overdex.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language
import com.example.overdex.ui.components.FilterSettings

@Language("AGSL")
const val POKEDEX_LCD_SHADER = """
    uniform shader image;
    uniform float2 resolution;
    uniform float scanlineIntensity;
    uniform float crtCurvature;
    uniform float noiseIntensity;

    vec2 curve(vec2 uv, float curv) {
        if (curv <= 0.0) return uv;
        vec2 centered = uv * 2.0 - 1.0;
        // Non-linear barrel distortion. Do not apply a uniform scale here:
        // uniform scaling only zooms the CRT image and hides its edges.
        float radiusSquared = dot(centered, centered);
        centered *= 1.0 + (0.18 * curv * radiusSquared);
        return centered * 0.5 + 0.5;
    }

    float random(vec2 st) {
        return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
    }

    half4 main(float2 fragCoord) {
        vec2 uv = fragCoord / resolution.xy;
        uv = curve(uv, crtCurvature);
        
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        half4 color = image.eval(uv * resolution.xy);
        
        // Scanlines
        if (scanlineIntensity > 0.0) {
            float scanline = sin(uv.y * resolution.y * 10.) * (0.05 * scanlineIntensity);
            color.rgb -= scanline;
        }
        
        // Noise
        if (noiseIntensity > 0.0) {
            float n = (random(uv * resolution.xy) - 5.0) * (0.15 * noiseIntensity);
            color.rgb += n;
        }
        
        // Vignette
        float vignette = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
        color.rgb *= pow(16.0 * vignette, 0.1);

        return color;
    }
"""

@Composable
fun Modifier.lcdDisplayEffect(settings: FilterSettings = FilterSettings()): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this
    }

    return this.graphicsLayer {
        val shader = RuntimeShader(POKEDEX_LCD_SHADER)
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("scanlineIntensity", settings.scanlineIntensity)
        shader.setFloatUniform("crtCurvature", settings.crtCurvature)
        shader.setFloatUniform("noiseIntensity", settings.noiseIntensity)
        
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
    }
}
