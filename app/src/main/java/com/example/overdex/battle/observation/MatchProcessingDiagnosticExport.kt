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
            match.finishingReviewDiagnostics?.let { diagnostics ->
                val ids = match.realityTimeline.getArticles().asSequence().filter { it.payload is com.example.overdex.battle.custody.FinishingHitCredited }
                    .map { it.id.value }.toSet()
                put("finishingReviews", buildJsonArray {
                    for (review in diagnostics.snapshot()) add(JsonObject(json.encodeToJsonElement(review).jsonObject +
                        ("presentInTimelineAtExport" to JsonPrimitive(review.decisionId in ids))))
                })
            }
            match.faintHistoryCache?.snapshot()?.let { c -> put("faintHistoryCache", buildJsonObject {
                put("hits",c.hits); put("misses",c.misses); put("oversized",c.oversized)
                put("evictions",c.evictions); put("entries",c.entries); put("retainedRows",c.retainedRows)
            }) }
            listOf("articleListeners" to match.articleListenerDiagnostics,
                "preBattleArticleListeners" to match.preBattleListenerDiagnostics).forEach { (key, diagnostics) ->
                val listeners = diagnostics ?: return@forEach
                put(key, buildJsonObject {
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
            put("readerWork", buildJsonObject { snapshot.readerWork.forEach { (name, count) -> put(name, count) } })
            put("recentGradeIntervals", buildJsonArray {
                for (span in snapshot.recentGradeIntervals) add(buildJsonObject {
                    put("decision", span.decision)
                    put("firstCropId", span.firstId)
                    put("lastCropId", span.lastId)
                    put("firstSourceNanos", span.firstSourceNanos)
                    put("lastSourceNanos", span.lastSourceNanos)
                    put("samples", span.samples)
                })
            })
            put("badgePixelReads", buildJsonObject { snapshot.badgePixelReads.forEach { (crop, count) -> put(crop, count) } })
            put("completedTestimony", snapshot.completed)
            put("lastCompletedSequence", snapshot.lastCompletedSequence)
            put("activeSequence", snapshot.activeSequence)
            put("activeStage", snapshot.activeStage)
            put("activeAgeNanos", snapshot.activeAgeNanos)
            put("localizedCryTiming", buildJsonObject {
                put("totalAccepted", snapshot.cryPositiveCount)
                put("retentionLimit", MatchProcessingDiagnostics.MAX_CRY_POSITIVES)
                put("recent", buildJsonArray {
                    for (cry in snapshot.recentCryPositives) add(buildJsonObject {
                        put("speciesId", cry.speciesId)
                        put("catchUp", cry.catchUp)
                        put("onsetNanos", cry.onsetNanos)
                        put("windowEndNanos", cry.windowEndNanos)
                        put("bufferedNanos", cry.bufferedNanos)
                        put("referencesStartedNanos", cry.referencesStartedNanos)
                        put("referencesReadyNanos", cry.referencesReadyNanos)
                        put("analysisStartNanos", cry.analysisStartNanos)
                        put("matchDoneNanos", cry.matchDoneNanos)
                        put("preservedNanos", cry.preservedNanos)
                        put("captureSubmittedNanos", cry.captureSubmittedNanos)
                        put("captureReceiptNanos", cry.captureReceiptNanos)
                        put("crySubmittedNanos", cry.crySubmittedNanos)
                        put("captureSequence", cry.captureSequence)
                        put("crySequence", cry.crySequence)
                    })
                })
            })
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
