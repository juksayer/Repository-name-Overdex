package com.example.overdex.battle.observation

import com.example.overdex.model.PokemonType
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.forecast.OpponentEnergyEstimate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Presentation-only state for the service-owned Droidball overlay. */
enum class DroidballOverlayMode {
    PRE_BATTLE,
    SEEKING_TEAM_SELECT,
    TEAM_SELECT,
    CALIBRATING,
    /** Display-only acknowledgement; the requested mode remains BATTLE_HUD. */
    BATTLE_READY,
    /** The Droidball Battle HUD is open; GO has not necessarily started the Match yet. */
    BATTLE_HUD,
    BATTLE_LIVE,
    RESULT
}

/** Acknowledge the request while native badge evidence still gates the battle layout. */
internal fun battleOverlayDisplayMode(mode: DroidballOverlayMode, badgeSeen: Boolean): DroidballOverlayMode =
    if (!badgeSeen && mode in setOf(DroidballOverlayMode.BATTLE_HUD, DroidballOverlayMode.BATTLE_LIVE))
        DroidballOverlayMode.BATTLE_READY else mode

data class ObservedOpponentSpecies(
    val speciesName: String,
    val speciesId: Int?,
    val isFainted: Boolean = false,
    /** Audio may populate the HUD immediately while visual witnesses catch up. */
    val isProvisional: Boolean = false,
    val observedAtNanos: Long? = null,
)

data class OpponentMovePossibilities(
    val speciesName: String,
    val fastMoves: List<OverlayMovePossibility>,
    val chargedMoves: List<OverlayMovePossibility>
)

/** Last witnessed counts, retained while the game's own badge is absent. */
data class HudOpponentResources(val pokeBalls: Int? = null, val shields: Int? = null)

data class OverlayMovePossibility(
    val name: String,
    val type: PokemonType,
    /** Damage multiplier from this move's type against the currently observed player types. */
    val effectivenessMultiplier: Double,
    val isConfirmed: Boolean = false,
    val energy: Int? = null,
    /** Repeated weak audio favors this candidate, without confirmation or energy attribution. */
    val isAudioFavored: Boolean = false,
) {
    val hazardous: Boolean get() = effectivenessMultiplier > 1.0
    /** The increase over neutral damage, for a compact live HUD warning. */
    val damageIncreasePercent: Int get() = ((effectivenessMultiplier - 1.0) * 100.0).toInt()
}

object DroidballOverlayPresentation {
    private val _opponentBadgeContent = MutableStateFlow(RetainedBadgeContent())
    internal val opponentBadgeContent = _opponentBadgeContent.asStateFlow()

    private fun invalidateOpponentBadge(at: Long) {
        val old = _opponentBadgeContent.value
        _opponentBadgeContent.value = RetainedBadgeContent(old.revision + 1, maxOf(old.changedAtNanos, at))
    }

    private val _opponentResources = MutableStateFlow(HudOpponentResources())
    val opponentResources = _opponentResources.asStateFlow()
    private val resourceTimes = mutableMapOf<OpponentBattleResource, Long>()
    private val _opponentEnergyEstimate = MutableStateFlow<OpponentEnergyEstimate?>(null)
    val opponentEnergyEstimate = _opponentEnergyEstimate.asStateFlow()
    private var energyForecastMatchId: String? = null
    private val _battleBadgeSeen = MutableStateFlow(false)
    val battleBadgeSeen = _battleBadgeSeen.asStateFlow()

    @Synchronized fun beginEnergyForecast(matchId: String) {
        energyForecastMatchId = matchId
        _opponentEnergyEstimate.value = null
    }

    @Synchronized fun setOpponentEnergyEstimate(matchId: String, estimate: OpponentEnergyEstimate?) {
        if (energyForecastMatchId == matchId) _opponentEnergyEstimate.value = estimate
    }

    /** Capture-only presence, never a species/count witness or a Timeline event. */
    fun recordBattleBadgePresent() { _battleBadgeSeen.value = true }

