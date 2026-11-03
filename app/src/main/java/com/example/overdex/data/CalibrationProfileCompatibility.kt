package com.example.overdex.data

import java.util.Locale

/** Device identity and physical display geometry select profiles without changing saved crops. */
object CalibrationProfileCompatibility {
    fun matchesDevice(
        profile: MatchCalibrationProfileSummary,
        deviceModel: String,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0 ||
            profile.screenWidth <= 0 || profile.screenHeight <= 0
        ) return false

        val detectedModel = normalizedModel(deviceModel)
        val savedModel = normalizedModel(profile.deviceModel)
        return detectedModel.isNotEmpty() && detectedModel != "unknown" &&
            savedModel == detectedModel &&
            profile.screenWidth == screenWidth && profile.screenHeight == screenHeight
    }

    /**
     * A shared aspect ratio or resolution does not establish a device match. The explicit
     * all-devices view keeps matches first and preserves the saved order within each group.
     */
    fun visibleProfiles(
        profiles: List<MatchCalibrationProfileSummary>,
        deviceModel: String,
        screenWidth: Int,
        screenHeight: Int,
        includeOtherDevices: Boolean = false
    ): List<MatchCalibrationProfileSummary> {
        val (matching, other) = profiles.partition {
            matchesDevice(it, deviceModel, screenWidth, screenHeight)
        }
        return if (includeOtherDevices) matching + other else matching
    }

    private fun normalizedModel(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
