package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.CapturedVisualFrame
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real preserved screenshots: unready and ready buttons place their labels at different heights. */
class RecordedChargedButtonTextDeviceTest {
    private suspend fun read(file: String, contract: BattleCropContract): String {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("player_controls/oct19/$file.png").use(BitmapFactory::decodeStream)!!
        try {
            val frame = CapturedVisualFrame(bitmap, System.currentTimeMillis(), System.nanoTime(), displayWidth = 1080, displayHeight = 2400)
            val crop = requireNotNull(contract.resolve(BattleCalibration(), frame))
            try { return PlayerControlTextRecognizer.read(crop.bitmap) }
            finally { crop.bitmap.recycle() }
        } finally { bitmap.recycle() }
    }

    @Test fun leftNotVeryEffectiveAndRightSuperEffectiveRemainSeparateFromHpText() = runBlocking {
        val left = read("abomasnow", BattleCropContracts.playerChargeMoveEffectivenessLeft)
        assertEquals("Left button OCR: $left", FastMoveEffectiveness.NOT_VERY_EFFECTIVE,
            FastMoveEffectivenessTextResolver.resolve(left)?.effectiveness)
        val right = read("gourgeist", BattleCropContracts.playerChargeMoveEffectivenessRight)
        assertEquals("Right button OCR: $right", FastMoveEffectiveness.SUPER_EFFECTIVE,
            FastMoveEffectivenessTextResolver.resolve(right)?.effectiveness)
        assertNull(FastMoveEffectivenessTextResolver.resolve(read("abomasnow", BattleCropContracts.playerChargeMoveEffectivenessRight)))
        assertNull(FastMoveEffectivenessTextResolver.resolve(read("gourgeist", BattleCropContracts.playerChargeMoveEffectivenessLeft)))
    }

    @Test fun nameRegionsReadActualEquippedMoves() = runBlocking {
        for ((file, contract, expected) in listOf(
            Triple("abomasnow", BattleCropContracts.playerChargeMoveNameLeft, "Weather Ball (Ice)"),
            Triple("abomasnow", BattleCropContracts.playerChargeMoveNameRight, "Energy Ball"),
            Triple("gourgeist", BattleCropContracts.playerChargeMoveNameLeft, "Shadow Ball"),
            Triple("gourgeist", BattleCropContracts.playerChargeMoveNameRight, "Seed Bomb"),
            Triple("afternoon-abomasnow", BattleCropContracts.playerChargeMoveNameLeft, "Weather Ball (Ice)"),
            Triple("afternoon-abomasnow", BattleCropContracts.playerChargeMoveNameRight, "Energy Ball"),
            Triple("afternoon-camerupt", BattleCropContracts.playerChargeMoveNameLeft, "Rock Tomb"),
            Triple("afternoon-camerupt", BattleCropContracts.playerChargeMoveNameRight, "Earth Power"),
        )) {
            val text = read(file, contract)
            assertTrue("$file ${contract.cropName}: $text", MoveTextAliases.matchesButtonLabel(text, expected))
        }
    }

    @Test fun afternoonButtonMatchupLabelsDoNotClaimThatAnAttackHappened() = runBlocking {
        for (contract in listOf(BattleCropContracts.playerChargeMoveEffectivenessLeft,
            BattleCropContracts.playerChargeMoveEffectivenessRight)) {
            val text = read("afternoon-abomasnow", contract)
            assertEquals("${contract.cropName}: $text", FastMoveEffectiveness.NOT_VERY_EFFECTIVE,
                FastMoveEffectivenessTextResolver.resolve(text)?.effectiveness)
        }
        val text = read("afternoon-camerupt", BattleCropContracts.playerChargeMoveEffectivenessLeft)
        assertEquals("Rock Tomb matchup: $text", FastMoveEffectiveness.SUPER_EFFECTIVE,
            FastMoveEffectivenessTextResolver.resolve(text)?.effectiveness)
    }
}
