package com.example.overdex.battle.observation

/** Bounded, out-of-band measurements. Never submits testimony or changes scheduling. */
class MatchProcessingDiagnostics(
    private val elapsedClock: () -> Long = System::nanoTime,
    private val cpuClock: () -> Long = android.os.Debug::threadCpuTimeNanos,
) {
    data class Timing(val calls: Long, val elapsedNanos: Long, val cpuNanos: Long,
        val maximumNanos: Long, val histogram: List<Long>, val invalidCpuSamples: Long,
        val maximumCpuNanos: Long, val cpuHistogram: List<Long>)
    data class Rebuild(val calls: Long, val totalInputs: Long, val maximumInputs: Int)
    /** Original audio clocks and worker milestones for one accepted localized cry. */
    data class CryPositiveTiming(
        val speciesId: Int,
        val catchUp: Boolean,
        val onsetNanos: Long,
        val windowEndNanos: Long,
        val bufferedNanos: Long,
        val referencesStartedNanos: Long,
        val referencesReadyNanos: Long,
        val analysisStartNanos: Long,
        val matchDoneNanos: Long,
        val preservedNanos: Long,
        val captureSubmittedNanos: Long,
        val captureReceiptNanos: Long,
        val crySubmittedNanos: Long,
        val captureSequence: Long,
        val crySequence: Long,
    )
    data class Snapshot(val stages: Map<String, Timing>, val rebuilds: Map<String, Rebuild>,
        val completed: Long, val lastCompletedSequence: Long, val activeSequence: Long?,
        val activeStage: String?, val activeAgeNanos: Long, val cryPositiveCount: Long,
        val recentCryPositives: List<CryPositiveTiming>)
    private class Counter {
        var calls = 0L; var elapsed = 0L; var cpu = 0L; var maximum = 0L; var invalidCpu = 0L
        var maximumCpu = 0L
        val buckets = LongArray(BOUNDS.size)
        val cpuBuckets = LongArray(BOUNDS.size)
    }
    private val counters = linkedMapOf<String, Counter>()
    private val rebuildCounters = linkedMapOf<String, Rebuild>()
    private val recentCryPositives = ArrayDeque<CryPositiveTiming>()
    private var cryPositiveCount = 0L
    private var completed = 0L
    private var lastCompleted = Long.MIN_VALUE
    private var sequence: Long? = null
    private var activeStage: String? = null
    private var activeFrom = 0L

    inner class Span internal constructor(private val kind: String) {
        private var stage = "raw_preparation"
        private var from = elapsedClock()
        private var cpuFrom = cpuClock()
        private var thread = Thread.currentThread().id
        fun next(next: String) {
            val now = elapsedClock(); val cpuNow = cpuClock(); val tid = Thread.currentThread().id
            record(stage, kind, (now - from).coerceAtLeast(0),
                if (thread == tid && cpuFrom >= 0 && cpuNow >= cpuFrom) cpuNow - cpuFrom else null)
            stage = next; from = now; cpuFrom = cpuNow; thread = tid
            synchronized(this@MatchProcessingDiagnostics) { activeStage = next }
        }
        fun complete(sequenceNumber: Long) {
            next("complete")
            synchronized(this@MatchProcessingDiagnostics) {
                completed++; lastCompleted = sequenceNumber; sequence = null; activeStage = null
            }
        }
    }
    @Synchronized fun begin(sequenceNumber: Long, kind: String): Span {
        sequence = sequenceNumber; activeFrom = elapsedClock(); activeStage = "raw_preparation"
        return Span(kind)
    }
    @Synchronized private fun record(stage: String, kind: String, elapsed: Long, cpu: Long?) {
        val requested = "$stage/$kind"
        val key = if (requested in counters || counters.size < MAX_KEYS) requested else "overflow"
        val c = counters.getOrPut(key) { Counter() }
        c.calls++; c.elapsed += elapsed; c.maximum = maxOf(c.maximum, elapsed)
        if (cpu == null) c.invalidCpu++ else {
            c.cpu += cpu; c.maximumCpu = maxOf(c.maximumCpu, cpu)
            c.cpuBuckets[BOUNDS.indexOfFirst { cpu <= it }]++
        }
        c.buckets[BOUNDS.indexOfFirst { elapsed <= it }]++
    }
    @Synchronized fun rebuild(name: String, inputs: Int) {
        val key = if (name in rebuildCounters || rebuildCounters.size < MAX_REBUILD_KEYS) name else "overflow"
        val old = rebuildCounters[key] ?: Rebuild(0, 0, 0)
        rebuildCounters[key] = Rebuild(old.calls + 1, old.totalInputs + inputs, maxOf(old.maximumInputs, inputs))
    }
    @Synchronized fun recordCryPositive(timing: CryPositiveTiming) {
        cryPositiveCount++
        if (recentCryPositives.size == MAX_CRY_POSITIVES) recentCryPositives.removeFirst()
        recentCryPositives.addLast(timing)
    }
    @Synchronized fun snapshot(): Snapshot = Snapshot(counters.mapValues { (_, c) ->
        Timing(c.calls, c.elapsed, c.cpu, c.maximum, c.buckets.toList(), c.invalidCpu, c.maximumCpu, c.cpuBuckets.toList())
    }, rebuildCounters.toMap(), completed, lastCompleted, sequence, activeStage,
        if (sequence == null) 0 else (elapsedClock() - activeFrom).coerceAtLeast(0),
        cryPositiveCount, recentCryPositives.toList())

    companion object {
        const val MAX_KEYS = 2048
        const val MAX_REBUILD_KEYS = 64
        const val MAX_CRY_POSITIVES = 64
        val BOUNDS = listOf(100_000L, 1_000_000L, 4_000_000L, 16_000_000L, 50_000_000L,
            250_000_000L, 1_000_000_000L, 5_000_000_000L, Long.MAX_VALUE)
    }
}
