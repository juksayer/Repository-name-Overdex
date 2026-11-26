package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion

/** Presentation invalidation only; this never changes a resource count or a Timeline Article. */
internal data class RetainedBadgeContent(val revision: Long = 0, val changedAtNanos: Long = Long.MIN_VALUE)

/** Admit a badge with a readable upper section; the image mask excludes actual HUD artwork. */
internal fun retainedBadgeSource(badge: AnchorRegion, hud: AnchorRegion?): AnchorRegion? {
    if (SpeciesCaptureVisibility.canRead(badge, hud)) return badge
    val exposedHeight = requireNotNull(hud).y - badge.y
    // Score the uncovered upper face; the rendered HUD mask excludes any lower overlap.
    return badge.takeIf { exposedHeight >= badge.height * .6f }
}