    @Synchronized fun recordOpponentResources(count: OpponentBattleResourceCountMeasured, at: Long) {
        if (at < (resourceTimes[count.resource] ?: Long.MIN_VALUE)) return
        resourceTimes[count.resource] = at
        val old = _opponentResources.value
        _opponentResources.value = when (count.resource) {
            OpponentBattleResource.POKE_BALLS -> old.copy(pokeBalls = count.visibleCount)
            OpponentBattleResource.SHIELDS -> old.copy(shields = count.visibleCount)
        }
        if (_opponentResources.value != old) invalidateOpponentBadge(at)
        recordBattleBadgePresent()
    }
    private val _mode = MutableStateFlow(DroidballOverlayMode.PRE_BATTLE)
    val mode = _mode.asStateFlow()
    private val _expanded = MutableStateFlow(false)
    val expanded = _expanded.asStateFlow()
    private val _panelAllowed = MutableStateFlow(false)
    /** Team Select or a deliberate tap may reveal a display; observation alone cannot. */
    val panelAllowed = _panelAllowed.asStateFlow()
    private val _opponentSpecies = MutableStateFlow<List<ObservedOpponentSpecies>>(emptyList())
    /** First-seen opponent identities for the three HUD cells. */
    val opponentSpecies = _opponentSpecies.asStateFlow()
    private val _inferredPlayerTeam = MutableStateFlow<List<String?>>(List(3) { null })
    val inferredPlayerTeam = _inferredPlayerTeam.asStateFlow()
    private val _playerTeamConfirmed = MutableStateFlow(false)
    val playerTeamConfirmed = _playerTeamConfirmed.asStateFlow()
    private val _configuredPlayerTeam = MutableStateFlow<List<String>>(emptyList())
    /** Deliberate pre-flight configuration; kept separate from visual Team Select testimony. */
    val configuredPlayerTeam = _configuredPlayerTeam.asStateFlow()
    private val _teamEditingAllowed = MutableStateFlow(false)
    val teamEditingAllowed = _teamEditingAllowed.asStateFlow()
    private val _activeOpponentMovePossibilities = MutableStateFlow<OpponentMovePossibilities?>(null)
    val activeOpponentMovePossibilities = _activeOpponentMovePossibilities.asStateFlow()
    private var activePlayerTypes: List<PokemonType> = emptyList()
    private var activeOpponentFastMoves: List<Pair<String, PokemonType>> = emptyList()
    private var activeOpponentChargedMoves: List<Pair<String, PokemonType>> = emptyList()
    private var activeOpponentSpeciesName: String? = null
    private var activeMoveEnergy: Map<String, Int> = emptyMap()
    private val confirmedFastMovesBySpecies = mutableMapOf<String, String>()
    private var opponentFastMoveCandidateNames: Set<String>? = null
    private var opponentAudioFavoredMoveName: String? = null
    private val confirmedChargedMovesBySpecies = mutableMapOf<String, MutableSet<String>>()
    private val faintedSpeciesNames = mutableSetOf<String>()

    fun showSessionPhase(phase: DroidballSessionPhase) {
        _teamEditingAllowed.value = phase !in setOf(DroidballSessionPhase.BATTLE_ACTIVE, DroidballSessionPhase.RESULT, DroidballSessionPhase.ENDED)
        // Session state is delivered asynchronously. A prebattle emission can
        // arrive after VS/countdown has requested the HUD layout; it must not
        // erase that layout. Layout requests do not themselves authorize a panel.
        _mode.value = when (phase) {
            DroidballSessionPhase.NAVIGATION_IDLE -> preserveBattleHudOr(DroidballOverlayMode.PRE_BATTLE)
            DroidballSessionPhase.SEEKING_TEAM_SELECT -> preserveBattleHudOr(DroidballOverlayMode.SEEKING_TEAM_SELECT)
            DroidballSessionPhase.TEAM_SELECT_ACTIVE -> preserveBattleHudOr(DroidballOverlayMode.TEAM_SELECT)
            DroidballSessionPhase.CALIBRATING -> if (_mode.value == DroidballOverlayMode.BATTLE_HUD) {
                DroidballOverlayMode.BATTLE_HUD
            } else {
                DroidballOverlayMode.CALIBRATING
            }
            DroidballSessionPhase.COUNTDOWN -> DroidballOverlayMode.BATTLE_HUD
            DroidballSessionPhase.BATTLE_ACTIVE -> DroidballOverlayMode.BATTLE_HUD
            DroidballSessionPhase.RESULT -> DroidballOverlayMode.RESULT
            DroidballSessionPhase.ENDED -> DroidballOverlayMode.PRE_BATTLE
        }
        if (phase == DroidballSessionPhase.COUNTDOWN ||
            phase == DroidballSessionPhase.BATTLE_ACTIVE ||
            phase == DroidballSessionPhase.RESULT
        ) _expanded.value = _panelAllowed.value
    }

