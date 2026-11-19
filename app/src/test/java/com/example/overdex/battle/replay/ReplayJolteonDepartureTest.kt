package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.WavPcm16
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReplayJolteonDepartureTest {
    private val archive = MatchArchiveSerializer.deserialize(requireNotNull(javaClass.getResource("/replay/jolteon/timeline.json")).readText())
    private val clip = requireNotNull(javaClass.getResource("/replay/jolteon/ending.wav")).readBytes()
    private val audio by lazy {
        fun reference(name: String, species: Int?, path: String): ReplayAudioReference {
            val bytes = File("src/main/assets/$path").readBytes()
            return ReplayAudioReference(name, species == null, species, ReplayRetainedAudioRecovery.sha(bytes),
                requireNotNull(WavPcm16.decodeBundledReference(bytes)))
        }
        val pool = listOf(reference("Jolteon", 135, "cry_references/pv135.wav"),
            reference("Electrode", 101, "cry_references/pv101.wav"),
            reference("Dragon Pulse", null, "charged_move_sounds/406-0_dragon_pulse.wav"))
        ReplayRetainedAudioRecovery.recover(archive, pool) { clip }
    }

    @Test fun completeCryAndContractingBurstAgreeOnTheLastOpponent() {
        val cry = audio.terminalWholeCries.single()
        assertEquals(135, cry.speciesId)
        val article = archive.articles.single { it.payload is ArchivedChargedMoveEnergySpent }
        val at = article.monotonicTimeNanos!!
        val charge = ReplayFinishingChargeCandidate("PLAYER", "Dragon Pulse", at, at, at + 1_000_000_000, setOf(article.articleId), true)
        fun link(rows: List<ArchivedRealityArticle> = archive.articles, next: Long? = null) =
            ReplayTerminalAudioAssociation.announcedFinish(rows, audio, listOf(charge), { side, _ ->
                if (side == "PLAYER") ReplayCombatant("Turtonator", 776, 0) else ReplayCombatant("Jolteon", 135, 0)
            }, { _, _ -> 0L }, { _, _ -> next })
        val confirmed = requireNotNull(link())
        assertEquals(cry.estimatedOnsetNanos, confirmed.faintSourceAtNanos)
        assertEquals(confirmed, link(archive.articles.reversed()))
        assertNull(link(next = at + 100_000_000))
        assertNull(link(archive.articles.filterNot { it.payload is ArchivedTerminalParticleObservations }))
        assertNull(link(archive.articles + article.copy(articleId = "competing-charge", monotonicTimeNanos = at + 100_000_000)))
        assertNull(link(archive.articles.map { row ->
            val badge = row.payload as? ArchivedOpponentBattleResourceCountMeasured
            if (badge == null) row else row.copy(payload = badge.copy(visibleCount = 2))
        }))
    }
}
