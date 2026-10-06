package com.example.overdex.battle.observation

import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.OutOfBattleMenuWitnessed
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.custody.VsScreenWitnessed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The user-initiated Droidball assistance session surrounding a possible battle. */
class DroidballSession(val match: Match) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _phase = MutableStateFlow(DroidballSessionPhase.NAVIGATION_IDLE)
    val phase = _phase.asStateFlow()
    private val battleSurfaceConfirmation = BattleSurfaceConfirmation()
    private val _battleSurfaceEstablished = MutableStateFlow(false)
    /**
     * True after direct pre-battle evidence or corroborating live-HUD witnesses establish
     * that Pokémon GO is showing a battle surface. It never starts the Match clock.
     */
    val battleSurfaceEstablished = _battleSurfaceEstablished.asStateFlow()
    /** True once this session's current Match has produced an end result. */
    @Volatile var hasCompletedMatch: Boolean = false
        private set
    private val _firstLiveCombatArticle = MutableStateFlow<RealityArticle?>(null)
    /** The first preserved in-battle HUD evidence; it retires countdown work. */
    val firstLiveCombatArticle = _firstLiveCombatArticle.asStateFlow()

    init {
        scope.launch {
            match.matchStarted.collect { article -> beginBattle(article) }
        }
        scope.launch {
            match.articles.collect { article ->
                when (article.payload) {
                    is com.example.overdex.battle.custody.TeamSelectPartyWitnessed -> {
                        if (_phase.value == DroidballSessionPhase.SEEKING_TEAM_SELECT) {
                            _phase.value = DroidballSessionPhase.TEAM_SELECT_ACTIVE
                        }
                    }
                    is MatchEnded -> {
                        hasCompletedMatch = true
                        battleSurfaceConfirmation.reset()
                        _battleSurfaceEstablished.value = false
                        _phase.value = DroidballSessionPhase.RESULT
                    }
                    is OutOfBattleMenuWitnessed -> {
                        battleSurfaceConfirmation.reset()
                        _battleSurfaceEstablished.value = false
                        _phase.value = DroidballSessionPhase.NAVIGATION_IDLE
                    }
                    else -> if (battleSurfaceConfirmation.observe(article.payload)) {
                        _battleSurfaceEstablished.value = true
                    }
                }
            }
        }
    }

    fun beginCalibration() {
        if (_phase.value in setOf(DroidballSessionPhase.NAVIGATION_IDLE, DroidballSessionPhase.SEEKING_TEAM_SELECT, DroidballSessionPhase.TEAM_SELECT_ACTIVE)) {
            _phase.value = DroidballSessionPhase.CALIBRATING
        }
    }

    fun beginTeamSelectScan() {
        if (_phase.value in setOf(DroidballSessionPhase.BATTLE_ACTIVE, DroidballSessionPhase.RESULT, DroidballSessionPhase.ENDED)) return
        battleSurfaceConfirmation.reset()
        _battleSurfaceEstablished.value = false
        _phase.value = DroidballSessionPhase.SEEKING_TEAM_SELECT
    }

    fun ignoreCurrentScreen(): Boolean {
        if (_phase.value in setOf(DroidballSessionPhase.BATTLE_ACTIVE, DroidballSessionPhase.RESULT, DroidballSessionPhase.ENDED)) return false
        battleSurfaceConfirmation.reset()
        _battleSurfaceEstablished.value = false
        _phase.value = DroidballSessionPhase.NAVIGATION_IDLE
        return true
    }

    /** Starts another pre-battle attempt without replacing this Match record. */
    fun restartObservation(): Boolean {
        if (_phase.value in setOf(DroidballSessionPhase.BATTLE_ACTIVE, DroidballSessionPhase.RESULT, DroidballSessionPhase.ENDED)) return false
        battleSurfaceConfirmation.reset()
        _battleSurfaceEstablished.value = false
        _firstLiveCombatArticle.value = null
        match.restartPreBattleObservationAttempt()
        _phase.value = DroidballSessionPhase.SEEKING_TEAM_SELECT
        return true
    }

    fun armCountdown() {
        if (_phase.value in setOf(DroidballSessionPhase.SEEKING_TEAM_SELECT, DroidballSessionPhase.TEAM_SELECT_ACTIVE, DroidballSessionPhase.CALIBRATING)) {
            _phase.value = DroidballSessionPhase.COUNTDOWN
        }
    }

    private fun beginBattle(article: RealityArticle) {
        requireNotNull(article.monotonicTimeNanos) {
            "MatchStarted article must preserve GO monotonic time"
        }
        _phase.value = DroidballSessionPhase.BATTLE_ACTIVE
    }

    fun recordFirstLiveCombat(article: RealityArticle) {
        // An active HP surface is already in-battle evidence even when GO OCR
        // was missed. Retire countdown work without inventing a MatchStarted
        // clock boundary.
        if (_phase.value in setOf(DroidballSessionPhase.COUNTDOWN, DroidballSessionPhase.BATTLE_ACTIVE) &&
            _firstLiveCombatArticle.value == null
        ) {
            _firstLiveCombatArticle.value = article
        }
    }

    fun end() {
        _phase.value = DroidballSessionPhase.ENDED
        scope.cancel("Droidball session ended")
    }
}

enum class DroidballSessionPhase {
    NAVIGATION_IDLE,
    SEEKING_TEAM_SELECT,
    TEAM_SELECT_ACTIVE,
    CALIBRATING,
    COUNTDOWN,
    BATTLE_ACTIVE,
    RESULT,
    ENDED
}

/**
 * Confirms that the screen has entered the battle surface without pretending that
 * the Match clock has started. VS and accepted countdown glyphs are direct evidence.
 * If they are missed, independently witnessed active species and active type icons
 * together establish the same surface.
 */
internal class BattleSurfaceConfirmation {
    private var speciesWitnessed = false
    private var typeWitnessed = false
    private var established = false

    fun observe(payload: TestimonyPayload): Boolean {
        when (payload) {
            VsScreenWitnessed -> established = true
            is CountdownGlyphWitnessed -> established = true
            ChargeMoveUsedAnnounced, GetReadyWitnessed -> established = true
            is ActivePokemonSpeciesWitnessed -> speciesWitnessed = true
            is ActivePokemonTypesWitnessed -> typeWitnessed = true
        }
        if (speciesWitnessed && typeWitnessed) established = true
        return established
    }

    fun reset() {
        speciesWitnessed = false
        typeWitnessed = false
        established = false
    }
}
