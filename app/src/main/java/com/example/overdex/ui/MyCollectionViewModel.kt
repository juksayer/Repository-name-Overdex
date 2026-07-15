package com.example.overdex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toDomain
import com.example.overdex.data.local.toEntity
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.RegistrationSession
import com.example.overdex.model.RegistrationSessionManager
import com.example.overdex.model.observation.Observation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MyCollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = PokedexDatabase.getDatabase(application)
    private val ownedPokemonDao = db.ownedPokemonDao()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val activeSession: StateFlow<RegistrationSession?> = RegistrationSessionManager.activeSession

    fun startRegistrationSession() {
        RegistrationSessionManager.startSession()
    }

    fun addObservation(observation: Observation) {
        RegistrationSessionManager.addObservation(observation)
    }

    fun completeRegistrationSession(speciesId: Int): OwnedPokemon? {
        val session = RegistrationSessionManager.completeSession()
        val specimen = session?.buildSpecimen(speciesId)
        if (specimen != null) {
            addOwnedPokemon(specimen)
        }
        return specimen
    }

    fun cancelRegistrationSession() {
        RegistrationSessionManager.cancelSession()
    }

    fun clearActiveSession() {
        RegistrationSessionManager.cancelSession()
    }

    fun updateSelectedIndex(index: Int) {
        _selectedIndex.value = index
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _selectedIndex.value = 0 // Reset selection on search
    }

    val ownedPokemon: StateFlow<List<OwnedPokemon>> = combine(
        ownedPokemonDao.getAllOwnedPokemonWithSpecies(),
        _searchQuery
    ) { entities, query ->
        val filtered = if (query.isBlank()) {
            entities
        } else {
            entities.filter { 
                it.owned.displayName?.contains(query, ignoreCase = true) == true ||
                it.speciesName?.contains(query, ignoreCase = true) == true
            }
        }
        
        filtered.map { it.owned.toDomain() }
            .sortedWith(compareByDescending<OwnedPokemon> { it.isFavorite }.thenByDescending { it.createdAt })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun getOwnedPokemon(id: String): Flow<OwnedPokemon?> = ownedPokemonDao.getOwnedPokemonById(id)
        .map { it?.toDomain() }

    fun addOwnedPokemon(pokemon: OwnedPokemon) {
        viewModelScope.launch {
            ownedPokemonDao.addOwnedPokemon(pokemon.toEntity())
        }
    }

    fun updateOwnedPokemon(pokemon: OwnedPokemon) {
        viewModelScope.launch {
            ownedPokemonDao.updateOwnedPokemon(pokemon.toEntity())
        }
    }

    fun removeOwnedPokemon(id: String) {
        viewModelScope.launch {
            ownedPokemonDao.deleteOwnedPokemon(id)
        }
    }
}
