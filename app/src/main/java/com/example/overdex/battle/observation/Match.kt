package com.example.overdex.battle.observation

import com.example.overdex.diagnostics.sourceJobLabel
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
import com.example.overdex.battle.custody.PlayerTeamRosterConfirmation
import com.example.overdex.battle.inference.PlayerTeamConfigurationScope
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SourceAvailabilityRecord
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.battle.custody.TestimonyRecord
import com.example.overdex.battle.custody.WitnessOperating
import com.example.overdex.battle.custody.VsScreenWitnessed
import android.util.Log
import com.example.overdex.battle.interpretation.BattleInterpreter
import com.example.overdex.battle.inference.FastMoveCadenceInference
import com.example.overdex.battle.inference.FastMoveCandidateSnapshot
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    val battleMemory: BattleMemory = BattleMemory(),
    val processingDiagnostics: MatchProcessingDiagnostics? = null,
    // Replay harness hooks retain original lineage and publication-grace clocks.
    // Normal Matches keep UUID allocation and the live wall clock.
    private val recordArticleId: ((Long) -> ArticleId)? = null,
    private val publicationClockMillis: () -> Long = System::currentTimeMillis,
    val faintHistoryCache: com.example.overdex.battle.inference.FaintHistoryCache? = com.example.overdex.battle.inference.FaintHistoryCache()
) {
    private val interpreter = BattleInterpreter(pokemonKnowledge)
    private fun rebuildCounter(owner: String): ((String, Int) -> Unit)? =
        processingDiagnostics?.let { diagnostic -> { name, size -> diagnostic.rebuild("$owner/$name", size) } }

    private val countdownMatchStartInference = com.example.overdex.battle.inference.CountdownMatchStartInference()
    private val opponentFormReferences = com.example.overdex.data.PokemonFormKnowledge(pokemonKnowledge)
    private val fastMoveCadenceInference = FastMoveCadenceInference(pokemonKnowledge, opponentFormReferences, rebuildCounter("cadence"), faintHistoryCache)
    private var lastOpponentCandidatePresentation: FastMoveCandidateSnapshot? = null
    private val playerInformationInference = com.example.overdex.battle.inference.PlayerBattleInformationInference(pokemonKnowledge)
    private val playerIdentityInference = com.example.overdex.battle.inference.PlayerActiveIdentityInference()
    private val fastMoveEnergyInference = FastMoveEnergyInference(pokemonKnowledge, opponentFormReferences, rebuildCounter("energy"), faintHistoryCache)
    private val opponentEnergyForecast = com.example.overdex.battle.forecast.OpponentEnergyForecast(rebuildCounter("forecast"), faintHistoryCache)

    /** Measurements of this pipeline only; none of these counters infer battle events. */
    internal val hpObservationDiagnostics = HpObservationDiagnostics()
    /** Scheduling only: accepted reader text can prepare capture before ledger publication finishes. */
    internal val acceptedChargeCapturePreparation = AcceptedChargeCapturePreparation(matchId)
    private val hpDiagnosticLock = Any()
    private var hpPublicationProgress: (() -> com.example.overdex.model.observation.VisualFramePublication?)? = null
    private var hpDiagnosticMatchEnded = false
    private var hpDiagnosticStopped = false

    internal fun beginHpObservationDiagnostics(
        publicationProgress: () -> com.example.overdex.model.observation.VisualFramePublication?,
    ) = synchronized(hpDiagnosticLock) {
        hpPublicationProgress = publicationProgress
        hpObservationDiagnostics.start(System.nanoTime(), publicationProgress())
    }

    internal fun publishHpObservationDiagnostics(reason: HpDiagnosticSummaryReason) = synchronized(hpDiagnosticLock) {
        val progress = hpPublicationProgress ?: return@synchronized
        if (hpDiagnosticStopped ||
            (reason == HpDiagnosticSummaryReason.PERIODIC && hpDiagnosticMatchEnded) ||
            (reason == HpDiagnosticSummaryReason.MATCH_END && hpDiagnosticMatchEnded)) return@synchronized
        val now = System.nanoTime()
        val summary = hpObservationDiagnostics.takeSummary(now, progress(), reason) ?: return@synchronized
        if (reason == HpDiagnosticSummaryReason.MATCH_END) hpDiagnosticMatchEnded = true
        if (reason == HpDiagnosticSummaryReason.SESSION_STOP) hpDiagnosticStopped = true
        custody.submitTestimony(
            SourceId("HP_OBSERVATION_PIPELINE"), summary, System.currentTimeMillis(),
            null, emptyList(), now,
        )
    }

    internal fun observeForecastBattlefield(at: Long, bothBarsVisible: Boolean) {
        opponentEnergyForecast.observeBattlefield(at, bothBarsVisible)
    }
    // Fast Move sound analysis now follows the shared PCM stream independently.
    private val fastMoveUseInference = FastMoveUseInference(onRebuild = rebuildCounter("fast_move"), faintHistoryCache = faintHistoryCache)
    private val chargedMoveAnnouncementInference = ChargedMoveAnnouncementInference(pokemonKnowledge, rebuildCounter("charge"), faintHistoryCache)
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
    private val publishedAvailabilitySequence = MutableStateFlow(Long.MIN_VALUE)
    private val publishedTestimonySequence = MutableStateFlow(Long.MIN_VALUE)
    private val playerConfigurationScope = PlayerTeamConfigurationScope()
    private val playerRosterBySlot = ConcurrentHashMap<Int, String>()
    private val playerRosterIdBySlot = ConcurrentHashMap<Int, Int>()
    private val playerRosterArticleBySlot = ConcurrentHashMap<Int, ArticleId>()
    private val configuredRosterArticleBySlot = ConcurrentHashMap<Int, ArticleId>()
    private val entrySideTracker = EntryAnnouncementSideTracker()
    private val opponentTeamLossHistory = com.example.overdex.battle.inference.LiveTeamLossHistory(ActivePokemonSide.OPPONENT, rebuildCounter("hud_faints"), faintHistoryCache)
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
            payload is GetReadyWitnessed || payload is AttackIncoming ||
                payload is ChargeMoveUsedAnnounced -> "CHARGE"
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
                    val references = opponentFormReferences.forObservedName(name)
                    opponentEnergyForecast.registerMoves(name, references.fastMoves)
                    loadedSpecies += name
                }
                when (val move = article.payload) {
                    is FastMoveIdentified -> DroidballOverlayPresentation.recordOpponentFastMove(move)
                    is com.example.overdex.battle.custody.ChargedMoveEnergySpent ->
                        if (move.side == ActivePokemonSide.OPPONENT)
                            DroidballOverlayPresentation.recordOpponentChargedMove(move.speciesName, move.moveName)
                    else -> Unit
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
                    id = recordArticleId?.invoke(availability.sequenceNumber) ?: ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = availability.timestamp,
                    recordedAt = publicationClockMillis(),
                    sourceId = availability.sourceId,
                    payload = WitnessOperating(availability.available),
                    sequenceNumber = availability.sequenceNumber,
                    matchId = MatchId(matchId),
                    monotonicTimeNanos = availability.monotonicTimeNanos
                )
                realityTimeline.append(article)
                _articles.tryEmit(article)
                battleMemory.timeline.record(article)
                publishedAvailabilitySequence.value = availability.sequenceNumber
            }
        }
        matchScope.launch {
            var matchStartRecorded = false
            var matchEndRecorded = false
            custody.testimonyFlow.collect { testimony ->
                val processingSpan = processingDiagnostics?.begin(testimony.sequenceNumber, testimony.payload.javaClass.simpleName)
                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "Match received TestimonyRecord: type=${testimony.payload::class.simpleName}, job=${sourceJobLabel(testimony.sourceId.id)}, confidence=${testimony.confidence}, sequence=${testimony.sequenceNumber}, refs=${testimony.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = testimony.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match received TestimonyRecord: type=${p::class.simpleName}, species=${p.species}, job=${sourceJobLabel(testimony.sourceId.id)}, confidence=${testimony.confidence}, sequence=${testimony.sequenceNumber}, refs=${testimony.evidenceReferences}")
                }

                val article = RealityArticle(
                    id = recordArticleId?.invoke(testimony.sequenceNumber) ?: ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = testimony.timestamp,
                    recordedAt = publicationClockMillis(),
                    sourceId = testimony.sourceId,
                    payload = testimony.payload,
                    confidence = testimony.confidence,
                    sequenceNumber = testimony.sequenceNumber,
                    evidenceReferences = testimony.evidenceReferences,
                    matchId = MatchId(matchId),
                    monotonicTimeNanos = testimony.monotonicTimeNanos
                )
                processingSpan?.next("identity_preparation")
                if (article.payload is ChargeMoveUsedAnnounced) {
                    article.monotonicTimeNanos?.let(acceptedChargeCapturePreparation::observeChargeCompletion)
                }
                if (playerConfigurationScope.accept(article)) {
                    val priorIds = playerRosterBySlot.entries.mapNotNull { (slot, name) ->
                        playerRosterIdBySlot[slot]?.let { name to it }
                    }.toMap()
                    playerRosterBySlot.clear()
                    playerRosterIdBySlot.clear()
                    playerRosterArticleBySlot.clear()
                    configuredRosterArticleBySlot.clear()
                    (article.payload as PlayerTeamRosterConfirmation).speciesBySlot.forEachIndexed { index, name ->
                        if (!name.isNullOrBlank()) {
                            val slot = index + 1
                            playerRosterBySlot[slot] = name
                            priorIds[name]?.let { playerRosterIdBySlot[slot] = it }
                            playerRosterArticleBySlot[slot] = article.id
                        }
                    }
                    playerRosterContradicted = false
                    entrySideTracker.discardRosterAssumptions()
                    activeSpeciesBySide.remove(ActivePokemonSide.PLAYER)
                    activeSpeciesAtBySide.remove(ActivePokemonSide.PLAYER)
                }
                (article.payload as? PlayerTeamRosterSlotWitnessed)?.let { rosterEntry ->
                    if (playerConfigurationScope.latestConfirmationNanos?.let {
                            (article.monotonicTimeNanos ?: Long.MIN_VALUE) <= it } == true) return@let
                    val name = com.example.overdex.data.PokemonFormReference.mergeRosterName(
                        playerRosterBySlot[rosterEntry.slot].takeIf { playerRosterArticleBySlot.containsKey(rosterEntry.slot) },
                        rosterEntry.speciesName,
                        explicitSprite = rosterEntry.spriteVariant != null)
                    playerRosterBySlot[rosterEntry.slot] = name
                    rosterEntry.speciesId?.let { playerRosterIdBySlot[rosterEntry.slot] = it }
                    DroidballOverlayPresentation.recordInferredPlayerRosterSlot(rosterEntry.slot, name)
                    // Retain the article that supplied the regional form.
                    if (name == rosterEntry.speciesName || !playerRosterArticleBySlot.containsKey(rosterEntry.slot)) {
                        playerRosterArticleBySlot[rosterEntry.slot] = article.id
                    }
                }
                (article.payload as? PlayerTeamSlotConfigured)?.takeIf { playerConfigurationScope.isCurrent(article) }
                    ?.let { configured ->
                        if (playerRosterBySlot[configured.slot]?.let(TeamRosterSpeciesAttributor::speciesKey) !=
                            TeamRosterSpeciesAttributor.speciesKey(configured.speciesName)) {
                            playerRosterArticleBySlot.remove(configured.slot)
                            entrySideTracker.discardRosterAssumptions()
                        }
                        playerRosterBySlot[configured.slot] = configured.speciesName
                        playerRosterIdBySlot[configured.slot] = configured.speciesId
                        configuredRosterArticleBySlot[configured.slot] = article.id
                    }
                (article.payload as? ActivePokemonFaintedWitnessed)?.let { faint ->
                    article.monotonicTimeNanos?.let { at ->
                    entrySideTracker.observeFaint(faint.side, faint.speciesName, at, article.id.value, faint.basis)
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
                    Log.d("ATTACK_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${article.payload::class.simpleName}, job=${sourceJobLabel(article.sourceId.id)}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = article.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${p::class.simpleName}, species=${p.species}, job=${sourceJobLabel(article.sourceId.id)}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }

                processingSpan?.next("raw_publication.timeline_append")
                realityTimeline.append(article)
                processingSpan?.next("raw_publication.hp_timing")
                // Record the actual append boundary, independently of the HP
                // reader's delivery/processing/submission clocks. The Timeline
                // remains an uninterpreted ledger.
                (article.payload as? com.example.overdex.battle.custody.ActiveHpBarMeasured)?.let { hp ->
                    if (article.sourceId.id == BattleWitnessContracts.playerActiveHpBar.witnessId ||
                        article.sourceId.id == BattleWitnessContracts.opponentActiveHpBar.witnessId ||
                        article.sourceId.id == "CHARGED_IMPACT_PLAYER_HP_BAR_FRAME_WITNESS" ||
                        article.sourceId.id == "CHARGED_IMPACT_OPPONENT_HP_BAR_FRAME_WITNESS") {
                        article.monotonicTimeNanos?.let { sourceAt ->
                            hpObservationDiagnostics.onTimelineRecorded(hp.side, sourceAt, System.nanoTime())
                        }
                    }
                }
                processingSpan?.next("raw_publication.species_cues")
                cueSpeciesChecks(article)
                processingSpan?.next("raw_publication.article_broadcast")
                _articles.tryEmit(article)
                processingSpan?.next("identity_projection")
                appendPlayerIdentityDerivations(article)
                processingSpan?.next("crop_delivery")
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
                if (!matchEndRecorded && (article.payload is ActivePokemonSpeciesWitnessed ||
                    article.payload is AttackIncoming ||
                    article.payload is GetReadyWitnessed ||
                    article.payload is ChargeMoveUsedAnnounced
                )) {
                    // Presentation is allowed to react immediately to accepted
                    // evidence. The ViewModel receives the matching signal to
                    // preserve the separate BattleOverlayOpened article.
                    // Buffered text may describe the finishing QTE after its
                    // result was already published. Preserve that testimony
                    // below without reopening the completed battle's live HUD.
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.BattleHudWitnessed)
                }
                processingSpan?.next("hud_references")
                (article.payload as? ActivePokemonSpeciesWitnessed)?.let { witnessed ->
                    if ((article.monotonicTimeNanos ?: Long.MAX_VALUE) <
                        (activeSpeciesAtBySide[witnessed.side] ?: Long.MIN_VALUE)) return@let
                    val referenceName = if (witnessed.side == ActivePokemonSide.PLAYER)
                        playerIdentityInference.at(article.monotonicTimeNanos ?: Long.MIN_VALUE)?.payload?.speciesName
                            ?: witnessed.speciesName else witnessed.speciesName
                    if (witnessed.side == ActivePokemonSide.PLAYER) {
                        val species = pokemonKnowledge.getPokemonByName(referenceName)
                        DroidballOverlayPresentation.setActivePlayerTypes(species?.types.orEmpty())
                    } else {
                        // A bare badge cannot shrink a regional form to the
                        // ordinary move pool in either the HUD or its forecast.
                        val references = opponentFormReferences.forObservedName(referenceName, witnessed.speciesId)
                        DroidballOverlayPresentation.recordOpponentSpecies(
                            speciesName = witnessed.speciesName,
                            speciesId = witnessed.speciesId ?: references.pokemon.filterNotNull()
                                .map { it.id }.distinct().singleOrNull(),
                            possibleFastMoves = references.fastMoves.map { it.name to it.type },
                            possibleChargedMoves = references.chargedMoves.map { it.name to it.type },
                            moveEnergy = (references.fastMoves + references.chargedMoves)
                                .associate { it.name to it.energy },
                            provisional = article.sourceId.id == "DECISIVE_BATTLE_CRY_SPECIES_WITNESS",
                            observedAtNanos = article.monotonicTimeNanos,
                        )
                    }
                }
                processingSpan?.next("faint_reconciliation")
                // A count conclusion may have been made before delayed identities
                // arrived. Render the current source-evidence resolution, never
                // accumulate names from potentially superseded faint conclusions.
                if (opponentTeamLossHistory.accept(article)) {
                    DroidballOverlayPresentation.reconcileOpponentFaints(
                        opponentTeamLossHistory.currentFaints.map { it.first.speciesName }.toSet(),
                        article.monotonicTimeNanos ?: 0L,
                    )
                }
                processingSpan?.next("hud_resources")
                (article.payload as? com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured)?.let { measured ->
                    DroidballOverlayPresentation.recordOpponentResources(measured, article.monotonicTimeNanos ?: 0L)
                }
                (article.payload as? PlayerTeamSlotConfigured)
                    ?.takeIf { it.slot == 1 && playerConfigurationScope.isCurrent(article) &&
                        activeSpeciesBySide[ActivePokemonSide.PLAYER] == null }
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

                processingSpan?.next("match_start")
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
                        appendTeamSelectLeadAtBattleEvidence(derivedArticle)
                        _matchStarted.tryEmit(derivedArticle)
                        Log.d("MATCH_START", "Derived MatchStarted article appended: articleId=${derivedArticle.id.value}, predecessor=${article.id.value}")
                    }
                }

                processingSpan?.next("player_information")
                // The selected lead remains known even if GO was missed or its
                // battle badge contains a nickname. Establish identity from real
                // combat before assigning this hit to an otherwise unknown actor.
                appendTeamSelectLeadAtBattleEvidence(article)
                appendPlayerInformationChecks(article)
                processingSpan?.next("charge_qte")
                appendChargeMoveQteDerivation(article)
                processingSpan?.next("energy")
                appendFastMoveEnergyDerivations(article)

                processingSpan?.next("fast_move")
                appendFastMoveUseDerivations(article)

                processingSpan?.next("cadence")
                appendFastMoveCadenceDerivations(article)

                processingSpan?.next("charge_announcement")
                appendChargedMoveAnnouncement(article)

                processingSpan?.next("attack_and_cues")
                interpreter.interpretAttackIncoming(article)?.let { derivedArticle ->
                    realityTimeline.append(derivedArticle)
                    _articles.tryEmit(derivedArticle)
                    battleMemory.timeline.record(derivedArticle)
                    appendChargeMoveQteDerivation(derivedArticle)
                    appendFastMoveUseDerivations(derivedArticle)
                    Log.d("ATTACK_SLICE", "Derived AttackIncoming article appended: articleId=${derivedArticle.id.value}, predecessor=${derivedArticle.predecessorIds.firstOrNull()?.value}")
                }

                (article.payload as? CountdownGlyphWitnessed)?.let { glyph ->
                    if (!matchEndRecorded) {
                        DroidballOverlayPresentation.showBattleHud()
                        DroidballService.emitSignal(DroidballSignal.CountdownWitnessed(glyph.glyph))
                    }
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
                if (!matchEndRecorded && article.payload is VsScreenWitnessed) {
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.VsScreenWitnessed)
                }
                val announcement = (article.payload as? RawTestimony)?.data as? String
                if (!matchEndRecorded && article.sourceId.id == "ANNOUNCEMENT_WITNESS" &&
                    announcement?.trim()?.uppercase()?.startsWith("GO,") == true
                ) {
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.BattleHudWitnessed)
                }

                processingSpan?.next("legacy_interpretation")
                interpreter.interpret(article)?.let { event ->
                    battleMemory.recordEvent(event)
                    if (event.type == BattleEventType.BATTLE_ENDED && event.result != null && !matchEndRecorded) {
                        // Preserve every raw result reading, including the later
                        // result screen that returns after the summary card, but
                        // establish exactly one semantic Match end at the first
                        // accepted outcome phrase.
                        matchEndRecorded = true
                        acceptedChargeCapturePreparation.close()
                        val derivedArticle = RealityArticle(
                            id = ArticleId(UUID.randomUUID().toString()),
                            perceivedAt = article.perceivedAt,
                            recordedAt = publicationClockMillis(),
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
                            // Accept the diagnostic flush before downstream
                            // result handlers take their archive snapshot.
                            publishHpObservationDiagnostics(HpDiagnosticSummaryReason.MATCH_END)
                        }
                        _articles.tryEmit(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                    }
                }
                // Publication includes this record's inline derivations and interpretation,
                // not merely the initial raw article appended above.
                processingSpan?.complete(testimony.sequenceNumber)
                publishedTestimonySequence.value = testimony.sequenceNumber
            }
        }
    }

    private suspend fun appendChargedMoveAnnouncement(article: RealityArticle) {
        chargedMoveAnnouncementInference
            .takeUnless { isRawPlayerSpecies(article) }
            ?.accept(playerIdentityInference.projectFaint(article), ::sideForRosterKnownSpecies)
            ?.let { derivation ->
                val derivedArticle = RealityArticle(
                    id = ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = derivation.observedArticle.perceivedAt,
                    recordedAt = publicationClockMillis(),
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
    }

    private fun isRawPlayerSpecies(article: RealityArticle): Boolean =
        (article.payload as? ActivePokemonSpeciesWitnessed)?.side == ActivePokemonSide.PLAYER &&
            article.sourceId.id != "PLAYER_ACTIVE_IDENTITY_INFERENCE"

    private suspend fun appendPlayerIdentityDerivations(article: RealityArticle) {
        playerIdentityInference.accept(article).forEach { resolution ->
            val derived = RealityArticle(
                ArticleId(UUID.randomUUID().toString()), resolution.source.perceivedAt, publicationClockMillis(),
                SourceId("PLAYER_ACTIVE_IDENTITY_INFERENCE"), resolution.payload,
                predecessorIds = resolution.evidence, matchId = MatchId(matchId),
                monotonicTimeNanos = resolution.payload.appearanceStartedAtNanos,
            )
            realityTimeline.append(derived)
            _articles.tryEmit(derived)
            battleMemory.timeline.record(derived)
            val projected = resolution.speciesProjection(derived)
            // Historical label OCR can refine an old interval without rolling
            // the currently active battler back to the departed Pokemon.
            appendChargedMoveAnnouncement(projected)
            appendPlayerInformationChecks(projected)
            if (playerIdentityInference.resolutions().lastOrNull()?.payload?.appearanceStartedAtNanos ==
                resolution.payload.appearanceStartedAtNanos) {
                appendFastMoveUseDerivations(projected)
                appendFastMoveEnergyDerivations(projected)
                appendFastMoveCadenceDerivations(projected)
                val species = pokemonKnowledge.getPokemonByName(resolution.payload.speciesName)
                DroidballOverlayPresentation.setActivePlayerTypes(species?.types.orEmpty())
            }
        }
    }

    private suspend fun appendPlayerInformationChecks(article: RealityArticle) {
        if (isRawPlayerSpecies(article)) return
        playerInformationInference.accept(playerIdentityInference.projectFaint(article)).forEach { check ->
            val derived = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = check.observedArticle.perceivedAt,
                recordedAt = publicationClockMillis(),
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
        if (isRawPlayerSpecies(article)) return
        fastMoveUseInference.accept(playerIdentityInference.projectFaint(article)).forEach { derivation ->
            val derivedArticle = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = derivation.observedArticle.perceivedAt,
                recordedAt = publicationClockMillis(),
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
                recordedAt = publicationClockMillis(),
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
        if (isRawPlayerSpecies(article)) return
        fastMoveEnergyInference.accept(playerIdentityInference.projectFaint(article)).forEach { derivation ->
            val derivedArticle = RealityArticle(
                id = ArticleId(UUID.randomUUID().toString()),
                perceivedAt = derivation.observedArticle.perceivedAt,
                recordedAt = publicationClockMillis(),
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
     * Carries the observed Team Select lead into battle. GO is preferred; when
     * it was missed, a valid loss plus an opponent identity establishes combat
     * without manufacturing the unknown start time or re-reading a nickname.
     */
    private suspend fun appendTeamSelectLeadAtBattleEvidence(boundary: RealityArticle) {
        val isMatchStart = boundary.payload is MatchStarted
        val damage = boundary.payload as? com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
        if (!isMatchStart && (damage == null || damage.beforeFraction !in 0f..1f ||
                damage.afterFraction !in 0f..1f || damage.lostFraction <= 0f ||
                damage.beforeFraction <= damage.afterFraction ||
                kotlin.math.abs(damage.beforeFraction - damage.afterFraction - damage.lostFraction) >= .001f)) return
        // A saved team is not proof of the selected party, and a known later
        // combatant must never be replaced by a late opening cue.
        if (playerRosterContradicted || activeSpeciesBySide[ActivePokemonSide.PLAYER] != null) return
        val at = boundary.monotonicTimeNanos ?: return
        if (at < observationAttemptStartedAtNanos) return
        val rosterId = playerRosterArticleBySlot[1] ?: return
        val rosterName = playerRosterBySlot[1] ?: return
        val history = realityTimeline.getArticles()
        val roster = history.firstOrNull { it.id == rosterId } ?: return
        val rosterAt = roster.monotonicTimeNanos ?: return
        if (rosterAt !in observationAttemptStartedAtNanos..at) return
        // The mutable roster follows delivery order. Do not use an older slot
        // reading after a newer source-time observation changed the opener.
        if (history.any { article ->
                val newer = article.payload as? PlayerTeamRosterSlotWitnessed ?: return@any false
                val time = article.monotonicTimeNanos ?: return@any false
                if (newer.slot != 1 || time <= rosterAt || time > at) return@any false
                val newerName = com.example.overdex.data.PokemonFormReference.mergeRosterName(
                    rosterName, newer.speciesName, explicitSprite = newer.spriteVariant != null)
                !newerName.equals(rosterName, ignoreCase = true)
            }) return
        val party = history.filter { it.payload is com.example.overdex.battle.custody.TeamSelectPartyWitnessed &&
            it.monotonicTimeNanos?.let { time -> time in observationAttemptStartedAtNanos..at } == true }
            .maxByOrNull { it.monotonicTimeNanos!! }
        if (!isMatchStart && (party == null || rosterAt < party.monotonicTimeNanos!!)) return
        val selectedAt = party?.monotonicTimeNanos ?: rosterAt
        // Source-time guards also cover a faint/replacement whose name could
        // not be read. A lingering hit or late GO cannot resurrect slot one.
        val invalidated = history.any { article ->
            val time = article.monotonicTimeNanos ?: return@any false
            // Historical combat may arrive after the session has closed. It
            // remains raw evidence, but must not reopen current presentation.
            if (time >= selectedAt && when (article.payload) {
                    com.example.overdex.battle.custody.TeamSelectScanStartedByUser,
                    com.example.overdex.battle.custody.ObservationRestartedByUser,
                    com.example.overdex.battle.custody.ScreenIgnoredByUser,
                    com.example.overdex.battle.custody.ObservationSessionStoppedByUser,
                    is MatchEnded -> true
                    else -> false
                }) return@any true
            if (time !in selectedAt..at || article.id == boundary.id) return@any false
            when (val payload = article.payload) {
                is ActivePokemonFaintedWitnessed -> payload.side == ActivePokemonSide.PLAYER
                is com.example.overdex.battle.custody.PlayerPokeBallCountMeasured -> payload.visibleCount < payload.maximumCount
                is PlayerTeamRosterConfirmation -> !payload.confirmed
                is com.example.overdex.battle.custody.SpeciesCheckMeasured ->
                    payload.side == ActivePokemonSide.PLAYER && payload.status == "OPENED" &&
                        payload.reason in setOf("SWITCH", "FAINT", "EMPTY_HP")
                else -> false
            }
        }
        if (invalidated) return
        val opponent = if (!isMatchStart) history.filter { article ->
            val payload = article.payload as? ActivePokemonSpeciesWitnessed
            payload?.side == ActivePokemonSide.OPPONENT && article.monotonicTimeNanos?.let {
                it in selectedAt..at } == true
        }.maxByOrNull { it.monotonicTimeNanos!! } ?: return else null
        // A bare badge label keeps regional form changes distinct from a switch;
        // the cited slot retains the exact form used by identity/knowledge lookup.
        val speciesName = rosterName.substringBefore(" (")
        val species = pokemonKnowledge.getPokemonByName(rosterName)
        val leadArticle = RealityArticle(
            id = ArticleId(UUID.randomUUID().toString()),
            perceivedAt = boundary.perceivedAt,
            recordedAt = publicationClockMillis(),
            sourceId = SourceId("TEAM_SELECT_LEAD_INFERENCE"),
            payload = ActivePokemonSpeciesWitnessed(
                side = ActivePokemonSide.PLAYER,
                speciesName = speciesName,
                speciesId = playerRosterIdBySlot[1] ?: species?.id,
                playerTeamSlot = 1,
                appearanceStartedAtNanos = at,
            ),
            predecessorIds = (listOfNotNull(party?.id, rosterId, opponent?.id) + boundary.id).distinct(),
            confidence = 0.99f,
            matchId = MatchId(matchId),
            monotonicTimeNanos = at,
        )
        realityTimeline.append(leadArticle)
        cueSpeciesChecks(leadArticle)
        _articles.tryEmit(leadArticle)
        battleMemory.timeline.record(leadArticle)
        appendPlayerIdentityDerivations(leadArticle)
        speciesChecks.delivered(ActivePokemonSide.PLAYER, speciesName, leadArticle.id.value)
        DroidballOverlayPresentation.setActivePlayerTypes(species?.types.orEmpty())
        DroidballOverlayPresentation.showBattleHud()
        DroidballService.emitSignal(DroidballSignal.BattleHudWitnessed)
    }

    private suspend fun appendFastMoveCadenceDerivations(article: RealityArticle) {
        // The raw badge cannot replace a resolved form, but its fresh source time
        // must still clear entry/cry checks that are waiting to attribute audio.
        val input = if (isRawPlayerSpecies(article))
            playerIdentityInference.projectSpeciesReconfirmation(article) ?: return
        else playerIdentityInference.projectFaint(article)
        val derivations = fastMoveCadenceInference.accept(input)
        val candidates = fastMoveCadenceInference.candidateSnapshot(ActivePokemonSide.OPPONENT)
        if (candidates !== lastOpponentCandidatePresentation) {
            // Keep the first presentation of each appearance and all evidence
            // changes prompt. Unrelated measurements reuse the cached snapshot.
            lastOpponentCandidatePresentation = candidates
            candidates?.let {
                opponentEnergyForecast.restrictCandidates(candidates.speciesName, candidates.remainingMoveNames)
                DroidballOverlayPresentation.recordOpponentFastMoveCandidates(
                    speciesName = candidates.speciesName,
                    moveNames = candidates.remainingMoveNames,
                    audioFavoredMoveName = candidates.audioPreference?.moveName,
                )
            }
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
                recordedAt = publicationClockMillis(),
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
     * Waits for the testimony and source-availability records already accepted by
     * Custody to finish publication, including their inline derivations. The two
     * collectors acknowledge their own sequence numbers because either lane may
     * be ahead of the other. Records accepted after this snapshot are not a part
     * of this barrier; callers ending capture must stop producers first.
     *
     * This does not release the Match or manufacture a battle outcome. Call it
     * before archiving/releasing, and let the caller own cancellation or timeout.
     */
    suspend fun awaitAcceptedEvidencePublished() {
        val accepted = custody.getRecords()
        val testimonySequence = accepted.filterIsInstance<TestimonyRecord>()
            .maxOfOrNull { it.sequenceNumber }
        val availabilitySequence = accepted.filterIsInstance<SourceAvailabilityRecord>()
            .maxOfOrNull { it.sequenceNumber }
        if (testimonySequence != null) {
            publishedTestimonySequence.first { it >= testimonySequence }
        }
        if (availabilitySequence != null) {
            publishedAvailabilitySequence.first { it >= availabilitySequence }
        }
    }

    /**
     * Releases resources and cancels active subscriptions.
     */
    fun release() {
        acceptedChargeCapturePreparation.close()
        speciesChecks.stop()
        matchScope.cancel("Match released")
        matchDispatcher.close()
        faintHistoryCache?.clear()
    }
}
