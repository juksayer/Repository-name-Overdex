package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.custody.ActivePokemonSide
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** Recorded clocks with deliberately reordered delivery; never supplies the missing final HP/faint. */
class RecordedCramorantHpReferenceTest {
    private val opponent = ActivePokemonSide.OPPONENT

    @Test fun `already seeded Cramorant HP survives delayed source-time species boundary`() {
        val fixture = Fixture()
        val policy = fixture.policyWithDimensions()
        policy.seed(opponent, fixture.latestHp, fixture.time("latestNormalOpponentHp"))

        policy.appearanceBoundary(opponent, fixture.time("cramorantSpeciesBoundary"))

        assertRecordedQtePrepares(fixture, policy)
    }

    @Test fun `same Cramorant HP arriving after source-time boundary prepares the same final QTE`() {
        val fixture = Fixture()
        val policy = fixture.policyWithDimensions()
        policy.appearanceBoundary(opponent, fixture.time("cramorantSpeciesBoundary"))

        policy.seed(opponent, fixture.latestHp, fixture.time("latestNormalOpponentHp"))

        assertRecordedQtePrepares(fixture, policy)
    }

    @Test fun `pre-boundary opponent HP cannot stand in for the confirmed Cramorant appearance`() {
        val fixture = Fixture()
        for (hpFirst in listOf(true, false)) {
            val policy = fixture.policyWithDimensions()
            val oldHp = fixture.measurement("preBoundaryOpponentHp")
            val oldAt = fixture.time("preBoundaryOpponentHp")
            assertTrue(oldAt < fixture.time("cramorantSpeciesBoundary"))
            if (hpFirst) policy.seed(opponent, oldHp, oldAt)
            policy.appearanceBoundary(opponent, fixture.time("cramorantSpeciesBoundary"))
            if (!hpFirst) policy.seed(opponent, oldHp, oldAt)

            assertEquals("pre-boundary geometry, hpFirst=$hpFirst",
                ChargedImpactHpWatchPolicy.PreparationResult.NO_REFERENCE,
                policy.prepareFromPlayerQte(fixture.time("acceptedFinalQte"),
                    fixture.article("finalQteCrop").articleId))
            assertNull(policy.request(fixture.width, fixture.height, fixture.nativeCompletionAt))
            assertFalse(policy.canCapture(fixture.nativeCompletionAt))
        }
    }

    private fun assertRecordedQtePrepares(fixture: Fixture, policy: ChargedImpactHpWatchPolicy) {
        val qteAt = fixture.time("acceptedFinalQte")
        val cueId = fixture.article("finalQteCrop").articleId
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
            policy.prepareFromPlayerQte(qteAt, cueId))
        val request = requireNotNull(policy.request(fixture.width, fixture.height, fixture.nativeCompletionAt))
        assertEquals(opponent, request.side)
        assertEquals(fixture.latestHp, request.reference)
        assertEquals(listOf(cueId), request.cueArticleIds)
        assertNull("Preparing a reference has not measured a charged impact", request.anchor)
        assertEquals(.7619048f, request.reference.filledFraction, 0f)
        assertTrue(policy.canCapture(qteAt + ChargedImpactHpWatchPolicy.PREPARATION_WINDOW_NANOS))
        assertFalse("Delayed delivery must not move the source-time deadline",
            policy.canCapture(qteAt + ChargedImpactHpWatchPolicy.PREPARATION_WINDOW_NANOS + 1L))
    }

    private class Fixture {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }
        private val bytes = resource("articles.json")
        private val articles = json.decodeFromString<List<ArchivedRealityArticle>>(bytes.toString(Charsets.UTF_8))
            .associateBy { it.articleId }
        private val roles = json.parseToJsonElement(resource("provenance.json").toString(Charsets.UTF_8))
            .jsonObject.getValue("articleRoles").jsonObject
        private val hp = article("latestNormalOpponentHp").payload as ArchivedActiveHpBarMeasured
        val width = requireNotNull(hp.sourceWidth)
        val height = requireNotNull(hp.sourceHeight)
        val latestHp = measurement("latestNormalOpponentHp")
        val nativeCompletionAt: Long

        init {
            assertEquals("3b04d47080f0039cd7918daedd65885511c306fa81cd25045e805c8098bac293",
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            assertEquals(11, articles.size)
            articles.values.forEach { article ->
                assertEquals("b508a605-132a-48a8-a4e1-67514c729fc1", article.matchId)
                (article.evidenceReferences.orEmpty() + article.predecessorIds).forEach {
                    assertTrue("Missing original dependency $it", articles.containsKey(it))
                }
            }
            val species = article("cramorantSpeciesBoundary").payload as ArchivedActivePokemonSpeciesWitnessed
            assertEquals("Cramorant", species.speciesName)
            assertEquals(ActivePokemonSide.OPPONENT.name, species.side)
            assertEquals(18_569_834_423_029L, time("cramorantSpeciesBoundary"))
            assertEquals(18_574_090_407_953L, time("latestNormalOpponentHp"))
            assertEquals(18_579_026_798_337L, time("acceptedFinalQte"))
            assertEquals(time("acceptedFinalQte"), time("finalQteCrop"))
            assertEquals(4_936_390_384L, time("acceptedFinalQte") - time("latestNormalOpponentHp"))
            assertTrue(time("latestNormalOpponentHp") > time("cramorantSpeciesBoundary"))
            assertEquals("GREAT!", (article("acceptedFinalQte").payload as ArchivedRawText).value)
            val originalFailure = (article("originalFailedPreparation").payload as ArchivedRawText).value
            assertTrue("Keep the actual failed outcome, not a rewritten successful archive",
                originalFailure.contains("kind=PLAYER_QTE_PROGRESS result=NO_REFERENCE"))
            nativeCompletionAt = Regex("recognizedAt=(\\d+)").find(originalFailure)!!.groupValues[1].toLong()
            assertEquals(18_581_445_169_184L, nativeCompletionAt)
            assertEquals(672, width)
            assertEquals(1493, height)
        }

        fun article(role: String) = articles.getValue(roles.getValue(role).jsonPrimitive.content)
        fun time(role: String) = requireNotNull(article(role).monotonicTimeNanos)
        fun measurement(role: String): ActiveHpBarMeasurement {
            val article = article(role)
            val hp = article.payload as ArchivedActiveHpBarMeasured
            return ActiveHpBarMeasurement(hp.barLeft, hp.barTop, hp.barRight, hp.barBottom,
                hp.filledFraction, requireNotNull(article.confidence))
        }
        fun policyWithDimensions() = ChargedImpactHpWatchPolicy().apply {
            request(width, height, time("preBoundaryOpponentHp") - 1L)
        }
        private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream(
            "/battle/recorded-evidence/2026-11-11-cramorant-hp-reference/$name")).use { it.readBytes() }
    }
}
