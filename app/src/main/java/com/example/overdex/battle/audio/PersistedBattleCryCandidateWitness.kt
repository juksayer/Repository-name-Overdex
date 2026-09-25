package com.example.overdex.battle.audio

import android.content.Context
import com.example.overdex.battle.artifact.AudioArtifactReference
import com.example.overdex.battle.custody.AudioCaptured
import com.example.overdex.battle.custody.BattleCryCandidateMeasurement
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.security.MessageDigest

class PersistedBattleCryCandidateWitness(private val context: Context, private val root: File) : Observer {
    override val observerId = ObserverId("BATTLE_CRY_CANDIDATE_WITNESS", ObservationSource.AUDIO_CAPTURE)
    override val name = "Battle Cry Candidate Witness"
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        val catalog = CryReferenceCatalog.load(context)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { scope -> scope.launch {
            match.articles.collect { article ->
                val audio = article.payload as? AudioCaptured ?: return@collect
                val bytes = File(root, audio.artifact.relativePath).takeIf { it.isFile }?.readBytes() ?: return@collect
                if (sha(bytes) != audio.artifact.sha256) return@collect
                val query = WavPcm16.decode(bytes) ?: return@collect
                val refs = catalog.references.distinctBy { it.sha256 }.mapNotNull { ref ->
                    catalog.openVerified(context, ref)?.let(WavPcm16::decode)?.let { ref to it }
                }
                val candidates = CryAcousticMatcher.rank(query, refs)
                    .map { BattleCryCandidateMeasurement(it.speciesId, it.referenceSha256, it.similarity) }
                match.custody.submitTestimony(
                    SourceId(observerId.id),
                    BattleCryCandidatesMeasured(audio.cueKind, candidates),
                    article.perceivedAt,
                    null,
                    listOf(article.id.value),
                    article.monotonicTimeNanos ?: System.nanoTime()
                )
            }
        }}
    }
    override fun stop() { scope?.cancel(); scope = null }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

}
