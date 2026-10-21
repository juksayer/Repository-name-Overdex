package com.example.overdex.ui

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import com.example.overdex.model.Move
import com.example.overdex.ui.screens.observatory.MatchReplayScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic timing fixture: tests production rendering and reference-type wiring, not live recognition. */
class ReplayChargeTypeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun growsForwardOfPerformerThenThrowsSameTypeAcrossFieldAndRestoresOnScrub() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cursor = mutableLongStateOf(1_500_000_000L)
        val archive = fixture()
        compose.setContent {
            MatchReplayScreen(archive, onBack = {}, initialCursorNanos = cursor.longValue,
                resolveChargedMoveType = { _, name ->
                    when (name) { "Lunge" -> PokemonType.BUG; "Flamethrower" -> PokemonType.FIRE; else -> null }
                },
                resolveChargedMoves = { species ->
                    if (species == "Galvantula") listOf(Move("Lunge", PokemonType.BUG, 60, 45, false)) else emptyList()
                })
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "PLAYER: Lunge; BUG; QTE level 0")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val player = compose.onNodeWithContentDescription("Galvantula").fetchSemanticsNode().boundsInRoot
        val opponent = compose.onNodeWithContentDescription("Combusken").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithContentDescription("Charging move").fetchSemanticsNode().boundsInRoot
        assertTrue(start.center.x < opponent.center.x)
        assertTrue(start.center.x > player.center.x)
        assertTrue(start.center.y < player.center.y)
        assertTrue(start.overlaps(player))

        fun show(at: Long, name: String) {
            compose.runOnIdle { cursor.longValue = at }
            compose.waitForIdle()
            File(context.cacheDir, "qte-type-$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        show(3_000_000_000L, "nice")
        val nice = compose.onNodeWithContentDescription("Charging move").fetchSemanticsNode().boundsInRoot
        val niceSprite = compose.onNodeWithContentDescription("Galvantula").fetchSemanticsNode().boundsInRoot
        show(7_000_000_000L, "excellent")
        val excellent = compose.onNodeWithContentDescription("Charging move").fetchSemanticsNode().boundsInRoot
        val excellentSprite = compose.onNodeWithContentDescription("Galvantula").fetchSemanticsNode().boundsInRoot
        assertTrue(excellent.width > nice.width)
        assertEquals("Icon keeps its forward offset while growing", nice.center.x - niceSprite.center.x,
            excellent.center.x - excellentSprite.center.x, 1f)
        assertEquals("Icon and sprite rise together", excellentSprite.center.y - niceSprite.center.y,
            excellent.center.y - nice.center.y, 1f)
        compose.onNodeWithText("CHARGING").assertDoesNotExist()
        show(9_600_000_000L, "player-flight")
        val thrown = compose.onNodeWithContentDescription("Charged move projectile").fetchSemanticsNode()
        assertTrue(thrown.boundsInRoot.center.x > start.center.x)
        assertTrue(thrown.config[SemanticsProperties.StateDescription].contains("BUG; QTE level 3"))

        show(19_000_000_000L, "opponent-charge")
        val opponentOrigin = compose.onNodeWithContentDescription("Charging move").fetchSemanticsNode().boundsInRoot
        val raisedOpponent = compose.onNodeWithContentDescription("Combusken").fetchSemanticsNode().boundsInRoot
        assertTrue(opponentOrigin.center.x > player.center.x)
        assertTrue(opponentOrigin.center.x < raisedOpponent.center.x)
        assertTrue(opponentOrigin.center.y < raisedOpponent.center.y)
        assertTrue(opponentOrigin.overlaps(raisedOpponent))
        show(21_600_000_000L, "opponent-flight")
        val opponentThrow = compose.onNodeWithContentDescription("Charged move projectile").fetchSemanticsNode()
        assertTrue(opponentThrow.boundsInRoot.center.x < opponentOrigin.center.x)
        assertTrue(opponentThrow.config[SemanticsProperties.StateDescription].contains("FIRE; QTE level 0"))

        show(3_000_000_000L, "reverse-nice")
        val restored = compose.onNodeWithContentDescription("Charging move").fetchSemanticsNode()
        assertEquals(nice.width, restored.boundsInRoot.width, 0.1f)
        assertTrue(restored.config[SemanticsProperties.StateDescription].endsWith("QTE level 1"))
    }

    @Test fun recordedChargesUsePhoneReferenceTypesAndOnlyOneIcon() = checkRecordedCharges(
        "repeated-charge-readings-20261013.json", listOf(
            Triple(16_765_969_463L, "OPPONENT: Foul Play; DARK", "Sneasel"),
            Triple(61_310_898_466L, "PLAYER: Brutal Swing; DARK", "Turtonator"),
            Triple(80_670_552_928L, "PLAYER: Brutal Swing; DARK", "Turtonator"),
            Triple(107_729_776_930L, "PLAYER: Rock Tomb; ROCK", "Camerupt")))

    @Test fun recordedWeatherBallUsesPhoneIceReferenceDuringChargeFlightAndReverseScrub() = checkRecordedCharges(
        "abomasnow-weather-ball-20261013.json", listOf(
            Triple(14_851_745_771L, "PLAYER: Weather Ball (Ice); ICE", "Abomasnow"),
            Triple(26_021_876_848L, "OPPONENT: Ice Punch; ICE", "Sneasel"),
            Triple(48_994_490_157L, "PLAYER: Energy Ball; GRASS", "Abomasnow"),
            Triple(68_115_814_697L, "PLAYER: Earth Power; GROUND", "Camerupt")))

    private fun checkRecordedCharges(fileName: String, cases: List<Triple<Long, String, String>>) = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchiveSerializer.deserialize(File(context.cacheDir, fileName).readText())
        val repository = com.example.overdex.data.PokemonRepository(
            com.example.overdex.data.local.PokedexDatabase.getDatabase(context).pokemonDao(),
            com.example.overdex.data.LocalSpriteProvider(context.assets), com.example.overdex.data.GameMasterLoader(context))
        val cursor = mutableLongStateOf(cases.first().first + 500_000_000L)
        compose.setContent {
            MatchReplayScreen(archive, onBack = {}, initialCursorNanos = cursor.longValue,
                resolveSpeciesId = { repository.getPokemonByName(it)?.id },
                resolveChargedMoves = { repository.getPokemonByName(it)?.chargedMoves.orEmpty() },
                resolveChargedMoveType = { species, name ->
                    repository.getPokemonByName(species)?.chargedMoves?.firstOrNull { it.name == name }?.type
                })
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(SemanticsMatcher("Recorded move type loaded") {
                it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith(cases.first().second) == true
            }).fetchSemanticsNodes().isNotEmpty()
        }
        for ((index, case) in cases.withIndex()) {
            val (at, label, species) = case
            fun check(offset: Long, description: String) {
                compose.runOnIdle { cursor.longValue = at + offset }
                compose.waitForIdle()
                compose.onAllNodesWithContentDescription("Charging move").assertCountEquals(if (description == "Charging move") 1 else 0)
                compose.onAllNodesWithContentDescription("Charged move projectile").assertCountEquals(if (description == "Charging move") 0 else 1)
                val icon = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode()
                assertTrue(icon.config[SemanticsProperties.StateDescription].startsWith(label))
                if (description == "Charging move") {
                    val actor = compose.onNodeWithContentDescription(species).fetchSemanticsNode()
                    val forward = icon.boundsInRoot.center.x - actor.boundsInRoot.center.x
                    assertTrue(if (label.startsWith("PLAYER")) forward > 0 else forward < 0)
                    assertTrue(icon.boundsInRoot.overlaps(actor.boundsInRoot))
                    assertTrue(icon.boundsInRoot.center.y < actor.boundsInRoot.center.y)
                }
                File(context.cacheDir, "${fileName.removeSuffix(".json")}-$index-$offset.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            check(500_000_000L, "Charging move")
            // This recorded Energy Ball is followed by Camerupt entering after
            // 1.259 s; its throw must finish before the new sprite appears.
            val flightOffset = if (fileName.startsWith("abomasnow-") && index == 2) 1_100_000_000L else 1_600_000_000L
            check(flightOffset, "Charged move projectile")
            check(500_000_000L, "Charging move") // reverse scrub preserves type/attachment
        }
    }

    private fun fixture() = MatchArchive(matchId = "qte-visual-test", articles = listOf(
        at("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Galvantula", 596), 0),
        at("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Combusken", 256), 0),
        at("ready", ArchivedGetReadyWitnessed, 1),
        at("nice", pulse(), 2), at("great", pulse(), 4), at("excellent", pulse(), 6),
        at("pattern", ArchivedChargeMoveQteVibrationPatternInferred("PLAYER", 3, 4_000_000_000L, "TEST"), 2)
            .copy(predecessorIds = listOf("nice", "great", "excellent")),
        // Named raw text, deliberately no energy article: production reference
        // wiring must still attach the Bug icon to Galvantula throughout QTE.
        at("lunge", ArchivedRawText("Galvantula used Lunge!"), 8).copy(sourceId = "ANNOUNCEMENT_WITNESS"),
        at("used", ArchivedChargeMoveUsedAnnounced, 8),
        at("incoming", ArchivedAttackIncoming, 12),
        at("fire", ArchivedChargedMoveEnergySpent("OPPONENT", "Combusken", "Flamethrower", 55, "TEST"), 20),
        at("end", ArchivedMatchEnded("WIN"), 23),
    ))
    private fun pulse() = ArchivedDeviceMotionPulseMeasured(36_000_000L, 1.2f, 0.7f, 8, "LINEAR_ACCELERATION")
    private fun at(id: String, payload: ArchivedTestimonyPayload, second: Long) = ArchivedRealityArticle(
        id, "qte-visual-test", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), second * 1_000_000_000L)
}
