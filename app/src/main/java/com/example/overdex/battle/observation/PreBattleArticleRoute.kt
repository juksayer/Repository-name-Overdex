package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Publisher-side routing, not another subscriber to the general article stream. */
internal class PreBattleArticleRoute {
    private val output = MutableSharedFlow<RealityArticle>(
        replay = 4096, extraBufferCapacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val articles = output.asSharedFlow()

    fun publish(article: RealityArticle) {
        if (accepts(article.payload)) output.tryEmit(article)
    }

    companion object {
        val cropNames = setOf(
            TeamSelectCropContracts.leagueBadge.cropName,
            TeamSelectCropContracts.leagueText.cropName,
            TeamSelectCropContracts.restrictions.cropName,
            TeamSelectCropContracts.useThisParty.cropName,
            TeamSelectCropContracts.playerRosterSlot1.cropName,
            TeamSelectCropContracts.playerRosterSlot2.cropName,
            TeamSelectCropContracts.playerRosterSlot3.cropName,
            TeamSelectCropContracts.playerRosterName1.cropName,
            TeamSelectCropContracts.playerRosterName2.cropName,
            TeamSelectCropContracts.playerRosterName3.cropName,
            BattleCropContracts.vsScreen.cropName,
        )

        fun accepts(payload: TestimonyPayload): Boolean = when (payload) {
            is CropCaptured -> payload.cropProvenance.cropName in cropNames
            TeamSelectPartyWitnessed, VsScreenWitnessed, MatchStarted,
            TeamSelectScanStartedByUser, ObservationRestartedByUser,
            ObservationSessionStoppedByUser, ScreenIgnoredByUser -> true
            is CountdownGlyphWitnessed, is MatchEnded -> true
            else -> false
        }
    }
}
