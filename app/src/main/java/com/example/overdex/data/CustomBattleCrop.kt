package com.example.overdex.data

import com.example.overdex.model.AnchorRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** A player-authored, purpose-named crop for the current battle UI layout. */
@Serializable
data class CustomBattleCrop(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val method: CustomCropMethod = CustomCropMethod.MANUAL,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val enabled: Boolean = true
) {
    val region: AnchorRegion get() = AnchorRegion(x, y, width, height)
}

@Serializable
enum class CustomCropMethod { OCR, IMAGE, COLOR, MOTION, MANUAL }

/** Persists Draggy Boxes separately from built-in battle calibration. */
class CustomBattleCropManager(private val preferences: android.content.SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): List<CustomBattleCrop> = runCatching {
        val encoded = preferences.getString(KEY, null) ?: return emptyList()
        json.decodeFromString<List<CustomBattleCrop>>(encoded)
    }.getOrDefault(emptyList())

    fun save(crops: List<CustomBattleCrop>) {
        preferences.edit().putString(KEY, json.encodeToString(crops)).apply()
    }

    companion object {
        private const val KEY = "custom_battle_crops_v1"
        fun from(context: android.content.Context) = CustomBattleCropManager(
            context.getSharedPreferences("overmon_calibration", android.content.Context.MODE_PRIVATE)
        )
    }
}
