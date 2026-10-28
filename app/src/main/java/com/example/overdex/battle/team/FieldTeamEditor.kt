package com.example.overdex.battle.team

import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.OwnedPokemonDraft
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
    val ownedCandidates: List<OwnedPokemon> = emptyList(),
    val newCard: OwnedPokemonDraft? = null,
) {
    val ready: Boolean get() = pokemon != null &&
        pokemon.fastMoves.any { it.name == fastMove } && chargedMoves.size in 1..2 &&
        chargedMoves.all { name -> pokemon.chargedMoves.any { it.name == name } }
}

data class FieldTeamDraft(
    val slots: List<FieldTeamSlot> = List(3) { FieldTeamSlot() },
    val loading: Boolean = false,
    val error: String? = null,
    val teamName: String = "",
    val collectionEnabled: Boolean = false,
    val isScannedTeam: Boolean = false,
) {
    val ready: Boolean get() = !loading && slots.size == 3 && slots.all { it.ready }
    val collectionReady: Boolean get() {
        if (!collectionEnabled || !ready || teamName.isBlank() ||
            teamName.any { it == ',' || it == '\n' || it == '\r' }) return false
        val ids = slots.map { slot ->
            val pokemon = slot.pokemon ?: return false
            if (slot.ownedPokemonId != null && slot.newCard != null) return false
            slot.newCard?.let { card ->
                if (card.validationError != null || card.speciesId != pokemon.id ||
                    card.speciesName != pokemon.name) return false
                card.id
            } ?: slot.ownedPokemonId ?: return false
        }
        return ids.none(String::isBlank) && ids.distinct().size == 3
    }
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
    private val loadOwnedCards: suspend () -> List<OwnedPokemon> = { emptyList() },
    private val saveToCollection: (suspend (FieldTeamDraft) -> CurrentBattleTeam)? = null,
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
    private var collectionWrite: Job? = null
    private var writingDraft: FieldTeamDraft? = null
    private var writingRevision: Long? = null

    fun open(team: CurrentBattleTeam) {
        scanRoster = null
        suspendedScanDraft = null
        _draft.value = null
        load {
            List(3) { index ->
                team.members.firstOrNull { it.slot == index + 1 }?.let { member ->
                    val pokemon = exactSpecies(member.speciesName, member.speciesId)
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
        _draft.value = FieldTeamDraft(isScannedTeam = true)
        load {
            // Even a same-species scan might be a different owned Pokémon with different moves.
            List(3) { index ->
                val name = names.getOrNull(index)
                FieldTeamSlot(pokemon = name?.let { exactSpecies(it) }, scannedName = name)
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
            val pokemon = exactSpecies(name)
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

    /** A candidate remains a suggestion until the trainer chooses this individual. */
    fun chooseOwnedCard(slot: Int, id: String) = change(slot) { member ->
        val pokemon = member.pokemon ?: return@change member
        val card = member.ownedCandidates.firstOrNull { it.id == id } ?: return@change member
        val fast = pokemon.fastMoves.firstOrNull { it.name.equals(card.fastMove, ignoreCase = true) }?.name
        val charged = listOfNotNull(card.chargedMove1, card.chargedMove2).mapNotNull { name ->
            pokemon.chargedMoves.firstOrNull { it.name.equals(name, ignoreCase = true) }?.name
        }.distinct().take(2)
        member.copy(ownedPokemonId = card.id, newCard = null, fastMove = fast, chargedMoves = charged)
    }

    /** Create the UUID once; edits, save retries and closing the panel keep it. */
    fun importNewCard(slot: Int) = change(slot) { member ->
        val pokemon = member.pokemon ?: return@change member
        member.copy(ownedPokemonId = null, newCard = member.newCard ?: OwnedPokemonDraft(
            speciesId = pokemon.id, speciesName = pokemon.name,
        ))
    }

    fun updateImport(slot: Int, transform: (OwnedPokemonDraft) -> OwnedPokemonDraft) = change(slot) { member ->
        val before = member.newCard ?: return@change member
        member.copy(newCard = transform(before).copy(
            id = before.id, speciesId = before.speciesId, speciesName = before.speciesName,
            createdAt = before.createdAt,
        ))
    }

    fun setTeamName(name: String) {
        val before = _draft.value ?: return
        if (!canEdit()) { close(); return }
        if (!before.loading) _draft.value = before.copy(teamName = name.take(48), error = null)
    }

    fun saveTeamToCollection() {
        val before = _draft.value ?: return
        val persist = saveToCollection ?: return
        if (!canEdit()) { close(); return }
        if (collectionWrite?.isActive == true) return
        if (before.teamName.isBlank()) {
            _draft.value = before.copy(error = "Enter a name for this team.")
            return
        }
        if (before.teamName.any { it == ',' || it == '\n' || it == '\r' }) {
            _draft.value = before.copy(error = "Use a team name without commas or line breaks.")
            return
        }
        if (!before.collectionReady) {
            _draft.value = before.copy(error = "Choose an existing card or import a new card for each slot, and finish their moves.")
            return
        }
        val version = ++revision
        job?.cancel()
        writingDraft = before
        writingRevision = version
        _draft.value = before.copy(loading = true, error = null)
        // Closing/rescanning can cancel reads, but must not interrupt an accepted database write.
        collectionWrite = scope.launch {
            try {
                val team = persist(before)
                if (version == revision && canEdit()) {
                    save(team)
                    scanRoster = null
                    suspendedScanDraft = null
                    close()
                } else if (version == revision) close()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val failed = before.copy(error = error.message ?: "Could not save these cards and team. Try again.")
                if (version == revision) _draft.value = failed
                else if (scanRoster != null && suspendedScanDraft == before) suspendedScanDraft = failed
            } finally {
                writingDraft = null
                writingRevision = null
            }
        }
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
        if (scanRoster != null) (_draft.value?.takeUnless { it.loading }
            ?: writingDraft?.takeIf { writingRevision == revision })
            ?.let { suspendedScanDraft = it }
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
            if (slot == index) edit(member).let { changed ->
                changed.copy(newCard = changed.newCard?.copy(fastMove = changed.fastMove,
                    chargedMove1 = changed.chargedMoves.getOrNull(0), chargedMove2 = changed.chargedMoves.getOrNull(1)))
            } else member
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
                var collectionError: String? = null
                val cards = try { loadOwnedCards() } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    collectionError = error.message ?: "Could not load owned cards. You can still continue without importing."
                    emptyList()
                }
                val withCandidates = members.map { member ->
                    val pokemon = member.pokemon
                    member.copy(ownedCandidates = if (pokemon == null) emptyList() else cards.filter { card ->
                        card.speciesId == pokemon.id && (card.speciesName.isNullOrBlank() ||
                            card.speciesName.equals(pokemon.name, ignoreCase = true))
                    })
                }
                if (_speciesNames.value.isEmpty()) _speciesNames.value = knowledge.getAllSpeciesNames().sorted()
                if (version == revision && canEdit()) _draft.value = before.copy(slots = withCandidates,
                    loading = false, error = collectionError,
                    collectionEnabled = saveToCollection != null && collectionError == null)
                else if (version == revision) close()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (version == revision) _draft.value = before.copy(error = error.message ?: "Could not load the team.")
            }
        }
    }

    private suspend fun exactSpecies(name: String, expectedId: Int? = null): Pokemon {
        val reference = knowledge.getPokemonByName(name.trim())
        check(reference != null && reference.name.equals(name.trim(), ignoreCase = true) &&
            (expectedId == null || reference.id == expectedId)) {
            "Could not load $name without changing its species or form. Choose another species or try again."
        }
        return reference
    }
}
