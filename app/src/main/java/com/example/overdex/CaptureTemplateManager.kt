package com.example.overdex

import android.content.Context
import com.example.overdex.model.CaptureRegion
import com.example.overdex.model.CaptureTemplate
import com.example.overdex.model.ObservationType

/**
 * Manages the definitions and user adjustments for visual capture templates.
 * 
 * Capture templates define the default regions of interest for different Pokémon GO
 * screens. This manager handles loading these defaults and applying persistent
 * user calibrations.
 */
class CaptureTemplateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("capture_templates", Context.MODE_PRIVATE)

    @Deprecated("Use getPokemonDetailTemplate instead", ReplaceWith("getPokemonDetailTemplate()"))
    fun getSummaryTemplate(): CaptureTemplate {
        val base = CaptureTemplate(
            name = "PokemonGoSummaryTemplate",
            regions = listOf(
                CaptureRegion("SpeciesName", 0.1f, 0.43f, 0.8f, 0.05f, ObservationType.OCR_TEXT),
                CaptureRegion("CombatPower", 0.35f, 0.06f, 0.3f, 0.06f, ObservationType.OCR_TEXT),
                CaptureRegion("TypeIcons", 0.35f, 0.62f, 0.3f, 0.05f, ObservationType.IMAGE_MATCH),
                CaptureRegion("CandyPanel", 0.05f, 0.68f, 0.9f, 0.12f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("SummaryFastMove", 0.05f, 0.85f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("ShinyIndicator", 0.8f, 0.1f, 0.1f, 0.05f, ObservationType.IMAGE_MATCH),
                CaptureRegion("Gender", 0.85f, 0.43f, 0.05f, 0.05f, ObservationType.IMAGE_MATCH),
                CaptureRegion("FavoriteStar", 0.88f, 0.06f, 0.08f, 0.06f, ObservationType.IMAGE_MATCH)
            )
        )
        return loadAdjustedTemplate(base)
    }

    @Deprecated("Use getPokemonDetailTemplate instead", ReplaceWith("getPokemonDetailTemplate()"))
    fun getMovesTemplate(): CaptureTemplate {
        val base = CaptureTemplate(
            name = "PokemonGoMovesTemplate",
            regions = listOf(
                CaptureRegion("FastMoveRow", 0.05f, 0.73f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("ChargedMoveRowA", 0.05f, 0.79f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("ChargedMoveRowB", 0.05f, 0.85f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH)
            )
        )
        return loadAdjustedTemplate(base)
    }

    fun getPokemonDetailTemplate(): CaptureTemplate {
        val base = CaptureTemplate(
            name = "PokemonGoDetailTemplate",
            regions = listOf(
                CaptureRegion("SpeciesName", 0.1f, 0.43f, 0.8f, 0.05f, ObservationType.OCR_TEXT),
                CaptureRegion("CombatPower", 0.35f, 0.06f, 0.3f, 0.06f, ObservationType.OCR_TEXT),
                CaptureRegion("TypeIcons", 0.35f, 0.62f, 0.3f, 0.05f, ObservationType.IMAGE_MATCH),
                CaptureRegion("CandyPanel", 0.05f, 0.68f, 0.9f, 0.12f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("SummaryFastMove", 0.05f, 0.85f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("ShinyIndicator", 0.8f, 0.1f, 0.1f, 0.05f, ObservationType.IMAGE_MATCH),
                CaptureRegion("Gender", 0.85f, 0.43f, 0.05f, 0.05f, ObservationType.IMAGE_MATCH),
                CaptureRegion("FavoriteStar", 0.88f, 0.06f, 0.08f, 0.06f, ObservationType.IMAGE_MATCH),
                CaptureRegion("FastMoveRow", 0.05f, 0.73f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("ChargedMoveRowA", 0.05f, 0.79f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH),
                CaptureRegion("ChargedMoveRowB", 0.05f, 0.85f, 0.9f, 0.06f, ObservationType.TEMPLATE_MATCH)
            )
        )
        return loadAdjustedTemplate(base)
    }

    fun saveAdjustment(templateName: String, region: CaptureRegion) {
        prefs.edit().apply {
            putFloat("${templateName}_${region.id}_x", region.x)
            putFloat("${templateName}_${region.id}_y", region.y)
            putFloat("${templateName}_${region.id}_w", region.width)
            putFloat("${templateName}_${region.id}_h", region.height)
            apply()
        }
    }

    private fun loadAdjustedTemplate(base: CaptureTemplate): CaptureTemplate {
        val adjustedRegions = base.regions.map { region ->
            var x = region.x
            var y = region.y
            var w = region.width
            var h = region.height
            var found = false

            // 1. Check current template first
            if (prefs.contains("${base.name}_${region.id}_x")) {
                x = prefs.getFloat("${base.name}_${region.id}_x", region.x)
                y = prefs.getFloat("${base.name}_${region.id}_y", region.y)
                w = prefs.getFloat("${base.name}_${region.id}_w", region.width)
                h = prefs.getFloat("${base.name}_${region.id}_h", region.height)
                found = true
            } 
            // 2. Compatibility Fallback for the canonical Detail template
            else if (base.name == "PokemonGoDetailTemplate") {
                val legacyTemplates = listOf("PokemonGoSummaryTemplate", "PokemonGoMovesTemplate")
                for (legacy in legacyTemplates) {
                    if (prefs.contains("${legacy}_${region.id}_x")) {
                        x = prefs.getFloat("${legacy}_${region.id}_x", region.x)
                        y = prefs.getFloat("${legacy}_${region.id}_y", region.y)
                        w = prefs.getFloat("${legacy}_${region.id}_w", region.width)
                        h = prefs.getFloat("${legacy}_${region.id}_h", region.height)
                        found = true
                        break
                    }
                }
            }

            if (found) {
                region.copy(x = x, y = y, width = w, height = h)
            } else {
                region
            }
        }
        return base.copy(regions = adjustedRegions)
    }

    /**
     * Resolves normalized coordinates to pixel coordinates.
     */
    fun resolveRegion(region: CaptureRegion, width: Int, height: Int): android.graphics.Rect {
        val left = (region.x * width).toInt()
        val top = (region.y * height).toInt()
        val right = ((region.x + region.width) * width).toInt()
        val bottom = ((region.y + region.height) * height).toInt()
        return android.graphics.Rect(left, top, right, bottom)
    }
}
