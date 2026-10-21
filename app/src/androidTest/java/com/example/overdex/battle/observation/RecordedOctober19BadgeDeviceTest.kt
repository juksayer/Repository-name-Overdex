package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Actual October 19 frames, saved assignments and recorded HUD footprint. No profile writes. */
class RecordedOctober19BadgeDeviceTest {
    private val calibration = BattleCalibration(
        playerSpeciesNameRegion = AnchorRegion(.032738097f, .09930009f, .23214285f, .021433357f),
        opponentSpeciesNameRegion = AnchorRegion(.73363096f, .09930009f, .23363096f, .021433355f),
        playerPokeBallsRegion = AnchorRegion(.02827381f, .12073343f, .15773809f, .021433355f),
        opponentPokeBallsRegion = AnchorRegion(.8125f, .12073344f, .15625f, .021433357f),
        playerShieldsRegion = AnchorRegion(.2127976f, .12073344f, .08928572f, .021433355f),
        opponentShieldsRegion = AnchorRegion(.6979167f, .12073344f, .08928572f, .021433355f),
        playerTeamInfoRegion = AnchorRegion(.017857144f, .097602166f, .38934523f, .050234426f),
        opponentTeamInfoRegion = AnchorRegion(.59375f, .09260216f, .38339287f, .050234426f),
    )
    private val hud = AnchorRegion(401.95557f / 672, 144.32333f / 1493, 258.22223f / 672, 240f / 1493)
    private fun load(name: String): Bitmap = InstrumentationRegistry.getInstrumentation().context.assets
        .open("species/oct19/$name.png").use(BitmapFactory::decodeStream)!!
    private fun frame(bitmap: Bitmap) = CapturedVisualFrame(bitmap, System.currentTimeMillis(), System.nanoTime(),
        displayWidth = 1080, displayHeight = 2400, overlayRegion = hud)

