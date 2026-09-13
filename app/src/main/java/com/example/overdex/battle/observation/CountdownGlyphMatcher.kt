package com.example.overdex.battle.observation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.example.overdex.BuildConfig
import com.example.overdex.R
import java.util.*

/**
 * Debug-only matcher for identifying countdown glyphs (3, 2, 1, GO) using template matching.
 */
object CountdownGlyphMatcher {
    private const val TAG = "COUNTDOWN_GLYPH"
    private const val COMPONENTS_TAG = "COUNTDOWN_GLYPH_COMPONENTS"
    private const val MATCH_THRESHOLD = 0.65f
    private const val NORMALIZED_SIZE = 128
    private const val BRIGHT_THRESHOLD = 220
    private const val NOISE_THRESHOLD_PIXELS = 100

    data class MatchResult(val candidate: String?, val similarity: Float)

    private var applicationContext: Context? = null
    private var loadedTemplates: Map<String, BooleanArray>? = null

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG) return
        applicationContext = context.applicationContext
    }

    private fun ensureTemplatesLoaded() {
        if (loadedTemplates != null) return
        val context = applicationContext ?: return
        
        val rawTemplates = mapOf(
            "3" to R.drawable.countdown_glyph_3,
            "2" to R.drawable.countdown_glyph_2,
            "1" to R.drawable.countdown_glyph_1,
            "GO" to R.drawable.countdown_glyph_go
        )
        
        loadedTemplates = rawTemplates.mapValues { (_, resId) ->
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
            val silhouette = normalizeTemplateBitmap(bitmap)
            bitmap.recycle()
            silhouette
        }
    }

    fun match(bitmap: Bitmap): MatchResult {
        if (!BuildConfig.DEBUG) return MatchResult(null, 0f)
        ensureTemplatesLoaded()
        val templates = loadedTemplates ?: return MatchResult(null, 0f)
        
        return try {
            val silhouette = extractAndNormalizeSilhouette(bitmap) ?: return MatchResult(null, 0f)
            
            var bestCandidate: String? = null
            var maxSimilarity = 0f
            
            templates.forEach { (name, template) ->
                val similarity = compareSilhouettes(silhouette, template)
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity
                    bestCandidate = name
                }
            }
            
            if (maxSimilarity >= MATCH_THRESHOLD) {
                MatchResult(bestCandidate, maxSimilarity)
            } else {
                MatchResult(null, maxSimilarity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error matching glyph", e)
            MatchResult(null, 0f)
        }
    }

    private class Component(val pixels: MutableList<Int>, val bounds: Rect)

    private fun extractAndNormalizeSilhouette(source: Bitmap): BooleanArray? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val binary = BooleanArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            binary[i] = Color.red(p) >= BRIGHT_THRESHOLD && 
                        Color.green(p) >= BRIGHT_THRESHOLD && 
                        Color.blue(p) >= BRIGHT_THRESHOLD
        }

        val visited = BitSet(width * height)
        val components = mutableListOf<Component>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (binary[idx] && !visited.get(idx)) {
                    val compPixels = mutableListOf<Int>()
                    val q: Queue<Int> = LinkedList()
                    q.add(idx)
                    visited.set(idx)
                    
                    var minX = x; var maxX = x; var minY = y; var maxY = y
                    
                    while (q.isNotEmpty()) {
                        val curr = q.remove()
                        compPixels.add(curr)
                        val cx = curr % width
                        val cy = curr / width
                        
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy
                        
                        // 4-connectivity
                        val neighbors = intArrayOf(curr - 1, curr + 1, curr - width, curr + width)
                        for (nb in neighbors) {
                            if (nb in binary.indices) {
                                val nx = nb % width
                                val ny = nb / width
                                if (Math.abs(nx - cx) <= 1 && Math.abs(ny - cy) <= 1 && 
                                    binary[nb] && !visited.get(nb)) {
                                    visited.set(nb)
                                    q.add(nb)
                                }
                            }
                        }
                    }
                    
                    if (compPixels.size >= NOISE_THRESHOLD_PIXELS) {
                        components.add(Component(compPixels, Rect(minX, minY, maxX + 1, maxY + 1)))
                    }
                }
            }
        }

        if (components.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(COMPONENTS_TAG, "source=${width}x${height} | componentCount=0 | largest=none | second=none | selection=none | selected=none")
            }
            return null
        }

        // Sort by size descending
        components.sortByDescending { it.pixels.size }
        
        val targetPixels = mutableListOf<Int>()
        val finalBounds = Rect()
        val selection: String

        if (components.size >= 2) {
            val c1 = components[0]
            val c2 = components[1]
            
            // Similar heights
            val h1 = c1.bounds.height().toFloat()
            val h2 = c2.bounds.height().toFloat()
            val heightRatio = Math.min(h1, h2) / Math.max(h1, h2)
            
            // Substantial vertical overlap
            val verticalOverlap = Math.max(0, Math.min(c1.bounds.bottom, c2.bounds.bottom) - Math.max(c1.bounds.top, c2.bounds.top))
            val overlapRatio = verticalOverlap.toFloat() / Math.min(h1, h2)
            
            // Broadly similar pixel areas
            val a1 = c1.pixels.size.toFloat()
            val a2 = c2.pixels.size.toFloat()
            val areaRatio = Math.min(a1, a2) / Math.max(a1, a2)
            
            // Small horizontal gap (adjacency)
            val gap = Math.max(0, Math.max(c1.bounds.left, c2.bounds.left) - Math.min(c1.bounds.right, c2.bounds.right))
            val gapRatio = gap.toFloat() / Math.min(c1.bounds.width(), c2.bounds.width())
            
            val isGoCandidate = heightRatio > 0.7f && 
                              overlapRatio > 0.7f && 
                              areaRatio > 0.5f && 
                              gapRatio < 0.5f
            
            if (isGoCandidate) {
                selection = "go-pair"
                targetPixels.addAll(c1.pixels)
                targetPixels.addAll(c2.pixels)
                finalBounds.set(
                    Math.min(c1.bounds.left, c2.bounds.left),
                    Math.min(c1.bounds.top, c2.bounds.top),
                    Math.max(c1.bounds.right, c2.bounds.right),
                    Math.max(c1.bounds.bottom, c2.bounds.bottom)
                )
            } else {
                selection = "single"
                targetPixels.addAll(c1.pixels)
                finalBounds.set(c1.bounds)
            }
        } else {
            selection = "single"
            targetPixels.addAll(components[0].pixels)
            finalBounds.set(components[0].bounds)
        }

        if (BuildConfig.DEBUG) {
            val largestStr = "${components[0].pixels.size}@${components[0].bounds.left},${components[0].bounds.top},${components[0].bounds.right},${components[0].bounds.bottom}"
            val secondStr = if (components.size >= 2) {
                "${components[1].pixels.size}@${components[1].bounds.left},${components[1].bounds.top},${components[1].bounds.right},${components[1].bounds.bottom}"
            } else {
                "none"
            }
            val selectedStr = "${finalBounds.left},${finalBounds.top},${finalBounds.right},${finalBounds.bottom}"
            Log.d(COMPONENTS_TAG, "source=${width}x${height} | componentCount=${components.size} | largest=$largestStr | second=$secondStr | selection=$selection | selected=$selectedStr")
        }

        return createNormalizedSilhouette(targetPixels, finalBounds, width)
    }

    private fun createNormalizedSilhouette(glyphPixels: List<Int>, bounds: Rect, sourceWidth: Int): BooleanArray {
        val tempBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bounds.width() * bounds.height())
        
        for (idx in glyphPixels) {
            val x = idx % sourceWidth - bounds.left
            val y = idx / sourceWidth - bounds.top
            if (x in 0 until bounds.width() && y in 0 until bounds.height()) {
                pixels[y * bounds.width() + x] = Color.BLACK
            }
        }
        tempBitmap.setPixels(pixels, 0, bounds.width(), 0, 0, bounds.width(), bounds.height())
        
        val scaled = Bitmap.createScaledBitmap(tempBitmap, NORMALIZED_SIZE, NORMALIZED_SIZE, true)
        tempBitmap.recycle()
        
        val result = BooleanArray(NORMALIZED_SIZE * NORMALIZED_SIZE)
        val scaledPixels = IntArray(NORMALIZED_SIZE * NORMALIZED_SIZE)
        scaled.getPixels(scaledPixels, 0, NORMALIZED_SIZE, 0, 0, NORMALIZED_SIZE, NORMALIZED_SIZE)
        scaled.recycle()
        
        for (i in scaledPixels.indices) {
            result[i] = Color.alpha(scaledPixels[i]) > 127
        }
        return result
    }

    private fun normalizeTemplateBitmap(bitmap: Bitmap): BooleanArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        var found = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                if (Color.red(p) < 128) { // Black glyph
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    found = true
                }
            }
        }

        if (!found) {
            // Fallback to full bitmap if no black pixels found
            minX = 0; minY = 0; maxX = width - 1; maxY = height - 1
        }

        val bWidth = maxX - minX + 1
        val bHeight = maxY - minY + 1
        val cropped = Bitmap.createBitmap(bitmap, minX, minY, bWidth, bHeight)
        
        val scaled = Bitmap.createScaledBitmap(cropped, NORMALIZED_SIZE, NORMALIZED_SIZE, true)
        if (cropped != bitmap) cropped.recycle()
        
        val scaledPixels = IntArray(NORMALIZED_SIZE * NORMALIZED_SIZE)
        scaled.getPixels(scaledPixels, 0, NORMALIZED_SIZE, 0, 0, NORMALIZED_SIZE, NORMALIZED_SIZE)
        scaled.recycle()
        
        val result = BooleanArray(NORMALIZED_SIZE * NORMALIZED_SIZE)
        for (i in scaledPixels.indices) {
            result[i] = Color.red(scaledPixels[i]) < 128
        }
        return result
    }

    private fun compareSilhouettes(a: BooleanArray, b: BooleanArray): Float {
        var intersection = 0
        var union = 0
        for (i in a.indices) {
            if (a[i] && b[i]) intersection++
            if (a[i] || b[i]) union++
        }
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
}