    private fun preserveBattleHudOr(requested: DroidballOverlayMode): DroidballOverlayMode =
        if (_mode.value == DroidballOverlayMode.BATTLE_HUD) DroidballOverlayMode.BATTLE_HUD else requested

    /** A scan request chooses the scanner but cannot reveal a display by itself. */
    fun showTeamSelectScan() {
        _battleBadgeSeen.value = false
        _mode.value = DroidballOverlayMode.SEEKING_TEAM_SELECT
        _expanded.value = _panelAllowed.value
    }

    /** A deliberate navigation boundary is not a late prebattle phase emission. */
    fun showNavigationIdle() {
        _battleBadgeSeen.value = false
        _mode.value = DroidballOverlayMode.PRE_BATTLE
    }

    fun showAcceptedTeamSelect() {
        val requestedMode = _mode.value
        val awaitingBattle = battleOverlayDisplayMode(requestedMode, _battleBadgeSeen.value) ==
            DroidballOverlayMode.BATTLE_READY
        val preserveVisibility = awaitingBattle && _panelAllowed.value
        _battleBadgeSeen.value = false
        // Recognition may finish after the user arms the HUD. Keep that request,
        // including an explicit Hide, while still accepting the roster evidence.
        _mode.value = if (awaitingBattle) requestedMode else DroidballOverlayMode.TEAM_SELECT
        _panelAllowed.value = true
        if (!preserveVisibility) _expanded.value = true
    }

    /** Battle evidence selects its layout; only Team Select or a user action reveals it. */
    fun showBattleHud(userRequested: Boolean = false) {
        if (userRequested) _panelAllowed.value = true
        if (_mode.value != DroidballOverlayMode.RESULT) {
            _mode.value = DroidballOverlayMode.BATTLE_HUD
        }
        _expanded.value = _panelAllowed.value
    }

    @Synchronized fun clearOpponentSpecies() {
        _opponentBadgeContent.value = RetainedBadgeContent(_opponentBadgeContent.value.revision + 1)
        _opponentResources.value = HudOpponentResources()
        resourceTimes.clear()
        _opponentEnergyEstimate.value = null
        _battleBadgeSeen.value = false
        _opponentSpecies.value = emptyList()
        _activeOpponentMovePossibilities.value = null
        activePlayerTypes = emptyList()
        activeOpponentFastMoves = emptyList()
        activeOpponentChargedMoves = emptyList()
        activeOpponentSpeciesName = null
        confirmedFastMovesBySpecies.clear()
        opponentFastMoveCandidateNames = null
        opponentAudioFavoredMoveName = null
        confirmedChargedMovesBySpecies.clear()
        faintedSpeciesNames.clear()
    }

    fun recordInferredPlayerRosterSlot(slot: Int, speciesName: String) {
        if (slot !in 1..3) return
        if (_inferredPlayerTeam.value[slot - 1] == speciesName) return
        _inferredPlayerTeam.value = _inferredPlayerTeam.value.toMutableList().also { it[slot - 1] = speciesName }
        _playerTeamConfirmed.value = false
    }

    fun confirmInferredPlayerTeam() {
        _playerTeamConfirmed.value = true
        _configuredPlayerTeam.value = emptyList()
    }

    fun clearInferredPlayerTeam() {
        _inferredPlayerTeam.value = List(3) { null }
        _playerTeamConfirmed.value = false
    }

    fun setConfiguredPlayerTeam(speciesNames: List<String>) {
        _configuredPlayerTeam.value = speciesNames.filter(String::isNotBlank).take(3)
    }

    @Synchronized fun setActivePlayerTypes(types: List<PokemonType>) {
        activePlayerTypes = types
        publishMovePossibilities()
    }

