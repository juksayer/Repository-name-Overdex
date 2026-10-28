package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.BattleDecisionTimerObserved
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Reads only the short decision dial between the team badges, never the opening countdown. */
class PersistedDecisionTimerWitness(private val artifacts: FileCropArtifactStore) : Observer {
    override val observerId = ObserverId("DECISION_TIMER_WITNESS", ObserverSource.SCREEN_CAPTURE)
    override val name = "Decision Timer"
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { worker ->
            worker.launch {
                val filter = DecisionTimerReadingFilter()
                match.articles.filter {
                    (it.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.decisionTimer.cropName
                }.conflate().collect { article ->
                    val crop = article.payload as CropCaptured
                    val at = article.monotonicTimeNanos ?: return@collect
                    val bitmap = artifacts.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                    try {
                        val dial = DecisionTimerAppearance.hasDial(bitmap)
                        // Admit one normal-priority read only after seeing the dial.
                        // Skipping every busy moment can miss the entire short timer;
                        // conflation keeps this worker bounded and species retain priority.
                        val text = if (dial) DecisionTimerTextReader.read(bitmap) else ""
                        val change = filter.accept(dial, text, at) ?: return@collect
                        match.custody.submitTestimony(SourceId(observerId.id),
                            BattleDecisionTimerObserved(change.seconds, text,
                                if (change.seconds == null) "DIAL_ABSENT_TWICE" else "NUMERIC_OCR_WITH_TIMER_DIAL"),
                            article.perceivedAt, null, listOf(article.id.value), at)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.util.Log.e("DecisionTimer", "Unable to read preserved timer crop", error)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() { scope?.cancel(); scope = null }
}

internal data class DecisionTimerChange(val seconds: Int?)

/** Require a dial and repeated legal readings; a missed OCR read is not disappearance. */
internal class DecisionTimerReadingFilter {
    private var candidate: Pair<Int, Long>? = null
    private var accepted: Int? = null
    private var acceptedAt = Long.MIN_VALUE
    private var missingAt: Long? = null

    fun accept(dial: Boolean, text: String, at: Long): DecisionTimerChange? {
        if (!dial) {
            candidate = null
            if (accepted == null) return null
            val missing = missingAt
            if (missing == null) { missingAt = at; return null }
            if (at - missing < 180_000_000L) return null
            accepted = null
            missingAt = null
            return DecisionTimerChange(null)
        }
        missingAt = null
        val number = parse(text) ?: return null
        val previous = candidate
        candidate = number to at
        if (previous == null || at - previous.second !in 100_000_000L..1_500_000_000L ||
            number !in (previous.first - 1)..previous.first) return null
        if (accepted == number && at - acceptedAt < 1_000_000_000L) return null
        accepted = number
        acceptedAt = at
        return DecisionTimerChange(number)
    }

    companion object {
        fun parse(text: String): Int? = text.trim().replace('O', '0').replace('o', '0')
            .replace('I', '1').replace('l', '1').replace('|', '1')
            .takeIf { it.matches(Regex("[0-9]{1,2}")) }?.toIntOrNull()?.takeIf { it in 0..15 }
    }
}

internal object DecisionTimerAppearance {
    fun hasDial(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return hasDial(bitmap.width, bitmap.height) { x, y -> pixels[y * bitmap.width + x] }
    }

    fun hasDial(width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean {
        if (minOf(width, height) < 16) return false
        // Device insets change the dial's vertical position. Search only this
        // central territory and require its dark circular rim as well as a white arc.
        for (scale in listOf(.75f, .875f, 1f)) {
            val size = (minOf(width, height) * scale).toInt()
            for (cy in size / 2..height - size / 2 step maxOf(2, size / 24)) {
                if (hasDialAt(width / 2, cy, size, width, height, pixel)) return true
            }
        }
        return false
    }

    private fun hasDialAt(cx: Int, cy: Int, size: Int, width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean {
        var ring = 0; var white = 0; var inner = 0; var neutral = 0
        var rim = 0; var dark = 0
        for (y in maxOf(0, cy - size / 2) until minOf(height, cy + size / 2) step 3)
            for (x in maxOf(0, cx - size / 2) until minOf(width, cx + size / 2) step 3) {
            val dx = (x - cx).toFloat() / size; val dy = (y - cy).toFloat() / size
            val radius2 = dx * dx + dy * dy
            val color = pixel(x, y)
            val r = (color shr 16) and 255; val g = (color shr 8) and 255; val b = color and 255
            val spread = maxOf(r, g, b) - minOf(r, g, b)
            val level = (r + g + b) / 3
            if (radius2 in .30f * .30f.. .45f * .45f) {
                ring++
                if (level > 185 && spread < 65) white++
            } else if (radius2 < .28f * .28f) {
                inner++
                // The dial is navy in night battles, not necessarily gray.
                // Keep the white-arc and dark-rim requirements; accept its dark interior.
                if (level < 180 && spread < 100) neutral++
            }
            if (radius2 in .44f * .44f.. .48f * .48f) {
                rim++
                if (level < 185) dark++
            }
        }
        return ring > 0 && inner > 0 && rim > 0 && white.toFloat() / ring >= .04f &&
            neutral.toFloat() / inner >= .30f && dark.toFloat() / rim > .65f
    }
}
