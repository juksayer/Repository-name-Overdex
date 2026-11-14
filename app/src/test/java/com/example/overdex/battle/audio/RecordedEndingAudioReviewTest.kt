package com.example.overdex.battle.audio

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in measurements of immutable external audio; never publishes a move, cry or faint. */
class RecordedEndingAudioReviewTest {
    private data class Ref(val label: String, val sha: String, val species: Int, val audio: Pcm16Audio)

    @Test fun compareEndingWindowsAndEarlierControls() {
        val path = System.getenv("OVERDEX_ENDING_AUDIO_REVIEW")
        assumeTrue(!path.isNullOrBlank())
        val root = File(requireNotNull(path))
        val fixture = Json.parseToJsonElement(File(root, "fixture.json").readText()).jsonObject
        assertEquals(fixture.s("archiveSha256"), sha(File(fixture.s("archive")).readBytes()))
        val refs = fixture.getValue("references").jsonArray.map { item ->
            val r = item.jsonObject; val bytes = File(r.s("path")).readBytes()
            assertEquals(r.s("sha256"), sha(bytes))
            Ref(r.s("label"), r.s("sha256"), r.getValue("speciesId").jsonPrimitive.int,
                requireNotNull(WavPcm16.decodeBundledReference(bytes)))
        }.distinctBy { it.label to it.sha }
        val cryRefs = refs.filter { it.species > 0 }.map { CryWaveformReference(it.species, it.sha, it.audio) }
        val cryMatcher = CryWaveformMatcher(cryRefs)
        // The existing spectral matcher is a diagnostic comparator here. Its Fast Move
        // acceptance thresholds have NOT been validated for charged effects or cries.
        val spectra = FastMoveReferenceMatcher(refs.map { FastMoveSoundReference(it.label, it.sha, it.audio) })
        val cryRows = mutableListOf("clipSeconds\tarticleId\tspeciesId\treferenceSha256\tcorrelation\toffsetSeconds\tdurationSeconds\tacceptedByCryPolicy")
        val spectralRows = mutableListOf("clipSeconds\tarticleId\tlabel\tsimilarity\tchangingSimilarity\tqueryOffsetSeconds\treferenceOffsetSeconds\tmatchedSeconds")
        val partialRows = mutableListOf("clipSeconds\tarticleId\tlabel\treferenceSha256\tcorrelation\tqueryOffsetSeconds\treferenceOffsetSeconds\tmatchedSeconds")
        val controlRows = mutableListOf("label\twholeCryCorrelation\tpartialSelfCorrelation")
        // Sanity controls test the matching path; they do not validate game-capture accuracy.
        for (ref in refs) {
            val samples = StatChangeSoundMatcher.mono8k(ref.audio)
            val fragment = Pcm16Audio(8000, 1, samples.copyOfRange(0, minOf(4800, samples.size)))
            val own = CryWaveformMatcher(listOf(CryWaveformReference(1, ref.sha, fragment))).measure(fragment).single()
            assertTrue(own.correlation > .999f)
            val whole = if (ref.species > 0 && samples.size <= 32000)
                CryWaveformMatcher(listOf(CryWaveformReference(ref.species, ref.sha, ref.audio))).measure(ref.audio).single().correlation else null
            if (whole != null) assertTrue(whole > .999f)
            controlRows += "${ref.label}\t$whole\t${own.correlation}"
        }
        for (item in fixture.getValue("clips").jsonArray) {
            val clip = item.jsonObject; val bytes = File(root, clip.s("path")).readBytes()
            assertEquals(clip.s("sha256"), sha(bytes))
            val fullAudio = requireNotNull(WavPcm16.decode(bytes))
            val mono = StatChangeSoundMatcher.mono8k(fullAudio)
            val starts = if (mono.size <= 32000) listOf(0) else
                ((0..mono.size - 32000 step 16000).toList() + (mono.size - 32000)).distinct()
            for (start in starts) {
                val audio = Pcm16Audio(8000, 1, mono.copyOfRange(start, minOf(start + 32000, mono.size)))
                val seconds = clip.getValue("seconds").jsonPrimitive.double + start / 8000.0
                val prefix = "$seconds\t${clip.s("id")}"
                val cries = cryMatcher.measure(audio)
                val accepted = CryWaveformAcceptance.select(cries)
                for (fit in cries) cryRows += "$prefix\t${fit.speciesId}\t${fit.referenceSha256}\t${fit.correlation}\t${fit.offsetNanos / 1e9}\t${fit.durationNanos / 1e9}\t${fit in accepted}"
                // Earlier clips are independent captured controls. Export all candidates, not just a target winner.
                for (fit in spectra.measure(audio)) spectralRows += "$prefix\t${fit.moveName}\t${fit.similarity}\t${fit.changingSimilarity}\t${fit.matchingOffsetNanos / 1e9}\t${fit.referenceOffsetNanos / 1e9}\t${fit.matchedDurationNanos / 1e9}"
                if (seconds >= 135.0) for (ref in refs) {
                    val samples = StatChangeSoundMatcher.mono8k(ref.audio)
                    // Separate 600 ms reference fragments. No gaps are padded or joined;
                    // each result remains a partial-reference score, never a full cry acceptance.
                    val offsets = if (samples.size < 4800) emptyList() else
                        ((0..samples.size - 4800 step 1600).toList() + (samples.size - 4800)).distinct()
                    if (offsets.isEmpty()) continue
                    val matcher = CryWaveformMatcher(offsets.mapIndexed { index, offset ->
                        CryWaveformReference(index + 1, ref.sha, Pcm16Audio(8000, 1, samples.copyOfRange(offset, offset + 4800)))
                    })
                    val best = matcher.measure(audio).first()
                    partialRows += "$prefix\t${ref.label}\t${ref.sha}\t${best.correlation}\t${best.offsetNanos / 1e9}\t${offsets[best.speciesId - 1] / 8000.0}\t0.6"
                }
            }
            assertEquals(clip.s("sha256"), sha(File(root, clip.s("path")).readBytes()))
        }
        // Fixed external regression: this archive retained two parts of one Seed Bomb,
        // then only the tail of Dragonair's cry. These are study bounds, not live thresholds.
        fun partial(id: String, label: String) = partialRows.drop(1).map { it.split('\t') }
            .filter { it[1] == id && it[2] == label }.maxOf { it[4].toFloat() }
        assertTrue(partial("2f840a89-7148-44a1-a24c-1dc36e528738", "charged:Seed Bomb") > .99f)
        assertTrue(partial("1aae02ab-5198-40b6-b789-ebae2291d262", "charged:Seed Bomb") > .99f)
        assertTrue(partial("b0ff96b6-8521-4db9-8377-56f3c19ec70f", "cry:148:None") > .95f)
        assertTrue(cryRows.drop(1).map { it.split('\t') }.filter {
            it[1] == "b0ff96b6-8521-4db9-8377-56f3c19ec70f" && it[2] == "148"
        }.all { it[7] == "false" })
        // Earlier genuine Dragonair entry, truncated deliberately to reproduce the missing head.
        val entry = fixture.getValue("clips").jsonArray.map { it.jsonObject }
            .single { it.s("id") == "b845a18e-c733-4c8e-a351-92972b14ba32" }
        val entryMono = StatChangeSoundMatcher.mono8k(requireNotNull(WavPcm16.decode(File(root, entry.s("path")).readBytes())))
        val dragon = cryRefs.first { it.speciesId == 148 }
        val dragonMatcher = CryWaveformMatcher(listOf(dragon))
        val complete = dragonMatcher.measure(Pcm16Audio(8000, 1, entryMono.copyOfRange(entryMono.size - 32000, entryMono.size))).single()
        val cutStart = entryMono.size - 32000 + (complete.offsetNanos * 8000 / 1_000_000_000L).toInt() + 2480
        val cut = Pcm16Audio(8000, 1, entryMono.copyOfRange(cutStart, cutStart + 9600))
        val cutWhole = dragonMatcher.measure(cut).single()
        val dragonMono = StatChangeSoundMatcher.mono8k(dragon.audio)
        val tail = Pcm16Audio(8000, 1, dragonMono.takeLast(4800).toShortArray())
        val cutTail = CryWaveformMatcher(listOf(CryWaveformReference(148, dragon.sha256, tail))).measure(cut).single()
        assertTrue(complete.correlation > .7f)
        assertTrue(cutWhole.correlation < CryWaveformAcceptance.MIN_CORRELATION)
        assertTrue(cutTail.correlation > .7f)
        File(root, "truncated-entry-control.tsv").writeText("completeCry\tcutWholeCry\tcutTail\n${complete.correlation}\t${cutWhole.correlation}\t${cutTail.correlation}\n")
        File(root, "cry-results.tsv").writeText(cryRows.joinToString("\n") + "\n")
        File(root, "spectral-results.tsv").writeText(spectralRows.joinToString("\n") + "\n")
        File(root, "partial-waveform-results.tsv").writeText(partialRows.joinToString("\n") + "\n")
        File(root, "sanity-controls.tsv").writeText(controlRows.joinToString("\n") + "\n")
        assertEquals(fixture.s("archiveSha256"), sha(File(fixture.s("archive")).readBytes()))
    }
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
