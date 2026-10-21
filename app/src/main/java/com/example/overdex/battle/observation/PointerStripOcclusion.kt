package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion

/** The coordinate OCR crop covers only P/X/Y, but Android's strip spans the screen. */
object PointerStripOcclusion {
    fun covers(crop: AnchorRegion, coordinateCrop: AnchorRegion, enabled: Boolean): Boolean =
        enabled && crop.height > 0f && coordinateCrop.height > 0f &&
            crop.y < coordinateCrop.y + coordinateCrop.height &&
            crop.y + crop.height > coordinateCrop.y
}
