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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.replay.ReplayExcerptStore
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

@Composable
fun MatchArchiveViewerScreen(
    archive: MatchArchive,
    onBack: () -> Unit,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    var showReplay by remember { mutableStateOf(false) }
    var excerptStart by remember { mutableStateOf<ArchivedRealityArticle?>(null) }
    var excerptStatus by remember { mutableStateOf<String?>(null) }
    
    val listState = rememberLazyListState()
    val detailScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val selectedArticle = archive.articles.getOrNull(selectedIndex)

    if (showReplay) {
        MatchReplayScreen(archive = archive, onBack = { showReplay = false })
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
        onA {
            if (!showDetails) {
                showReplay = true
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

    TerminalScreen {
        TerminalPathIndicator(path = "/signal_observatory/archive_viewer/")

        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "MATCH ARCHIVE")
            TerminalText(
                text = "ID: ${archive.matchId}",
                color = TerminalPurple,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            TerminalText(
                text = "ARTICLES: ${archive.articles.size}",
                color = TerminalDimGreen,
                fontSize = 10.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                androidx.compose.material3.TextButton(
                    enabled = selectedArticle?.monotonicTimeNanos != null,
                    onClick = {
                        excerptStart = selectedArticle
                        excerptStatus = "START MARKED: #${selectedIndex + 1}"
                    }
                ) { TerminalText(text = "[ MARK START ]", color = TerminalGreen, fontSize = 10.sp) }
                androidx.compose.material3.TextButton(
                    enabled = excerptStart != null && selectedArticle?.monotonicTimeNanos != null,
                    onClick = {
                        val start = excerptStart ?: return@TextButton
                        val end = selectedArticle ?: return@TextButton
                        excerptStatus = runCatching {
                            val excerpt = ReplayExcerptStore(context.filesDir).save(archive, start, end)
                            excerptStart = null
                            "EXCERPT SAVED: ${excerpt.excerptId.take(8)}"
                        }.getOrElse { "EXCERPT NOT SAVED: ${it.message}" }
                    }
                ) { TerminalText(text = "[ MARK END + SAVE ]", color = TerminalGreen, fontSize = 10.sp) }
            }
            androidx.compose.material3.TextButton(onClick = { showReplay = true }) {
                TerminalText(text = "[ OPEN REPLAY ]", color = TerminalGreen, fontSize = 10.sp)
            }
            excerptStatus?.let { TerminalText(text = it, color = TerminalDimGreen, fontSize = 9.sp) }

            Spacer(modifier = Modifier.height(8.dp))

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
            
            Spacer(modifier = Modifier.height(8.dp))
            TerminalText(
                text = if (showDetails) "[B] CLOSE DETAILS" else "[A] OPEN REPLAY  [B] RETURN TO VIEWER",
                color = TerminalDimGreen,
                fontSize = 10.sp
            )
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
            is ArchivedVsScreenWitnessed -> "VS SCREEN WITNESSED [central anchor available]"
            is ArchivedAttackIncoming -> "ATTACK INCOMING"
            is ArchivedPokemonIdentified -> "POKEMON: ${p.species}"
            is ArchivedActivePokemonSpeciesWitnessed -> "ACTIVE SPECIES [side=${p.side}, species=${p.speciesName}, id=${p.speciesId ?: "unresolved"}]"
            is ArchivedSupportingMatchStart -> "MATCH START SUPPORT [frame=${p.frameIndex}, upper=${String.format(Locale.ROOT, "%.3f", p.upperColorfulPixelFraction)}, lower=${String.format(Locale.ROOT, "%.3f", p.lowerColorfulPixelFraction)}, basis=${p.basis}]"
            is ArchivedCountdownGlyphWitnessed -> "COUNTDOWN GLYPH [glyph=${p.glyph}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}, frame=${p.frameIndex}, basis=${p.basis}]"
            is ArchivedCropCaptured -> "CROP CAPTURED [crop=${p.cropName}, artifact=${p.artifactPath}, sha256=${p.sha256.take(12)}…]"
            is ArchivedAudioCaptured -> "AUDIO CAPTURED [cue=${p.cueKind}, ${p.sampleRateHz} Hz, ${p.channelCount} ch, ${p.durationNanos / 1_000_000} ms, sha256=${p.sha256.take(12)}…]"
            is ArchivedBattleCryCandidatesMeasured -> "CRY CANDIDATES [cue=${p.cueKind}, ${p.candidates.joinToString { "#${it.speciesId} ${String.format(Locale.ROOT, "%.3f", it.similarity)}" }}]"
            is ArchivedVisualCaptureGapObserved -> "VISUAL GAP [${p.durationNanos / 1_000_000} ms unobserved]"
            is ArchivedOutOfBattleMenuWitnessed -> "OUT OF BATTLE MENU WITNESSED"
            is ArchivedWitnessOperating -> "WITNESS ${if (p.operating) "OPERATING" else "STOPPED"}"
            is ArchivedActivePokemonTypesWitnessed -> "ACTIVE TYPES [side=${p.side}, types=${p.types.joinToString()}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}]"
            is ArchivedGetReadyWitnessed -> "GET READY"
            is ArchivedChargeMoveUsedAnnounced -> "CHARGE MOVE USED"
            is ArchivedPlayerInactiveHpBarMeasured -> "INACTIVE HP [slot=${p.slot}, fill=${String.format(Locale.ROOT, "%.1f", p.filledFraction * 100)}%]"
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
                is ArchivedVsScreenWitnessed -> "VS SCREEN WITNESSED [central anchor available]"
                is ArchivedAttackIncoming -> "ATTACK INCOMING"
                is ArchivedPokemonIdentified -> "POKEMON IDENTIFIED: ${p.species}"
                is ArchivedActivePokemonSpeciesWitnessed -> "ACTIVE SPECIES [side=${p.side}, species=${p.speciesName}, id=${p.speciesId ?: "unresolved"}]"
                is ArchivedSupportingMatchStart -> "MATCH START SUPPORT [frame=${p.frameIndex}, upper=${String.format(Locale.ROOT, "%.3f", p.upperColorfulPixelFraction)}, lower=${String.format(Locale.ROOT, "%.3f", p.lowerColorfulPixelFraction)}, basis=${p.basis}]"
                is ArchivedCountdownGlyphWitnessed -> "COUNTDOWN GLYPH [glyph=${p.glyph}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}, frame=${p.frameIndex}, basis=${p.basis}]"
                is ArchivedCropCaptured -> "CROP CAPTURED [crop=${p.cropName}, artifact=${p.artifactPath}, sha256=${p.sha256}, bytes=${p.byteCount}, bounds=${p.cropLeft},${p.cropTop},${p.cropRight},${p.cropBottom}]"
                is ArchivedAudioCaptured -> "AUDIO CAPTURED [artifact=${p.artifactPath}, sha256=${p.sha256}, bytes=${p.byteCount}, cue=${p.cueKind}, ${p.sampleRateHz} Hz, ${p.channelCount} channel(s), ${p.durationNanos / 1_000_000} ms]"
                is ArchivedBattleCryCandidatesMeasured -> "CRY CANDIDATES [cue=${p.cueKind}, ${p.candidates.joinToString { "#${it.speciesId} ${String.format(Locale.ROOT, "%.3f", it.similarity)}" }}]"
            is ArchivedVisualCaptureGapObserved -> "VISUAL GAP [${p.durationNanos / 1_000_000} ms unobserved]"
            is ArchivedOutOfBattleMenuWitnessed -> "OUT OF BATTLE MENU WITNESSED"
            is ArchivedWitnessOperating -> "WITNESS ${if (p.operating) "OPERATING" else "STOPPED"}"
                is ArchivedActivePokemonTypesWitnessed -> "ACTIVE TYPES [side=${p.side}, types=${p.types.joinToString()}, similarity=${String.format(Locale.ROOT, "%.3f", p.similarity)}, basis=${p.basis}]"
                is ArchivedGetReadyWitnessed -> "GET READY"
                is ArchivedChargeMoveUsedAnnounced -> "CHARGE MOVE USED"
                is ArchivedPlayerInactiveHpBarMeasured -> "INACTIVE HP [slot=${p.slot}, fill=${String.format(Locale.ROOT, "%.1f", p.filledFraction * 100)}%]"
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
