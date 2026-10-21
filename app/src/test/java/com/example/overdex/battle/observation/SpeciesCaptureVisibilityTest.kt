package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import org.junit.Assert.*
import org.junit.Test

class SpeciesCaptureVisibilityTest {
    private val hud = AnchorRegion(.6f, .12f, .38f, .3f)
    @Test fun `badge name above the HUD stays readable and a displaced name box cannot read our label`() {
        assertTrue(SpeciesCaptureVisibility.canRead(AnchorRegion(.7f, .075f, .26f, .04f), hud))
        assertFalse(SpeciesCaptureVisibility.canRead(AnchorRegion(.7f, .18f, .26f, .04f), hud))
        assertFalse(SpeciesCaptureVisibility.canRead(AnchorRegion(.7f, .1f, .26f, .04f), hud))
        assertTrue(SpeciesCaptureVisibility.canRead(AnchorRegion(.02f, .18f, .3f, .04f), hud))
        assertTrue(SpeciesCaptureVisibility.canRead(AnchorRegion(.7f, .18f, .26f, .04f), null))
    }
}
