package com.example.overdex.battle.observation

import android.os.Debug

/** Fixed stages keep the per-call and archived diagnostic storage bounded. */
enum class HpBarLocatorStage {
    LOCAL_SEARCH,
    RECOVERY_SEARCH,
    PIXEL_EXTRACTION,
    GEOMETRY_DETECTION,
    ASSIGNMENT,
}

data class HpBarStageTiming(
    val elapsedNanos: Long,
    val cpuNanos: Long?,
    val calls: Int,
)

/**
 * One call, not cached geometry's original work. Search includes extraction and
 * detection, so the stage elapsed/CPU values overlap and must not be added to
 * recover total duration. A null CPU value means the thread clock was unavailable.
 */
data class HpBarLocatorTimings(
    val workPerformed: Boolean,
    val lockWaitNanos: Long,
    val threadCpuNanos: Long?,
    val stages: Map<HpBarLocatorStage, HpBarStageTiming>,
)

/** Android's current-thread CPU clock is optional evidence, never a requirement for recognition. */
internal fun hpLocatorThreadCpuTimeNanos(): Long? = try {
    Debug.threadCpuTimeNanos().takeIf { it > 0L }
} catch (_: RuntimeException) {
    null
} catch (_: LinkageError) {
    null
}

/** One bounded accumulator owned by the caller thread while the tracker lock is held. */
internal class HpBarLocatorTimingProbe(
    private val elapsedTimeNanos: () -> Long,
    private val threadCpuTimeNanos: () -> Long?,
    private val cpuStartedAt: Long?,
    private val lockWaitNanos: Long,
) {
    private data class Stage(var elapsed: Long = 0L, var cpu: Long? = 0L, var calls: Int = 0)
    private val stages = arrayOfNulls<Stage>(HpBarLocatorStage.entries.size)
    var workPerformed: Boolean = true

    fun <T> measure(stage: HpBarLocatorStage, work: () -> T): T {
        val cpuStarted = threadCpuTimeNanos()
        val elapsedStarted = elapsedTimeNanos()
        try {
            return work()
        } finally {
            val elapsed = elapsedTimeNanos() - elapsedStarted
            val cpu = cpuDifference(cpuStarted, threadCpuTimeNanos())
            val total = stages[stage.ordinal] ?: Stage().also { stages[stage.ordinal] = it }
            total.elapsed += elapsed
            total.cpu = total.cpu?.let { accumulated -> cpu?.let { accumulated + it } }
            total.calls++
        }
    }

    fun finish(): HpBarLocatorTimings = HpBarLocatorTimings(
        workPerformed = workPerformed,
        lockWaitNanos = lockWaitNanos,
        threadCpuNanos = cpuDifference(cpuStartedAt, threadCpuTimeNanos()),
        stages = HpBarLocatorStage.entries.mapNotNull { stage ->
            stages[stage.ordinal]?.let { stage to HpBarStageTiming(it.elapsed, it.cpu, it.calls) }
        }.toMap(),
    )

    private fun cpuDifference(start: Long?, end: Long?): Long? =
        if (start != null && end != null && end >= start) end - start else null
}
