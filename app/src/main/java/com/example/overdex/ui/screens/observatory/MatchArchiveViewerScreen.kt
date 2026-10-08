package com.example.overdex.ui.screens.observatory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.replay.ReplayExcerptStore
import com.example.overdex.battle.replay.ReplayIdentityObservation
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalPathIndicator
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ArchiveViewerAction {
    OPEN_REPLAY,
    MARK_START,
    MARK_END_AND_SAVE;

    fun move(delta: Int): ArchiveViewerAction {
        val values = entries
        return values[(ordinal + delta + values.size) % values.size]
    }
}

@Composable
fun MatchArchiveViewerScreen(
    archive: MatchArchive,
    onBack: () -> Unit,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onLcdDrag: ((Offset) -> Unit) -> Unit = {},
    onLcdTap: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String, String) -> Unit = { _, _ -> },
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {},
    resolveSpeciesId: suspend (String) -> Int? = { null },
    resolveFastMoveType: suspend (speciesName: String, moveName: String) -> PokemonType? = { _, _ -> null },
    resolveFastMoves: suspend (speciesName: String) -> List<com.example.overdex.model.Move> = { emptyList() },
    resolveArchivedSpeciesCrops: suspend (MatchArchive, (com.example.overdex.battle.replay.ReplayCropProgress) -> Unit) -> List<ReplayIdentityObservation> = { _, _ -> emptyList() }
) {
    val context = LocalContext.current
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    var showReplay by remember { mutableStateOf(false) }
    var excerptStart by remember { mutableStateOf<ArchivedRealityArticle?>(null) }
    var excerptStatus by remember { mutableStateOf<String?>(null) }
    var selectedAction by remember { mutableStateOf(ArchiveViewerAction.OPEN_REPLAY) }
    
    val listState = rememberLazyListState()
    val detailScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val selectedArticle = archive.articles.getOrNull(selectedIndex)

    fun markStart() {
        val article = selectedArticle
        if (article?.monotonicTimeNanos == null) {
            excerptStatus = "SELECT A TIMED ARTICLE FIRST"
            return
        }
        excerptStart = article
        excerptStatus = "START MARKED: #${selectedIndex + 1}"
    }

    fun markEndAndSave() {
        val start = excerptStart
        val end = selectedArticle
        if (start == null) {
            excerptStatus = "MARK A START FIRST"
            return
        }
        if (end?.monotonicTimeNanos == null) {
            excerptStatus = "SELECT A TIMED END ARTICLE"
            return
        }
        excerptStatus = runCatching {
            val excerpt = ReplayExcerptStore(context.filesDir).save(archive, start, end)
            excerptStart = null
            "EXCERPT SAVED: ${excerpt.excerptId.take(8)}"
        }.getOrElse { "EXCERPT NOT SAVED: ${it.message}" }
    }

    if (showReplay) {
        MatchReplayScreen(
            archive = archive,
            onBack = { showReplay = false },
            onUp = onUp,
            onDown = onDown,
            onA = onA,
            onB = onB,
            onLcdDrag = onLcdDrag,
            onLcdTap = onLcdTap,
            onLcdUpdate = onLcdUpdate,
            onLcdContentUpdate = onLcdContentUpdate,
            resolveSpeciesId = resolveSpeciesId,
            resolveFastMoveType = resolveFastMoveType,
            resolveFastMoves = resolveFastMoves,
            resolveArchivedSpeciesCrops = resolveArchivedSpeciesCrops
        )
        return
    }

    BackHandler {
        if (showDetails) {
            showDetails = false
        } else {
            onBack()
        }
    }

    // Handle physical controls
    SideEffect {
        onLcdDrag { }
        onLcdTap { }
        onUp {
            if (showDetails) {
                scope.launch { detailScrollState.scrollBy(-100f) }
            } else if (archive.articles.isNotEmpty()) {
                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                scope.launch { listState.animateScrollToItem(selectedIndex) }
            }
        }
        onDown {
            if (showDetails) {
                scope.launch { detailScrollState.scrollBy(100f) }
            } else if (archive.articles.isNotEmpty()) {
                selectedIndex = (selectedIndex + 1).coerceAtMost(archive.articles.size - 1)
                scope.launch { listState.animateScrollToItem(selectedIndex) }
            }
        }
        onLeft {
            if (!showDetails) selectedAction = selectedAction.move(-1)
        }
        onRight {
            if (!showDetails) selectedAction = selectedAction.move(1)
        }
        onA {
            if (!showDetails) {
                when (selectedAction) {
                    ArchiveViewerAction.MARK_START -> markStart()
                    ArchiveViewerAction.MARK_END_AND_SAVE -> markEndAndSave()
                    ArchiveViewerAction.OPEN_REPLAY -> showReplay = true
                }
            }
        }
        onB {
            if (showDetails) {
                showDetails = false
            } else {
                onBack()
            }
        }
    }

    PublishMatchLcd(onLcdContentUpdate) {
        MatchLcdColumn {
            MatchLcdText("ID: ${archive.matchId}")
            MatchLcdText("ARTICLES: ${archive.articles.size}   SELECTED: ${selectedIndex + 1}")
            MatchLcdButton("OPEN REPLAY", { selectedAction = ArchiveViewerAction.OPEN_REPLAY; showReplay = true },
                selected = selectedAction == ArchiveViewerAction.OPEN_REPLAY)
            MatchLcdButton("MARK START", { selectedAction = ArchiveViewerAction.MARK_START; markStart() },
                selected = selectedAction == ArchiveViewerAction.MARK_START, enabled = selectedArticle?.monotonicTimeNanos != null)
            MatchLcdButton("MARK END + SAVE", { selectedAction = ArchiveViewerAction.MARK_END_AND_SAVE; markEndAndSave() },
                selected = selectedAction == ArchiveViewerAction.MARK_END_AND_SAVE,
                enabled = excerptStart != null && selectedArticle?.monotonicTimeNanos != null)
            excerptStatus?.let { MatchLcdText(it) }
            MatchLcdButton(if (showDetails) "CLOSE DETAILS" else "ARTICLE DETAILS", { showDetails = !showDetails }, enabled = selectedArticle != null)
            MatchLcdButton("BACK", onBack)
            MatchLcdText("←/→ ACTION   ↑/↓ ARTICLE   A SELECT")
        }
    }

    TerminalScreen {
        TerminalPathIndicator(path = "/signal_observatory/archive_viewer/")

        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "MATCH ARCHIVE")
            if (archive.articles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TerminalText(text = "ARCHIVE IS EMPTY", color = Color.Gray)
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(archive.articles) { index, article ->
                            ArchiveArticleRow(
                                article = article,
                                isSelected = index == selectedIndex && !showDetails
                            )
                        }
                    }

                    if (showDetails && selectedArticle != null) {
                        ArticleDetailsOverlay(
                            article = selectedArticle,
                            scrollState = detailScrollState
                        )
                    }
                }
            }
            

        }
    }
}

