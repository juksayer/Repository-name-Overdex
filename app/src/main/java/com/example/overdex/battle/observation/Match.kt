package com.example.overdex.battle.observation

import com.example.overdex.BattleMemory
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.PlayerTeamRosterSlotWitnessed
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SourceAvailabilityRecord
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.battle.custody.WitnessOperating
import com.example.overdex.battle.custody.VsScreenWitnessed
import android.util.Log
import com.example.overdex.battle.interpretation.BattleInterpreter
import com.example.overdex.battle.inference.FastMoveCadenceInference
import com.example.overdex.battle.inference.FastMoveEnergyInference
import com.example.overdex.battle.inference.FastMoveUseInference
import com.example.overdex.battle.inference.ChargedMoveAnnouncementInference
import com.example.overdex.battle.inference.ChargeMoveQteVibrationInference
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.reality.RealityTimeline
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.BattleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Represents the record surrounding one possible Pokémon GO battle.
 * 
 * The record begins when Droidball deploys, so it can preserve VS, cries, and
 * countdown evidence. An accepted GO separately establishes live battle timing.
 * 
 * @property matchId A unique identifier for the battle.
 * @property state The current lifecycle phase of the match.
 * @property workspace The mutable storage area where incoming observations are collected.
 * @property custody The briefcase for preserving accepted testimony.
 * @property realityTimeline The foundational ledger for preserving the objective history.
 */
