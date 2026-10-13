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
class CaptureTemplateManager(private val context: Context, preferencesName: String = "capture_templates") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    @Deprecated("Use getPokemonDetailTemplate instead", ReplaceWith("getPokemonDetailTemplate()"))
    fun getSummaryTemplate(): CaptureTemplate = getPokemonDetailTemplate()

    @Deprecated("Use getPokemonDetailTemplate instead", ReplaceWith("getPokemonDetailTemplate()"))
    fun getMovesTemplate(): CaptureTemplate = getPokemonDetailTemplate()

    fun getPokemonDetailTemplate(): CaptureTemplate = loadAdjustedTemplate(
        CaptureTemplate("PokemonGoDetailTemplate", com.example.overdex.data.registration.RegistrationFields.regions)
    )

    fun saveAdjustment(templateName: String, region: CaptureRegion) {
        prefs.edit().apply {
            putFloat("${templateName}_${region.id}_x", region.x)
            putFloat("${templateName}_${region.id}_y", region.y)
            putFloat("${templateName}_${region.id}_w", region.width)
            putFloat("${templateName}_${region.id}_h", region.height)
            apply()
        }
    }

    /** Publish all screenshot adjustments together, keeping every other template intact. */
    fun saveTemplate(template: CaptureTemplate, sourceWidth: Int, sourceHeight: Int) {
        val editor = prefs.edit()
        template.regions.forEach { region ->
            editor.putFloat("${template.name}_${region.id}_x", region.x)
                .putFloat("${template.name}_${region.id}_y", region.y)
                .putFloat("${template.name}_${region.id}_w", region.width)
                .putFloat("${template.name}_${region.id}_h", region.height)
        }
        editor.putInt("${template.name}_source_width", sourceWidth)
            .putInt("${template.name}_source_height", sourceHeight)
            .putString("${template.name}_device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        check(editor.commit()) { "Unable to save registration boxes." }
    }

    private fun loadAdjustedTemplate(base: CaptureTemplate): CaptureTemplate {
        val adjustedRegions = base.regions.map { region ->
            // Preserve an explicitly calibrated Fast Move Row ahead of its obsolete alias.
            // Read old keys without removing them, so installing never destroys calibration.
            val ids = if (region.id == "FastMoveRow") listOf("FastMoveRow", "SummaryFastMove") else listOf(region.id)
            val keys = ids.flatMap { id ->
                listOf(base.name, "PokemonGoMovesTemplate", "PokemonGoSummaryTemplate").map { "${it}_${id}" }
            }
            val key = keys.firstOrNull { prefs.contains("${it}_x") } ?: return@map region
            region.copy(
                x = prefs.getFloat("${key}_x", region.x), y = prefs.getFloat("${key}_y", region.y),
                width = prefs.getFloat("${key}_w", region.width), height = prefs.getFloat("${key}_h", region.height),
            )
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
