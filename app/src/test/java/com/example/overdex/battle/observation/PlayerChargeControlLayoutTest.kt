package com.example.overdex.battle.observation

import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PlayerChargeControlLayoutTest {
    private val width = 660
    private val height = 250
    private fun field(vararg centers: Int): IntArray = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (centers.any { abs(hypot((x - it).toDouble(), (y - 140).toDouble()) - 80) < 2 }) -1
        else 0xff222244.toInt()
    }

    @Test fun `a lone center rim routes only one label after repeated geometric evidence`() {
        val pixels = field(330)
        val left = ChargeButtonFillHeightReader.measure(width, height, pixels, 0)
        val right = ChargeButtonFillHeightReader.measure(width, height, pixels, 1)
        val center = requireNotNull(ChargeButtonFillHeightReader.measureCentered(width, height, pixels))
        assertNull(left)
        assertNull(right)
        assertEquals(330.0, center.centerX.toDouble(), 2.0)
        val layout = PlayerChargeControlLayout()
        layout.observe(0L, width, height, left, right, center)
        assertNull(layout.centerAt(0L))
        layout.observe(500_000_000L, width, height, left, right, center)
        assertNotNull(layout.centerAt(500_000_000L))
        assertEquals(BattleCropContracts.playerChargeMoveNameCenter, PlayerChargeControlLayout.textCrop(1, false, true))
        assertEquals(BattleCropContracts.playerChargeMoveEffectivenessCenter, PlayerChargeControlLayout.textCrop(1, true, true))
        assertNull(PlayerChargeControlLayout.textCrop(2, false, true))
        assertNull(PlayerChargeControlLayout.textCrop(2, true, true))
    }

    @Test fun `two side rims never become a centered move even when a center ring is also present`() {
        val pixels = field(170, 330, 490)
        val left = requireNotNull(ChargeButtonFillHeightReader.measure(width, height, pixels, 0))
        val right = requireNotNull(ChargeButtonFillHeightReader.measure(width, height, pixels, 1))
        val center = requireNotNull(ChargeButtonFillHeightReader.measureCentered(width, height, pixels))
        val layout = PlayerChargeControlLayout()
        layout.observe(0L, width, height, left, right, center)
        layout.observe(500_000_000L, width, height, left, right, center)
        assertNull(layout.centerAt(500_000_000L))
        assertEquals(BattleCropContracts.playerChargeMoveNameLeft, PlayerChargeControlLayout.textCrop(1, false, false))
        assertEquals(BattleCropContracts.playerChargeMoveNameRight, PlayerChargeControlLayout.textCrop(2, false, false))
    }

    @Test fun `a single side rim clears center and stale moving or resized evidence cannot select it`() {
        val center = ChargeButtonFillHeightReader.Reading(330, 140, 80, null, "UNKNOWN_SEPARATOR")
        val layout = PlayerChargeControlLayout()
        layout.observe(0L, width, height, null, null, center)
        layout.observe(500_000_000L, width, height, null, null, center)
        assertNull(layout.centerAt(400_000_000L))
        assertNull(layout.centerAt(500_000_000L, width / 2, height / 2))
        assertNull(layout.centerAt(2_000_000_000L))
        layout.observe(1_000_000_000L, width, height, center.copy(centerX = 170), null, center)
        assertNull(layout.centerAt(1_000_000_000L))
        layout.observe(1_500_000_000L, width, height, null, null, center)
        layout.observe(2_000_000_000L, width, height, null, null, center.copy(centerY = 170))
        assertNull(layout.centerAt(2_000_000_000L))
        layout.reset()
        assertNull(layout.centerAt(2_000_000_000L))
    }

    @Test fun `center crop derives both adjusted labels without changing calibration`() {
        val left = AnchorRegion(.08f, .84f, .40f, .03f)
        val right = AnchorRegion(.54f, .85f, .34f, .03f)
        val calibration = BattleCalibration(playerChargeMoveNameLeftRegion = left,
            playerChargeMoveNameRightRegion = right)
        val region = BattleCropContracts.playerChargeMoveNameCenter.region.regionIn(calibration)
        assertEquals(.495f, region.x + region.width / 2f, .0001f)
        assertEquals(.40f, region.width, .0001f)
        assertEquals(.84f, region.y, .0001f)
        assertEquals(.04f, region.height, .0001f)
        assertEquals(left, calibration.playerChargeMoveNameLeftRegion)
        assertEquals(right, calibration.playerChargeMoveNameRightRegion)
    }

    @Test fun `center fill uses its measured circle and supplies no fabricated second control`() {
        val center = ChargeButtonFillHeightReader.Reading(330, 140, 80, null, "UNKNOWN_SEPARATOR")
        fun sample(boundary: Int) = PlayerChargeMoveEnergyFillSampler.measure(width, height,
            { x, y -> if (x in 310..350 && y >= boundary) 0xffaa88ff.toInt() else 0xff222244.toInt() }, center)
        val low = sample(165)
        val high = sample(140)
        assertEquals(1, low.size)
        assertTrue(high.single() > low.single() + .1f)
    }
}
