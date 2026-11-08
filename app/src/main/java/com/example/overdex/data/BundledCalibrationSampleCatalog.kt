package com.example.overdex.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class BundledCalibrationSampleLabel(val name: String, val deviceLabel: String)

/** Labels describe supplied sources; decoded image headers remain the geometry authority. */
object BundledCalibrationSampleCatalog {
    const val ASSET_PATH = "battle_sample_catalog.json"

    fun labels(text: String): Map<String, BundledCalibrationSampleLabel> {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyMap()
        if ((root["schemaVersion"] as? JsonPrimitive)?.intOrNull != 1) return emptyMap()
        val entries = root["entries"] as? JsonArray ?: return emptyMap()
        return entries.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            if ((entry["calibrationEligible"] as? JsonPrimitive)?.booleanOrNull != true) return@mapNotNull null
            val file = (entry["assetFile"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (!FILE_NAME.matches(file)) return@mapNotNull null
            val name = (entry["name"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val device = (entry["deviceLabel"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            file to BundledCalibrationSampleLabel(name, device)
        }.toMap()
    }

    private val FILE_NAME = Regex("[A-Za-z0-9_.-]+\\.(png|jpe?g|webp)", RegexOption.IGNORE_CASE)
}
