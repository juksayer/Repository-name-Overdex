package com.example.overdex.battle.replay

/** Capture time and original article for an endpoint. Original observations keep their clocks independently of playback. */
data class ReplayTimeBound(val atNanos: Long, val articleId: String)

sealed interface ReplayFaintTiming {
    data class Cry(val onset: ReplayTimeBound) : ReplayFaintTiming
    data class ZeroHp(val frame: ReplayTimeBound) : ReplayFaintTiming
    data class TrainerMark(val atNanos: Long) : ReplayFaintTiming
    data class Window(val after: ReplayTimeBound?, val by: ReplayTimeBound) : ReplayFaintTiming {
        init { require(after == null || after.atNanos < by.atNanos) }
    }
    data object Unknown : ReplayFaintTiming

    companion object {
        internal fun window(after: ReplayTimeBound?, by: ReplayTimeBound): ReplayFaintTiming =
            if (after == null || after.atNanos < by.atNanos) Window(after, by) else Unknown
    }
}
