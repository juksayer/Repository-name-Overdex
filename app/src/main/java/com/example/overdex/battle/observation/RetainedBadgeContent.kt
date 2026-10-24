package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion

/** Presentation invalidation only; this never changes a resource count or a Timeline Article. */
internal data class RetainedBadgeContent(val revision: Long = 0, val changedAtNanos: Long = Long.MIN_VALUE)

/** Keep the native pixels above the HUD seam, without copying any of our own artwork. */
internal fun retainedBadgeSource(badge: AnchorRegion, hud: AnchorRegion?): AnchorRegion? {
    if (SpeciesCaptureVisibility.canRead(badge, hud)) return badge
    val exposedHeight = requireNotNull(hud).y - badge.y
    // A lower-edge overlap is normal when the HUD joins the badge. A window
    // across its name/resource rows is not enough of a badge to retain safely.
    return badge.copy(height = exposedHeight).takeIf { exposedHeight >= badge.height * .6f }
}
