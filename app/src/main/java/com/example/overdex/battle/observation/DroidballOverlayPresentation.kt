package com.example.overdex.battle.observation

import com.example.overdex.model.PokemonType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Presentation-only state for the service-owned Droidball overlay. */
enum class DroidballOverlayMode {
    PRE_BATTLE,
    SEEKING_TEAM_SELECT,
    TEAM_SELECT,
    CALIBRATING,
    /** The Droidball Battle HUD is open; GO has not necessarily started the Match yet. */
    BATTLE_HUD,
    BATTLE_LIVE,
    RESULT
}

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

data class OverlayMovePossibility(
    val name: String,
    val type: PokemonType,
    /** Damage multiplier from this move's type against the currently observed player types. */
    val effectivenessMultiplier: Double
) {
    val hazardous: Boolean get() = effectivenessMultiplier > 1.0
    /** The increase over neutral damage, for a compact live HUD warning. */
    val damageIncreasePercent: Int get() = ((effectivenessMultiplier - 1.0) * 100.0).toInt()
}

object DroidballOverlayPresentation {
    private val _mode = MutableStateFlow(DroidballOverlayMode.PRE_BATTLE)
    val mode = _mode.asStateFlow()
    private val _expanded = MutableStateFlow(false)
    val expanded = _expanded.asStateFlow()
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
    private val _activeOpponentMovePossibilities = MutableStateFlow<OpponentMovePossibilities?>(null)
    val activeOpponentMovePossibilities = _activeOpponentMovePossibilities.asStateFlow()
    private var activePlayerTypes: List<PokemonType> = emptyList()
    private var activeOpponentFastMoves: List<Pair<String, PokemonType>> = emptyList()
    private var activeOpponentChargedMoves: List<Pair<String, PokemonType>> = emptyList()
    private var activeOpponentSpeciesName: String? = null
    private var identifiedOpponentFastMove: String? = null

    fun showSessionPhase(phase: DroidballSessionPhase) {
        // Session state is delivered asynchronously. A prebattle emission can
        // arrive after VS/countdown has already opened the HUD; it must not erase
        // the field presentation that the accepted witness just requested.
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
        ) _expanded.value = true
    }

    private fun preserveBattleHudOr(requested: DroidballOverlayMode): DroidballOverlayMode =
        if (_mode.value == DroidballOverlayMode.BATTLE_HUD) DroidballOverlayMode.BATTLE_HUD else requested

    /** Opens the field HUD as soon as VS is witnessed, independent of GO. */
    fun showBattleHud() {
        if (_mode.value != DroidballOverlayMode.RESULT) {
            _mode.value = DroidballOverlayMode.BATTLE_HUD
            _expanded.value = true
        }
    }

    fun clearOpponentSpecies() {
        _opponentSpecies.value = emptyList()
        _activeOpponentMovePossibilities.value = null
        activePlayerTypes = emptyList()
        activeOpponentFastMoves = emptyList()
        activeOpponentChargedMoves = emptyList()
        activeOpponentSpeciesName = null
        identifiedOpponentFastMove = null
    }

    fun recordInferredPlayerRosterSlot(slot: Int, speciesName: String) {
        if (slot !in 1..3) return
        _inferredPlayerTeam.value = _inferredPlayerTeam.value.toMutableList().also { it[slot - 1] = speciesName }
        _playerTeamConfirmed.value = false
    }

    fun confirmInferredPlayerTeam() { _playerTeamConfirmed.value = true }

    fun clearInferredPlayerTeam() {
        _inferredPlayerTeam.value = List(3) { null }
        _playerTeamConfirmed.value = false
    }

    fun setConfiguredPlayerTeam(speciesNames: List<String>) {
        _configuredPlayerTeam.value = speciesNames.filter(String::isNotBlank).take(3)
    }

    fun setActivePlayerTypes(types: List<PokemonType>) {
        activePlayerTypes = types
        publishMovePossibilities()
    }

    fun recordOpponentSpecies(
        speciesName: String,
        speciesId: Int?,
        possibleFastMoves: List<Pair<String, PokemonType>>,
        possibleChargedMoves: List<Pair<String, PokemonType>>,
        provisional: Boolean = false,
        observedAtNanos: Long? = null,
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
                        observedAtNanos = observedAtNanos,
                    )
                }
            } else if (_opponentSpecies.value.size < 3) {
                _opponentSpecies.value += ObservedOpponentSpecies(
                    speciesName,
                    speciesId,
                    isProvisional = false,
                    observedAtNanos = observedAtNanos,
                )
            }
        } else if (existing == null && _opponentSpecies.value.size < 3) {
            _opponentSpecies.value += ObservedOpponentSpecies(
                speciesName,
                speciesId,
                isProvisional = true,
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
        if (activeOpponentSpeciesName != speciesName) identifiedOpponentFastMove = null
        activeOpponentSpeciesName = speciesName
        activeOpponentFastMoves = possibleFastMoves
        activeOpponentChargedMoves = possibleChargedMoves
        publishMovePossibilities()
    }

    private const val PROVISIONAL_CORRECTION_WINDOW_NANOS = 4_000_000_000L

    fun markOpponentFainted(speciesName: String? = activeOpponentSpeciesName) {
        val target = speciesName ?: return
        _opponentSpecies.value = _opponentSpecies.value.map { observed ->
            if (observed.speciesName.equals(target, ignoreCase = true)) {
                observed.copy(isFainted = true)
            } else observed
        }
        if (activeOpponentSpeciesName.equals(target, ignoreCase = true)) {
            _activeOpponentMovePossibilities.value = null
        }
    }

    /** Replace the opponent's candidate fast moves after cadence identifies one. */
    fun recordOpponentFastMove(moveName: String) {
        identifiedOpponentFastMove = moveName
        publishMovePossibilities()
    }

    private fun publishMovePossibilities() {
        val speciesName = activeOpponentSpeciesName ?: return
        fun scored(moves: List<Pair<String, PokemonType>>) = moves.map { (name, type) ->
            OverlayMovePossibility(name, type, effectivenessAgainstPlayer(type))
        }
        val fastMoves = identifiedOpponentFastMove?.let { identified ->
            activeOpponentFastMoves.filter { it.first.equals(identified, ignoreCase = true) }
        } ?: activeOpponentFastMoves
        _activeOpponentMovePossibilities.value = OpponentMovePossibilities(
            speciesName, scored(fastMoves), scored(activeOpponentChargedMoves)
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

    fun toggleExpanded() {
        if (_mode.value != DroidballOverlayMode.BATTLE_LIVE) {
            _expanded.value = !_expanded.value
        }
    }

    fun reset() {
        _mode.value = DroidballOverlayMode.PRE_BATTLE
        _expanded.value = false
        clearOpponentSpecies()
        clearInferredPlayerTeam()
        setConfiguredPlayerTeam(emptyList())
    }
}
