package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import org.junit.Assert.*
import org.junit.Test

class RetainedBadgeSourceTest {
    private val badge = AnchorRegion(.59f, .06f, .38f, .055f)

    @Test fun `unobstructed and adjacent HUD keep the full badge`() {
        assertEquals(badge, retainedBadgeSource(badge, null))
        assertEquals(badge, retainedBadgeSource(badge, AnchorRegion(.59f, .115f, .38f, .2f)))
        assertEquals(badge, retainedBadgeSource(badge, AnchorRegion(.1f, .06f, .2f, .2f)))
    }

    @Test fun `HUD at lower seam admits the full badge for curved masking`() {
        val hud = AnchorRegion(.59f, .10f, .38f, .2f)
        val source = requireNotNull(retainedBadgeSource(badge, hud))
        assertEquals(badge, source)
        assertTrue("The image mask must preserve native pixels below the rectangular seam",
            source.y + source.height > hud.y)
    }

    @Test fun `HUD covering the name or resource band cannot supply a retained badge`() {
        assertNull(retainedBadgeSource(badge, AnchorRegion(.59f, .07f, .38f, .2f)))
        assertNull(retainedBadgeSource(badge, badge))
    }
}
