package com.example.overdex.battle.observation

import com.example.overdex.BattleMemory
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.PlayerTeamRosterSlotWitnessed
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SourceAvailabilityRecord
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.battle.custody.WitnessOperating
import com.example.overdex.battle.custody.VsScreenWitnessed
import android.util.Log
import com.example.overdex.battle.interpretation.BattleInterpreter
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

    /** Owned by this match and baselined by the enclosing Droidball session at GO. */
    val clock = MatchClock()

    private val _matchStarted = MutableSharedFlow<RealityArticle>(replay = 1, extraBufferCapacity = 1)
    /** The derived GO boundary for the owning Droidball session to act upon. */
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

    /**
     * Roster-based side attribution is available only after all three Team Select
     * slots have been independently preserved. It is a conclusion from the
     * roster and a later species mention, never a claim that the mention carried
     * an on-screen side label.
     */
    fun sideForRosterKnownSpecies(speciesName: String): ActivePokemonSide? =
        TeamRosterSpeciesAttributor.sideFor(speciesName, playerRosterBySlot.values)

    /** The total number of frames processed during this Match. */
    var frameCount: Long = 0
        private set

    init {
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

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${article.payload::class.simpleName}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = article.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${p::class.simpleName}, species=${p.species}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }

                realityTimeline.append(article)
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
                // A preserved crop from one of the battle-transition surfaces is
                // enough to show the HUD. Recognition may arrive later or fail,
                // but the user must never have to open Droidball manually while
                // Pokémon GO is already presenting battle evidence.
                if (article.payload is com.example.overdex.battle.custody.CropCaptured &&
                    article.sourceId.id in setOf(
                        // Any live battle surface is enough to make Droidball visible.
                        // Recognition remains downstream; an unreadable crop must not
                        // conceal the field HUD from the user.
                        "VS_SCREEN_CAPTURE",
                        "COUNTDOWN_CROP_CAPTURE",
                        "ANNOUNCEMENT_CROP_CAPTURE",
                        "PLAYER_ACTIVE_SPECIES_TEXT_CAPTURE",
                        "OPPONENT_ACTIVE_SPECIES_TEXT_CAPTURE",
                        "PLAYER_ACTIVE_TYPE_ICONS_CAPTURE",
                        "OPPONENT_ACTIVE_TYPE_ICONS_CAPTURE",
                        "PLAYER_TEAM_STATUS_CAPTURE",
                        "OPPONENT_TEAM_STATUS_CAPTURE",
                        "OPPONENT_POKE_BALLS_CAPTURE",
                        "OPPONENT_SHIELDS_CAPTURE",
                        "PLAYER_HP_EVIDENCE_CAPTURE",
                        "OPPONENT_HP_EVIDENCE_CAPTURE",
                        "CHARGE_MOVE_EXECUTION_CAPTURE",
                        "PLAYER_CHARGE_MOVE_CONTROLS_CAPTURE",
                        "PLAYER_INACTIVE_SPECIES_SPRITE_CAPTURE",
                        "PLAYER_INACTIVE_HP_BAR_CAPTURE",
                        "INACTIVE_MATCH_START_TIMER_CAPTURE",
                        "SWITCH_LOCKOUT_TIMER_CAPTURE"
                    )
                ) {
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.BattleHudWitnessed)
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
                    val species = pokemonKnowledge.getPokemonByName(witnessed.speciesName)
                    if (witnessed.side == ActivePokemonSide.PLAYER) {
                        DroidballOverlayPresentation.setActivePlayerTypes(species?.types.orEmpty())
                    } else {
                        DroidballOverlayPresentation.recordOpponentSpecies(
                            speciesName = witnessed.speciesName,
                            speciesId = witnessed.speciesId ?: species?.id,
                            possibleFastMoves = species?.fastMoves?.map { it.name to it.type }.orEmpty(),
                            possibleChargedMoves = species?.chargedMoves?.map { it.name to it.type }.orEmpty()
                        )
                    }
                }

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }
                if (testimony.payload is PokemonIdentified) {
                    Log.d("SPECIES_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }

                battleMemory.timeline.record(article)

                if (!matchStartRecorded) {
                    interpreter.interpretMatchStart(article)?.let { derivedArticle ->
                        realityTimeline.append(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                        matchStartRecorded = true
                        _matchStarted.tryEmit(derivedArticle)
                        Log.d("MATCH_START", "Derived MatchStarted article appended: articleId=${derivedArticle.id.value}, predecessor=${article.id.value}")
                    }
                }

                interpreter.interpretAttackIncoming(article)?.let { derivedArticle ->
                    realityTimeline.append(derivedArticle)
                    battleMemory.timeline.record(derivedArticle)
                    Log.d("ATTACK_SLICE", "Derived AttackIncoming article appended: articleId=${derivedArticle.id.value}, predecessor=${derivedArticle.predecessorIds.firstOrNull()?.value}")
                }

                (article.payload as? CountdownGlyphWitnessed)?.let { glyph ->
                    DroidballOverlayPresentation.showBattleHud()
                    DroidballService.emitSignal(DroidballSignal.CountdownWitnessed(glyph.glyph))
                    if (glyph.glyph in setOf("3", "2", "1", "GO")) {
                        DroidballService.requestCueCenteredAudio(article.id.value, com.example.overdex.battle.audio.BattleCryCueKind.valueOf("COUNTDOWN_${glyph.glyph}"))
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
                    if (event.type == BattleEventType.BATTLE_ENDED && event.result != null) {
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
                        _articles.tryEmit(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                    }
                }
            }
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
        matchScope.cancel("Match released")
        matchDispatcher.close()
    }
}
