package com.example.overdex.battle.audio

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in original-recording check. Media stays on the backup drive, not in source control. */
class CryWaveformRecordedTest {
    @Test fun `measure preserved KO switch and no cry clips with the Kotlin matcher`() {
        val path=System.getenv("OVERDEX_CRY_FIXTURES")
        assumeTrue("Set OVERDEX_CRY_FIXTURES to verified recording excerpts", !path.isNullOrBlank())
        val root=File(requireNotNull(path))
        val hashes=Json.parseToJsonElement(File(root,"sha256.json").readText()).jsonObject
        val refs=File(root,"references.tsv").readLines().filter(String::isNotBlank).map { line ->
            val parts=line.split('\t'); val bytes=File(parts[2]).readBytes()
            assertEquals(parts[1],sha(bytes))
            CryWaveformReference(parts[0].toInt(),parts[1],requireNotNull(WavPcm16.decodeBundledReference(bytes)))
        }
        val matcher=CryWaveformMatcher(refs)
        val failures=mutableListOf<String>()
        val output=mutableListOf("case\texpected\tbestSpecies\tcorrelation\tobservedSeconds\texpectedSeconds\tmatchMilliseconds")
        for (line in File(root,"cases.tsv").readLines().filter(String::isNotBlank)) {
            val p=line.split('\t');val bytes=File(root,p[4]).readBytes()
            assertEquals(hashes.getValue(p[4]).jsonPrimitive.content,sha(bytes))
            val audio=requireNotNull(WavPcm16.decode(bytes))
            val before=System.nanoTime();val ranked=matcher.measure(audio)
            val millis=(System.nanoTime()-before)/1e6
            val best=ranked.first()
            val at=p[1].toDouble()+best.offsetNanos/1e9
            if (p[2].toInt()==0) {
                if(best.correlation>=.15f) failures += "${p[0]}: unexpected cry-like correlation"
            } else if(best.speciesId!=p[2].toInt() || best.correlation<.5f || kotlin.math.abs(at-p[3].toDouble())>.005) {
                failures += "${p[0]}: expected species/onset was not recovered"
            }
            output += "${p[0]}\t${p[2]}\t${best.speciesId}\t${best.correlation}\t$at\t${p[3]}\t$millis"
        }
        File(root,"kotlin-waveform-results.tsv").writeText(output.joinToString("\n")+"\n")
        println(output.joinToString("\n"))
        // Retrospective regression bounds for these excerpts, not a production confidence policy.
        assertEquals(11,output.size)
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
    private fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