    @Test fun misplacedResourcesRecoverNativeBallsShieldsAndCompleteBadge() {
        for ((name, balls) in listOf("sneasel" to 3, "sealeo" to 2, "gesture_sealeo" to 2)) {
            val bitmap = load(name)
            try {
                val source = frame(bitmap)
                val original = calibration.copy()
                assertFalse(SpeciesCaptureVisibility.canRead(calibration.opponentSpeciesNameRegion, hud))
                val located = SpeciesBadgeTextLocator.locate(bitmap, calibration.opponentSpeciesNameRegion, listOf(hud))
                assertTrue("$name name must clear HUD", SpeciesCaptureVisibility.canRead(located, hud))
                assertEquals(calibration.opponentSpeciesNameRegion.x, located.x)
                assertEquals(calibration.opponentSpeciesNameRegion.width, located.width)
                for ((contract, expected) in listOf(
                    BattleCropContracts.opponentPokeBalls to balls,
                    BattleCropContracts.playerPokeBalls to 3,
                    BattleCropContracts.opponentShields to 2,
                    BattleCropContracts.playerShields to 2,
                )) {
                    val resolved = requireNotNull(contract.resolve(calibration, source))
                    try {
                        val actual = if (contract.region in setOf(BattleRegionId.PLAYER_POKE_BALLS, BattleRegionId.OPPONENT_POKE_BALLS))
                            OpponentBattleResourceCounter.pokeBallCount(resolved.bitmap)
                        else OpponentBattleResourceCounter.shieldCount(resolved.bitmap)
                        assertEquals("$name ${contract.cropName} at ${resolved.provenance}", expected, actual)
                        assertTrue("Record the actual source row", resolved.provenance.bounds.top < 150)
                    } finally { resolved.bitmap.recycle() }
                }
                for (region in listOf(BattleRegionId.PLAYER_TEAM_INFO, BattleRegionId.OPPONENT_TEAM_INFO)) {
                    val area = TeamBadgeCropLocator.locate(region, calibration, source)
                    assertTrue("$name $region must be native", TeamBadgeCropLocator.nativeBadgePresent(source, area))
                    assertTrue("$name $region must include the name row", area.y * bitmap.height < 112)
                }
                assertEquals("Runtime recovery must not rewrite any saved box", original, calibration)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun oldMisplacedRetainedCopyBlocksBallEvidenceButNotTheNativeNameAboveIt() {
        val bitmap = load("vaporeon")
        try {
            // This recording contains the bug itself: an old three-ball image
            // covers the lower half of the real, now-dark balls. Never count
            // those composite pixels as three living opponents.
            val source = frame(bitmap).copy(retainedBadgeRegion = calibration.opponentTeamInfoRegion)
            val located = SpeciesBadgeTextLocator.locate(bitmap, calibration.opponentSpeciesNameRegion,
                listOf(hud, calibration.opponentTeamInfoRegion))
            assertTrue(source.canObserve(located))
            assertTrue(SpeciesCaptureVisibility.canRead(located, hud))
            assertNull(BattleCropContracts.opponentPokeBalls.resolve(calibration, source))
            assertNull(BattleCropContracts.opponentShields.resolve(calibration, source))
            assertFalse(TeamBadgeCropLocator.nativeBadgePresent(source, calibration.opponentTeamInfoRegion))
            val player = TeamBadgeCropLocator.locate(BattleRegionId.PLAYER_TEAM_INFO, calibration, source)
            assertTrue("Native player badge can release the stale copy", TeamBadgeCropLocator.nativeBadgePresent(source, player))
        } finally { bitmap.recycle() }
    }

    @Test fun correctIndependentBallBoxWinsAndRetainedPresentationIsNeverRead() {
        val bitmap = load("vaporeon")
        try {
            val source = frame(bitmap)
            val corrected = TeamBadgeCropLocator.locate(BattleRegionId.OPPONENT_POKE_BALLS, calibration, source)
            val independentlyAligned = calibration.copy(opponentPokeBallsRegion = corrected)
            assertEquals(corrected, TeamBadgeCropLocator.locate(BattleRegionId.OPPONENT_POKE_BALLS, independentlyAligned, source))
            val nativeBadge = TeamBadgeCropLocator.locate(BattleRegionId.OPPONENT_TEAM_INFO, calibration, source)
            val retained = source.copy(retainedBadgeRegion = nativeBadge)
            assertFalse(TeamBadgeCropLocator.nativeBadgePresent(retained, nativeBadge))
            for (crop in listOf(BattleCropContracts.opponentPokeBalls, BattleCropContracts.opponentShields)) {
                val assigned = TeamBadgeCropLocator.locate(crop.region, calibration, source)
                val blockedCalibration = when (crop.region) {
                    BattleRegionId.OPPONENT_POKE_BALLS -> calibration.copy(opponentPokeBallsRegion = assigned)
                    else -> calibration.copy(opponentShieldsRegion = assigned)
                }
                assertNull("${crop.cropName} must reject our retained image", crop.resolve(blockedCalibration, retained))
            }
            val name = SpeciesBadgeTextLocator.locate(bitmap, calibration.opponentSpeciesNameRegion, listOf(hud, nativeBadge))
            assertFalse("Own retained name must not reopen OCR", SpeciesCaptureVisibility.canRead(name, hud) && retained.canObserve(name))
        } finally { bitmap.recycle() }
    }

    @Test fun hudNoLongerLocksIdentityToFirstOpponent() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val frames = listOf("sneasel", "sealeo", "vaporeon").map(::load)
        val current = AtomicReference(frames.first())
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                while (true) { onVisualData(current.get()); delay(40) }
            }
            override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
                while (true) { onFrame(frame(current.get())); delay(40) }
            }
        }
        val knowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val timeline = InMemoryRealityTimeline()
        val match = Match(matchId = "OCT19_HUD_OCCLUSION_REGRESSION", custody = InMemoryTestimonyCustody(),
            realityTimeline = timeline, pokemonKnowledge = knowledge)
        val root = File(context.cacheDir, "oct19-species-${System.nanoTime()}").apply { mkdirs() }
        val store = FileCropArtifactStore(root)
        val player = LiveOverlaySpeciesPipeline.player(input, calibration, store) { true }
        val opponent = LiveOverlaySpeciesPipeline.opponent(input, calibration, store) { true }
        try {
            DroidballOverlayPresentation.clearOpponentSpecies()
            SpeciesNameRecognizer.warmUp()
            player.start(match); opponent.start(match)
            for ((index, expected) in listOf("Sneasel", "Sealeo", "Vaporeon").withIndex()) {
                current.set(frames[index])
                val start = System.nanoTime()
                for (side in ActivePokemonSide.entries) match.speciesChecks.request(side, "SWITCH", null)
                withTimeout(12_000) {
                    while (timeline.getArticles().none {
                        val payload = it.payload as? ActivePokemonSpeciesWitnessed
                        payload?.side == ActivePokemonSide.OPPONENT && payload.speciesName == expected
                    }) delay(25)
                    while (DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName != expected) delay(25)
                }
                val moves = requireNotNull(DroidballOverlayPresentation.activeOpponentMovePossibilities.value)
                assertTrue(moves.fastMoves.isNotEmpty()); assertTrue(moves.chargedMoves.isNotEmpty())
                Log.i("Oct19BadgeRegression", "$expected identity and HUD moves in ${(System.nanoTime() - start) / 1_000_000} ms")
                delay(200)
            }
            val articles = timeline.getArticles()
            for (identity in articles.filter { it.payload is ActivePokemonSpeciesWitnessed }) {
                val species = identity.payload as ActivePokemonSpeciesWitnessed
                assertEquals(if (species.speciesName in listOf("Abomasnow", "Camerupt")) ActivePokemonSide.PLAYER else ActivePokemonSide.OPPONENT, species.side)
                assertTrue(identity.evidenceReferences.orEmpty().any { id -> articles.any { it.id.value == id && it.payload is CropCaptured } })
            }
            assertEquals(setOf("Sneasel", "Sealeo", "Vaporeon"),
                DroidballOverlayPresentation.opponentSpecies.value.map { it.speciesName }.toSet())
        } finally {
            player.stop(); opponent.stop(); match.release()
            delay(200)
            frames.forEach(Bitmap::recycle); root.deleteRecursively()
            DroidballOverlayPresentation.clearOpponentSpecies()
        }
    }
}
