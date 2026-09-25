package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.OutOfBattleMenuWitnessed
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Accepts a menu only when its two independently preserved views agree in time. */
class PersistedOutOfBattleMenuWitness(
    private val store: FileCropArtifactStore
) : Observer {
    override val observerId = ObserverId("OUT_OF_BATTLE_MENU_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Out Of Battle Menu Witness"

    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var accepted = false
                var tabs: TimedArticle? = null
                var menu: TimedArticle? = null

                match.articles.collect { article ->
                    if (accepted) return@collect
                    val crop = article.payload as? CropCaptured ?: return@collect
                    val observedAt = article.monotonicTimeNanos ?: return@collect
                    when (crop.cropProvenance.cropName) {
                        BattleCropContracts.battlePartyTabs.cropName -> {
                            val bitmap = store.loadVerifiedPng(crop.artifact) ?: return@collect
                            val text = try {
                                AnnouncementRecognizer.recognize(bitmap).value.orEmpty().uppercase()
                            } finally {
                                bitmap.recycle()
                            }
                            if (text.contains("BATTLE") && text.contains("PARTY")) {
                                tabs = TimedArticle(article.id.value, observedAt)
                            }
                        }
                        BattleCropContracts.outOfBattleMenu.cropName -> {
                            menu = TimedArticle(article.id.value, observedAt)
                        }
                        else -> return@collect
                    }

                    val confirmedTabs = tabs ?: return@collect
                    val supportingMenu = menu ?: return@collect
                    if (abs(confirmedTabs.monotonicTimeNanos - supportingMenu.monotonicTimeNanos) > SAME_MENU_WINDOW_NANOS) {
                        return@collect
                    }

                    accepted = true
                    match.custody.submitTestimony(
                        SourceId(observerId.id),
                        OutOfBattleMenuWitnessed,
                        article.perceivedAt,
                        null,
                        listOf(confirmedTabs.articleId, supportingMenu.articleId),
                        observedAt
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
    }

    private data class TimedArticle(val articleId: String, val monotonicTimeNanos: Long)

    private companion object {
        const val SAME_MENU_WINDOW_NANOS = 2_000_000_000L
    }
}
