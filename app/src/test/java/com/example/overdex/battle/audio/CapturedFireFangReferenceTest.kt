package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.custody.ActivePokemonSide.*
import com.example.overdex.battle.inference.FastMoveCadenceDerivation
import com.example.overdex.battle.inference.FastMoveCadenceInference
import com.example.overdex.battle.inference.FastMoveCandidateSnapshot
import com.example.overdex.battle.inference.FastMoveEnergyInference
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.PokemonFormReference
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.GameMasterPokemon
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Unchanged, held-out PCM; human recollection supplies no configured Arcanine move. */
class CapturedFireFangReferenceTest {
    private val assets = File("src/main/assets/fast_move_sounds")
    private val fixtures = File("src/androidTest/assets/fast_move_20261108")
    private val manifest by lazy { readJson(File(fixtures, "manifest.json")) }
    private val samples by lazy { manifest.objects("samples").associateBy { it.text("id") } }
    private val knowledge by lazy { currentKnowledge() }
    private val arcaninePool = setOf("Bite", "Fire Fang", "Snarl", "Thunder Fang", "Dragon Tail", "Mud Shot")
    private val sourceHashes = mapOf(
        "b7344c07-2c4e-43f5-9a88-4a09c7dc099f" to "101f88e7e54c276ab9fb2fc6c8c692e4c693343bb3a8c7219fc9c8433b3381e2",
        "756c9985-ea6a-4c6b-b96f-00451a0706a6" to "ab86446d45364d3cf0b3fc771d0132c445a4093318d9bd13df307c4e652d7b5c",
        "48a36312-70d1-4232-a9dd-6d69e1c98a89" to "bdea0bde54e950e67dd308fefe4d01921b0edee02c7f5d4f8ff18097ace2da02",
    )

