package com.example.overdex.battle.observation

import com.example.overdex.model.PokemonType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Presentation-only state for the service-owned Droidball overlay. */
enum class DroidballOverlayMode {
    PRE_BATTLE,
    CALIBRATING,
    /** The Droidball Battle HUD is open; GO has not necessarily started the Match yet. */
    BATTLE_HUD,
    BATTLE_LIVE,
    RESULT
}

data class ObservedOpponentSpecies(
    val speciesName: String,
    val speciesId: Int?
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
    private val _activeOpponentMovePossibilities = MutableStateFlow<OpponentMovePossibilities?>(null)
    val activeOpponentMovePossibilities = _activeOpponentMovePossibilities.asStateFlow()
    private var activePlayerTypes: List<PokemonType> = emptyList()
    private var activeOpponentFastMoves: List<Pair<String, PokemonType>> = emptyList()
    private var activeOpponentChargedMoves: List<Pair<String, PokemonType>> = emptyList()
    private var activeOpponentSpeciesName: String? = null

    fun showSessionPhase(phase: DroidballSessionPhase) {
        // Session state is delivered asynchronously.  An initial ARMED emission can
        // arrive after VS/countdown has already opened the HUD; it must not erase
        // the field presentation that the accepted witness just requested.
        _mode.value = when (phase) {
            DroidballSessionPhase.ARMED -> if (_mode.value == DroidballOverlayMode.BATTLE_HUD) {
                DroidballOverlayMode.BATTLE_HUD
            } else {
                DroidballOverlayMode.PRE_BATTLE
            }
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
    }

    fun setActivePlayerTypes(types: List<PokemonType>) {
        activePlayerTypes = types
        publishMovePossibilities()
    }

    fun recordOpponentSpecies(
        speciesName: String,
        speciesId: Int?,
        possibleFastMoves: List<Pair<String, PokemonType>>,
        possibleChargedMoves: List<Pair<String, PokemonType>>
    ) {
        if (_opponentSpecies.value.none { it.speciesName == speciesName } && _opponentSpecies.value.size < 3) {
            _opponentSpecies.value += ObservedOpponentSpecies(speciesName, speciesId)
        }
        activeOpponentSpeciesName = speciesName
        activeOpponentFastMoves = possibleFastMoves
        activeOpponentChargedMoves = possibleChargedMoves
        publishMovePossibilities()
    }

    private fun publishMovePossibilities() {
        val speciesName = activeOpponentSpeciesName ?: return
        fun scored(moves: List<Pair<String, PokemonType>>) = moves.map { (name, type) ->
            OverlayMovePossibility(name, type, effectivenessAgainstPlayer(type))
        }
        _activeOpponentMovePossibilities.value = OpponentMovePossibilities(
            speciesName, scored(activeOpponentFastMoves), scored(activeOpponentChargedMoves)
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
    }
}
