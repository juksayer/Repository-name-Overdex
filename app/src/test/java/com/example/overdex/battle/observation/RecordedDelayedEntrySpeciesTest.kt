package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedSpeciesCheckMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RecordedDelayedEntrySpeciesTest {
    @Test fun `later accepted badges cover recorded delayed entry without suppressing final charge`() {
        val fixture = Fixture()
        var now = 0L
        val reports = mutableListOf<Pair<SpeciesCheckMeasured, List<String>>>()
        val checks = SpeciesCheckCoordinator({ now }) { report, refs -> reports += report to refs }
        val windows = mutableMapOf<ActivePokemonSide, SpeciesCheckCoordinator.Window>()
        val charge = fixture.article("precedingCharge")
        for ((side, role) in listOf(ActivePokemonSide.PLAYER to "precedingPlayerWindowOpened",
                ActivePokemonSide.OPPONENT to "precedingOpponentWindowOpened")) {
            now = fixture.time(role)
            checks.request(side, "CHARGE", charge.articleId, requireNotNull(charge.monotonicTimeNanos))
            windows[side] = requireNotNull(checks.windowFor(side, now))
        }
        // Recognition really completed after each original three-second assignment expired.
        for (role in listOf("precedingPlayerWindowExpired", "precedingOpponentWindowExpired")) {
            now = fixture.time(role)
            checks.tick()
        }
        for ((side, prefix) in listOf(ActivePokemonSide.PLAYER to "player",
                ActivePokemonSide.OPPONENT to "opponent")) {
            val crop = fixture.article("${prefix}CoveringBadgeCrop")
            val confirmed = fixture.article("${prefix}CoveredWindowConfirmed")
            val measurement = confirmed.payload as ArchivedSpeciesCheckMeasured
            assertTrue(requireNotNull(confirmed.evidenceReferences).contains(crop.articleId))
            now = requireNotNull(confirmed.monotonicTimeNanos)
            assertNotNull(checks.acceptImmediate(side, windows.getValue(side).id,
                requireNotNull(measurement.speciesName), crop.articleId, crop.monotonicTimeNanos))
        }

        val entry = fixture.article("delayedEntryAccepted")
        val openingsBefore = reports.count { it.first.status == "OPENED" }
        for ((side, prefix) in listOf(ActivePokemonSide.PLAYER to "Player",
                ActivePokemonSide.OPPONENT to "Opponent")) {
            now = fixture.time("redundant${prefix}WindowOpened")
            checks.request(side, "ENTRY", entry.articleId, requireNotNull(entry.monotonicTimeNanos))
            assertFalse(checks.captureEnabled(side))
            val covered = reports.last()
            assertEquals("COVERED", covered.first.status)
            assertEquals(entry.monotonicTimeNanos, covered.first.triggerMonotonicNanos)
            assertEquals(listOf(entry.articleId,
                fixture.article("${prefix.lowercase()}CoveringBadgeCrop").articleId), covered.second)
            val leftover = fixture.time("redundant${prefix}BadgeCrop")
            assertFalse(checks.needsReading(side, windows.getValue(side).id, leftover))
        }
        assertEquals(openingsBefore, reports.count { it.first.status == "OPENED" })

        val finalCharge = fixture.article("finalChargeUsedTyped")
        for ((side, role) in listOf(ActivePokemonSide.PLAYER to "finalChargePlayerWindowOpened",
                ActivePokemonSide.OPPONENT to "finalChargeOpponentWindowOpened")) {
            now = fixture.time(role)
            checks.request(side, "CHARGE", finalCharge.articleId,
                requireNotNull(finalCharge.monotonicTimeNanos))
            val window = requireNotNull(checks.windowFor(side, now))
            assertTrue(checks.needsReading(side, window.id, now))
            assertEquals(finalCharge.articleId, window.triggerId)
        }
        // This scheduling result supplies neither the missing terminal HP nor a faint.
        assertEquals(2, reports.count { it.first.status == "COVERED" })
        assertEquals(openingsBefore + 2, reports.count { it.first.status == "OPENED" })
    }

    private class Fixture {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }
        private val bytes = resource("articles.json")
        private val articles = json.decodeFromString<List<ArchivedRealityArticle>>(bytes.toString(Charsets.UTF_8))
            .associateBy { it.articleId }
        private val roles = json.parseToJsonElement(resource("provenance.json").toString(Charsets.UTF_8))
            .jsonObject.getValue("articleRoles").jsonObject
        init {
            assertEquals("860277e88872d6fee69fb70163fba34e9d95c013a9a5a6da8b25bc7aa3222fdb",
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            assertEquals(39, articles.size)
            for (article in articles.values) {
                for (ref in article.evidenceReferences.orEmpty() + article.predecessorIds)
                    assertTrue("Missing original dependency $ref", articles.containsKey(ref))
            }
        }
        fun article(role: String) = articles.getValue(roles.getValue(role).jsonPrimitive.content)
        fun time(role: String) = requireNotNull(article(role).monotonicTimeNanos)
        private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream(
            "/battle/recorded-evidence/2026-11-11-delayed-entry-species/$name")).use { it.readBytes() }
    }
}
