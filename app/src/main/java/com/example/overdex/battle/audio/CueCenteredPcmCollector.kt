package com.example.overdex.battle.audio

/** A visual Timeline article asks for a short, pre/post-roll microphone clip. */
/**
 * The visual phenomenon that requested this audio window. Countdown is currently wired; the
 * other values reserve independent future cue routes, rather than treating every departure as a
 * faint or reusing a countdown trigger for unrelated combat sound.
 */
enum class BattleCryCueKind {
    VS_SCREEN,
    COUNTDOWN_3,
    COUNTDOWN_2,
    COUNTDOWN_1,
    COUNTDOWN_GO,
    /** A countdown-shaped visual was present, before OCR identified its glyph. */
    COUNTDOWN_VISUAL_PRESENCE,
    POKEMON_FAINTED,
    OPPONENT_DEPARTED_NON_FAINT,
    CHARGE_MOVE_QTE_ENTERED,
    CHARGE_MOVE_QTE_COMPLETED
}

data class AudioCaptureCue(val articleId: String, val kind: BattleCryCueKind)
data class CueCenteredPcmCapture(val cue: AudioCaptureCue, val pcm16le: ByteArray)

/**
 * Holds only a small volatile pre-roll. It emits no audio until a visual cue arrives, then
 * produces one exact pre/post window that the caller must persist before testimony.
 */
class CueCenteredPcmCollector(
    private val preRollBytes: Int,
    private val postRollBytes: Int
) {
    private val preRoll = ArrayDeque<ByteArray>()
    private var preRollSize = 0
    private val pending = mutableListOf<PendingCapture>()

    fun cue(cue: AudioCaptureCue) {
        pending += PendingCapture(cue, concatenate(preRoll), java.io.ByteArrayOutputStream(postRollBytes))
    }

    fun ingest(chunk: ByteArray): List<CueCenteredPcmCapture> {
        val completed = mutableListOf<CueCenteredPcmCapture>()
        pending.toList().forEach { capture ->
            val remaining = postRollBytes - capture.postRoll.size()
            if (remaining > 0) capture.postRoll.write(chunk, 0, minOf(remaining, chunk.size))
            if (capture.postRoll.size() >= postRollBytes) {
                completed += CueCenteredPcmCapture(capture.cue, capture.preRoll + capture.postRoll.toByteArray())
                pending.remove(capture)
            }
        }
        preRoll += chunk.copyOf()
        preRollSize += chunk.size
        while (preRollSize > preRollBytes && preRoll.isNotEmpty()) {
            preRollSize -= preRoll.removeFirst().size
        }
        return completed
    }

    private fun concatenate(parts: Collection<ByteArray>): ByteArray {
        val size = parts.sumOf { it.size }
        val result = ByteArray(size)
        var offset = 0
        parts.forEach { bytes ->
            bytes.copyInto(result, offset)
            offset += bytes.size
        }
        return if (result.size <= preRollBytes) result else result.copyOfRange(result.size - preRollBytes, result.size)
    }

    private data class PendingCapture(
        val cue: AudioCaptureCue,
        val preRoll: ByteArray,
        val postRoll: java.io.ByteArrayOutputStream
    )
}
