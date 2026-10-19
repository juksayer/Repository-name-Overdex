package com.example.overdex.battle.audio

/**
 * The visual phenomenon that requested this audio window. Countdown is currently wired; the
 * other values reserve independent future cue routes, rather than treating every departure as a
 * faint or reusing a countdown trigger for unrelated combat sound.
 */
enum class BattleCryCueKind {
    VS_SCREEN,
    SPECIES_ENTRY,
    COUNTDOWN_3,
    COUNTDOWN_2,
    COUNTDOWN_1,
    COUNTDOWN_GO,
    /** A countdown-shaped visual was present, before OCR identified its glyph. */
    COUNTDOWN_VISUAL_PRESENCE,
    POKEMON_FAINTED,
    OPPONENT_DEPARTED_NON_FAINT,
    CHARGE_MOVE_QTE_ENTERED,
    CHARGE_MOVE_QTE_COMPLETED,
    /** A visual HP-bar cadence measurement requests a short independent audio sample. */
    FAST_MOVE_IMPACT,
    /** Detected directly in the live PCM stream, with no inferred Pokemon side. */
    STAT_CHANGE_SOUND,
    /** A bounded charged-move window, whether or not a stat cue is subsequently recognized. */
    CHARGED_MOVE_EFFECT
}

data class AudioCaptureCue(
    val articleId: String,
    val kind: BattleCryCueKind,
    val observedAtMonotonicNanos: Long? = null,
)

sealed interface CueCenteredPcmResult { val cue: AudioCaptureCue }

data class CueCenteredPcmCapture(
    override val cue: AudioCaptureCue,
    val pcm16le: ByteArray,
    val startedAtMonotonicNanos: Long,
    val startedAtWallTimeMillis: Long,
) : CueCenteredPcmResult

data class CueCenteredPcmUnavailable(
    override val cue: AudioCaptureCue,
    val reason: String,
) : CueCenteredPcmResult

/**
 * Keeps bounded, volatile PCM history so a delayed visual reading can request the
 * sound at its source time. Only the requested pre/post window is ever persisted.
 * Callers serialize access and supply monotonic/wall timestamps for each read's end.
 * Missing history or a discontinuity is unavailable evidence, never newer substitute audio.
 */
class CueCenteredPcmCollector(
    private val preRollBytes: Int,
    private val postRollBytes: Int,
    private val bytesPerSecond: Int = 96_000, // 48 kHz mono PCM-16
    historyBytes: Int = bytesPerSecond * 8,
    channelCount: Int = 1,
) {
    private val bytesPerFrame = channelCount * 2
    private val history = ByteArray(historyBytes)
    private var next = 0
    private var size = 0
    private var endNanos: Long? = null
    private var endWallMillis = 0L
    private val pending = ArrayDeque<AudioCaptureCue>()

    init {
        require(channelCount in 1..2)
        require(bytesPerSecond > 0 && bytesPerSecond % bytesPerFrame == 0)
        require(preRollBytes >= 0 && postRollBytes > 0)
        require(preRollBytes % bytesPerFrame == 0 && postRollBytes % bytesPerFrame == 0)
        require(historyBytes >= preRollBytes + postRollBytes && historyBytes % bytesPerFrame == 0)
    }

    fun cue(cue: AudioCaptureCue): CueCenteredPcmUnavailable? {
        if (cue.observedAtMonotonicNanos == null) return CueCenteredPcmUnavailable(cue, "MISSING_SOURCE_TIME")
        if (pending.size >= MAX_PENDING) return CueCenteredPcmUnavailable(cue, "REQUEST_QUEUE_FULL")
        pending += cue
        return null
    }

    fun ingest(chunk: ByteArray, endedAtMonotonicNanos: Long, endedAtWallTimeMillis: Long): List<CueCenteredPcmResult> {
        require(chunk.size % bytesPerFrame == 0)
        if (chunk.isEmpty()) return emptyList()
        val completed = mutableListOf<CueCenteredPcmResult>()
        val previousEnd = endNanos
        if (previousEnd != null && (endedAtMonotonicNanos <= previousEnd ||
                kotlin.math.abs(endedAtMonotonicNanos - previousEnd - duration(chunk.size)) > MAX_READ_GAP_NANOS)) {
            completed += pending.map { CueCenteredPcmUnavailable(it, "INPUT_GAP") }
            pending.clear()
            next = 0
            size = 0
        }

        val count = minOf(chunk.size, history.size)
        val sourceStart = chunk.size - count
        val firstCopy = minOf(count, history.size - next)
        chunk.copyInto(history, next, sourceStart, sourceStart + firstCopy)
        if (firstCopy < count) chunk.copyInto(history, 0, sourceStart + firstCopy, chunk.size)
        next = (next + count) % history.size
        size = minOf(size + count, history.size)
        endNanos = endedAtMonotonicNanos
        endWallMillis = endedAtWallTimeMillis

        val firstAt = endedAtMonotonicNanos - duration(size)
        val oldest = (next - size + history.size) % history.size
        val length = preRollBytes + postRollBytes
        val requests = pending.iterator()
        while (requests.hasNext()) {
            val request = requests.next()
            val startAt = requireNotNull(request.observedAtMonotonicNanos) - duration(preRollBytes)
            if (startAt < firstAt) {
                completed += CueCenteredPcmUnavailable(request, "REQUEST_OUTSIDE_RETAINED_AUDIO")
                requests.remove()
                continue
            }
            if (startAt + duration(length) > endedAtMonotonicNanos) continue
            // Keep the entire L/R frame together, including sub-frame visual cue times.
            val from = ((startAt - firstAt) * bytesPerSecond / 1_000_000_000L /
                bytesPerFrame * bytesPerFrame).toInt()
            if (from + length > size) continue
            val pcm = ByteArray(length)
            val source = (oldest + from) % history.size
            val head = minOf(length, history.size - source)
            history.copyInto(pcm, 0, source, source + head)
            if (head < length) history.copyInto(pcm, head, 0, length - head)
            val actualStart = firstAt + duration(from)
            completed += CueCenteredPcmCapture(request, pcm, actualStart,
                endWallMillis - (endedAtMonotonicNanos - actualStart) / 1_000_000L)
            requests.remove()
        }
        return completed
    }

    fun reset() {
        next = 0; size = 0; endNanos = null
        pending.clear()
    }

    private fun duration(bytes: Int) = bytes.toLong() * 1_000_000_000L / bytesPerSecond

    private companion object {
        const val MAX_PENDING = 32
        const val MAX_READ_GAP_NANOS = 250_000_000L
    }
}
