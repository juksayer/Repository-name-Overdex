package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.OcrBox
import kotlin.math.ceil
import kotlin.math.floor

/** Use the existing crop-ranking pass before preservation and OCR so the HUD can yield immediately. */
internal class LiveAnnouncementPreview(
    private val onVisible: (Long) -> Unit,
    private val enabled: () -> Boolean = { true },
) {
    private val readings = LinkedHashMap<Pair<Long, BattleCropProvenance>, TransientTextProbe.Reading>()

    fun inspect(crop: ResolvedBattleCrop, at: Long, overlay: OcrBox?) {
        if (!enabled()) return
        val reading = readingFor(at, crop.provenance) ?: crop.withOcrPixels { readTransientTextPixels(it, overlay) }.also { result ->
            synchronized(readings) {
                readings[at to crop.provenance] = result
                if (readings.size > 32) readings.remove(readings.keys.first())
            }
        }
        val line = reading.textBounds ?: return
        // A long banner may have only its left-hand words unobscured by the HUD.
        if (reading.hasText && line.right > crop.bitmap.width / 4 && line.left < crop.bitmap.width * 3 / 4) onVisible(at)
    }

    // A late or restored article can always use the original verified PNG.
    fun readingFor(at: Long?, provenance: BattleCropProvenance): TransientTextProbe.Reading? =
        at?.let { synchronized(readings) { readings[it to provenance] } }
}

internal fun readTransientTextPixels(bitmap: Bitmap, overlay: OcrBox?): TransientTextProbe.Reading {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val excluded = overlay?.let {
        TransientTextProbe.PixelBounds(floor(it.left).toInt(), floor(it.top).toInt(),
            ceil(it.right).toInt(), ceil(it.bottom).toInt())
    }
    return TransientTextProbe.analyze(pixels, bitmap.width, bitmap.height, listOfNotNull(excluded))
}
