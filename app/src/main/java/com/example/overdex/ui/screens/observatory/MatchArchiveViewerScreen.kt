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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.*
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
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val detailScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val selectedArticle = archive.articles.getOrNull(selectedIndex)

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
            if (!showDetails && selectedArticle != null) {
                showDetails = true
                scope.launch { detailScrollState.scrollTo(0) }
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
                text = if (showDetails) "[B] CLOSE DETAILS" else "[B] RETURN TO VIEWER",
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
            is ArchivedAttackIncoming -> "ATTACK INCOMING"
            is ArchivedPokemonIdentified -> "POKEMON: ${p.species}"
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
                is ArchivedAttackIncoming -> "ATTACK INCOMING"
                is ArchivedPokemonIdentified -> "POKEMON IDENTIFIED: ${p.species}"
            }
            DetailField(label = "PAYLOAD CONTENT", value = payloadText)
            
            DetailField(label = "CONFIDENCE SCORE", value = article.confidence?.toString() ?: "Unknown")
            
            DetailField(label = "PERCEIVED TIME", value = fullTimeFormatter.format(Instant.ofEpochMilli(article.perceivedAt)))
            DetailField(label = "RECORDED TIME", value = fullTimeFormatter.format(Instant.ofEpochMilli(article.recordedAt)))

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
