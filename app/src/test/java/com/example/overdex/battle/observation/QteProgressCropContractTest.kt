package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QteProgressCropContractTest {
    @Test fun `progress capture uses its edited box independently of announcements and execution`() {
        val progress = AnchorRegion(.13f, .27f, .73f, .06f)
        val calibration = BattleCalibration(qteProgressTextRegion = progress)

        assertEquals(progress, BattleCropContracts.qteProgressText.region.regionIn(calibration))
        assertNotEquals(progress, BattleCropContracts.announcement.region.regionIn(calibration))
        assertNotEquals(progress, BattleCropContracts.chargeMoveExecution.region.regionIn(calibration))
        assertEquals("QteProgressTextCrop", BattleWitnessContracts.qteProgressTextCapture.crop.cropName)
        assertEquals(CropCaptured::class, BattleWitnessContracts.qteProgressTextCapture.testimonyType)
    }
}
