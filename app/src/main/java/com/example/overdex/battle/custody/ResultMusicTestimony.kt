package com.example.overdex.battle.custody

/** The measured tune, not a second MatchEnded. Cites the preserved PCM Article. */
data class MatchResultMusicMeasured(
    val theme: String, val referenceSha256: String,
    val similarity: Float, val runnerUpSimilarity: Float,
    val referenceOffsetNanos: Long, val durationNanos: Long,
    val confirmingWindows: Int = 2,
    val followingWindowSimilarity: Float? = null,
    val followingWindowRunnerUpSimilarity: Float? = null,
) : TestimonyPayload

/** An explicit comparison of independent music and result evidence; conflicts stay visible. */
data class MatchResultMusicCompared(
    val musicTheme: String, val recordedResult: String, val agrees: Boolean,
    val musicArticleId: String, val resultArticleId: String,
) : TestimonyPayload