@Composable
private fun ArchiveArticleRow(
    article: ArchivedRealityArticle,
    isSelected: Boolean
) {
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) TerminalGreen else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TerminalText(
                text = article.sourceId,
                color = if (isSelected) TerminalGreen else TerminalPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            TerminalText(
                text = "Conf: ${article.confidence ?: "Unknown"}",
                color = TerminalDimGreen,
                fontSize = 9.sp
            )
        }

        val payloadText = when (val p = article.payload) {
            is ArchivedRawText -> p.value
            is ArchivedRawInt -> p.value.toString()
            is ArchivedMatchRecordStarted -> "MATCH RECORD STARTED [basis=DROIDBALL DEPLOYED]"
            is ArchivedBattleOverlayOpened -> "BATTLE OVERLAY OPENED [reason=${p.reason}]"
            is ArchivedTeamSelectScanStartedByUser -> "TEAM SELECT SCAN STARTED BY USER"
            is ArchivedScreenIgnoredByUser -> "SCREEN INTERPRETATION SUSPENDED BY USER"
            is ArchivedObservationRestartedByUser -> "OBSERVATION RESTARTED BY USER"
            is ArchivedObservationSessionStoppedByUser -> "OBSERVATION SESSION STOPPED BY USER"
            is ArchivedVsScreenWitnessed -> "VS SCREEN WITNESSED [central anchor available]"
            is ArchivedAttackIncoming -> "ATTACK INCOMING"
            is ArchivedPokemonIdentified -> "POKEMON: ${p.species}"
            is ArchivedActivePokemonSpeciesWitnessed -> "ACTIVE SPECIES [side=${p.side}, species=${p.speciesName}, id=${p.speciesId ?: "unresolved"}]"
            is ArchivedActivePokemonFaintedWitnessed -> "FAINTED [side=${p.side}, species=${p.speciesName ?: "unresolved"}, basis=${p.basis}]"
            is ArchivedTeamSelectPartyWitnessed -> "TEAM SELECT PARTY WITNESSED"
            is ArchivedTeamSelectLeagueWitnessed -> "LEAGUE [${p.league.replace('_', ' ')}; ${p.basis}]"
            is ArchivedPlayerTeamRosterSlotWitnessed -> "PLAYER ROSTER [slot=${p.slot}, species=${p.speciesName}, id=${p.speciesId ?: "unresolved"}]"
            is ArchivedPlayerTeamRosterConfirmation -> "PLAYER ROSTER ${if (p.confirmed) "CONFIRMED" else "REJECTED"} [${p.speciesBySlot.joinToString()}]"
            is ArchivedPlayerTeamSlotConfigured -> "CURRENT TEAM [slot=${p.slot}, species=${p.speciesName}, fast=${p.fastMoveName}, charged=${p.chargedMoveNames.joinToString(" / ")}]"
            is ArchivedSupportingMatchStart -> "MATCH START SUPPORT [frame=${p.frameIndex}, upper=${String.format(Locale.ROOT, "%.3f", p.upperColorfulPixelFraction)}, lower=${String.format(Locale.ROOT, "%.3f", p.lowerColorfulPixelFraction)}, basis=${p.basis}]"
            is ArchivedCountdownGlyphWitnessed -> "COUNTDOWN GLYPH [glyph=${p.glyph}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}, frame=${p.frameIndex}, basis=${p.basis}]"
            is ArchivedCropCaptured -> "CROP CAPTURED [crop=${p.cropName}, artifact=${p.artifactPath}, sha256=${p.sha256.take(12)}…]"
            is ArchivedSpeciesCheckMeasured -> "SPECIES CHECK [${p.side} ${p.status}, ${p.reason}, ${p.elapsedNanos / 1_000_000} ms / ${p.targetNanos / 1_000_000} ms target${if (p.elapsedNanos > p.targetNanos) " MISSED" else ""}, species=${p.speciesName ?: "unresolved"}]"
            is ArchivedAudioInputStatus -> "AUDIO INPUT [${p.captureSource}: ${p.state}]"
            is ArchivedAudioCaptured -> "AUDIO CAPTURED [source=${p.captureSource}, peak=${p.peakAmplitude}, cue=${p.cueKind}, ${p.sampleRateHz} Hz, ${p.channelCount} ch, ${p.durationNanos / 1_000_000} ms, sha256=${p.sha256.take(12)}…]"
            is ArchivedBattleCryCandidatesMeasured -> "CRY CANDIDATES [cue=${p.cueKind}, ${p.candidates.joinToString { "#${it.speciesId} ${String.format(Locale.ROOT, "%.3f", it.similarity)}" }}]"
            is ArchivedVisualCaptureGapObserved -> "VISUAL GAP [${p.durationNanos / 1_000_000} ms unobserved]"
            is ArchivedOutOfBattleMenuWitnessed -> "OUT OF BATTLE MENU WITNESSED"
            is ArchivedWitnessOperating -> "WITNESS ${if (p.operating) "OPERATING" else "STOPPED"}"
            is ArchivedActivePokemonTypesWitnessed -> "ACTIVE TYPES [side=${p.side}, types=${p.types.joinToString()}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}]"
            is ArchivedGetReadyWitnessed -> "GET READY"
            is ArchivedChargeMoveUsedAnnounced -> "CHARGE MOVE USED"
            is ArchivedDeviceMotionPulseMeasured -> "DEVICE MOTION PULSE [${p.durationNanos / 1_000_000} ms, peak=${String.format(Locale.ROOT, "%.3f", p.peakLinearAccelerationMetersPerSecondSquared)} m/s², samples=${p.sampleCount}]"
            is ArchivedChargeMoveQteVibrationPatternInferred -> "CHARGE MOVE QTE VIBRATION [side=${p.side}, pulses=${p.pulseCount}, window=${String.format(Locale.ROOT, "%.2f", p.windowNanos / 1_000_000_000.0)}s]"
            is ArchivedPlayerInactiveHpBarMeasured -> "INACTIVE HP [slot=${p.slot}, fill=${String.format(Locale.ROOT, "%.1f", p.filledFraction * 100)}%]"
            is ArchivedPlayerPokeBallCountMeasured -> "PLAYER POKÉ BALLS [${p.visibleCount}/${p.maximumCount} visible]"
            is ArchivedOpponentBattleResourceCountMeasured -> "OPPONENT ${p.resource.replace('_', ' ')} [${p.visibleCount}/${p.maximumCount} visible]"
            is ArchivedActiveHpBarMeasured -> "ACTIVE HP [side=${p.side}, fill=${String.format(Locale.ROOT, "%.1f", p.filledFraction * 100)}%, bounds=${p.barLeft},${p.barTop},${p.barRight},${p.barBottom}]"
            is ArchivedActiveHpBarMotionCadenceMeasured -> "HP MOTION CADENCE [attacker=${p.movingSide}, interval=${String.format(Locale.ROOT, "%.2f", p.intervalNanos / 1_000_000_000.0)}s, excursion=${String.format(Locale.ROOT, "%.1f", p.verticalExcursionPixels)}px]"
            is ArchivedPlayerChargeMoveEnergyFillIncreased -> "PLAYER ENERGY FILL [slots=${p.changedSlots.joinToString()}, before=${p.beforeBySlot.joinToString { String.format(Locale.ROOT, "%.2f", it) }}, after=${p.afterBySlot.joinToString { String.format(Locale.ROOT, "%.2f", it) }}]"
            is ArchivedPlayerChargeMoveEnergyFillCadenceMeasured -> "PLAYER ENERGY FILL CADENCE [interval=${String.format(Locale.ROOT, "%.2f", p.intervalNanos / 1_000_000_000.0)}s, slots=${p.contributingSlots.joinToString()}]"
            is ArchivedActiveHpBarBorderCadenceMeasured -> "HP BORDER CADENCE [bar=${p.damagedBarSide}, attacker=${if (p.damagedBarSide == "PLAYER") "OPPONENT" else "PLAYER"}, interval=${String.format(Locale.ROOT, "%.2f", p.intervalNanos / 1_000_000_000.0)}s, orange=${p.peakOrangeFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "legacy"}]"
            is ArchivedActiveHpBarBorderPulseObserved -> "HP BORDER PULSE [bar=${p.damagedBarSide}, attacker=${if (p.damagedBarSide == "PLAYER") "OPPONENT" else "PLAYER"}, orange=${p.peakOrangeFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: String.format(Locale.ROOT, "legacy %.3f", p.peakColorDistance)}]"
            is ArchivedHpBarBorderPulse -> "HP BORDER PULSE [bar=${p.barSide}, status=${p.status}, orange=${p.peakOrangeFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "n/a"}]"
            is ArchivedActiveHpBarDamageTickMeasured -> "HP DAMAGE TICK [bar=${p.damagedSide}, attacker=${if (p.damagedSide == "PLAYER") "OPPONENT" else "PLAYER"}, lost=${String.format(Locale.ROOT, "%.1f", p.lostFraction * 100)}%]"
            is ArchivedFastMoveRecipientVisualArtifactMeasured -> "FAST MOVE VISUAL ARTIFACT [recipient=${p.damagedSide}, area=${String.format(Locale.ROOT, "%.1f", p.changedPixelFraction * 100)}%, color=${String.format(Locale.ROOT, "%.3f", p.meanColorDistance)}, center=${String.format(Locale.ROOT, "%.2f", p.centroidX)},${String.format(Locale.ROOT, "%.2f", p.centroidY)}]"
            is ArchivedFastMoveRecipientVisualCadenceMeasured -> "FAST MOVE VISUAL CADENCE [recipient=${p.damagedSide}, interval=${String.format(Locale.ROOT, "%.2f", p.intervalNanos / 1_000_000_000.0)}s]"
            is ArchivedFastMoveUseObserved -> "FAST MOVE USE [id=${p.useId}, attacker=${p.attackingSide}, recipient=${p.damagedSide}, species=${p.attackerSpeciesName ?: "unresolved"}, evidence=${p.evidenceKinds.joinToString()}]"
            is ArchivedFastMoveEffectivenessWitnessed -> "FAST MOVE EFFECTIVENESS [${p.effectiveness}, damaged=${p.damagedSide ?: "direction inferred downstream"}, text=${p.recognizedText}]"
            is ArchivedFastMoveIdentified -> "FAST MOVE [side=${p.side}, species=${p.speciesName}, move=${p.moveName}, cadence=${p.observedMedianIntervalNanos?.let { String.format(Locale.ROOT, "%.2f", it / 1_000_000_000.0) + "s" } ?: "not yet measured"}]"
            is ArchivedFastMoveEnergyDerived -> "ENERGY DERIVED [side=${p.side}, move=${p.moveName}, uses=${p.observedCompletedUses}, generated=${p.totalEnergyGenerated}]"
            is ArchivedChargedMoveEnergySpent -> "CHARGED ENERGY SPENT [side=${p.side}, species=${p.speciesName}, move=${p.moveName}, cost=${p.energyCost}]"
            is ArchivedFastMoveSoundMeasured -> if (p.audible) "FAST MOVE SOUND [onset=${p.onsetOffsetNanos?.div(1_000_000)} ms, duration=${p.soundDurationNanos?.div(1_000_000)} ms, centroid=${String.format(Locale.ROOT, "%.0f", p.spectralCentroidHz)} Hz]" else "FAST MOVE SOUND [no distinct acoustic pulse]"
            is ArchivedPlayerInactiveSpeciesSpriteFingerprintMeasured -> "INACTIVE SPRITE [slot=${p.slot}, fingerprint=${p.fingerprint}]"
            is ArchivedMatchStarted -> "MATCH STARTED [basis=GO GLYPH]"
            is ArchivedMatchEnded -> "MATCH ENDED [result=${p.result}]"
        }

        TerminalText(text = payloadText, color = Color.White, fontSize = 12.sp)

        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalText(
                text = "P: ${timeFormatter.format(Instant.ofEpochMilli(article.perceivedAt))}",
                color = Color.Gray,
                fontSize = 8.sp,
                modifier = Modifier.weight(1f)
            )
            TerminalText(
                text = "R: ${timeFormatter.format(Instant.ofEpochMilli(article.recordedAt))}",
                color = Color.Gray,
                fontSize = 8.sp,
                modifier = Modifier.weight(1f)
            )
        }
        article.monotonicTimeNanos?.let { monotonicTimeNanos ->
            TerminalText(
                text = "M: ${monotonicTimeNanos}ns",
                color = Color.Gray,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun ArticleDetailsOverlay(
    article: ArchivedRealityArticle,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val fullTimeFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneId.systemDefault())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack.copy(alpha = 0.95f))
            .border(1.dp, TerminalGreen, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            TerminalHeader(text = "ARTICLE DETAILS")
            
            DetailField(label = "SOURCE ID", value = article.sourceId)
            
            val payloadText = when (val p = article.payload) {
                is ArchivedRawText -> p.value
                is ArchivedRawInt -> p.value.toString()
                is ArchivedMatchRecordStarted -> "MATCH RECORD STARTED [basis=DROIDBALL DEPLOYED]"
                is ArchivedBattleOverlayOpened -> "BATTLE OVERLAY OPENED [reason=${p.reason}]"
                is ArchivedTeamSelectScanStartedByUser -> "TEAM SELECT SCAN STARTED BY USER"
                is ArchivedScreenIgnoredByUser -> "SCREEN INTERPRETATION SUSPENDED BY USER"
                is ArchivedObservationRestartedByUser -> "OBSERVATION RESTARTED BY USER"
                is ArchivedObservationSessionStoppedByUser -> "OBSERVATION SESSION STOPPED BY USER"
                is ArchivedVsScreenWitnessed -> "VS SCREEN WITNESSED [central anchor available]"
                is ArchivedAttackIncoming -> "ATTACK INCOMING"
                is ArchivedPokemonIdentified -> "POKEMON IDENTIFIED: ${p.species}"
                is ArchivedActivePokemonSpeciesWitnessed -> "ACTIVE SPECIES [side=${p.side}, species=${p.speciesName}, id=${p.speciesId ?: "unresolved"}]"
                is ArchivedActivePokemonFaintedWitnessed -> "FAINTED [side=${p.side}, species=${p.speciesName ?: "unresolved"}, id=${p.speciesId ?: "unresolved"}, basis=${p.basis}]"
                is ArchivedTeamSelectPartyWitnessed -> "TEAM SELECT PARTY WITNESSED"
                is ArchivedTeamSelectLeagueWitnessed -> "LEAGUE [${p.league.replace('_', ' ')}; ${p.basis}]"
                is ArchivedPlayerTeamRosterSlotWitnessed -> "PLAYER ROSTER [slot=${p.slot}, species=${p.speciesName}, id=${p.speciesId ?: "unresolved"}]"
                is ArchivedPlayerTeamRosterConfirmation -> "PLAYER ROSTER ${if (p.confirmed) "CONFIRMED" else "REJECTED"} [${p.speciesBySlot.joinToString()}]"
                is ArchivedPlayerTeamSlotConfigured -> "CURRENT TEAM [slot=${p.slot}, species=${p.speciesName}, id=${p.speciesId}, fast=${p.fastMoveName}, charged=${p.chargedMoveNames.joinToString(" / ")}]"
                is ArchivedSupportingMatchStart -> "MATCH START SUPPORT [frame=${p.frameIndex}, upper=${String.format(Locale.ROOT, "%.3f", p.upperColorfulPixelFraction)}, lower=${String.format(Locale.ROOT, "%.3f", p.lowerColorfulPixelFraction)}, basis=${p.basis}]"
                is ArchivedCountdownGlyphWitnessed -> "COUNTDOWN GLYPH [glyph=${p.glyph}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}, frame=${p.frameIndex}, basis=${p.basis}]"
                is ArchivedCropCaptured -> "CROP CAPTURED [crop=${p.cropName}, artifact=${p.artifactPath}, sha256=${p.sha256}, bytes=${p.byteCount}, bounds=${p.cropLeft},${p.cropTop},${p.cropRight},${p.cropBottom}]"
                is ArchivedSpeciesCheckMeasured -> "SPECIES CHECK [${p.side} ${p.status}, ${p.reason}, ${p.elapsedNanos / 1_000_000} ms / ${p.targetNanos / 1_000_000} ms target${if (p.elapsedNanos > p.targetNanos) " MISSED" else ""}, species=${p.speciesName ?: "unresolved"}]"
            is ArchivedAudioInputStatus -> "AUDIO INPUT [${p.captureSource}: ${p.state}]"
            is ArchivedAudioCaptured -> "AUDIO CAPTURED [source=${p.captureSource}, peak=${p.peakAmplitude}, artifact=${p.artifactPath}, sha256=${p.sha256}, bytes=${p.byteCount}, cue=${p.cueKind}, ${p.sampleRateHz} Hz, ${p.channelCount} channel(s), ${p.durationNanos / 1_000_000} ms]"
                is ArchivedBattleCryCandidatesMeasured -> "CRY CANDIDATES [cue=${p.cueKind}, ${p.candidates.joinToString { "#${it.speciesId} ${String.format(Locale.ROOT, "%.3f", it.similarity)}" }}]"
            is ArchivedVisualCaptureGapObserved -> "VISUAL GAP [${p.durationNanos / 1_000_000} ms unobserved]"
            is ArchivedOutOfBattleMenuWitnessed -> "OUT OF BATTLE MENU WITNESSED"
            is ArchivedWitnessOperating -> "WITNESS ${if (p.operating) "OPERATING" else "STOPPED"}"
                is ArchivedActivePokemonTypesWitnessed -> "ACTIVE TYPES [side=${p.side}, types=${p.types.joinToString()}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}, basis=${p.basis}]"
                is ArchivedGetReadyWitnessed -> "GET READY"
                is ArchivedChargeMoveUsedAnnounced -> "CHARGE MOVE USED"
                is ArchivedDeviceMotionPulseMeasured -> "DEVICE MOTION PULSE [duration=${p.durationNanos} ns, peak=${p.peakLinearAccelerationMetersPerSecondSquared} m/s², rms=${p.rmsLinearAccelerationMetersPerSecondSquared} m/s², samples=${p.sampleCount}, sensor=${p.sensorType}]"
                is ArchivedChargeMoveQteVibrationPatternInferred -> "CHARGE MOVE QTE VIBRATION [side=${p.side}, pulses=${p.pulseCount}, window=${p.windowNanos} ns, basis=${p.basis}]"
                is ArchivedPlayerInactiveHpBarMeasured -> "INACTIVE HP [slot=${p.slot}, fill=${String.format(Locale.ROOT, "%.1f", p.filledFraction * 100)}%]"
                is ArchivedPlayerPokeBallCountMeasured -> "PLAYER POKÉ BALLS [${p.visibleCount}/${p.maximumCount} visible]"
                is ArchivedOpponentBattleResourceCountMeasured -> "OPPONENT ${p.resource.replace('_', ' ')} [${p.visibleCount}/${p.maximumCount} visible]"
            is ArchivedActiveHpBarMeasured -> "ACTIVE HP [side=${p.side}, fill=${String.format(Locale.ROOT, "%.1f", p.filledFraction * 100)}%, bounds=${p.barLeft},${p.barTop},${p.barRight},${p.barBottom}]"
            is ArchivedActiveHpBarMotionCadenceMeasured -> "HP MOTION CADENCE [attacker=${p.movingSide}, interval=${String.format(Locale.ROOT, "%.2f", p.intervalNanos / 1_000_000_000.0)}s, excursion=${String.format(Locale.ROOT, "%.1f", p.verticalExcursionPixels)}px]"
            is ArchivedPlayerChargeMoveEnergyFillIncreased -> "PLAYER ENERGY FILL [slots=${p.changedSlots.joinToString()}, before=${p.beforeBySlot.joinToString { String.format(Locale.ROOT, "%.3f", it) }}, after=${p.afterBySlot.joinToString { String.format(Locale.ROOT, "%.3f", it) }}]"
            is ArchivedPlayerChargeMoveEnergyFillCadenceMeasured -> "PLAYER ENERGY FILL CADENCE [interval=${p.intervalNanos} ns (${String.format(Locale.ROOT, "%.3f", p.intervalNanos / 1_000_000_000.0)}s), slots=${p.contributingSlots.joinToString()}]"
            is ArchivedActiveHpBarBorderCadenceMeasured -> "HP BORDER CADENCE [bar=${p.damagedBarSide}, attacker=${if (p.damagedBarSide == "PLAYER") "OPPONENT" else "PLAYER"}, interval=${String.format(Locale.ROOT, "%.2f", p.intervalNanos / 1_000_000_000.0)}s, orange=${p.peakOrangeFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "legacy"}, whiteBaseline=${p.baselineWhiteFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "legacy"}]"
            is ArchivedActiveHpBarBorderPulseObserved -> "HP BORDER PULSE [bar=${p.damagedBarSide}, attacker=${if (p.damagedBarSide == "PLAYER") "OPPONENT" else "PLAYER"}, orange=${p.peakOrangeFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: String.format(Locale.ROOT, "legacy %.3f", p.peakColorDistance)}, whiteBaseline=${p.baselineWhiteFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "legacy"}]"
            is ArchivedHpBarBorderPulse -> "HP BORDER PULSE [bar=${p.barSide}, status=${p.status}, orange=${p.peakOrangeFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "n/a"}, whiteBaseline=${p.baselineWhiteFraction?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "n/a"}]"
            is ArchivedActiveHpBarDamageTickMeasured -> "HP DAMAGE TICK [bar=${p.damagedSide}, attacker=${if (p.damagedSide == "PLAYER") "OPPONENT" else "PLAYER"}, before=${String.format(Locale.ROOT, "%.1f", p.beforeFraction * 100)}%, after=${String.format(Locale.ROOT, "%.1f", p.afterFraction * 100)}%, lost=${String.format(Locale.ROOT, "%.1f", p.lostFraction * 100)}%]"
            is ArchivedFastMoveRecipientVisualArtifactMeasured -> "FAST MOVE VISUAL ARTIFACT [recipient=${p.damagedSide}, changed=${p.changedSampleCount} samples (${String.format(Locale.ROOT, "%.1f", p.changedPixelFraction * 100)}%), colorDistance=${String.format(Locale.ROOT, "%.3f", p.meanColorDistance)}, rgb=${String.format(Locale.ROOT, "%.3f", p.meanRed)},${String.format(Locale.ROOT, "%.3f", p.meanGreen)},${String.format(Locale.ROOT, "%.3f", p.meanBlue)}, centroid=${String.format(Locale.ROOT, "%.3f", p.centroidX)},${String.format(Locale.ROOT, "%.3f", p.centroidY)}]"
            is ArchivedFastMoveRecipientVisualCadenceMeasured -> "FAST MOVE VISUAL CADENCE [recipient=${p.damagedSide}, interval=${p.intervalNanos} ns (${String.format(Locale.ROOT, "%.3f", p.intervalNanos / 1_000_000_000.0)}s)]"
            is ArchivedFastMoveUseObserved -> "FAST MOVE USE [id=${p.useId}, attacker=${p.attackingSide}, recipient=${p.damagedSide}, appearance=${p.appearanceId ?: "unresolved"}, species=${p.attackerSpeciesName ?: "unresolved"}, evidence=${p.evidenceKinds.joinToString()}, basis=${p.basis}]"
            is ArchivedFastMoveEffectivenessWitnessed -> "FAST MOVE EFFECTIVENESS [${p.effectiveness}, damaged=${p.damagedSide ?: "direction inferred downstream"}, text=${p.recognizedText}]"
            is ArchivedFastMoveIdentified -> "FAST MOVE [side=${p.side}, species=${p.speciesName}, move=${p.moveName}, cadence=${p.observedMedianIntervalNanos?.let { String.format(Locale.ROOT, "%.2f", it / 1_000_000_000.0) + "s" } ?: "not yet measured"}]"
            is ArchivedFastMoveEnergyDerived -> "ENERGY DERIVED [side=${p.side}, move=${p.moveName}, uses=${p.observedCompletedUses}, generated=${p.totalEnergyGenerated}]"
            is ArchivedChargedMoveEnergySpent -> "CHARGED ENERGY SPENT [side=${p.side}, species=${p.speciesName}, move=${p.moveName}, cost=${p.energyCost}, basis=${p.basis}]"
            is ArchivedFastMoveSoundMeasured -> if (p.audible) "FAST MOVE SOUND [onset=${p.onsetOffsetNanos?.div(1_000_000)} ms, duration=${p.soundDurationNanos?.div(1_000_000)} ms, centroid=${String.format(Locale.ROOT, "%.0f", p.spectralCentroidHz)} Hz, peak=${String.format(Locale.ROOT, "%.3f", p.peakAmplitude)}]" else "FAST MOVE SOUND [no distinct acoustic pulse, peak=${String.format(Locale.ROOT, "%.3f", p.peakAmplitude)}]"
                is ArchivedPlayerInactiveSpeciesSpriteFingerprintMeasured -> "INACTIVE SPRITE [slot=${p.slot}, fingerprint=${p.fingerprint}, bounds=${p.sampleLeft},${p.sampleTop},${p.sampleRight},${p.sampleBottom}]"
                is ArchivedMatchStarted -> "MATCH STARTED [basis=GO GLYPH]"
                is ArchivedMatchEnded -> "MATCH ENDED [result=${p.result}]"
            }
            DetailField(label = "PAYLOAD CONTENT", value = payloadText)
            
            DetailField(label = "CONFIDENCE SCORE", value = article.confidence?.toString() ?: "Unknown")
            
            DetailField(label = "PERCEIVED TIME", value = fullTimeFormatter.format(Instant.ofEpochMilli(article.perceivedAt)))
            DetailField(label = "RECORDED TIME", value = fullTimeFormatter.format(Instant.ofEpochMilli(article.recordedAt)))
            DetailField(label = "MONOTONIC TIME", value = article.monotonicTimeNanos?.let { "${it} ns" } ?: "Not recorded")

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = TerminalGreen.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            DetailField(label = "ARTICLE ID", value = article.articleId)
            DetailField(label = "SEQUENCE NUMBER", value = article.sequenceNumber?.toString() ?: "Not recorded")
            DetailField(label = "MATCH ID", value = article.matchId)
            
            Spacer(modifier = Modifier.height(8.dp))
            TerminalText(text = "PREDECESSOR IDS:", color = TerminalPurple, fontSize = 10.sp)
            if (article.predecessorIds.isEmpty()) {
                TerminalText(text = "  None", color = Color.Gray, fontSize = 10.sp)
            } else {
                article.predecessorIds.forEach { id ->
                    TerminalText(text = "  • $id", color = Color.White, fontSize = 9.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TerminalText(text = "EVIDENCE REFERENCES:", color = TerminalPurple, fontSize = 10.sp)
            if (article.evidenceReferences.isNullOrEmpty()) {
                TerminalText(text = "  Not recorded", color = Color.Gray, fontSize = 10.sp)
            } else {
                article.evidenceReferences.forEach { ref ->
                    TerminalText(text = "  • $ref", color = Color.White, fontSize = 9.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        TerminalText(text = label, color = TerminalDimGreen, fontSize = 9.sp)
        TerminalText(text = value, color = Color.White, fontSize = 11.sp)
    }
}