class Match(
    @Suppress("unused") val matchId: String,
    var state: MatchState = MatchState.CREATED,
    val workspace: BattleWorkspace = BattleWorkspace(),
    val custody: TestimonyCustody,
    val realityTimeline: RealityTimeline,
    val pokemonKnowledge: PokemonKnowledge,
    val battleMemory: BattleMemory = BattleMemory()
) {
    private val interpreter = BattleInterpreter(pokemonKnowledge)
    private val countdownMatchStartInference = com.example.overdex.battle.inference.CountdownMatchStartInference()
    private val fastMoveCadenceInference = FastMoveCadenceInference(pokemonKnowledge)
    private val playerInformationInference = com.example.overdex.battle.inference.PlayerBattleInformationInference(pokemonKnowledge)
    private val fastMoveEnergyInference = FastMoveEnergyInference(pokemonKnowledge)
    private val opponentEnergyForecast = com.example.overdex.battle.forecast.OpponentEnergyForecast()

    internal fun observeForecastBattlefield(at: Long, bothBarsVisible: Boolean) {
        opponentEnergyForecast.observeBattlefield(at, bothBarsVisible)
    }
    private val fastMoveUseInference = FastMoveUseInference { impact ->
        // Capture the sound when the first raw impact arrives, without waiting
        // for the bounded corroboration buffer to publish the fused use.
        DroidballService.requestCueCenteredAudio(
            impact.id.value, com.example.overdex.battle.audio.BattleCryCueKind.FAST_MOVE_IMPACT,
            impact.monotonicTimeNanos
        )
    }
    private val chargedMoveAnnouncementInference = ChargedMoveAnnouncementInference(pokemonKnowledge)
    private val chargeMoveQteVibrationInference = ChargeMoveQteVibrationInference()

    /** Owned by this match and baselined by the session at the accepted start boundary. */
    val clock = MatchClock()

    private val _matchStarted = MutableSharedFlow<RealityArticle>(replay = 1, extraBufferCapacity = 1)
    /** The observed GO or combat-confirmed countdown boundary for the owning session. */
    val matchStarted = _matchStarted.asSharedFlow()

    // Crop capture can publish several independent witness articles per frame while
    // OCR is still reading an earlier one. Keep the whole practical recording burst
    // available to downstream witnesses; a dropped CropCaptured article is a dropped
    // opportunity to identify an otherwise preserved combatant.
    private val _articles = MutableSharedFlow<RealityArticle>(
        replay = 4_096,
        extraBufferCapacity = 4_096,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Accepted Timeline articles for downstream recognizers; the Timeline remains authoritative. */
    val articles = _articles.asSharedFlow()

    // Species OCR has one job: inspect the two purpose-specific name strips.
    // Do not make it wait behind every HP, timer, and announcement crop in the
    // general article stream.  The Timeline is appended first; this is only a
    // small, post-publication delivery lane for the two species witnesses.
    private val _activeSpeciesCropArticles = MutableSharedFlow<RealityArticle>(
        replay = 256,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val activeSpeciesCropArticles = _activeSpeciesCropArticles.asSharedFlow()

    // Active HP bars have their own post-publication lane so their geometry and
    // fill measurements are never queued behind unrelated crop witnesses.
    private val _activeHpCropArticles = MutableSharedFlow<RealityArticle>(
        replay = 128,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val activeHpCropArticles = _activeHpCropArticles.asSharedFlow()

    // Custody-to-Timeline delivery must keep moving while screen capture is busy.
    // A dedicated serial lane also preserves the ledger order without borrowing
    // the Default pool that image capture and image processing can saturate.
    private val matchDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "overdex-match-$matchId").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val matchScope = CoroutineScope(matchDispatcher + SupervisorJob())
    private val playerRosterBySlot = ConcurrentHashMap<Int, String>()
    private val playerRosterIdBySlot = ConcurrentHashMap<Int, Int>()
    private val playerRosterArticleBySlot = ConcurrentHashMap<Int, ArticleId>()
    private val configuredRosterArticleBySlot = ConcurrentHashMap<Int, ArticleId>()
    private val entrySideTracker = EntryAnnouncementSideTracker()
    @Volatile private var playerRosterContradicted = false
    private val openingCrySideGate = OpeningCrySideGate()
    val speciesChecks = SpeciesCheckCoordinator { measurement, refs ->
        custody.submitTestimony(SourceId("SPECIES_CHECK_COORDINATOR"), measurement,
            System.currentTimeMillis(), null, refs, System.nanoTime())
    }

    private val countdownCuesSeen = mutableSetOf<String>()
    private var vsCueSeen = false
    private var lastEntryText: String? = null
    private var lastEntryAt = Long.MIN_VALUE
    private val activeSpeciesBySide = ConcurrentHashMap<ActivePokemonSide, String>()
    private val activeSpeciesAtBySide = ConcurrentHashMap<ActivePokemonSide, Long>()
    @Volatile var observationAttemptStartedAtNanos: Long = System.nanoTime()
        private set

    private fun cueSpeciesChecks(article: RealityArticle) {
        val payload = article.payload
        val text = ((payload as? RawTestimony)?.data as? String)?.trim()
        val at = article.monotonicTimeNanos ?: System.nanoTime()
        if (payload is CountdownGlyphWitnessed && !countdownCuesSeen.add(payload.glyph)) return
        if (payload is VsScreenWitnessed) {
            if (vsCueSeen) return
            vsCueSeen = true
        }
        if (article.sourceId.id == "ANNOUNCEMENT_WITNESS" && text?.startsWith("Go,", true) == true) {
            val repeated = lastEntryText.equals(text, true) && at - lastEntryAt < 2_000_000_000L
            lastEntryText = text
            lastEntryAt = at
            if (repeated) return
        }
        val switchedSide = (payload as? ActivePokemonSpeciesWitnessed)?.let { witnessed ->
            if (at < (activeSpeciesAtBySide[witnessed.side] ?: Long.MIN_VALUE)) return@let null
            activeSpeciesAtBySide[witnessed.side] = at
            val prior = activeSpeciesBySide.put(witnessed.side, witnessed.speciesName)
            witnessed.side.takeIf { prior != null && !prior.equals(witnessed.speciesName, ignoreCase = true) }
        }
        val reason = when {
            payload is VsScreenWitnessed -> "VS"
            payload is CountdownGlyphWitnessed -> "COUNTDOWN"
            payload is MatchStarted -> "MATCH_START"
            article.sourceId.id == "ANNOUNCEMENT_WITNESS" && text?.startsWith("Go,", true) == true -> "ENTRY"
            payload is ActivePokemonFaintedWitnessed -> {
                // A delayed resource count can correctly faint the departed
                // combatant after its replacement has already been identified.
                // Do not erase the replacement's move identity in that case.
                val active = activeSpeciesBySide[payload.side]
                if (payload.speciesName != null && active != null &&
                    !active.equals(payload.speciesName, ignoreCase = true)
                ) return
                "FAINT"
            }
            // A side-located badge just completed this measurement. Reopening
            // OCR from its own result adds no independent evidence and queues
            // another full crop pair during the next opponent's entrance.
            switchedSide != null && !isSideLocatedSpeciesSource(article.sourceId.id) -> "SWITCH"
            payload is com.example.overdex.battle.custody.BattleCryCandidatesMeasured &&
                com.example.overdex.battle.audio.BattleCryListeningCue.accepts(payload.cueKind) -> "CRY"
            else -> return
        }
        val sides = when {
            payload is ActivePokemonFaintedWitnessed -> listOf(payload.side)
            switchedSide != null -> listOf(switchedSide)
            payload is com.example.overdex.battle.custody.BattleCryCandidatesMeasured ->
                openingCrySideGate.sidesFor(
                    payload.candidates.map { it.speciesId to it.similarity },
                    playerLeadSpeciesId()
                )
            else -> ActivePokemonSide.entries
        }
        when (reason) {
            "ENTRY" -> DroidballService.requestCueCenteredAudio(
                article.id.value,
                com.example.overdex.battle.audio.BattleCryCueKind.SPECIES_ENTRY,
                article.monotonicTimeNanos
            )
        }
        sides.forEach { side -> speciesChecks.request(side, reason, article.id.value,
            article.monotonicTimeNanos ?: System.nanoTime()) }
    }


    /**
     * A saved team supplies positive membership, not proof of the team actually
     * brought to this battle. Exclusion requires all three witnessed Team Select
     * slots, and independent badge evidence always has priority.
     */
    fun sideForRosterKnownSpecies(speciesName: String): ActivePokemonSide? =
        entrySpeciesAttribution(speciesName)?.side

    fun entrySpeciesAttribution(speciesName: String, entryAtNanos: Long? = null): EntrySpeciesAttribution? {
        val attribution = entrySideTracker.attribute(
            speciesName, playerRosterSpecies(),
            allowRosterExclusion = !playerRosterContradicted && playerRosterArticleBySlot.size == 3,
            entryAtNanos = entryAtNanos,
        ) ?: return null
        return if (attribution.basis in setOf("PLAYER_ROSTER", "ABSENT_FROM_WITNESSED_PLAYER_ROSTER")) {
            attribution.copy(evidenceArticleIds =
                (configuredRosterArticleBySlot.values + playerRosterArticleBySlot.values).map { it.value }.distinct())
        } else attribution
    }

    /** An unsided move announcement cannot select a trainer in an established mirror. */
    fun sideForNamedMovePerformer(speciesName: String): ActivePokemonSide? {
        val key = TeamRosterSpeciesAttributor.speciesKey(speciesName)
        val activeSides = activeSpeciesBySide.filterValues {
            TeamRosterSpeciesAttributor.speciesKey(it) == key
        }.keys
        return when (activeSides.size) {
            0 -> sideForRosterKnownSpecies(speciesName)
            1 -> activeSides.single()
            else -> null
        }
    }

    fun playerRosterSpecies(): Collection<String> =
        if (playerRosterContradicted) emptyList() else playerRosterBySlot.values.toList()

    fun playerLeadSpeciesId(): Int? = playerRosterIdBySlot[1]
        .takeIf { !playerRosterContradicted && playerRosterArticleBySlot.containsKey(1) }

    /** Clears only mutable conclusions from an abandoned pre-battle attempt. */
    fun restartPreBattleObservationAttempt() {
        observationAttemptStartedAtNanos = System.nanoTime()
        countdownMatchStartInference.reset()
        speciesChecks.restartObservationAttempt()
        playerRosterBySlot.clear()
        playerRosterIdBySlot.clear()
        playerRosterArticleBySlot.clear()
        configuredRosterArticleBySlot.clear()
        playerRosterContradicted = false
        entrySideTracker.reset()
        openingCrySideGate.reset()
        countdownCuesSeen.clear()
        vsCueSeen = false
        lastEntryText = null
        lastEntryAt = Long.MIN_VALUE
        activeSpeciesBySide.clear()
        activeSpeciesAtBySide.clear()
    }

    /** The total number of frames processed during this Match. */
    var frameCount: Long = 0
        private set

    init {
        DroidballOverlayPresentation.beginEnergyForecast(matchId)
        // Presentation consumes the same published evidence, including derived
        // uses/spends, but its elapsed-time hypothesis never returns to custody.
        matchScope.launch {
            val loadedSpecies = mutableSetOf<String>()
            articles.collect { article ->
                val name = when (val p = article.payload) {
                    is ActivePokemonSpeciesWitnessed -> p.speciesName.takeIf { p.side == ActivePokemonSide.OPPONENT }
                    is FastMoveIdentified -> p.speciesName.takeIf { p.side == ActivePokemonSide.OPPONENT }
                    is com.example.overdex.battle.custody.FastMoveUseObserved -> p.attackerSpeciesName
                        .takeIf { p.attackingSide == ActivePokemonSide.OPPONENT }
                    else -> null
                }
                if (name != null && name !in loadedSpecies) {
                    pokemonKnowledge.getPokemonByName(name)?.let { species ->
                        opponentEnergyForecast.registerMoves(name, species.fastMoves)
                        loadedSpecies += name
                    }
                }
                opponentEnergyForecast.observe(article)
            }
        }
        matchScope.launch {
            while (!speciesChecks.isStopped()) {
                DroidballOverlayPresentation.setOpponentEnergyEstimate(matchId, opponentEnergyForecast.estimate(System.nanoTime()))
                kotlinx.coroutines.delay(250)
            }
        }
        matchScope.launch {
            while (!speciesChecks.isStopped()) { speciesChecks.tick(); kotlinx.coroutines.delay(50) }
        }
        matchScope.launch {
            custody.custodyRecordFlow.collect { record ->
                val availability = record as? SourceAvailabilityRecord ?: return@collect
                val article = RealityArticle(
                    id = ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = availability.timestamp,
                    recordedAt = System.currentTimeMillis(),
                    sourceId = availability.sourceId,
                    payload = WitnessOperating(availability.available),
                    sequenceNumber = availability.sequenceNumber,
                    matchId = MatchId(matchId),
                    monotonicTimeNanos = availability.monotonicTimeNanos
                )
                realityTimeline.append(article)
                _articles.tryEmit(article)
                battleMemory.timeline.record(article)
            }
        }
        matchScope.launch {
            var matchStartRecorded = false
            var matchEndRecorded = false
            custody.testimonyFlow.collect { testimony ->
                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "Match received TestimonyRecord: type=${testimony.payload::class.simpleName}, sourceId=${testimony.sourceId.id}, confidence=${testimony.confidence}, sequence=${testimony.sequenceNumber}, refs=${testimony.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = testimony.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match received TestimonyRecord: type=${p::class.simpleName}, species=${p.species}, sourceId=${testimony.sourceId.id}, confidence=${testimony.confidence}, sequence=${testimony.sequenceNumber}, refs=${testimony.evidenceReferences}")
                }

                (testimony.payload as? PlayerTeamRosterSlotWitnessed)?.let { rosterEntry ->
                    playerRosterBySlot[rosterEntry.slot] = rosterEntry.speciesName
                    rosterEntry.speciesId?.let { playerRosterIdBySlot[rosterEntry.slot] = it }
                    DroidballOverlayPresentation.recordInferredPlayerRosterSlot(rosterEntry.slot, rosterEntry.speciesName)
                }
                (testimony.payload as? PlayerTeamSlotConfigured)?.let { configured ->
                    if (playerRosterBySlot[configured.slot]?.let(TeamRosterSpeciesAttributor::speciesKey) !=
                        TeamRosterSpeciesAttributor.speciesKey(configured.speciesName)) {
                        // Editing a saved slot is not a fresh Team Select scan.
                        playerRosterArticleBySlot.remove(configured.slot)
                        entrySideTracker.discardRosterAssumptions()
                    }
                    playerRosterBySlot[configured.slot] = configured.speciesName
                    playerRosterIdBySlot[configured.slot] = configured.speciesId
                }

                val article = RealityArticle(
                    id = ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = testimony.timestamp,
                    recordedAt = System.currentTimeMillis(),
                    sourceId = testimony.sourceId,
                    payload = testimony.payload,
                    confidence = testimony.confidence,
                    sequenceNumber = testimony.sequenceNumber,
                    evidenceReferences = testimony.evidenceReferences,
                    matchId = MatchId(matchId),
                    monotonicTimeNanos = testimony.monotonicTimeNanos
                )
                (article.payload as? PlayerTeamRosterSlotWitnessed)?.let { rosterEntry ->
                    playerRosterArticleBySlot[rosterEntry.slot] = article.id
                }
                (article.payload as? PlayerTeamSlotConfigured)?.let {
                    configuredRosterArticleBySlot[it.slot] = article.id
                }
                (article.payload as? ActivePokemonFaintedWitnessed)?.let { faint ->
                    article.monotonicTimeNanos?.let { at ->
                        entrySideTracker.observeFaint(faint.side, faint.speciesName, at, article.id.value)
                    }
                }
                (article.payload as? ActivePokemonSpeciesWitnessed)
                    ?.takeIf { isSideLocatedSpeciesSource(article.sourceId.id) }
                    ?.let { badge ->
                        DroidballOverlayPresentation.recordBattleBadgePresent()
                        article.monotonicTimeNanos?.let { at ->
                            entrySideTracker.observeBadge(badge.side, badge.speciesName, article.id.value, at)
                        }
                        if (badge.side == ActivePokemonSide.PLAYER && playerRosterBySlot.isNotEmpty() &&
                            playerRosterBySlot.values.none {
                                TeamRosterSpeciesAttributor.speciesKey(it) == TeamRosterSpeciesAttributor.speciesKey(badge.speciesName)
                            }) {
                            playerRosterContradicted = true
                            entrySideTracker.discardRosterAssumptions()
                        }
                    }

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${article.payload::class.simpleName}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = article.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${p::class.simpleName}, species=${p.species}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }

                realityTimeline.append(article)
                cueSpeciesChecks(article)
                _articles.tryEmit(article)
                val capturedCropName = (article.payload as? com.example.overdex.battle.custody.CropCaptured)
                    ?.cropProvenance?.cropName
                if (capturedCropName == BattleCropContracts.playerActiveSpeciesText.cropName ||
                    capturedCropName == BattleCropContracts.opponentActiveSpeciesText.cropName
                ) {
                    _activeSpeciesCropArticles.tryEmit(article)
                }
                if (capturedCropName == BattleCropContracts.playerHpEvidence.cropName ||
                    capturedCropName == BattleCropContracts.opponentHpEvidence.cropName
                ) {
                    _activeHpCropArticles.tryEmit(article)
                }
                if (article.payload is ActivePokemonSpeciesWitnessed ||
                    article.payload is ActivePokemonTypesWitnessed ||
                    article.payload is AttackIncoming ||
                    article.payload is GetReadyWitnessed ||
                    article.payload is ChargeMoveUsedAnnounced
                ) {
                    // Presentation is allowed to react immediately to accepted
                    // evidence. The ViewModel receives the matching signal to
                    // preserve the separate BattleOverlayOpened article.
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.BattleHudWitnessed)
                }
                (article.payload as? ActivePokemonSpeciesWitnessed)?.let { witnessed ->
                    if ((article.monotonicTimeNanos ?: Long.MAX_VALUE) <
                        (activeSpeciesAtBySide[witnessed.side] ?: Long.MIN_VALUE)) return@let
                    val species = pokemonKnowledge.getPokemonByName(witnessed.speciesName)
                    if (witnessed.side == ActivePokemonSide.PLAYER) {
                        DroidballOverlayPresentation.setActivePlayerTypes(species?.types.orEmpty())
                    } else {
                        DroidballOverlayPresentation.recordOpponentSpecies(
                            speciesName = witnessed.speciesName,
                            speciesId = witnessed.speciesId ?: species?.id,
                            possibleFastMoves = species?.fastMoves
                                ?.filter { it.isFast && it.turns != null }
                                ?.map { it.name to it.type }
                                .orEmpty(),
                            possibleChargedMoves = species?.chargedMoves?.map { it.name to it.type }.orEmpty(),
                            provisional = article.sourceId.id == "DECISIVE_BATTLE_CRY_SPECIES_WITNESS",
                            observedAtNanos = article.monotonicTimeNanos,
                        )
                    }
                }
                (article.payload as? ActivePokemonFaintedWitnessed)?.let { witnessed ->
                    if (witnessed.side == ActivePokemonSide.OPPONENT) {
                        DroidballOverlayPresentation.markOpponentFainted(witnessed.speciesName)
                    }
                }
                (article.payload as? com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured)?.let { measured ->
                    DroidballOverlayPresentation.recordOpponentResources(measured, article.monotonicTimeNanos ?: 0L)
                    if (measured.resource == com.example.overdex.battle.custody.OpponentBattleResource.POKE_BALLS && measured.visibleCount == 0) {
                        DroidballOverlayPresentation.markOpponentTeamFainted()
                    }
                }
                (article.payload as? PlayerTeamSlotConfigured)
                    ?.takeIf { it.slot == 1 }
                    ?.let { configuredLead ->
                        val species = pokemonKnowledge.getPokemonByName(configuredLead.speciesName)
                        DroidballOverlayPresentation.setActivePlayerTypes(species?.types.orEmpty())
                    }

                (article.payload as? ActivePokemonSpeciesWitnessed)?.let {
                    speciesChecks.delivered(it.side, it.speciesName, article.id.value)
                }

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }
                if (testimony.payload is PokemonIdentified) {
                    Log.d("SPECIES_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }

                battleMemory.timeline.record(article)

                // Prefer witnessed GO; a countdown prediction needs later combat
                // to establish the clock. Independently measured combat can enable
                // use accounting even when the exact start remains unknown.
                if (!matchStartRecorded) {
                    val started = interpreter.interpretMatchStart(article) ?: article
                        .takeIf { (it.monotonicTimeNanos ?: Long.MIN_VALUE) >= observationAttemptStartedAtNanos }
                        ?.let(countdownMatchStartInference::accept)
                    started?.let { derivedArticle ->
                        realityTimeline.append(derivedArticle)
                        cueSpeciesChecks(derivedArticle)
                        _articles.tryEmit(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                        matchStartRecorded = true
                        fastMoveUseInference.accept(derivedArticle)
                        appendFastMoveEnergyDerivations(derivedArticle)
                        appendFastMoveCadenceDerivations(derivedArticle)
                        appendTeamSelectLeadAtMatchStart(derivedArticle)
                        _matchStarted.tryEmit(derivedArticle)
                        Log.d("MATCH_START", "Derived MatchStarted article appended: articleId=${derivedArticle.id.value}, predecessor=${article.id.value}")
                    }
                }

                appendPlayerInformationChecks(article)
                appendChargeMoveQteDerivation(article)
                appendFastMoveEnergyDerivations(article)

                appendFastMoveUseDerivations(article)

                appendFastMoveCadenceDerivations(article)

                chargedMoveAnnouncementInference
                    .accept(article, ::sideForRosterKnownSpecies)
                    ?.let { derivation ->
                        val derivedArticle = RealityArticle(
                            id = ArticleId(UUID.randomUUID().toString()),
                            perceivedAt = derivation.observedArticle.perceivedAt,
                            recordedAt = System.currentTimeMillis(),
                            sourceId = SourceId("CHARGED_MOVE_ANNOUNCEMENT_INTERPRETER"),
                            payload = derivation.payload,
                            predecessorIds = derivation.predecessorIds,
                            matchId = MatchId(matchId),
                            monotonicTimeNanos = derivation.observedArticle.monotonicTimeNanos
                        )
                        realityTimeline.append(derivedArticle)
                        _articles.tryEmit(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                        appendChargeMoveQteDerivation(derivedArticle)
                        if (derivation.payload.side == ActivePokemonSide.OPPONENT) {
                            DroidballOverlayPresentation.recordOpponentChargedMove(
                                derivation.payload.speciesName,
                                derivation.payload.moveName,
                            )
                        }
                    }

                interpreter.interpretAttackIncoming(article)?.let { derivedArticle ->
                    realityTimeline.append(derivedArticle)
                    _articles.tryEmit(derivedArticle)
                    battleMemory.timeline.record(derivedArticle)
                    appendChargeMoveQteDerivation(derivedArticle)
                    appendFastMoveUseDerivations(derivedArticle)
                    Log.d("ATTACK_SLICE", "Derived AttackIncoming article appended: articleId=${derivedArticle.id.value}, predecessor=${derivedArticle.predecessorIds.firstOrNull()?.value}")
                }

                (article.payload as? CountdownGlyphWitnessed)?.let { glyph ->
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.CountdownWitnessed(glyph.glyph))
                    // A countdown-shaped battlefield animation may fool this
                    // crop later in combat. It may still remain raw visual
                    // testimony, but it must not open a new battle-cry window.
                    if (!matchStartRecorded && glyph.glyph in setOf("3", "2", "1", "GO")) {
                        DroidballService.requestCueCenteredAudio(article.id.value,
                            com.example.overdex.battle.audio.BattleCryCueKind.valueOf("COUNTDOWN_${glyph.glyph}"),
                            article.monotonicTimeNanos)
                    }
                    Log.d("COUNTDOWN_SLICE", "Countdown glyph article received: articleId=${article.id.value}, value=${glyph.glyph}")
                }
                if (article.payload is VsScreenWitnessed) {
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.VsScreenWitnessed)
                }
                val announcement = (article.payload as? RawTestimony)?.data as? String
                if (article.sourceId.id == "ANNOUNCEMENT_WITNESS" &&
                    announcement?.trim()?.uppercase()?.startsWith("GO,") == true
                ) {
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.BattleHudWitnessed)
                }

                interpreter.interpret(article)?.let { event ->
                    battleMemory.recordEvent(event)
                    if (event.type == BattleEventType.BATTLE_ENDED && event.result != null && !matchEndRecorded) {
                        // Preserve every raw result reading, including the later
                        // result screen that returns after the summary card, but
                        // establish exactly one semantic Match end at the first
                        // accepted outcome phrase.
                        matchEndRecorded = true
                        val derivedArticle = RealityArticle(
                            id = ArticleId(UUID.randomUUID().toString()),
                            perceivedAt = article.perceivedAt,
                            recordedAt = System.currentTimeMillis(),
                            sourceId = SourceId("BATTLE_INTERPRETER"),
                            payload = MatchEnded(event.result),
                            predecessorIds = listOf(article.id),
                            matchId = MatchId(matchId),
                            monotonicTimeNanos = article.monotonicTimeNanos
                        )
                        realityTimeline.append(derivedArticle)
                        if (derivedArticle.payload is MatchEnded) {
                            speciesChecks.stop()
                            appendFastMoveUseDerivations(derivedArticle)
                        }
                        _articles.tryEmit(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                    }
                }
            }
        }
    }

    private suspend fun appendPlayerInformationChecks(article: RealityArticle) {
        playerInformationInference.accept(article).forEach { check ->
            val derived = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = check.observedArticle.perceivedAt,
                recordedAt = System.currentTimeMillis(),
                sourceId = SourceId("PLAYER_INFORMATION_COMPARISON"),
                payload = check.payload, predecessorIds = check.predecessorIds,
                matchId = MatchId(matchId), monotonicTimeNanos = check.observedArticle.monotonicTimeNanos,
            )
            realityTimeline.append(derived)
            _articles.tryEmit(derived)
            battleMemory.timeline.record(derived)
        }
    }

    private suspend fun appendFastMoveUseDerivations(article: RealityArticle) {
        fastMoveUseInference.accept(article).forEach { derivation ->
            val derivedArticle = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = derivation.observedArticle.perceivedAt,
                recordedAt = System.currentTimeMillis(),
                sourceId = SourceId("FAST_MOVE_USE_INTERPRETER"),
                payload = derivation.payload,
                predecessorIds = derivation.predecessorIds,
                confidence = derivation.confidence,
                matchId = MatchId(matchId),
                monotonicTimeNanos = derivation.observedArticle.monotonicTimeNanos
            )
            realityTimeline.append(derivedArticle)
            _articles.tryEmit(derivedArticle)
            battleMemory.timeline.record(derivedArticle)
            appendFastMoveEnergyDerivations(derivedArticle)
            appendFastMoveCadenceDerivations(derivedArticle)
        }
    }

    private fun appendChargeMoveQteDerivation(article: RealityArticle) {
        chargeMoveQteVibrationInference.accept(article)?.let { derivation ->
            val derivedArticle = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = derivation.observedArticle.perceivedAt,
                recordedAt = System.currentTimeMillis(),
                sourceId = SourceId("CHARGE_MOVE_QTE_VIBRATION_INTERPRETER"),
                payload = derivation.payload,
                predecessorIds = derivation.predecessorIds,
                confidence = derivation.confidence,
                matchId = MatchId(matchId),
                monotonicTimeNanos = derivation.observedArticle.monotonicTimeNanos
            )
            realityTimeline.append(derivedArticle)
            _articles.tryEmit(derivedArticle)
            battleMemory.timeline.record(derivedArticle)
        }
    }

    private suspend fun appendFastMoveEnergyDerivations(article: RealityArticle) {
        fastMoveEnergyInference.accept(article).forEach { derivation ->
            val derivedArticle = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = derivation.observedArticle.perceivedAt,
                recordedAt = System.currentTimeMillis(),
                sourceId = SourceId("FAST_MOVE_ENERGY_INTERPRETER"),
                payload = derivation.payload,
                predecessorIds = derivation.predecessorIds,
                confidence = derivation.confidence,
                matchId = MatchId(matchId),
                monotonicTimeNanos = derivation.observedArticle.monotonicTimeNanos
            )
            realityTimeline.append(derivedArticle)
            _articles.tryEmit(derivedArticle)
            battleMemory.timeline.record(derivedArticle)
        }
    }

    /**
     * Team Select slot 1 is the player's lead. Publish that known identity at GO
     * when a transient entry announcement was missed, while retaining both the
     * roster observation and MatchStarted as causal predecessors.
     */
    private suspend fun appendTeamSelectLeadAtMatchStart(matchStartArticle: RealityArticle) {
        // A saved slot is a move/roster configuration, not an observation of the
        // lead selected in GO. Never fabricate that appearance at the countdown.
        if (playerRosterContradicted || !playerRosterArticleBySlot.containsKey(1)) return
        val speciesName = playerRosterBySlot[1] ?: return
        // A late GO result can arrive after the player has already switched.
        // Team Select may fill an unknown lead; it must not replace a witnessed active species.
        if (activeSpeciesBySide[ActivePokemonSide.PLAYER] != null) return
        val species = pokemonKnowledge.getPokemonByName(speciesName)
        val leadArticle = RealityArticle(
            id = ArticleId(UUID.randomUUID().toString()),
            perceivedAt = matchStartArticle.perceivedAt,
            recordedAt = System.currentTimeMillis(),
            sourceId = SourceId("TEAM_SELECT_LEAD_INFERENCE"),
            payload = ActivePokemonSpeciesWitnessed(
                side = ActivePokemonSide.PLAYER,
                speciesName = speciesName,
                speciesId = playerRosterIdBySlot[1] ?: species?.id,
            ),
            predecessorIds = buildList {
                playerRosterArticleBySlot[1]?.let(::add)
                add(matchStartArticle.id)
            }.distinct(),
            confidence = 0.99f,
            matchId = MatchId(matchId),
            monotonicTimeNanos = matchStartArticle.monotonicTimeNanos,
        )
        realityTimeline.append(leadArticle)
        cueSpeciesChecks(leadArticle)
        _articles.tryEmit(leadArticle)
        battleMemory.timeline.record(leadArticle)
        speciesChecks.delivered(ActivePokemonSide.PLAYER, speciesName, leadArticle.id.value)
        DroidballOverlayPresentation.setActivePlayerTypes(species?.types.orEmpty())
        DroidballOverlayPresentation.showBattleHud()
        DroidballService.emitSignal(DroidballSignal.BattleHudWitnessed)
        fastMoveUseInference.accept(leadArticle)
        // This identity is derived locally rather than received through custody.
        // Charged-move attribution needs the same lead that Fast Move inference sees.
        chargedMoveAnnouncementInference.accept(leadArticle, ::sideForRosterKnownSpecies)
        appendFastMoveEnergyDerivations(leadArticle)
        appendFastMoveCadenceDerivations(leadArticle)
    }

    private suspend fun appendFastMoveCadenceDerivations(article: RealityArticle) {
        val derivations = fastMoveCadenceInference.accept(article)
        fastMoveCadenceInference.candidateSnapshot(ActivePokemonSide.OPPONENT)?.let { candidates ->
            opponentEnergyForecast.restrictCandidates(candidates.speciesName, candidates.remainingMoveNames)
            DroidballOverlayPresentation.recordOpponentFastMoveCandidates(
                speciesName = candidates.speciesName,
                moveNames = candidates.remainingMoveNames,
            )
        }
        derivations.forEach { derivation ->
            (derivation.payload as? FastMoveIdentified)?.let { identified ->
                fastMoveUseInference.observeIdentity(identified)
                if (identified.side == ActivePokemonSide.OPPONENT) {
                    DroidballOverlayPresentation.recordOpponentFastMove(identified)
                }
            }
            val derivedArticle = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = derivation.observedArticle.perceivedAt,
                recordedAt = System.currentTimeMillis(),
                sourceId = SourceId("FAST_MOVE_CADENCE_INTERPRETER"),
                payload = derivation.payload,
                predecessorIds = derivation.predecessorIds,
                confidence = derivation.confidence,
                matchId = MatchId(matchId),
                monotonicTimeNanos = derivation.observedArticle.monotonicTimeNanos
            )
            realityTimeline.append(derivedArticle)
            _articles.tryEmit(derivedArticle)
            battleMemory.timeline.record(derivedArticle)
            appendPlayerInformationChecks(derivedArticle)
            appendFastMoveEnergyDerivations(derivedArticle)
        }
    }


    /**
     * Submits a transient observation to the match workspace.
     */
    fun submit(observation: Observation) {
        workspace.add(observation)
    }

    /**
     * Increments the frame count.
     */
    fun incrementFrameCount() {
        frameCount++
    }

    /**
     * Releases resources and cancels active subscriptions.
     */
    fun release() {
        speciesChecks.stop()
        matchScope.cancel("Match released")
        matchDispatcher.close()
    }
}
