package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion

/** A displaced name box must not identify the species already printed in our HUD. */
internal object SpeciesCaptureVisibility {
    fun canRead(region: AnchorRegion, overlay: AnchorRegion?): Boolean = overlay == null ||
        region.x >= overlay.x + overlay.width || region.x + region.width <= overlay.x ||
        region.y >= overlay.y + overlay.height || region.y + region.height <= overlay.y
}
