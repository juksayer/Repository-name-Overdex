package com.example.overdex.battle.replay

import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Explicit read-only external fixture; ordinary device test runs skip without the input argument. */
class ReplayEndingAudioDeviceTest {
    @Test fun recoverOriginalEndingWithPackagedReferences() = runBlocking<Unit> {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val name = InstrumentationRegistry.getArguments().getString("endingAudioFixture")
        assumeTrue(name != null)
        require(name == "ending-audio-fixture.zip")
        val context = instrument.targetContext
        val file = File(context.cacheDir, name!!)
        val before = file.readBytes()
        assertEquals("e12961a030758d39d6affbb1fcb5fe743941d5bfec32c66191f21a6f07497cb1", ReplayRetainedAudioRecovery.sha(before))
        val archive = ZipFile(file).use { zip -> MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText()) }
        val jsonBefore = MatchArchiveSerializer.serialize(archive)
        val gm = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(context.assets.open("gamemaster.json").bufferedReader().use { it.readText() })
        val moves = gm.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moves[id]?.let { Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
            if (fast) it.energyGain else kotlin.math.abs(it.energy), fast, if (fast) it.turns else null) }
        val names = MatchReplayModel.referencedSpeciesNames(archive)
        val pokemon = gm.pokemon.filter { it.speciesName in names }
        val fast = pokemon.associate { it.speciesName to it.fastMoves.mapNotNull { id -> move(id, true) } }
        val charged = pokemon.associate { it.speciesName to it.chargedMoves.mapNotNull { id -> move(id, false) } }
        val started = android.os.SystemClock.elapsedRealtime()
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, fast.values.flatten() + charged.values.flatten(), pokemon.map { it.dex }.toSet(),
            { file.inputStream() }, { context.assets.open(it).use { stream -> stream.readBytes() } })
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        val replay = MatchReplayModel(archive, pokemon.associate { it.speciesName to it.dex },
            fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged, audioRecovery = audio)
        val link = replay.finishingChargeAssociations.single { it.moveName == "Seed Bomb" }
        val scene = replay.sceneAt(link.presentationImpactAtNanos)
        assertTrue(scene.opponent!!.isFainted)
        assertEquals("Dragonair", scene.opponent.speciesName)
        assertEquals(1f, scene.chargedMoveActions.single { it.moveName == "Seed Bomb" }.progress, 0f)
        assertEquals(jsonBefore, MatchArchiveSerializer.serialize(archive))
        assertArrayEquals(before, file.readBytes())
        android.util.Log.i("ENDING_AUDIO_TRIAL", "recoveryMs=$elapsed charges=${audio.charges.size} cries=${audio.cries.size} referenceNames=${audio.referenceCandidateNames.size} association=${link.basis}")
    }
}
