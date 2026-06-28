package com.example.overdex.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language

@Language("AGSL")
const val POKEDEX_LCD_SHADER = """
    uniform shader image;
    uniform float2 resolution;

    vec2 curve(vec2 uv) {
        uv = (uv - 0.5) * 2.0;
        uv *= 1.05;
        uv.x *= 1.0 + pow((abs(uv.y) / 8.0), 2.0);
        uv.y *= 1.0 + pow((abs(uv.x) / 7.0), 2.0);
        uv = (uv / 2.0) + 0.5;
        return uv;
    }

    half4 main(float2 fragCoord) {
        vec2 uv = fragCoord / resolution.xy;
        uv = curve(uv);
        
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        half4 color = image.eval(uv * resolution.xy);
        
        // Scanlines
        float scanline = sin(uv.y * resolution.y * 1.5) * 0.05;
        color.rgb -= scanline;
        
        // Vignette
        float vignette = uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
        color.rgb *= pow(16.0 * vignette, 0.1);

        return color;
    }
"""

@Composable
fun Modifier.lcdDisplayEffect(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this
    }

    return this.graphicsLayer {
        val shader = RuntimeShader(POKEDEX_LCD_SHADER)
        shader.setFloatUniform("resolution", size.width, size.height)
        
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
    }
}
