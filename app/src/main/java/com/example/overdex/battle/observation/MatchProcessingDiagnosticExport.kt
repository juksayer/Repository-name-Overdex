package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.RealityArticleArchiveMapper
import com.example.overdex.battle.custody.HpObservationPipelineMeasured
import com.example.overdex.battle.custody.TestimonyRecord
import kotlinx.serialization.json.*
import java.io.File

/** Called by the archive worker, never by the testimony queue or a Witness. */
internal object MatchProcessingDiagnosticExport {
    fun write(directory: File, match: Match) {
        val snapshot = match.processingDiagnostics?.snapshot() ?: return
        val hp = match.realityTimeline.getArticles().lastOrNull { it.payload is HpObservationPipelineMeasured }
        val json = Json { classDiscriminator = "kind" }
        val report = buildJsonObject {
            put("matchId", match.matchId)
            match.faintHistoryCache?.snapshot()?.let { c -> put("faintHistoryCache", buildJsonObject {
                put("hits",c.hits); put("misses",c.misses); put("oversized",c.oversized)
                put("evictions",c.evictions); put("entries",c.entries); put("retainedRows",c.retainedRows)
            }) }
            match.articleListenerDiagnostics?.let { listeners ->
                put("articleListeners", buildJsonObject {
                    put("activeSubscriptions", listeners.activeSubscriptions)
                    put("peakSubscriptions", listeners.peakSubscriptions)
                    put("sampleEvery", 64)
                    put("timingScope", "Sampled downstream callback elapsed time, including suspension; not publisher blocking time or CPU time")
                    put("listeners", buildJsonObject {
                        listeners.snapshot().forEach { (name, value) -> put(name, buildJsonObject {
                            put("subscriptions", value.subscriptions); put("active", value.active)
                            put("deliveries", value.deliveries); put("samples", value.samples)
                            put("callbackElapsedNanos", value.callbackElapsedNanos)
                            put("maximumCallbackNanos", value.maximumCallbackNanos)
                        }) }
                    })
                })
            }
            put("acceptedTestimony", match.custody.getRecords().count { it is TestimonyRecord })
            put("completedTestimony", snapshot.completed)
            put("lastCompletedSequence", snapshot.lastCompletedSequence)
            put("activeSequence", snapshot.activeSequence)
            put("activeStage", snapshot.activeStage)
            put("activeAgeNanos", snapshot.activeAgeNanos)
            put("histogramUpperBoundsNanos", JsonArray(MatchProcessingDiagnostics.BOUNDS.map(::JsonPrimitive)))
            put("stages", buildJsonObject {
                snapshot.stages.forEach { (key, v) -> put(key, buildJsonObject {
                    put("calls", v.calls); put("elapsedNanos", v.elapsedNanos); put("cpuNanos", v.cpuNanos)
                    put("maximumNanos", v.maximumNanos); put("maximumCpuNanos", v.maximumCpuNanos)
                    put("invalidCpuSamples", v.invalidCpuSamples)
                    put("histogram", JsonArray(v.histogram.map(::JsonPrimitive)))
                    put("cpuHistogram", JsonArray(v.cpuHistogram.map(::JsonPrimitive)))
                }) }
            })
            put("rebuilds", buildJsonObject {
                snapshot.rebuilds.forEach { (key, v) -> put(key, buildJsonObject {
                    put("calls", v.calls); put("totalInputs", v.totalInputs); put("maximumInputs", v.maximumInputs)
                }) }
            })
            // Reuse the existing HP admission/age diagnostics, including their bounded-tracker limits.
            if (hp != null) put("latestArchivedHpDiagnostics", json.encodeToJsonElement(RealityArticleArchiveMapper.map(hp).payload))
        }
        directory.mkdirs()
        val safeName = match.matchId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(100)
        File(directory, "$safeName.json").writeText(report.toString())
        directory.listFiles { file -> file.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?.drop(8)?.forEach { it.delete() }
    }
}
