package com.example.overdex.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.overdex.CalibrationManager
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.debug.observatory.ObservationRecorder
import com.example.overdex.battle.observation.CountdownObserver
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.DroidballSignal
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.ObservationDispatcher
import com.example.overdex.battle.observation.PlayerSpeciesWitness
import com.example.overdex.battle.observation.SpeciesWitness
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.witness.AnnouncementWitness
import com.example.overdex.battle.witness.GoodEffortWitness
import com.example.overdex.battle.witness.YouWinWitness
import com.example.overdex.data.FallbackSpriteProvider
import com.example.overdex.data.FieldNoteRepository
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.GithubSpriteProvider
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonJsonLoader
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.SpeciesJsonLoader
import com.example.overdex.data.SpriteProvider
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.PokemonEntity
import com.example.overdex.data.observation.DroidballObservationInput
import com.example.overdex.model.*
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.InstrumentTree
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class PokedexViewModel(application: Application) : AndroidViewModel(application) {
    private val db = PokedexDatabase.getDatabase(application)
    private val pokemonDao = db.pokemonDao()

    val spriteProvider: SpriteProvider = FallbackSpriteProvider(
        primary = LocalSpriteProvider(application.assets),
        secondary = GithubSpriteProvider(),
    )

    private val pokemonRepository = PokemonRepository(pokemonDao, spriteProvider)
    private val pokemonLoader = PokemonJsonLoader(application)
    private val gameMasterLoader = GameMasterLoader(application)
    private val fieldNoteRepository = FieldNoteRepository(application)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _searchRequest = MutableStateFlow(SearchRequest())
    val searchRequest = _searchRequest.asStateFlow()

    private val _observationSessionState = MutableStateFlow(value = ObservationSessionState.IDLE)
    val observationSessionState = _observationSessionState.asStateFlow()

    private val _deploymentState = MutableStateFlow(InstrumentDeploymentState.IDLE)
    val deploymentState = _deploymentState.asStateFlow()

    private val _frameCount = MutableStateFlow(0L)
    val frameCount = _frameCount.asStateFlow()

    private var observationDispatcher = ObservationDispatcher()

    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch = _activeMatch.asStateFlow()

    fun getFieldNotes(pokemonId: Int) = flow {
        emit(fieldNoteRepository.getFieldNotesForPokemon(pokemonId))
    }.flowOn(Dispatchers.IO)

    // Instrument Workspace
    private val instrumentTree = InstrumentTree(
        listOf(
            ActionNode("OVERDEX", InstrumentCommand.OpenSearch),
            DirectoryNode("BATTLE", listOf(
                ActionNode("Roster", InstrumentCommand.OpenCollection),
                ActionNode("History", InstrumentCommand.OpenBattleHistory)
            )),
            ActionNode("OBSERVE", InstrumentCommand.OpenCalibration),
            DirectoryNode("TRAINER", listOf(
                ActionNode("Profile", InstrumentCommand.OpenProfile)
            ))
        )
    )

    private val _treeState = MutableStateFlow(instrumentTree.getState())
    val treeState = _treeState.asStateFlow()

    // Command handling
    private val _pendingCommand = MutableSharedFlow<InstrumentCommand>(extraBufferCapacity = 1)
    val pendingCommand = _pendingCommand.asSharedFlow()

    fun handleUp() {
        instrumentTree.moveSelection(-1)
        _treeState.value = instrumentTree.getState()
    }

    fun handleDown() {
        instrumentTree.moveSelection(1)
        _treeState.value = instrumentTree.getState()
    }

    fun handleA() {
        instrumentTree.executeSelected()?.let { command ->
            _pendingCommand.tryEmit(command)
        }
        _treeState.value = instrumentTree.getState()
    }

    fun handleB() {
        if (!instrumentTree.navigateBack()) {
            // Root back action if needed, or ignore
        }
        _treeState.value = instrumentTree.getState()
    }

    // App-session state for the boot sequence
    private val _hasBootedInSession = MutableStateFlow(value = false)
    val hasBootedInSession = _hasBootedInSession.asStateFlow()

    fun markBooted() {
        _hasBootedInSession.value = true
    }

    fun toggleObservation() {
        val current = _deploymentState.value
        if (current == InstrumentDeploymentState.IDLE) {
            startObservation()
        } else if (current == InstrumentDeploymentState.OBSERVING || 
                   current == InstrumentDeploymentState.DEPLOYING ||
                   current == InstrumentDeploymentState.READY) {
            stopObservation()
        }
    }

    fun startObservation() {
        if (_deploymentState.value != InstrumentDeploymentState.IDLE) return
        _deploymentState.value = InstrumentDeploymentState.REQUESTING_PERMISSIONS
    }

    fun onPermissionsGranted() {
        _deploymentState.value = InstrumentDeploymentState.READY
    }

    fun deployInstrument(resultCode: Int, data: android.content.Intent) {
        _deploymentState.value = InstrumentDeploymentState.DEPLOYING
        
        // Initialize Match
        val matchId = java.util.UUID.randomUUID().toString()
        val match = Match(
            matchId = matchId,
            custody = InMemoryTestimonyCustody(),
            realityTimeline = InMemoryRealityTimeline(),
            pokemonKnowledge = pokemonRepository
        )
        _activeMatch.value = match
        _frameCount.value = 0
        Log.d("DEPLOY", "1 Match created")
        
        // Re-initialize dispatcher to ensure observers are registered exactly once per deployment
        observationDispatcher = ObservationDispatcher()
        
        // Load calibration and register production observers
        val input = DroidballObservationInput()
        val calibration = CalibrationManager(getApplication()).load()
        
        Log.d("DEPLOY", "2 Registering observers")
        observationDispatcher.register(SpeciesWitness(input, calibration))
        observationDispatcher.register(PlayerSpeciesWitness(input, calibration))
        observationDispatcher.register(CountdownObserver(input, calibration))
        observationDispatcher.register(YouWinWitness(input, calibration))
        observationDispatcher.register(GoodEffortWitness(input, calibration))
        observationDispatcher.register(AnnouncementWitness(input, calibration))

        // Start Service
        DroidballService.start(getApplication(), resultCode, data)
        startDroidBallService()

        // Start Observation lifecycle
        Log.d("DEPLOY", "3 Starting observers")
        observationDispatcher.startAll(match)
        
        // Listen for signals
        viewModelScope.launch {
            DroidballService.signals.collect { signal ->
                when (signal) {
                    is DroidballSignal.Started -> {
                        // Service is up, but no frames yet
                        val now = System.currentTimeMillis()
                        match.realityTimeline.append(
                            RealityArticle(
                                id = ArticleId(java.util.UUID.randomUUID().toString()),
                                perceivedAt = now,
                                recordedAt = now,
                                sourceId = SourceId("DroidballService"),
                                payload = RawTestimony("DroidballService started")
                            )
                        )
                    }
                    is DroidballSignal.FrameCaptured -> {
                        if (_deploymentState.value == InstrumentDeploymentState.DEPLOYING || _deploymentState.value == InstrumentDeploymentState.READY) {
                            _deploymentState.value = InstrumentDeploymentState.OBSERVING
                        }
                        match.incrementFrameCount()
                        _frameCount.value = match.frameCount
                    }
                    is DroidballSignal.Stopped -> {
                        _deploymentState.value = InstrumentDeploymentState.IDLE
                    }
                    is DroidballSignal.Error -> {
                        Log.e("DROIDBALL_SERVICE", "Error: ${signal.message}")
                        stopObservation()
                    }
                    is DroidballSignal.CountdownWitnessed -> {
                        // Git #275: Publication only. No presentation or intelligence yet.
                    }
                }
            }
        }
    }

    fun stopObservation() {
        _deploymentState.value = InstrumentDeploymentState.RETURNING
        DroidballService.stop(getApplication())
        stopDroidBallService()
        observationDispatcher.stopAll()
        _activeMatch.value = null
        _deploymentState.value = InstrumentDeploymentState.IDLE
    }

    fun startDroidBallService() {
        setObservationSessionState(ObservationSessionState.SERVICE_ACTIVE)
    }

    fun stopDroidBallService() {
        if (_observationSessionState.value == ObservationSessionState.SERVICE_ACTIVE) {
            setObservationSessionState(ObservationSessionState.IDLE)
        }
    }

    fun setObservationSessionState(state: ObservationSessionState) {
        val oldState = _observationSessionState.value
        _observationSessionState.value = state
        
        // Lifecycle management for ObservationRecorder
        if (state == ObservationSessionState.SERVICE_ACTIVE && oldState != ObservationSessionState.SERVICE_ACTIVE) {
            // Droidball deployed - Start recording
            ObservationRecorder.startRecording(getApplication()) 
        } else if (state != ObservationSessionState.SERVICE_ACTIVE && oldState == ObservationSessionState.SERVICE_ACTIVE) {
            // Droidball docked - Stop recording
            val recording = ObservationRecorder.stopRecording()
            if (recording != null) {
                Log.d("OBSERVATION_RECORDER", "Match captured: ${recording.matchId} with ${recording.events.size} events")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedPokemon: Flow<PagingData<Pokemon>> = createSearchFlow(_searchQuery, _searchRequest.map { it.type })

    /**
     * Creates an independent, paged search flow.
     * 
     * @param queryFlow A flow of search strings.
     * @param typeFlow A flow of optional type filters.
     * @return A flow of PagingData that maintains its own loading state.
     */
    //Design Note: PokedexViewModel provides a reusable mechanism for constructing independent search sessions. Individual workflows, such as Register Specimen, own their transient search state while sharing the same repository and paging infrastructure.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun createSearchFlow(
        queryFlow: Flow<String>,
        typeFlow: Flow<PokemonType?> = flowOf(null)
    ): Flow<PagingData<Pokemon>> {
        return combine(queryFlow, typeFlow) { q, t -> q to t }
            .flatMapLatest { (q, t) ->
                Pager(
                    config = PagingConfig(pageSize = 50, enablePlaceholders = false),
                ) {
                    pokemonRepository.search(query = q, type = t)
                }.flow
            }

            .cachedIn(viewModelScope)
    }


    init {
        viewModelScope.launch {
            Log.d("STARTUP", "1: ViewModel init [BUILD AUG27_B]")

            val currentCount = pokemonDao.getCount()
            Log.d("STARTUP", "Current Pokemon count in DB: $currentCount")

            if (currentCount < 1025) {
                Log.d("STARTUP", "2: Database incomplete. Starting populateFullPokedex")
                populateFullPokedex()
            } else {
                Log.d("STARTUP", "2: Database already populated. Skipping import.")
            }

            Log.d("STARTUP", "3: Initialization complete")
        }
    }

    suspend fun populateFullPokedex() {
        try {
            val imported = pokemonLoader.loadPokemon()
            val importedMap = imported.pokemon.associateBy { it.id }
            val speciesLoader = SpeciesJsonLoader(getApplication())

            val speciesMap =
                speciesLoader.loadSpecies()
                    .associateBy { it.id }

            Log.d("SPECIES_TEST", "Species count = ${speciesMap.size}")
            Log.d("SPECIES_TEST", "Charizard = ${speciesMap[6]}")


            val pokemonEntities = mutableListOf<PokemonEntity>()
            
            // Populate up to 1025 pokemon
            for (id in 1..1025) {
                val importedPokemon = importedMap[id]

                if (id == 5) {
                    Log.d(
                        "EVO_TEST",
                        "name=${importedPokemon?.name} prev=${importedPokemon?.prev_evolution} next=${importedPokemon?.next_evolution}"
                    )
                }

                val speciesInfo = speciesMap[id]

                val gameMasterPokemon =
                    gameMasterLoader.getPokemonByDex(id)

                val name =
                    importedPokemon?.name
                        ?: gameMasterPokemon?.speciesName
                        ?: "Pokemon #$id"
                val rawTypes =
                    importedPokemon?.type
                        ?: gameMasterPokemon?.types
                            ?.asSequence()
                            ?.filter { it != "none" }
                            ?.map { it.replaceFirstChar(Char::uppercase) }
                            ?.toList()
                        ?: listOf("Normal")
                val spriteUrl = spriteProvider.getSpriteUrl(id = id)

                if ((id == 152) || (id == 249) || (id == 445) || (id == 1000)) {
                    Log.d(
                        "TYPE_DEBUG",
                        "#$id importedPokemon=${importedPokemon != null} rawTypes=$rawTypes"
                    )
                }
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
                    ?: emptyList()

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
                    ?: emptyList()
                pokemonEntities.add(
                    PokemonEntity(
                        id = id,
                        name = name,
                        typesJson = Json.encodeToString(mappedTypes),
                        region = region,

                        genus = speciesInfo?.genus ?: "",

                        height = importedPokemon?.height ?: "",
                        weight = importedPokemon?.weight ?: "",

                        prevEvolutionsJson = Json.encodeToString(
                            importedPokemon?.prev_evolution ?: emptyList()
                        ),
                        nextEvolutionsJson = Json.encodeToString(
                            importedPokemon?.next_evolution ?: emptyList()
                        ),

                        baseAttack = gameMasterPokemon?.baseStats?.atk ?: 0,
                        baseDefense = gameMasterPokemon?.baseStats?.def ?: 0,
                        baseStamina = gameMasterPokemon?.baseStats?.hp ?: 0,

                        fastMovesJson = Json.encodeToString(fastMoves),
                        chargedMovesJson = Json.encodeToString(chargedMoves),

                        spriteUrl = spriteUrl,
                        cryUrl = "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/$id.ogg",

                        description =
                            speciesInfo?.flavor_text
                                ?: "A ${mappedTypes.joinToString("/") { it.name.lowercase() }} type Pokémon from the $region region."
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
        id <= 1025 -> "Paldea"
        else -> "Unknown"
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _searchRequest.value =
            _searchRequest.value.copy(
                text = query,
                type = if (query.isBlank()) null else _searchRequest.value.type
            )
    }

    fun removeFilter(filter: SearchRequest.ActiveFilter) {
        when (filter.label) {
            _searchRequest.value.type?.name -> {
                _searchQuery.value = ""

                _searchRequest.value =
                    _searchRequest.value.copy(
                        text = "",
                        type = null
                    )
            }
        }
    }
    fun updateTypeFilter(type: PokemonType?) {
        _searchRequest.value =
            _searchRequest.value.copy(
                type = type
            )
    }

    private val pokemonNameCache = mutableMapOf<Int, String>()

    suspend fun getPokemonName(id: Int): String {
        return pokemonNameCache[id] ?: run {
            val name = pokemonRepository.getPokemonById(id)?.name ?: "Unknown"
            pokemonNameCache[id] = name
            name
        }
    }

    suspend fun getPokemonById(id: Int): Pokemon? {
        return pokemonRepository.getPokemonById(id)
    }

    suspend fun getPokemonByName(name: String): Pokemon? {
        return pokemonRepository.getPokemonByName(name)
    }
    /**
     * Resolves the entire evolution family (names) that a given species belongs to.
     */
    suspend fun getEvolutionFamily(name: String): List<String> {
        val base = getPokemonByName(name) ?: return emptyList()
        val family = mutableSetOf<String>()
        family.add(base.name)
        base.prevEvolutions.forEach { family.add(it.name) }
        base.nextEvolutions.forEach { family.add(it.name) }
        return family.toList()
    }
}
