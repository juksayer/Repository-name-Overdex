package com.example.overdex.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.example.overdex.data.PokemonJsonLoader
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.local.*
import com.example.overdex.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PokedexViewModel(application: Application) : AndroidViewModel(application) {
    private val db = PokedexDatabase.getDatabase(application)
    private val pokemonDao = db.pokemonDao()
    private val pokemonLoader = PokemonJsonLoader(application)
    private val gameMasterLoader = GameMasterLoader(application)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedPokemon: Flow<PagingData<Pokemon>> = _searchQuery
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            ) {
                if (query.isBlank()) pokemonDao.getAllPokemon()
                else pokemonDao.searchPokemon("%$query%")
            }.flow
        }
        .map { pagingData ->
            pagingData.map { entity -> entity.toDomain() }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            Log.d("STARTUP", "1: ViewModel init")

            val gameMasterText = gameMasterLoader.loadRawJson()
            println("GameMaster length = ${gameMasterText.length}")

            Log.d("STARTUP", "2: GameMaster loaded")

            gameMasterLoader.testParse()

            Log.d("STARTUP", "3: Starting populateFullPokedex")

            populateFullPokedex()

            Log.d("STARTUP", "4: Finished populateFullPokedex")
        }
    }

    suspend fun populateFullPokedex() {
        try {
            pokemonDao.clearAll()
            val imported = pokemonLoader.loadPokemon()
            val importedMap = imported.pokemon.associateBy { it.id }
            
            val commonFastMoves = listOf(
                Move("Counter", PokemonType.FIGHTING, 8, 7, isFast = true, 2),
                Move("Dragon Breath", PokemonType.DRAGON, 4, 3, isFast = true, 1),
                Move("Mud Shot", PokemonType.GROUND, 3, 9, isFast = true, 2),
                Move("Vine Whip", PokemonType.GRASS, 5, 8, isFast = true, 2)
            )
            
            val commonChargedMoves = listOf(
                Move("Hydro Cannon", PokemonType.WATER, 80, 40, isFast = false),
                Move("Frenzy Plant", PokemonType.GRASS, 100, 45, isFast = false),
                Move("Blast Burn", PokemonType.FIRE, 110, 50, isFast = false),
                Move("Dragon Claw", PokemonType.DRAGON, 50, 35, isFast = false),
                Move("Earthquake", PokemonType.GROUND, 120, 65, isFast = false),
                Move("Body Slam", PokemonType.NORMAL, 60, 35, isFast = false),
                Move("Sludge Bomb", PokemonType.POISON, 80, 50, isFast = false),
                Move("Wild Charge", PokemonType.ELECTRIC, 100, 45, isFast = false)
            )

            val pokemonEntities = mutableListOf<PokemonEntity>()
            
            // Populate up to 1025 pokemon
            for (id in 1..1025) {
                val importedPokemon = importedMap[id]

                val gameMasterPokemon =
                    gameMasterLoader.getPokemonByDex(id)

                val name =
                    importedPokemon?.name
                        ?: gameMasterPokemon?.speciesName
                        ?: "Pokemon #$id"
                val rawTypes = importedPokemon?.type ?: listOf("Normal")
                val spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"

                if (id <= 3) {
                    Log.d("SPRITE_TEST", "ID=$id importedImg=${importedPokemon?.img}")
                    Log.d("SPRITE_TEST", "ID=$id spriteUrl=$spriteUrl")
                }
                val mappedTypes = rawTypes.mapNotNull { typeName ->
                    try {
                        PokemonType.valueOf(typeName.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                }.ifEmpty { listOf(PokemonType.NORMAL) }

                val region = getRegionForId(id)



                if (id == 149) {
                    Log.d("OVERDEX", "Lookup result = ${gameMasterPokemon?.speciesName}")
                    Log.d("OVERDEX", "Fast = ${gameMasterPokemon?.fastMoves}")
                    Log.d("OVERDEX", "Charged = ${gameMasterPokemon?.chargedMoves}")

                    val bubble = gameMasterLoader.getMove("BUBBLE")

                    Log.d("OVERDEX", "Move Name = ${bubble?.name}")
                    Log.d("OVERDEX", "Move Type = ${bubble?.type}")
                    Log.d("OVERDEX", "Move Power = ${bubble?.power}")
                    Log.d("OVERDEX", "Move EnergyGain = ${bubble?.energyGain}")
                }

// Assign moves based on type for some realism



                // Assign moves based on type for some realism

                val fastMoves = gameMasterPokemon?.fastMoves
                    ?.mapNotNull { moveId ->
                        gameMasterLoader.getMove(moveId)?.let { move ->
                            Move(
                                name = move.name,
                                type = try {
                                    PokemonType.valueOf(move.type.uppercase())
                                } catch (e: Exception) {
                                    PokemonType.NORMAL
                                },
                                damage = move.power,
                                energy = move.energyGain,
                                isFast = true,
                                turns = move.turns
                            )
                        }
                    }
                    ?.ifEmpty { null }
                    ?: commonFastMoves.filter { it.type in mappedTypes }
                        .ifEmpty { listOf(commonFastMoves.random()) }

                val chargedMoves = gameMasterPokemon?.chargedMoves
                    ?.mapNotNull { moveId ->
                        gameMasterLoader.getMove(moveId)?.let { move ->
                            Move(
                                name = move.name,
                                type = try {
                                    PokemonType.valueOf(move.type.uppercase())
                                } catch (e: Exception) {
                                    PokemonType.NORMAL
                                },
                                damage = move.power,
                                energy = move.energy,
                                isFast = false
                            )
                        }
                    }
                    ?.take(3)
                    ?.ifEmpty { null }
                    ?: commonChargedMoves.filter { it.type in mappedTypes }
                        .ifEmpty { listOf(commonChargedMoves.random()) }
                        .take(2)
                pokemonEntities.add(
                    PokemonEntity(
                        id = id,
                        name = name,
                        typesJson = Json.encodeToString(mappedTypes),
                        region = region,
                        baseAttack = gameMasterPokemon?.baseStats?.atk ?: 0,
                        baseDefense = gameMasterPokemon?.baseStats?.def ?: 0,
                        baseStamina = gameMasterPokemon?.baseStats?.hp ?: 0,
                        fastMovesJson = Json.encodeToString(fastMoves),
                        chargedMovesJson = Json.encodeToString(chargedMoves),
                        spriteUrl = spriteUrl,
                        cryUrl = "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/$id.ogg",
                        description = "A ${mappedTypes.joinToString("/") { it.name.lowercase() }} type Pokémon from the $region region."
                    )
                )
                
                if (pokemonEntities.size >= 100) {
                    pokemonDao.insertAll(pokemonEntities)
                    pokemonEntities.clear()
                }
            }

            if (pokemonEntities.isNotEmpty()) {
                pokemonDao.insertAll(pokemonEntities)
            }
            Log.d("PokedexViewModel", "Successfully imported 1025 Pokemon into Room")
        } catch (e: Exception) {
            Log.e("PokedexViewModel", "Error populating pokedex", e)
        }
    }

    private fun getRegionForId(id: Int): String = when {
        id <= 151 -> "Kanto"
        id <= 251 -> "Johto"
        id <= 386 -> "Hoenn"
        id <= 493 -> "Sinnoh"
        id <= 649 -> "Unova"
        id <= 721 -> "Kalos"
        id <= 809 -> "Alola"
        id <= 898 -> "Galar"
        else -> "Paldea"
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getPokemonById(id: Int): Pokemon? {
        return pokemonDao.getPokemonById(id)?.toDomain()
    }

    private fun PokemonEntity.toDomain(): Pokemon {
        val types = try { Json.decodeFromString<List<PokemonType>>(typesJson) } catch (e: Exception) { emptyList() }
        val fastMoves = try { Json.decodeFromString<List<Move>>(fastMovesJson) } catch (e: Exception) { emptyList() }
        val chargedMoves = try { Json.decodeFromString<List<Move>>(chargedMovesJson) } catch (e: Exception) { emptyList() }
        return Pokemon(
            id = id,
            name = name,
            types = types,
            region = region,
            baseAttack = baseAttack,
            baseDefense = baseDefense,
            baseStamina = baseStamina,
            fastMoves = fastMoves,
            chargedMoves = chargedMoves,
            spriteUrl = spriteUrl,
            cryUrl = cryUrl,
            description = description
        )
    }
}
