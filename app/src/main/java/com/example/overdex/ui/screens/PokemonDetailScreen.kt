package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.TypeBadge
import com.example.overdex.ui.components.TypeIconStyle
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.example.overdex.ui.components.*

sealed interface PokemonDetailNavItem {
    data object Back : PokemonDetailNavItem
    data object Audio : PokemonDetailNavItem
    data object Artwork : PokemonDetailNavItem
    data object Region : PokemonDetailNavItem
    data class Type(val type: PokemonType) : PokemonDetailNavItem
    data class Evolution(val id: Int, val name: String) : PokemonDetailNavItem
    data class Weakness(val type: PokemonType) : PokemonDetailNavItem
    data class Resistance(val type: PokemonType) : PokemonDetailNavItem
    data class Move(val move: com.example.overdex.model.Move) : PokemonDetailNavItem
    data class FieldNoteItem(val note: com.example.overdex.model.FieldNote) : PokemonDetailNavItem
}

@Composable
fun PokemonDetailScreen(
    pokemon: Pokemon,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onBackClick: () -> Unit,
    onPlayCry: (String) -> Unit,
    onWarmUpCry: (String) -> Unit = {},
    onMoveClick: (String) -> Unit,
    onTypeClick: (PokemonType) -> Unit,
    onRegionClick: (String) -> Unit,
    onEvolutionClick: (Int) -> Unit,
    onLaunchProbe: () -> Unit = {},
    onLaunchObservatory: () -> Unit = {},
    viewModel: PokedexViewModel,
) {
    val fieldNotes by remember(pokemon.id) { viewModel.getFieldNotes(pokemon.id) }.collectAsState(initial = emptyList())

    val navItems = remember(pokemon, fieldNotes) {
        val header = listOf(
            PokemonDetailNavItem.Back,
            PokemonDetailNavItem.Audio,
            PokemonDetailNavItem.Artwork
        )
        
        val body = buildList {
            add(PokemonDetailNavItem.Region)
            pokemon.types.forEach { add(PokemonDetailNavItem.Type(it)) }
            
            pokemon.prevEvolutions.forEach { add(PokemonDetailNavItem.Evolution(it.num.toInt(), it.name)) }
            pokemon.nextEvolutions.forEach { add(PokemonDetailNavItem.Evolution(it.num.toInt(), it.name)) }
            
            val weaknesses = pokemon.getWeaknesses().filter { it.value > 1.0 }
            weaknesses.keys.sortedBy { it.ordinal }.forEach {
                add(PokemonDetailNavItem.Weakness(it))
            }
            
            val resistances = pokemon.getWeaknesses().filter { it.value < 1.0 }
            resistances.keys.sortedBy { it.ordinal }.forEach {
                add(PokemonDetailNavItem.Resistance(it))
            }
            
            pokemon.fastMoves.forEach { add(PokemonDetailNavItem.Move(it)) }
            pokemon.chargedMoves.forEach { add(PokemonDetailNavItem.Move(it)) }

            fieldNotes.forEach { add(PokemonDetailNavItem.FieldNoteItem(it)) }
        }
        
        header + body
    }

    val nav = rememberHandheldNavigationController(
        key = pokemon.id,
        itemCount = { navItems.size },
        onActivate = { index ->
            when (val item = navItems[index]) {
                PokemonDetailNavItem.Back -> onBackClick()
                PokemonDetailNavItem.Artwork -> onPlayCry(pokemon.cryUrl)
                PokemonDetailNavItem.Audio -> onPlayCry(pokemon.cryUrl)
                PokemonDetailNavItem.Region -> onRegionClick(pokemon.region)
                is PokemonDetailNavItem.Type -> onTypeClick(item.type)
                is PokemonDetailNavItem.Evolution -> onEvolutionClick(item.id)
                is PokemonDetailNavItem.Weakness -> onTypeClick(item.type)
                is PokemonDetailNavItem.Resistance -> onTypeClick(item.type)
                is PokemonDetailNavItem.Move -> onMoveClick(item.move.name)
                is PokemonDetailNavItem.FieldNoteItem -> { /* No action for now */ }
            }
        }
    )

    val requesters = remember(navItems) {
        navItems.associateWith { BringIntoViewRequester() }
    }

    HandheldFocusSync<PokemonDetailNavItem>(nav.selectedIndex, navItems, requesters)

    LaunchedEffect(pokemon.id) {
        onWarmUpCry(pokemon.cryUrl)
    }

    val scrollState = rememberScrollState()
    ODXFiShell(
        onUp = { nav.moveUp() },
        onDown = { nav.moveDown() },
        onB = onBackClick,
        onA = { nav.activate() },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onSelect = onSelect,
        onStart = onStart,
        onLaunchProbe = onLaunchProbe,
        onLaunchObservatory = onLaunchObservatory,
        viewModel = viewModel
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Visual Back Button Target
                val backItem = PokemonDetailNavItem.Back
                val isBackSelected = navItems[nav.selectedIndex] == backItem
                Box(
                    modifier = Modifier
                        .bringIntoViewRequester(requesters[backItem]!!)
                        .border(
                            width = 2.dp,
                            color = if (isBackSelected) TerminalGreen else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(
                            color = if (isBackSelected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isBackSelected) TerminalGreen else TerminalDimGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Audio Navigation Target
                val audioItem = PokemonDetailNavItem.Audio
                val isAudioSelected = navItems[nav.selectedIndex] == audioItem
                Box(
                    modifier = Modifier
                        .bringIntoViewRequester(requesters[audioItem]!!)
                        .border(
                            width = 2.dp,
                            color = if (isAudioSelected) TerminalGreen else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(
                            color = if (isAudioSelected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Play Cry",
                        tint = if (isAudioSelected) TerminalGreen else TerminalDimGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Sprite resolution through unified pipeline
            val isArtworkSelected = navItems[nav.selectedIndex] == PokemonDetailNavItem.Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .bringIntoViewRequester(requesters[PokemonDetailNavItem.Artwork]!!)
                    .border(
                        width = 2.dp,
                        color = if (isArtworkSelected) TerminalGreen else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = pokemon.spriteUrl,
                    contentDescription = pokemon.name,
                    modifier = Modifier.size(180.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            // TODO: Implement long-press behavior for artwork (e.g., download or share)

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = pokemon.formattedId, fontSize = 16.sp, color = TerminalDimGreen)
                    Text(
                        text = pokemon.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalGreen
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Region Badge
                    val regionItem = PokemonDetailNavItem.Region
                    val isRegionSelected = navItems[nav.selectedIndex] == regionItem
                    Surface(
                        color = if (isRegionSelected) TerminalGreen else TerminalGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .bringIntoViewRequester(requesters[regionItem]!!)
                            .border(
                                width = 1.dp,
                                color = if (isRegionSelected) TerminalBlack else TerminalGreen,
                                shape = RoundedCornerShape(4.dp)
                            )
                    ) {
                        Text(
                            text = pokemon.region.uppercase(),
                            color = if (isRegionSelected) TerminalBlack else TerminalGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Row {
                    pokemon.types.forEach { type ->
                        val item = PokemonDetailNavItem.Type(type)
                        val isSelected = navItems[nav.selectedIndex] == item
                        TypeBadge(
                            type = type,
                            onClick = null,
                            modifier = Modifier
                                .bringIntoViewRequester(requesters[item]!!)
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        TerminalGreen,
                                        RoundedCornerShape(4.dp)
                                    ) else Modifier
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (pokemon.genus.isNotBlank()) {
                Text(
                    text = pokemon.genus,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalDimGreen,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
            }

// Description
            Text(
                text = pokemon.description,
                fontSize = 14.sp,
                color = TerminalGreen,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, TerminalGreen, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "ATK ${pokemon.baseAttack}  DEF ${pokemon.baseDefense}  STA ${pokemon.baseStamina}",
                        color = TerminalGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "HT ${pokemon.height}  WT ${pokemon.weight}",
                color = TerminalDimGreen,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (pokemon.prevEvolutions.isNotEmpty() || pokemon.nextEvolutions.isNotEmpty()) {

                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle("EVO")

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    pokemon.prevEvolutions.forEach { evo ->
                        val item = PokemonDetailNavItem.Evolution(evo.num.toInt(), evo.name)
                        val isSelected = navItems[nav.selectedIndex] == item
                        Text(
                            text = evo.name,
                            color = if (isSelected) TerminalBlack else TerminalGreen,
                            modifier = Modifier
                                .bringIntoViewRequester(requesters[item]!!)
                                .background(
                                    if (isSelected) TerminalGreen else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp)
                        )

                        Text(
                            text = "↓",
                            color = TerminalDimGreen
                        )
                    }

                    Text(
                        text = pokemon.name,
                        color = TerminalGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    pokemon.nextEvolutions.forEach { evo ->
                        val item = PokemonDetailNavItem.Evolution(evo.num.toInt(), evo.name)
                        val isSelected = navItems[nav.selectedIndex] == item
                        Text(
                            text = "↓",
                            color = TerminalDimGreen
                        )

                        Text(
                            text = evo.name,
                            color = if (isSelected) TerminalBlack else TerminalGreen,
                            modifier = Modifier
                                .bringIntoViewRequester(requesters[item]!!)
                                .background(
                                    if (isSelected) TerminalGreen else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

// Effectiveness
            SectionTitle("Weaknesses")
            val weaknessItems = navItems.filterIsInstance<PokemonDetailNavItem.Weakness>()
            EffectivenessRow(
                multipliers = pokemon.getWeaknesses(),
                items = weaknessItems,
                nav = nav,
                navItems = navItems,
                requesters = requesters
            )
            
            SectionTitle("Resistances")
            val resistanceItems = navItems.filterIsInstance<PokemonDetailNavItem.Resistance>()
            EffectivenessRow(
                multipliers = pokemon.getWeaknesses(),
                items = resistanceItems,
                nav = nav,
                navItems = navItems,
                requesters = requesters
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Moves
            SectionTitle("Fast Moves")
            pokemon.fastMoves.forEach { move ->
                val item = PokemonDetailNavItem.Move(move)
                val isSelected = navItems[nav.selectedIndex] == item
                MoveRow(
                    move = move,
                    modifier = Modifier.bringIntoViewRequester(requesters[item]!!),
                    selected = isSelected
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            SectionTitle("Charged Moves")
            pokemon.chargedMoves.forEach { move ->
                val item = PokemonDetailNavItem.Move(move)
                val isSelected = navItems[nav.selectedIndex] == item
                MoveRow(
                    move = move,
                    modifier = Modifier.bringIntoViewRequester(requesters[item]!!),
                    selected = isSelected
                )
            }

            if (fieldNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                fieldNotes.forEach { note ->
                    val item = PokemonDetailNavItem.FieldNoteItem(note)
                    val isSelected = navItems[nav.selectedIndex] == item
                    FieldNoteSection(
                        note = note,
                        modifier = Modifier
                            .bringIntoViewRequester(requesters[item]!!)
                            .padding(vertical = 8.dp),
                        isSelected = isSelected
                    )
                }
            }
        }
    }
}

@Composable
fun FieldNoteSection(
    note: com.example.overdex.model.FieldNote,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) TerminalGreen else TerminalDimGreen.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) TerminalGreen.copy(alpha = 0.05f) else Color.Transparent,
            contentColor = TerminalGreen
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FIELD NOTE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalPurple,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = note.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalGreen,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            note.lines.forEach { line ->
                Text(
                    text = line,
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic,
                    color = TerminalGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            if (note.lesson != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "LESSON LEARNED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalDimGreen
                )
                Text(
                    text = note.lesson,
                    fontSize = 14.sp,
                    color = TerminalDimGreen,
                    textAlign = TextAlign.Center
                )
            }

            if (note.author != null) {
                Text(
                    text = "— ${note.author}",
                    fontSize = 12.sp,
                    color = TerminalDimGreen.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TerminalPurple,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun EffectivenessRow(
    multipliers: Map<PokemonType, Double>,
    items: List<PokemonDetailNavItem>,
    nav: HandheldNavigationController,
    navItems: List<PokemonDetailNavItem>,
    requesters: Map<PokemonDetailNavItem, BringIntoViewRequester>
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val type = when (item) {
                is PokemonDetailNavItem.Weakness -> item.type
                is PokemonDetailNavItem.Resistance -> item.type
                else -> return@forEach
            }
            val mult = multipliers[type] ?: 1.0
            val isSelected = navItems[nav.selectedIndex] == item
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TypeBadge(
                    type = type,
                    style = TypeIconStyle.OVERDEX,
                    onClick = null,
                    modifier = Modifier
                        .bringIntoViewRequester(requesters[item]!!)
                        .then(
                            if (isSelected) Modifier.border(
                                2.dp,
                                TerminalGreen,
                                RoundedCornerShape(4.dp)
                            ) else Modifier
                        )
                )
                Text(
                    text = "%.2fx".format(mult),
                    fontSize = 16.sp,
                    color = TerminalGreen
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
    ) {
        content()
    }
}

@Composable
fun MoveRow(
    move: Move,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) TerminalGreen else TerminalDimGreen,
                shape = CardDefaults.shape
            ),
        colors = CardDefaults.cardColors(
            containerColor = TerminalBlack,
            contentColor = TerminalGreen
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Type Badge inside MoveRow is not individually navigable here
                    // based on the requirement to keep the MoveRow as one item.
                    TypeBadge(
                        type = move.type,
                        onClick = null // Non-navigable within MoveRow for now
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = move.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    text = if (move.isFast) "FAST" else "CHARGE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalPurple,
                    modifier = Modifier
                        .border(1.dp, TerminalPurple, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Damage", move.damage.toString())
                if (move.isFast) {
                    StatItem("Energy Gain", "+${move.energy}")
                    StatItem("Turns", (move.turns ?: 0).toString())
                } else {
                    StatItem("Energy Cost", "-${move.energy}")
                    StatItem("DPE", String.format(Locale.ROOT, "%.2f", move.dpe ?: 0.0))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = TerminalDimGreen)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TerminalGreen)
    }
}
