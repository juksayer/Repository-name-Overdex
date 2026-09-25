package com.example.overdex.model.observation

/**
 * A microphone PCM-16 snippet at the moment it entered Overdex's evidence pipeline.
 * The byte array is transient; AudioCaptureWitness must durably preserve it before testimony.
 */
data class CapturedAudioFrame(
    val pcm16le: ByteArray,
    val sampleRateHz: Int,
    val channelCount: Int,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long,
    /** The accepted visual Timeline article that requested this precise audio window. */
    val cueArticleId: String,
    val cueKind: com.example.overdex.battle.audio.BattleCryCueKind
) {
    val durationNanos: Long
        get() = pcm16le.size.toLong() * 1_000_000_000L / (sampleRateHz * channelCount * 2L)
}
