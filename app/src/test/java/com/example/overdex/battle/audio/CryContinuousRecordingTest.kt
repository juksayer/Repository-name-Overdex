package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.inference.RepeatCryDepartureCandidate
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/** Actual uninterrupted PCM and the live buffer/matcher/policy; appearances are video labels. */
class CryContinuousRecordingTest {
    @Test fun `continuous 1605 PCM preserves four candidates and both ordinary switch controls`() {
        val path=System.getenv("OVERDEX_CRY_FIXTURES")
        assumeTrue(!path.isNullOrBlank())
        val root=File(requireNotNull(path))
        val bytes=File(root,"continuous-1605.wav").readBytes()
        assertEquals(File(root,"continuous-1605.sha256").readText().trim(),sha(bytes))
        val audio=requireNotNull(WavPcm16.decode(bytes))
        val refs=File(root,"references-all.tsv").readLines().filter(String::isNotBlank).map { line ->
            val p=line.split('\t');val b=File(p[2]).readBytes();assertEquals(p[1],sha(b))
            CryWaveformReference(p[0].toInt(),p[1],requireNotNull(WavPcm16.decodeBundledReference(b)))
        }
        val matcher=CryWaveformMatcher(refs)
        val buffer=CryAudioWindow()
        val evidence=CryDepartureEvidence()
        val archivedEvidence=CryDepartureEvidence()
        val history=Json.parseToJsonElement(File(root,"archive-history-1605.json").readText()).jsonObject
        assertEquals(history.getValue("archiveSha256").jsonPrimitive.content,sha(File(history.getValue("archive").jsonPrimitive.content).readBytes()))
        val origin=history.getValue("recordingOriginNanos").jsonPrimitive.long
        // Retrospective reconstruction with the actual source readings, not inferred faints.
        // Clock alignment is independent retained PCM, with ~30 ms uncertainty.
        val originalIdentities=mutableListOf<RealityArticle>()
        for (item in history.getValue("articles").jsonArray) {
            val row=item.jsonObject;val payload=row.getValue("payload").jsonObject
            val original=RealityArticle(ArticleId(row.getValue("articleId").jsonPrimitive.content),
                row.getValue("perceivedAt").jsonPrimitive.long,row.getValue("recordedAt").jsonPrimitive.long,
                SourceId(row.getValue("sourceId").jsonPrimitive.content),
                ActivePokemonSpeciesWitnessed(ActivePokemonSide.valueOf(payload.getValue("side").jsonPrimitive.content),
                    payload.getValue("speciesName").jsonPrimitive.content,payload.getValue("speciesId").jsonPrimitive.int),
                evidenceReferences=row.getValue("evidenceReferences").jsonArray.map { it.jsonPrimitive.content },
                monotonicTimeNanos=row.getValue("monotonicTimeNanos").jsonPrimitive.long)
            originalIdentities += original
            archivedEvidence.observe(original)
        }
        val deliveryEvidence=CryDepartureEvidence()
        val deliveryCandidates=mutableListOf<RepeatCryDepartureCandidate>()
        val deliverySeen=mutableListOf<Pair<Int,Long>>()
        val pending=originalIdentities.sortedBy { it.monotonicTimeNanos!! + (it.recordedAt-it.perceivedAt)*1_000_000 }
        var delivered=0
        var searched=emptySet<Int>()
        val recent=CryRecentWindows()
        val scores=mutableMapOf<Long,List<CryWaveformFit>>()
        val archivedCandidates=mutableListOf<RepeatCryDepartureCandidate>()
        val candidates=mutableListOf<RepeatCryDepartureCandidate>()
        val seen=mutableListOf<Pair<Int,Long>>()
        val rows=mutableListOf("species\tonsetSeconds\tcorrelation\tdiscoveredSeconds\tanalysisMs")
        fun article(id:String,at:Long,payload:TestimonyPayload,source:String)=RealityArticle(ArticleId(id),at/1_000_000,
            at/1_000_000,SourceId(source),payload,evidenceReferences=listOf("video-or-pcm-$id"),monotonicTimeNanos=at)
        val identities=listOf(Triple(30.0,ActivePokemonSide.PLAYER,776),Triple(30.0,ActivePokemonSide.OPPONENT,42),
            Triple(76.0,ActivePokemonSide.PLAYER,323),Triple(96.0,ActivePokemonSide.OPPONENT,260),
            Triple(98.0,ActivePokemonSide.PLAYER,711),Triple(100.0,ActivePokemonSide.OPPONENT,460),
            Triple(109.0,ActivePokemonSide.OPPONENT,260)).iterator()
        var next=if(identities.hasNext())identities.next() else null
        val step=audio.sampleRateHz/4
        var sequence=0L
        val measured=mutableListOf<Double>()
        for(end in step..audio.samples.size step step) {
            val endNanos=end.toLong()*1_000_000_000/audio.sampleRateHz
            while(next!=null && next.first*1e9<=endNanos) {
                val identity=next
                candidates += evidence.observe(article("identity-${identity.second}-${identity.first}",(identity.first*1e9).toLong(),
                    ActivePokemonSpeciesWitnessed(identity.second,"species${identity.third}",identity.third),"ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"))
                next=if(identities.hasNext())identities.next() else null
            }
            val window=buffer.ingest(LivePcmChunk(Pcm16Audio(audio.sampleRateHz,1,audio.samples.copyOfRange(end-step,end)),
                sequence++,endNanos,endNanos/1_000_000,"RECORDED_PLAYBACK"),0) ?: continue
            if(endNanos<30_000_000_000 || endNanos>142_000_000_000)continue
            val started=System.nanoTime()
            val ranked=matcher.measure(window.audio)
            val fits=CryWaveformAcceptance.select(ranked)
            val millis=(System.nanoTime()-started)/1e6;measured+=millis
            // Reproduce the original identity arrival delays. Per-reference correlation is
            // independent of the search pool; filter scores BEFORE acoustic acceptance.
            while(delivered<pending.size && pending[delivered].let {
                it.monotonicTimeNanos!!+(it.recordedAt-it.perceivedAt)*1_000_000 <= origin+endNanos }) {
                deliveryCandidates += deliveryEvidence.observe(pending[delivered++])
            }
            recent.remember(window)
            scores[window.sequence]=ranked
            val ids=deliveryEvidence.speciesIds()
            val work=listOf(window)+if(ids!=searched)recent.preceding(window) else emptyList()
            searched=ids
            for (previousWindow in work) {
                for(fit in CryWaveformAcceptance.select(scores.getValue(previousWindow.sequence).filter { it.speciesId in ids })) {
                    val onset=previousWindow.startNanos+fit.offsetNanos
                    if(deliverySeen.any { it.first==fit.speciesId && abs(it.second-onset)<fit.durationNanos })continue
                    deliverySeen += fit.speciesId to onset
                    deliveryCandidates += deliveryEvidence.observe(article("delivered-cry-${deliverySeen.size}",origin+onset,
                        BattleCryLocalized(fit.speciesId,fit.referenceSha256,fit.correlation,fit.durationNanos),CryDepartureEvidence.SOURCE))
                }
            }
            for(fit in fits) {
                val onset=window.startNanos+fit.offsetNanos
                if(seen.any { it.first==fit.speciesId && abs(it.second-onset)<fit.durationNanos })continue
                seen+=fit.speciesId to onset
                rows += "${fit.speciesId}\t${onset/1e9}\t${fit.correlation}\t${endNanos/1e9}\t$millis"
                val localized=article("cry-${seen.size}",onset,
                    BattleCryLocalized(fit.speciesId,fit.referenceSha256,fit.correlation,fit.durationNanos),CryDepartureEvidence.SOURCE)
                candidates += evidence.observe(localized)
                archivedCandidates += archivedEvidence.observe(localized.copy(monotonicTimeNanos=onset+origin))
            }
        }
        val report=rows+"# analysis meanMs=${measured.average()} maxMs=${measured.maxOrNull()} windows=${measured.size}"+
            candidates.map { "# candidate ${it.speciesId} ${it.side} ${it.atNanos/1e9}" } +
            archivedCandidates.map { "# archive candidate ${it.speciesId} ${it.side} ${(it.atNanos-origin)/1e9}" } +
            deliveryCandidates.map { "# delayed candidate ${it.speciesId} ${it.side} ${(it.atNanos-origin)/1e9}" }
        File(root,"continuous-1605-kotlin-results.tsv").writeText(report.joinToString("\n")+"\n")
        println(report.joinToString("\n"))
        assertEquals(listOf(776,42,460,260),candidates.map { it.speciesId })
        assertEquals(listOf(ActivePokemonSide.PLAYER,ActivePokemonSide.OPPONENT,ActivePokemonSide.OPPONENT,ActivePokemonSide.OPPONENT),candidates.map { it.side })
        assertEquals(candidates.map { it.speciesId to it.side },archivedCandidates.map { it.speciesId to it.side })
        assertEquals(candidates.map { it.speciesId to it.side },deliveryCandidates.map { it.speciesId to it.side })
        val expected=listOf(74.615,93.943,107.895,137.015)
        candidates.zip(expected).forEach { (candidate,time) -> assertEquals(time,candidate.atNanos/1e9,.005) }
    }
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
