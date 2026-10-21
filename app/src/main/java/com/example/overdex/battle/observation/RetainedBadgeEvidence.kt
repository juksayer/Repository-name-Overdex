package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame

internal fun CapturedVisualFrame.canObserve(region: AnchorRegion): Boolean =
    SpeciesCaptureVisibility.canRead(region, retainedBadgeRegion)

internal fun CapturedVisualFrame.canObserve(bounds: BattleCropBounds): Boolean =
    canObserve(AnchorRegion(bounds.left.toFloat() / bitmap.width, bounds.top.toFloat() / bitmap.height,
        (bounds.right - bounds.left).toFloat() / bitmap.width, (bounds.bottom - bounds.top).toFloat() / bitmap.height))
