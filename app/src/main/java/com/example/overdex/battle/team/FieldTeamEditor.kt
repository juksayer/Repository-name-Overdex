package com.example.overdex.battle.team

import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A scan proposes species, not an owned-card identity or a set of moves. */
data class FieldTeamSlot(
    val pokemon: Pokemon? = null,
    val scannedName: String? = null,
    val fastMove: String? = null,
    val chargedMoves: List<String> = emptyList(),
    val ownedPokemonId: String? = null,
) {
    val ready: Boolean get() = pokemon != null &&
        pokemon.fastMoves.any { it.name == fastMove } && chargedMoves.size in 1..2 &&
        chargedMoves.all { name -> pokemon.chargedMoves.any { it.name == name } }
}

data class FieldTeamDraft(
    val slots: List<FieldTeamSlot> = List(3) { FieldTeamSlot() },
    val loading: Boolean = false,
    val error: String? = null,
) {
    val ready: Boolean get() = !loading && slots.all { it.ready }
    fun team(): CurrentBattleTeam {
        check(ready) { "Choose a species, one Fast Move, and one or two Charged Moves for each slot." }
        return CurrentBattleTeam(slots.mapIndexed { index, slot ->
            CurrentBattleTeamMember(index + 1, slot.pokemon!!.id, slot.pokemon.name,
                slot.fastMove!!, slot.chargedMoves, slot.ownedPokemonId)
        })
    }
}

/** Application-owned draft: opening/cancelling never changes the saved lineup. */
class FieldTeamEditor(
    private val knowledge: PokemonKnowledge,
    private val scope: CoroutineScope,
    private val canEdit: () -> Boolean,
    private val save: (CurrentBattleTeam) -> Unit,
) {
    private val _draft = MutableStateFlow<FieldTeamDraft?>(null)
    val draft = _draft.asStateFlow()
    private val _speciesNames = MutableStateFlow<List<String>>(emptyList())
    val speciesNames = _speciesNames.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    private var scanRoster: List<String?>? = null
    private var suspendedScanDraft: FieldTeamDraft? = null

    fun open(team: CurrentBattleTeam) {
        scanRoster = null
        suspendedScanDraft = null
        load {
            List(3) { index ->
                team.members.firstOrNull { it.slot == index + 1 }?.let { member ->
                    val pokemon = knowledge.getPokemonByName(member.speciesName)
                    FieldTeamSlot(pokemon, member.speciesName, member.fastMoveName,
                        member.chargedMoveNames, member.ownedPokemonId)
                } ?: FieldTeamSlot()
            }
        }
    }

    fun openScan(names: List<String?>) {
        if (!canEdit()) { close(); return }
        scanRoster = names.toList()
        suspendedScanDraft = null
        _draft.value = null
        load {
            // Even a same-species scan might be a different owned Pokémon with different moves.
            List(3) { index ->
                val name = names.getOrNull(index)
                FieldTeamSlot(pokemon = name?.let { knowledge.getPokemonByName(it) }, scannedName = name)
            }
        }
    }

    /** Closing the field panel must not erase choices when the same scan is reopened. */
    fun resumeScan(names: List<String?>) {
        if (!canEdit()) { close(); return }
        if (scanRoster == names) {
            if (_draft.value != null) return
            suspendedScanDraft?.let { _draft.value = it; return }
        }
        openScan(names)
    }

    fun chooseSpecies(slot: Int, name: String) {
        val before = _draft.value ?: return
        if (before.loading || slot !in 0..2) return
        load { before.slots.toMutableList().apply {
            val pokemon = knowledge.getPokemonByName(name)
                ?: error("Could not load $name. Choose another species or try again.")
            this[slot] = if (this[slot].pokemon?.name == pokemon.name) this[slot]
                else FieldTeamSlot(pokemon = pokemon)
        } }
    }

    fun chooseFast(slot: Int, name: String) = change(slot) { member ->
        if (member.pokemon?.fastMoves?.any { it.name == name } == true) member.copy(fastMove = name) else member
    }

    fun toggleCharged(slot: Int, name: String) = change(slot) { member ->
        if (member.pokemon?.chargedMoves?.none { it.name == name } != false) member
        else if (name in member.chargedMoves) member.copy(chargedMoves = member.chargedMoves - name)
        else if (member.chargedMoves.size < 2) member.copy(chargedMoves = member.chargedMoves + name)
        else member
    }

    fun useTeam() {
        val before = _draft.value ?: return
        if (!canEdit()) { close(); return }
        if (!before.ready) { _draft.value = before.copy(error = "Finish all three slots and their moves first."); return }
        try {
            save(before.team())
            scanRoster = null
            suspendedScanDraft = null
            close()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _draft.value = before.copy(error = error.message ?: "Could not save the team. Try again.")
        }
    }

    fun close() {
        if (scanRoster != null) _draft.value?.takeUnless { it.loading }?.let { suspendedScanDraft = it }
        revision++
        job?.cancel()
        _draft.value = null
    }

    /** An explicit rescan may contain different owned copies with the same names. */
    fun discardScan() {
        close()
        scanRoster = null
        suspendedScanDraft = null
    }

    private fun change(slot: Int, edit: (FieldTeamSlot) -> FieldTeamSlot) {
        val before = _draft.value ?: return
        if (!canEdit()) { close(); return }
        if (before.loading || slot !in 0..2) return
        _draft.value = before.copy(slots = before.slots.mapIndexed { index, member ->
            if (slot == index) edit(member) else member
        }, error = null)
    }

    private fun load(slots: suspend () -> List<FieldTeamSlot>) {
        if (!canEdit()) { close(); return }
        job?.cancel()
        val version = ++revision
        val before = _draft.value ?: FieldTeamDraft()
        _draft.value = before.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val members = slots()
                if (_speciesNames.value.isEmpty()) _speciesNames.value = knowledge.getAllSpeciesNames().sorted()
                if (version == revision && canEdit()) _draft.value = FieldTeamDraft(members)
                else if (version == revision) close()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (version == revision) _draft.value = before.copy(error = error.message ?: "Could not load the team.")
            }
        }
    }
}
