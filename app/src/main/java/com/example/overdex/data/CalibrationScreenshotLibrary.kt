package com.example.overdex.data

import kotlin.math.abs
import kotlin.math.min

/** Geometry describes the untouched source image, never the app's content bounds. */
data class CalibrationScreenshotReference(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    /** An optional user-supplied folder label, not an inferred hardware identity. */
    val deviceLabel: String? = null,
) {
    val hasDimensions: Boolean get() = width > 0 && height > 0
}

sealed interface CalibrationScreenshotFilter {
    data object ThisScreen : CalibrationScreenshotFilter
    data object All : CalibrationScreenshotFilter
    data class Shape(val width: Int, val height: Int) : CalibrationScreenshotFilter
    data class Device(val label: String) : CalibrationScreenshotFilter
}

object CalibrationScreenshotLibrary {
    // Allows a few aligned RGBA columns / rounding pixels, but not another phone's shape.
    private const val ASPECT_TOLERANCE = 0.005

    fun sameShape(width: Int, height: Int, otherWidth: Int, otherHeight: Int): Boolean {
        if (width <= 0 || height <= 0 || otherWidth <= 0 || otherHeight <= 0) return false
        val ratio = width.toDouble() / height
        val otherRatio = otherWidth.toDouble() / otherHeight
        return abs(ratio / otherRatio - 1.0) <= ASPECT_TOLERANCE
    }

    /** An unreadable header is never evidence that an image belongs to this screen. */
    fun matchesScreen(reference: CalibrationScreenshotReference?, screenWidth: Int, screenHeight: Int): Boolean =
        reference?.hasDimensions == true && sameShape(reference.width, reference.height, screenWidth, screenHeight)

    /** Decoders may downsample uniformly, but cannot silently change the source's screen shape. */
    fun matchesDecodedScreen(reference: CalibrationScreenshotReference?, decodedWidth: Int, decodedHeight: Int,
        screenWidth: Int, screenHeight: Int): Boolean =
        matchesScreen(reference, screenWidth, screenHeight) && reference != null &&
            sameShape(decodedWidth, decodedHeight, reference.width, reference.height) &&
            sameShape(decodedWidth, decodedHeight, screenWidth, screenHeight)

    fun filter(
        references: List<CalibrationScreenshotReference>,
        filter: CalibrationScreenshotFilter,
        screenWidth: Int,
        screenHeight: Int,
    ): List<CalibrationScreenshotReference> = references.filter { reference ->
        when (filter) {
            CalibrationScreenshotFilter.All -> true
            CalibrationScreenshotFilter.ThisScreen -> matchesScreen(reference, screenWidth, screenHeight)
            is CalibrationScreenshotFilter.Shape ->
                sameShape(reference.width, reference.height, filter.width, filter.height)
            is CalibrationScreenshotFilter.Device -> reference.deviceLabel == filter.label
        }
    }

    fun shapeChoices(references: List<CalibrationScreenshotReference>): List<CalibrationScreenshotFilter.Shape> {
        val shapes = mutableListOf<CalibrationScreenshotFilter.Shape>()
        references.filter { it.hasDimensions }
            .sortedWith(compareBy<CalibrationScreenshotReference>({ it.width.toDouble() / it.height }, { -it.height }))
            .forEach { reference ->
                if (shapes.none { sameShape(reference.width, reference.height, it.width, it.height) }) {
                    shapes += CalibrationScreenshotFilter.Shape(reference.width, reference.height)
                }
            }
        return shapes
    }

    fun shapeLabel(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "Unknown shape"
        var a = width
        var b = height
        while (b != 0) { val remainder = a % b; a = b; b = remainder }
        return "${width / a}:${height / a}"
    }
}

/** The same centered fit used by the preview and its normalized crop outlines. */
data class CalibrationImageViewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    companion object {
        fun fit(containerWidth: Float, containerHeight: Float, sourceWidth: Int, sourceHeight: Int): CalibrationImageViewport {
            if (containerWidth <= 0f || containerHeight <= 0f || sourceWidth <= 0 || sourceHeight <= 0) {
                return CalibrationImageViewport(0f, 0f, 0f, 0f)
            }
            val scale = min(containerWidth / sourceWidth, containerHeight / sourceHeight)
            val width = sourceWidth * scale
            val height = sourceHeight * scale
            return CalibrationImageViewport((containerWidth - width) / 2f, (containerHeight - height) / 2f, width, height)
        }
    }
}
