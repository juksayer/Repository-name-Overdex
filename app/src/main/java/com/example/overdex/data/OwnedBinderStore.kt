package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.OwnedPokemonBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OwnedBinderStore(context: Context, preferencesName: String = "owned_binders") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(load())
    val binders = state.asStateFlow()

    private fun load(): List<OwnedPokemonBinder> = prefs.getString("binders", null)?.let {
        json.decodeFromString<List<OwnedPokemonBinder>>(it)
    }.orEmpty()

    @Synchronized fun save(binder: OwnedPokemonBinder) {
        require(!binder.isBuiltIn) { "Built-in binders cannot be changed." }
        val name = binder.displayName.trim()
        require(name.isNotBlank() && '/' !in name) { "Use a binder name without slashes." }
        require(!name.equals("Add or Edit Binders", true)) { "Choose a different binder name." }
        require((OwnedPokemonBinder.entries + state.value).none { it.routeKey != binder.routeKey && it.displayName.equals(name, true) }) { "That binder name is already in use." }
        require(binder.rules.validationError() == null) { binder.rules.validationError().orEmpty() }
        persist(state.value.filterNot { it.routeKey == binder.routeKey } + binder.copy(displayName = name))
    }

    @Synchronized fun remove(id: String) = persist(state.value.filterNot { it.routeKey == id })

    private fun persist(value: List<OwnedPokemonBinder>) {
        check(prefs.edit().putString("binders", json.encodeToString(value)).commit()) { "Unable to save binders." }
        state.value = value
    }
}