    @Synchronized fun recordOpponentSpecies(
        speciesName: String,
        speciesId: Int?,
        possibleFastMoves: List<Pair<String, PokemonType>>,
        possibleChargedMoves: List<Pair<String, PokemonType>>,
        provisional: Boolean = false,
        observedAtNanos: Long? = null,
        moveEnergy: Map<String, Int> = emptyMap(),
    ) {
        val existing = _opponentSpecies.value.firstOrNull {
            it.speciesName.equals(speciesName, ignoreCase = true)
        }
        if (existing == null && !provisional) {
            val replaceable = _opponentSpecies.value.indexOfFirst { observed ->
                observed.isProvisional &&
                    observed.observedAtNanos != null &&
                    observedAtNanos != null &&
                    observedAtNanos - observed.observedAtNanos in 0..PROVISIONAL_CORRECTION_WINDOW_NANOS
            }
            if (replaceable >= 0) {
                _opponentSpecies.value = _opponentSpecies.value.toMutableList().also { species ->
                    species[replaceable] = ObservedOpponentSpecies(
                        speciesName = speciesName,
                        speciesId = speciesId,
                        isProvisional = false,
                        isFainted = normalizeMoveName(speciesName) in faintedSpeciesNames,
                        observedAtNanos = observedAtNanos,
                    )
                }
            } else if (_opponentSpecies.value.size < 3) {
                _opponentSpecies.value += ObservedOpponentSpecies(
                    speciesName,
                    speciesId,
                    isProvisional = false,
                    isFainted = normalizeMoveName(speciesName) in faintedSpeciesNames,
                    observedAtNanos = observedAtNanos,
                )
            }
        } else if (existing == null && _opponentSpecies.value.size < 3) {
            _opponentSpecies.value += ObservedOpponentSpecies(
                speciesName,
                speciesId,
                isProvisional = true,
                isFainted = normalizeMoveName(speciesName) in faintedSpeciesNames,
                observedAtNanos = observedAtNanos,
            )
        } else {
            _opponentSpecies.value = _opponentSpecies.value.map { observed ->
                if (observed.speciesName.equals(speciesName, ignoreCase = true)) {
                    // Pokémon GO can leave the same badge readable after a
                    // faint. Repeated identity evidence must not revive it.
                    observed.copy(
                        speciesId = speciesId ?: observed.speciesId,
                        isProvisional = observed.isProvisional && provisional,
                        observedAtNanos = observedAtNanos ?: observed.observedAtNanos,
                    )
                } else observed
            }
        }
        if (activeOpponentSpeciesName != speciesName) {
            invalidateOpponentBadge(observedAtNanos ?: System.nanoTime())
            opponentFastMoveCandidateNames = null
            opponentAudioFavoredMoveName = null
        }
        activeOpponentSpeciesName = speciesName
        activeOpponentFastMoves = possibleFastMoves
        activeOpponentChargedMoves = possibleChargedMoves
        activeMoveEnergy = moveEnergy.mapKeys { normalizeMoveName(it.key) }
        publishMovePossibilities()
    }

    private const val PROVISIONAL_CORRECTION_WINDOW_NANOS = 4_000_000_000L

    @Synchronized fun markOpponentFainted(speciesName: String? = activeOpponentSpeciesName, at: Long = System.nanoTime()) {
        val target = speciesName ?: return
        if (faintedSpeciesNames.add(normalizeMoveName(target))) invalidateOpponentBadge(at)
        _opponentSpecies.value = _opponentSpecies.value.map { observed ->
            if (observed.speciesName.equals(target, ignoreCase = true)) {
                observed.copy(isFainted = true)
            } else observed
        }
        if (activeOpponentSpeciesName.equals(target, ignoreCase = true)) {
            _activeOpponentMovePossibilities.value = null
        }
    }

    /** Only a visible, validated badge showing zero remaining balls calls this. */
    @Synchronized fun markOpponentTeamFainted(at: Long = System.nanoTime()) {
        if (_opponentSpecies.value.any { !it.isFainted }) invalidateOpponentBadge(at)
        _opponentSpecies.value.forEach { faintedSpeciesNames += normalizeMoveName(it.speciesName) }
        _opponentSpecies.value = _opponentSpecies.value.map { it.copy(isFainted = true) }
        _activeOpponentMovePossibilities.value = null
    }

    /** Only evidence-resolved identities earn a checkmark; energy tie-breaks remain guesses. */
    @Synchronized fun recordOpponentFastMove(identity: com.example.overdex.battle.custody.FastMoveIdentified) {
        if (identity.side != com.example.overdex.battle.custody.ActivePokemonSide.OPPONENT ||
            identity.basis.contains("TIE_BREAK")) return
        confirmedFastMovesBySpecies[normalizeMoveName(identity.speciesName)] = identity.moveName
        publishMovePossibilities()
    }

