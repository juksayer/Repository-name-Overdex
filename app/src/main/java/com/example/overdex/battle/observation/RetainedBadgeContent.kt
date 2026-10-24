package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion

/** Presentation invalidation only; this never changes a resource count or a Timeline Article. */
internal data class RetainedBadgeContent(val revision: Long = 0, val changedAtNanos: Long = Long.MIN_VALUE)

/** Admit a badge with a readable upper section; the image mask excludes actual HUD artwork. */
internal fun retainedBadgeSource(badge: AnchorRegion, hud: AnchorRegion?): AnchorRegion? {
    if (SpeciesCaptureVisibility.canRead(badge, hud)) return badge
    val exposedHeight = requireNotNull(hud).y - badge.y
    // A lower-edge overlap is normal when the HUD joins the badge. A window
    // across its name/resource rows is not enough of a badge to retain safely.
    // The HUD has a concave upper edge. Its rectangular window includes native
    // ball/shield pixels in that opening; trimming at hud.y cuts those in half.
    return badge.takeIf { exposedHeight >= badge.height * .6f }
}
