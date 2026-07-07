package com.example.overdex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.toDomain
import com.example.overdex.data.local.toEntity
import com.example.overdex.model.OwnedPokemon
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MyCollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = PokedexDatabase.getDatabase(application)
    private val ownedPokemonDao = db.ownedPokemonDao()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    fun updateSelectedIndex(index: Int) {
        _selectedIndex.value = index
    }

    val ownedPokemon: StateFlow<List<OwnedPokemon>> = ownedPokemonDao.getAllOwnedPokemon()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(
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