    /**
     * Narrows the visible move pool without discarding Reference Knowledge.
     * A later, contradictory observation may expand or replace this set.
     */
    @Synchronized fun recordOpponentFastMoveCandidates(speciesName: String, moveNames: List<String>, audioFavoredMoveName: String? = null) {
        if (!activeOpponentSpeciesName.equals(speciesName, ignoreCase = true)) return
        val candidates = moveNames.map(::normalizeMoveName).filter(String::isNotBlank).toSet()
        if (candidates.isEmpty() && activeOpponentFastMoves.isNotEmpty()) return
        opponentFastMoveCandidateNames = candidates
        opponentAudioFavoredMoveName = audioFavoredMoveName?.let(::normalizeMoveName)?.takeIf { it in candidates }
        // Candidate pruning is provisional. Only a new resolved identity replaces
        // an evidence-confirmed move; a transient candidate set must not erase it.
        publishMovePossibilities()
    }

    /** A resolved named announcement confirms a move for that combatant across switches. */
    @Synchronized fun recordOpponentChargedMove(speciesName: String, moveName: String) {
        val confirmed = confirmedChargedMovesBySpecies.getOrPut(normalizeMoveName(speciesName)) { linkedSetOf() }
        val move = normalizeMoveName(moveName)
        if (move.isBlank()) return
        if (confirmed.size < 2) confirmed += move
        publishMovePossibilities()
    }

    private fun publishMovePossibilities() {
        val speciesName = activeOpponentSpeciesName ?: return
        if (normalizeMoveName(speciesName) in faintedSpeciesNames) {
            _activeOpponentMovePossibilities.value = null
            return
        }
        val confirmedCharged = confirmedChargedMovesBySpecies[normalizeMoveName(speciesName)].orEmpty()
        val identifiedOpponentFastMove = confirmedFastMovesBySpecies[normalizeMoveName(speciesName)]
        fun scored(moves: List<Pair<String, PokemonType>>) = moves.map { (name, type) ->
            OverlayMovePossibility(name, type, effectivenessAgainstPlayer(type), energy = activeMoveEnergy[normalizeMoveName(name)])
        }
        val fastMoves = identifiedOpponentFastMove?.let { identified ->
            activeOpponentFastMoves.filter { normalizeMoveName(it.first) == normalizeMoveName(identified) }
        } ?: opponentFastMoveCandidateNames?.let { candidates ->
            activeOpponentFastMoves.filter { normalizeMoveName(it.first) in candidates }
        } ?: activeOpponentFastMoves
        _activeOpponentMovePossibilities.value = OpponentMovePossibilities(
            speciesName,
            scored(fastMoves).map { it.copy(
                isConfirmed = identifiedOpponentFastMove != null && normalizeMoveName(it.name) == normalizeMoveName(identifiedOpponentFastMove),
                isAudioFavored = identifiedOpponentFastMove == null && normalizeMoveName(it.name) == opponentAudioFavoredMoveName,
            ) },
            scored(activeOpponentChargedMoves.filter { confirmedCharged.size < 2 || normalizeMoveName(it.first) in confirmedCharged })
                .map { it.copy(isConfirmed = normalizeMoveName(it.name) in confirmedCharged) }
        )
    }

    private fun effectivenessAgainstPlayer(attackType: PokemonType): Double =
        activePlayerTypes.fold(1.0) { multiplier, defenseType ->
            multiplier * when {
                attackType in defenseType.getWeaknesses() -> 1.6
                attackType in defenseType.getResistances() -> 0.625
                else -> 1.0
            }
        }

    private fun normalizeMoveName(value: String): String =
        value.uppercase().filter(Char::isLetterOrDigit)

    fun toggleExpanded() {
        _panelAllowed.value = true
        _expanded.value = !_expanded.value
    }

    fun reset() {
        energyForecastMatchId = null
        _panelAllowed.value = false
        _teamEditingAllowed.value = false
        _mode.value = DroidballOverlayMode.PRE_BATTLE
        _expanded.value = false
        clearOpponentSpecies()
        clearInferredPlayerTeam()
        setConfiguredPlayerTeam(emptyList())
    }
}

/** Readiness is presentation based on the labeled energy estimate, not testimony. */
enum class HudChargeReadiness { BUILDING, ONE_FAST_MOVE_AWAY, READY }

fun hudChargeReadiness(cost: Int?, energy: Int?, fastGain: Int?): HudChargeReadiness {
    if (cost == null || cost <= 0 || cost > 100 || energy == null) return HudChargeReadiness.BUILDING
    if (energy >= cost) return HudChargeReadiness.READY
    return if (fastGain != null && fastGain > 0 && (energy + fastGain).coerceAtMost(100) >= cost)
        HudChargeReadiness.ONE_FAST_MOVE_AWAY else HudChargeReadiness.BUILDING
}
