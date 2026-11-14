package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.observation.ActiveHpBarGeometry
import kotlin.math.abs
import kotlin.math.max

/** A changed camera lock is a new HP baseline, not a measured damage interval.
 * This qualifies only synthetic recovery from HP samples, never independent attacks. */
internal fun replayHpBaselineContinues(a: ArchivedActiveHpBarMeasured, b: ArchivedActiveHpBarMeasured): Boolean {
    if (a.side != b.side || a.sourceWidth != b.sourceWidth || a.sourceHeight != b.sourceHeight) return false
    val width = a.barRight - a.barLeft
    val height = a.barBottom - a.barTop
    if (!ActiveHpBarGeometry.matchesSize(b.barRight - b.barLeft, b.barBottom - b.barTop, width, height)) return false
    // Ordinary attack bobbing and end-cap jitter remain continuous. A large
    // translation requires a fresh baseline even when phase OCR was missed.
    // The recorded Camerupt attack bob spans 87 px on a 192 px bar; a
    // height-only tolerance rejected that real hit. Allow up to 60% of bar
    // width vertically (115 px there), below the paired 146 px entry jump.
    // This conservative replay tolerance is not a general camera classifier.
    return abs(b.barLeft - a.barLeft) <= max(4, width / 4) &&
        abs(b.barTop - a.barTop) <= max(max(4, height * 4).toFloat(), width * .60f)
}
