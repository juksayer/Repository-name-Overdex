package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.CompleteHpBarObserved
import kotlin.math.abs

/** UI hint only. Reuses complete outlines already measured by the HP reader. */
internal class ChargeHudCameraCue {
    private var frameAt = Long.MIN_VALUE
    private var player: CompleteHpBarObserved? = null
    private var opponent: CompleteHpBarObserved? = null
    private var anchorAt = Long.MIN_VALUE
    private var anchorPlayer: CompleteHpBarObserved? = null
    private var anchorOpponent: CompleteHpBarObserved? = null

    fun observe(bar: CompleteHpBarObserved, at: Long): Boolean {
        if (at < frameAt) return false
        if (at > frameAt) {
            frameAt = at
            player = null
            opponent = null
        }
        if (bar.side == ActivePokemonSide.PLAYER) player = bar else opponent = bar
        val p = player ?: return false
        val o = opponent ?: return false
        player = null
        opponent = null
        if (p.sourceWidth != o.sourceWidth || p.sourceHeight != o.sourceHeight ||
            !complete(p) || !complete(o)) return false
        val width = p.sourceWidth
        val previousP = anchorPlayer
        val previousO = anchorOpponent
        if (previousP != null && previousO != null && at - anchorAt in 1..2_000_000_000L &&
            sameBar(previousP, p) && sameBar(previousO, o) &&
            p.barLeft - previousP.barLeft >= width * .04f && o.barLeft - previousO.barLeft >= width * .04f) {
            anchorPlayer = null
            anchorOpponent = null
            return true
        }
        // Retain the ordinary split-field anchors across intermediate panning frames.
        if (p.barLeft in 0..(width * .11f).toInt() && o.barLeft in (width * .50f).toInt()..(width * .65f).toInt()) {
            if (previousP == null || previousO == null || at - anchorAt > 2_000_000_000L ||
                !sameBar(previousP, p) || !sameBar(previousO, o)) {
                anchorPlayer = p
                anchorOpponent = o
                anchorAt = at
            } else if (abs(p.barLeft - previousP.barLeft) <= width * .015f &&
                abs(o.barLeft - previousO.barLeft) <= width * .015f) {
                anchorAt = at
            }
        }
        return false
    }

    private fun complete(p: CompleteHpBarObserved) = p.status == "LIVE_COLOR" && p.liveColumns > 0 &&
        p.barLeft >= 0 && p.barRight < p.sourceWidth && p.barTop >= 0 && p.barBottom < p.sourceHeight &&
        p.barBottom > p.barTop && p.interiorColumns in 40..(p.barRight - p.barLeft) &&
        p.barRight - p.barLeft >= 6 * (p.barBottom - p.barTop)

    private fun sameBar(a: CompleteHpBarObserved, b: CompleteHpBarObserved) =
        a.sourceWidth == b.sourceWidth && a.sourceHeight == b.sourceHeight &&
            a.appearanceStartedAtNanos == b.appearanceStartedAtNanos &&
            abs((a.barRight - a.barLeft) - (b.barRight - b.barLeft)) <= 3
}