    @Test fun `captured reference and held out WAVs retain exact source provenance and recognition only status`() {
        val provenance = readJson(File(assets, "captured-provenance-20261108.json"))
            .objects("samples").single()
        val referenceBytes = File(assets, provenance.text("runtimeFile")).readBytes()
        val expectedReferenceSha = "751ebfcd5967fd112e25dc7868f5e2df1d08279cd64940df8a7837f44b9dc82b"
        assertEquals(expectedReferenceSha, sha(referenceBytes))
        assertEquals(provenance.obj("source").text("sha256"), sha(referenceBytes))
        assertEquals(provenance.obj("derived").text("sha256"), sha(referenceBytes))
        assertEquals(0, provenance.obj("trim").number("startFrame"))
        assertEquals(9_600, provenance.obj("trim").number("frameCount"))
        assertEquals(1_200_000_000L, provenance.obj("source").long("durationNanos"))
        val runtime = readJson(File(assets, "manifest.json")).objects("references")
            .single { it.text("file") == provenance.text("runtimeFile") }
        assertEquals("Fire Fang", runtime.text("moveName"))
        assertFalse(runtime.getValue("replayEligible").jsonPrimitive.boolean)
        assertFalse(provenance.getValue("replayEligible").jsonPrimitive.boolean)
        assertEquals("recognition_excerpt", runtime.text("purpose"))
        val source = provenance.obj("sourceArchive")
        assertEquals(sourceHashes.getValue(source.text("matchId")), source.text("sha256"))
        assertEquals(7, samples.size)
        samples.values.forEach { sample ->
            val bytes = File(fixtures, sample.text("file")).readBytes()
            val original = sample.obj("sourceAudioArticle")
            val originalPayload = original.obj("payload")
            val derived = sample.obj("derived")
            val recordedArchive = sample.obj("sourceArchive")
            assertEquals(sourceHashes.getValue(recordedArchive.text("matchId")), recordedArchive.text("sha256"))
            assertEquals(recordedArchive.text("matchId"), original.text("matchId"))
            assertEquals(originalPayload.text("sha256"), sha(bytes))
            assertEquals(derived.text("sha256"), sha(bytes))
            assertEquals(originalPayload.number("byteCount"), bytes.size)
            val audio = requireNotNull(WavPcm16.decode(bytes))
            assertEquals(8_000, audio.sampleRateHz)
            assertEquals(1, audio.channelCount)
            assertEquals(9_600, audio.samples.size)
            val pcmBytes = ByteBuffer.allocate(audio.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                .apply { audio.samples.forEach { putShort(it) } }.array()
            assertEquals(derived.text("pcmSha256"), sha(pcmBytes))
            assertFalse(sample.getValue("replayEligible").jsonPrimitive.boolean)
            assertNotEquals("Held-out PCM must not be the fitted reference", expectedReferenceSha, sha(bytes))
        }
    }

    @Test fun `real Fire Fang reference separates a distinct attack and a separate battle within full legal comparisons`() {
        val provenance = readJson(File(assets, "captured-provenance-20261108.json"))
            .objects("samples").single()
        listOf("fire-fang-distinct-attack", "fire-fang-separate-battle").forEach { id ->
            val sample = samples.getValue(id)
            val allowed = sample.strings("legalMoves").toSet()
            // All five Mawile choices plus both choices of the other active species.
            assertEquals(setOf("Astonish", "Bite", "Fire Fang", "Ice Fang", "Fairy Wind", "Air Slash", "Psycho Cut"), allowed)
            val ranked = matcher(allowed).measure(audio(id), allowed)
            assertEquals(allowed, ranked.map { it.moveName }.toSet())
            assertTrue("Separate captured attack was not distinguished: $id: $ranked", FastMoveReferenceMatcher.accepts(ranked))
            assertEquals("Fire Fang", ranked.first().moveName)
            val before = matcher(allowed, includeFireFang = false).measure(audio(id), allowed)
            assertFalse("A missing reference must not force the closest other move: $id: $before", FastMoveReferenceMatcher.accepts(before))
            assertNotEquals(provenance.obj("source").text("audioArticleId"), sample.obj("sourceAudioArticle").text("articleId"))
        }
        assertNotEquals("One query must be from another battle", provenance.obj("sourceArchive").text("matchId"),
            samples.getValue("fire-fang-separate-battle").obj("sourceArchive").text("matchId"))
    }

    @Test fun `missing Fire Fang leaves scoped Arcanine unconfirmed despite a globally accepted Thunder Fang fit`() = runBlocking {
        val ids = listOf("arcanine-first", "arcanine-overlap", "arcanine-distinct")
        val baseline = matcher(arcaninePool, includeFireFang = false)
        val first = baseline.measure(audio(ids.first()), arcaninePool)
        assertEquals(arcaninePool - "Fire Fang", first.map { it.moveName }.toSet())
        assertEquals("Thunder Fang", first.first().moveName)
        assertTrue("Fixture must exercise the accepted-global versus incomplete-scope distinction", FastMoveReferenceMatcher.accepts(first))
        val steps = replay(baseline, ids)
        assertTrue(steps.all { it.snapshot?.identifiedMoveName == null })
        assertTrue(steps.flatMap { it.output }.none { (it.payload as? FastMoveIdentified)?.side == PLAYER })
        assertTrue(steps.all { it.energyAwards == 0 })
    }

    @Test fun `completing legal coverage rejects overlapping audio then identifies Thunder Fang with original source clocks and citations`() = runBlocking {
        val ids = listOf("arcanine-first", "arcanine-overlap", "arcanine-distinct")
        val steps = replay(matcher(arcaninePool), ids)
        assertEquals(arcaninePool, steps.first().ranked.map { it.moveName }.toSet())
        assertEquals(setOf("Bite", "Fire Fang", "Snarl", "Thunder Fang"),
            requireNotNull(knowledge.getPokemonByName("Arcanine")).fastMoves.map { it.name }.toSet())
        assertEquals(setOf("Dragon Tail", "Mud Shot"),
            requireNotNull(knowledge.getPokemonByName("Garchomp")).fastMoves.map { it.name }.toSet())
        assertNull("One observed acoustic occurrence is insufficient", steps[0].snapshot?.identifiedMoveName)
        assertNull("Overlapping PCM cannot be the second occurrence", steps[1].snapshot?.identifiedMoveName)
        val first = steps[0].ranked.single { it.moveName == "Thunder Fang" }
        val overlap = steps[1].ranked.single { it.moveName == "Thunder Fang" }
        fun aligned(step: Step, fit: FastMoveSoundCandidate) = requireNotNull(step.input.monotonicTimeNanos) +
            fit.matchingOffsetNanos - fit.referenceOffsetNanos
        assertTrue("The selected windows must describe the same aligned occurrence",
            kotlin.math.abs(aligned(steps[0], first) - aligned(steps[1], overlap)) < 10_000_000L)
        val identities = steps.flatMap { it.output }.filter { (it.payload as? FastMoveIdentified)?.side == PLAYER }
        assertEquals(1, identities.size)
        val conclusion = identities.single()
        val identity = conclusion.payload as FastMoveIdentified
        assertEquals("Arcanine", identity.speciesName)
        assertEquals("Thunder Fang", identity.moveName)
        assertEquals("AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE", identity.basis)
        assertEquals(1_000_000_000L, identity.moveDurationNanos)
        val last = steps.last()
        val fit = last.ranked.single { it.moveName == "Thunder Fang" }
        assertEquals(requireNotNull(last.input.monotonicTimeNanos) + fit.matchingOffsetNanos,
            conclusion.observedArticle.monotonicTimeNanos)
        assertEquals(364_233_343_253_113L, conclusion.observedArticle.monotonicTimeNanos)
        assertEquals(last.input.perceivedAt + fit.matchingOffsetNanos / 1_000_000L,
            conclusion.observedArticle.perceivedAt)
        assertEquals(last.input.recordedAt, conclusion.observedArticle.recordedAt)
        assertEquals(last.input.id, conclusion.observedArticle.id)
        assertTrue(conclusion.predecessorIds.containsAll(listOf(steps[0].input.id, last.input.id,
            ArticleId("15e51566-211e-4b43-89ef-745d186c1712"), ArticleId("e9a3920a-4664-46c5-9371-4f4394ae360b"))))
        assertFalse("The overlapping observation must not substitute for independent support",
            conclusion.predecessorIds.contains(steps[1].input.id))
        assertTrue("Identity analysis must not manufacture attacks", steps.flatMap { it.output }.all { it.payload is FastMoveIdentified })
        assertTrue("Audio identity alone must not award energy", steps.all { it.energyAwards == 0 })
    }

    @Test fun `actual weak and QTE adjacent captured comparisons remain unconfirmed after adding Fire Fang`() = runBlocking {
        val matcher = matcher(arcaninePool)
        val ids = listOf("arcanine-weak", "arcanine-qte-adjacent")
        ids.forEach { id ->
            val ranked = matcher.measure(audio(id), arcaninePool)
            assertEquals(arcaninePool, ranked.map { it.moveName }.toSet())
            assertFalse("Weak or QTE-adjacent audio must not force a move: $id: $ranked", FastMoveReferenceMatcher.accepts(ranked))
        }
        val grade = manifest.objects("qteAdjacentContextArticles").single { it.text("sourceId") == "QTE_PROGRESS_TEXT_WITNESS" }
        assertEquals("GREAT!", grade.obj("payload").text("value"))
        val delta = samples.getValue("arcanine-qte-adjacent").obj("sourceAudioArticle").long("monotonicTimeNanos") -
            grade.long("monotonicTimeNanos")
        assertTrue("This is an actual nearby grade, not a synthetic charge start", delta in 0..1_000_000_000L)
        val steps = replay(matcher, ids)
        assertTrue(steps.all { it.snapshot?.identifiedMoveName == null })
        assertTrue(steps.all { it.energyAwards == 0 })
    }

    private data class Step(val input: RealityArticle, val ranked: List<FastMoveSoundCandidate>,
        val output: List<FastMoveCadenceDerivation>, val snapshot: FastMoveCandidateSnapshot?, val energyAwards: Int)

    private suspend fun replay(matcher: FastMoveReferenceMatcher, ids: List<String>): List<Step> {
        val engine = FastMoveCadenceInference(knowledge)
        val energy = FastMoveEnergyInference(knowledge)
        manifest.objects("arcanineContextArticles").forEach { row ->
            val p = row.obj("payload")
            val payload = when (p.text("kind")) {
                "match_started" -> MatchStarted
                "active_pokemon_species_witnessed" -> ActivePokemonSpeciesWitnessed(
                    ActivePokemonSide.valueOf(p.text("side")), p.text("speciesName"), p.number("speciesId"))
                // Match projects the separately retained canonical appearance into species testimony.
                "player_active_identity_resolved" -> ActivePokemonSpeciesWitnessed(PLAYER,
                    p.text("speciesName"), p.number("speciesId"), playerTeamSlot = p.number("teamSlot"),
                    appearanceStartedAtNanos = p.long("appearanceStartedAtNanos"))
                else -> error("Unexpected context: $p")
            }
            val input = envelope(row, payload)
            assertTrue(engine.accept(input).isEmpty())
            assertTrue(energy.accept(input).isEmpty())
        }
        return ids.map { id ->
            val sample = samples.getValue(id)
            val original = sample.obj("sourceMeasurementArticle")
            val p = original.obj("payload")
            val scopes = p.objects("referenceSearchScopes").map { s -> FastMoveSoundSearchScope(
                ActivePokemonSide.valueOf(s.text("side")), s.text("speciesName"), s.strings("moveNames"), s.strings("contextArticleIds")) }
            assertEquals(arcaninePool, scopes.flatMap { it.moveNames }.toSet())
            val ranked = matcher.measure(audio(id), arcaninePool)
            val payload = FastMoveSoundMeasured(
                audible = p.getValue("audible").jsonPrimitive.boolean,
                onsetOffsetNanos = p["onsetOffsetNanos"]?.jsonPrimitive?.longOrNull,
                soundDurationNanos = p["soundDurationNanos"]?.jsonPrimitive?.longOrNull,
                spectralCentroidHz = p["spectralCentroidHz"]?.jsonPrimitive?.floatOrNull,
                peakAmplitude = p.getValue("peakAmplitude").jsonPrimitive.float,
                attackingSide = null, soundOnsetMonotonicNanos = p["soundOnsetMonotonicNanos"]?.jsonPrimitive?.longOrNull,
                cueAttackingSide = null, analysisVersion = FastMoveReferenceMatcher.VERSION,
                referenceCandidates = ranked, referenceMatchAccepted = FastMoveReferenceMatcher.accepts(ranked), referenceSearchScopes = scopes)
            // A re-analysis is a new measurement that cites the unchanged historical one.
            val input = envelope(original, payload).copy(id = ArticleId(original.text("articleId") + ":pcm-fixture-rematch"),
                sourceId = SourceId(original.text("sourceId") + ":PCM_FIXTURE_REMATCH"),
                evidenceReferences = original.strings("evidenceReferences") + original.text("articleId"))
            val output = engine.accept(input)
            val awards = energy.accept(input) + output.flatMap { d -> energy.accept(d.observedArticle.copy(
                payload = d.payload, predecessorIds = d.predecessorIds, confidence = d.confidence)) }
            assertTrue(input.evidenceReferences.orEmpty().contains(sample.obj("sourceAudioArticle").text("articleId")))
            assertEquals(original.long("monotonicTimeNanos"), input.monotonicTimeNanos)
            assertEquals(original.long("perceivedAt"), input.perceivedAt)
            assertEquals(original.long("recordedAt"), input.recordedAt)
            Step(input, ranked, output, engine.candidateSnapshot(PLAYER), awards.size)
        }
    }

    private fun envelope(row: JsonObject, payload: TestimonyPayload) = RealityArticle(
        ArticleId(row.text("articleId")), row.long("perceivedAt"), row.long("recordedAt"), SourceId(row.text("sourceId")), payload,
        row.strings("predecessorIds").map(::ArticleId), row["confidence"]?.jsonPrimitive?.floatOrNull,
        row["sequenceNumber"]?.jsonPrimitive?.longOrNull, row.strings("evidenceReferences"),
        MatchId(row.text("matchId")), row.long("monotonicTimeNanos"))

    private fun matcher(allowed: Set<String>, includeFireFang: Boolean = true): FastMoveReferenceMatcher {
        val references = readJson(File(assets, "manifest.json")).objects("references")
            .filter { it.text("moveName") in allowed && (includeFireFang || it.text("moveName") != "Fire Fang") }
            .map { entry ->
                val bytes = File(assets, entry.text("file")).readBytes()
                assertEquals(entry.text("sha256"), sha(bytes))
                FastMoveSoundReference(entry.text("moveName"), sha(bytes), requireNotNull(WavPcm16.decode(bytes)))
            }
        return FastMoveReferenceMatcher(references)
    }

    private fun audio(id: String) = requireNotNull(WavPcm16.decode(File(fixtures, samples.getValue(id).text("file")).readBytes()))
    private fun readJson(file: File) = Json.parseToJsonElement(file.readText()).jsonObject
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long
    private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.obj(key: String) = getValue(key).jsonObject
    private fun JsonObject.objects(key: String) = getValue(key).jsonArray.map { it.jsonObject }
    private fun JsonObject.strings(key: String) = this[key]?.takeIf { it != JsonNull }?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    /** Use actual current species pools and turn/energy data rather than a shortened test pool. */
    private fun currentKnowledge(): PokemonKnowledge {
        val loader = GameMasterLoader { File("src/main/assets/gamemaster.json").readText() }
        fun domain(p: GameMasterPokemon): Pokemon {
            fun move(id: String, fast: Boolean): Move? = loader.getMove(id)?.let { source ->
                val type = runCatching { PokemonType.valueOf(source.type.uppercase()) }.getOrNull() ?: return@let null
                Move(source.name, type, source.power, if (fast) source.energyGain else kotlin.math.abs(source.energy),
                    fast, if (fast) source.turns else null)
            }
            return Pokemon(p.dex, p.speciesName, p.types.mapNotNull { runCatching { PokemonType.valueOf(it.uppercase()) }.getOrNull() },
                "", fastMoves = p.fastMoves.mapNotNull { move(it, true) }, chargedMoves = p.chargedMoves.mapNotNull { move(it, false) })
        }
        return object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = PokemonFormReference.byName(name, loader.allPokemon())?.let(::domain)
            override suspend fun getPokemonById(id: Int) = loader.getPokemonByDex(id)?.let(::domain)
            override suspend fun getAllSpeciesNames() = loader.allPokemonNames()
        }
    }
}
