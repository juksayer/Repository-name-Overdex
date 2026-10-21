package com.example.overdex.data.registration

import android.graphics.Bitmap
import com.example.overdex.battle.observation.BattleCropResolver
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.CaptureTemplate

/** Calibrated reads supplement semantic anchors in screenshots and deliberately aligned live scans. */
object RegistrationScreenshotReader {


    suspend fun read(source: Bitmap, template: CaptureTemplate): List<DetailTextLine> {
        val lines = readArea(source, 0f, 0f, 1f, 1f).toMutableList()
        template.regions.filter { it.type != com.example.overdex.model.ObservationType.IMAGE_MATCH }.forEach { region ->
            lines += readField(source, region)
        }
        return lines.distinctBy { listOf(textKey(it.text), (it.left * 1000).toInt(), (it.top * 1000).toInt()) }
    }

    /** Reads exactly this field. Never falls back to full-screen OCR. */
    suspend fun readField(source: Bitmap, region: com.example.overdex.model.CaptureRegion): List<DetailTextLine> {
        val crop = BattleCropResolver.resolve(region.id, AnchorRegion(region.x, region.y, region.width, region.height), source, 1) ?: return emptyList()
        return try {
            val bounds = crop.provenance.bounds
            readArea(crop.bitmap, bounds.left.toFloat() / source.width, bounds.top.toFloat() / source.height,
                crop.bitmap.width.toFloat() / source.width, crop.bitmap.height.toFloat() / source.height)
        } finally { if (crop.bitmap !== source) crop.bitmap.recycle() }
    }

    private suspend fun readArea(source: Bitmap, x: Float, y: Float, width: Float, height: Float): List<DetailTextLine> {
        val scale = maxOf(1f, 32f / source.height, 32f / source.width, if (height < 0.15f) 80f / source.height else 1008f / source.width)
        val input = if (scale > 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true) else source
        return try {
            SharedLatinTextRecognizer.read(input).textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { b -> DetailTextLine(line.text, x + width * b.left / input.width,
                    y + height * b.top / input.height, x + width * b.right / input.width, y + height * b.bottom / input.height) }
            }
        } finally { if (input !== source) input.recycle() }
    }
}
