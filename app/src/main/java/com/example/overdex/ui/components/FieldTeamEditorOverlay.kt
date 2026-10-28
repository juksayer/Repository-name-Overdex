package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.team.FieldTeamEditor
import com.example.overdex.battle.team.SavedBattleTeam
import com.example.overdex.model.OwnedPokemon

private val FieldTeamInk = Color(0xFF315E63)

/** Touch controls belong to this field panel, never to the CRT's GlassShield. */
@Composable
fun FieldTeamEditorOverlay(
    editor: FieldTeamEditor,
    savedTeams: List<SavedBattleTeam>,
    width: androidx.compose.ui.unit.Dp,
    onRescan: () -> Unit,
) {
    val draft by editor.draft.collectAsState()
    val names by editor.speciesNames.collectAsState()
    val team = draft ?: return
    var slot by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var legacyCardToConfirm by remember(slot) { mutableStateOf<String?>(null) }
    val member = team.slots[slot]
    val ink = FieldTeamInk
    Column(Modifier.width(width).heightIn(max = 470.dp)
        .background(Color(0xFFF3FCF5), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("TEAM & MOVES", color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldTeamAction("SAVED TEAMS", !team.loading, Modifier.weight(1f)) { picker = "teams" }
            FieldTeamAction("RESCAN", !team.loading, Modifier.weight(1f)) { editor.close(); onRescan() }
            FieldTeamAction("CLOSE", modifier = Modifier.weight(1f)) { editor.close() }
        }
        when (picker) {
            "teams" -> {
                FieldTeamAction("‹ BACK") { picker = null }
                if (savedTeams.isEmpty()) Text("No named teams saved yet.", color = ink)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(savedTeams, key = { it.id }) { saved ->
                        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !team.loading) {
                            editor.open(saved.team); picker = null
                        }.padding(vertical = 10.dp)) {
                            Text(saved.name, color = ink, fontWeight = FontWeight.Bold)
                            Text(saved.team.members.joinToString(" · ") { it.speciesName }, color = ink, fontSize = 11.sp)
                        }
                    }
                }
            }
            "species" -> {
                FieldTeamAction("‹ BACK") { picker = null }
                FieldTeamInput("Search species", query, "Search species…", enabled = !team.loading) { query = it }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(names.filter { it.contains(query, ignoreCase = true) }, key = { it }) { name ->
                        FieldTeamAction(name, !team.loading, Modifier.fillMaxWidth()) {
                            editor.chooseSpecies(slot, name); picker = null
                        }
                    }
                }
            }
            "cards" -> {
                FieldTeamAction("‹ BACK") { picker = null; legacyCardToConfirm = null }
                Text("Choose the card for ${member.pokemon?.name ?: member.scannedName ?: "this Pokémon"}.",
                    color = ink, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(member.ownedCandidates, key = { it.id }) { card ->
                        Column(Modifier.fillMaxWidth().background(Color(0xFFE1F1E8), RoundedCornerShape(6.dp)).padding(8.dp)) {
                            FieldOwnedCardDescription(card)
                            if (legacyCardToConfirm == card.id) {
                                Text("Confirm this is ${member.pokemon?.name ?: member.scannedName ?: "the scanned Pokémon"}?",
                                    color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("This older card has no confirmed form.", color = ink, fontSize = 11.sp)
                                FieldTeamAction("YES, USE THIS CARD", !team.loading, Modifier.fillMaxWidth()) {
                                    editor.chooseOwnedCard(slot, card.id)
                                    legacyCardToConfirm = null
                                    picker = null
                                }
                                FieldTeamAction("CANCEL", modifier = Modifier.fillMaxWidth()) { legacyCardToConfirm = null }
                            } else {
                                FieldTeamAction("USE THIS CARD", !team.loading, Modifier.fillMaxWidth()) {
                                    if (card.speciesName.isNullOrBlank()) legacyCardToConfirm = card.id
                                    else {
                                        editor.chooseOwnedCard(slot, card.id)
                                        picker = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        team.slots.forEachIndexed { index, item ->
                            Column(Modifier.weight(1f).heightIn(min = 48.dp)
                                .background(if (slot == index) Color(0xFFC2E7DB) else Color.Transparent,
                                    RoundedCornerShape(6.dp)).clickable { slot = index }.padding(6.dp)) {
                                Text("${index + 1}${if (item.ready) " ✓" else ""}", color = ink, fontWeight = FontWeight.Bold)
                                Text(item.pokemon?.name ?: item.scannedName ?: "Choose", color = ink, fontSize = 11.sp)
                            }
                        }
                    }
                    Text(if (slot == 0) "Slot 1 enters first" else "Slot ${slot + 1}", color = ink, fontSize = 11.sp)
                    FieldTeamAction("${member.pokemon?.name ?: member.scannedName ?: "CHOOSE SPECIES"}  ›", !team.loading,
                        Modifier.fillMaxWidth()) {
                        query = ""; picker = "species"
                    }
                    if (team.collectionEnabled) {
                        if (team.isScannedTeam && team.slots.any { it.ownedCandidates.isNotEmpty() } &&
                            team.slots.any { it.ownedPokemonId == null }) {
                            Text("Some Pokémon may already have cards. Choose their cards to keep their battle history together.",
                                color = ink, fontSize = 12.sp)
                        }
                        if (team.isScannedTeam && team.slots.all { it.pokemon != null && it.ownedCandidates.isEmpty() && it.newCard == null && it.ownedPokemonId == null }) {
                            Text("These Pokémon do not have matching cards in your binders yet.", color = ink, fontSize = 12.sp)
                            FieldTeamAction("IMPORT THIS TEAM", !team.loading, Modifier.fillMaxWidth()) {
                                team.slots.indices.forEach(editor::importNewCard)
                            }
                        }
                        val selectedCard = member.ownedCandidates.firstOrNull { it.id == member.ownedPokemonId }
                        selectedCard?.let {
                            Text("SELECTED CARD", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            FieldOwnedCardDescription(it)
                        }
                        if (member.ownedCandidates.isNotEmpty()) {
                            FieldTeamAction("USE EXISTING CARD", !team.loading, Modifier.fillMaxWidth()) {
                                legacyCardToConfirm = null; picker = "cards"
                            }
                        }
                        FieldTeamAction(if (member.newCard != null) "NEW CARD SELECTED" else "IMPORT NEW CARD",
                            !team.loading && member.pokemon != null && member.newCard == null, Modifier.fillMaxWidth()) {
                            editor.importNewCard(slot)
                        }
                        member.newCard?.let { card ->
                            Text("Check CP, shadow and shiny before saving.", color = ink, fontSize = 12.sp)
                            FieldTeamInput("Nickname", card.nickname, "Optional", enabled = !team.loading) { value ->
                                editor.updateImport(slot) { it.copy(nickname = value) }
                            }
                            FieldTeamInput("CP", card.cp, "Optional", enabled = !team.loading, numeric = true) { value ->
                                editor.updateImport(slot) { it.copy(cp = value.filter(Char::isDigit)) }
                            }
                            FieldTeamAction("${if (card.shadow) "✓" else "○"} Shadow", !team.loading,
                                Modifier.fillMaxWidth()) {
                                editor.updateImport(slot) { it.copy(shadow = !it.shadow, purified = false) }
                            }
                            FieldTeamAction("${if (card.shiny) "✓" else "○"} Shiny", !team.loading,
                                Modifier.fillMaxWidth()) {
                                editor.updateImport(slot) { it.copy(shiny = !it.shiny) }
                            }
                        }
                    }
                    Text("FAST MOVE · choose one", color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    member.pokemon?.fastMoves?.forEach { move ->
                        FieldTeamAction("${if (member.fastMove == move.name) "✓" else "○"} ${move.name}", !team.loading,
                            Modifier.fillMaxWidth()) { editor.chooseFast(slot, move.name) }
                    }
                    Text("CHARGED MOVES · choose one or two", color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    member.pokemon?.chargedMoves?.forEach { move ->
                        FieldTeamAction("${if (move.name in member.chargedMoves) "✓" else "○"} ${move.name}",
                            !team.loading && (member.chargedMoves.size < 2 || move.name in member.chargedMoves),
                            Modifier.fillMaxWidth()) { editor.toggleCharged(slot, move.name) }
                    }
                    Text("${team.slots.count { it.ready }}/3 move sets selected", color = ink, fontSize = 12.sp)
                    if (team.collectionEnabled) {
                        FieldTeamInput("Team name", team.teamName, "Give this team a name", enabled = !team.loading,
                            onValueChange = editor::setTeamName)
                        FieldTeamAction("SAVE TEAM & CARDS", team.collectionReady, Modifier.fillMaxWidth()) {
                            editor.saveTeamToCollection()
                        }
                        Text("Or continue with these moves without importing cards.", color = ink, fontSize = 11.sp)
                    } else {
                        Text("Tap USE THIS TEAM to save these moves.", color = ink, fontSize = 10.sp)
                    }
                    FieldTeamAction("USE THIS TEAM", team.ready, Modifier.fillMaxWidth()) { editor.useTeam() }
                }
            }
        }
        if (team.loading) Text("Loading team…", color = ink, fontSize = 12.sp)
        team.error?.let { Text(it, color = Color(0xFFAD3126), fontSize = 12.sp) }
    }
}

@Composable
private fun FieldOwnedCardDescription(card: OwnedPokemon) {
    Text(card.displayName?.takeIf(String::isNotBlank) ?: "Unnamed card", color = FieldTeamInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Text("Species / form: ${card.speciesName?.takeIf(String::isNotBlank) ?: "Not confirmed"}", color = FieldTeamInk, fontSize = 12.sp)
    Text("CP ${card.cp ?: "not recorded"} · ${when {
        card.isShadow -> "Shadow"
        card.isPurified -> "Purified"
        else -> "Regular"
    }} · ${if (card.isShiny) "Shiny" else "Not shiny"}", color = FieldTeamInk, fontSize = 11.sp)
    Text(listOfNotNull(card.fastMove, card.chargedMove1, card.chargedMove2).joinToString(" · ")
        .ifEmpty { "Moves not recorded" }, color = FieldTeamInk, fontSize = 11.sp)
}

@Composable
private fun FieldTeamInput(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean = true,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = FieldTeamInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        BasicTextField(value, onValueChange, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            textStyle = TextStyle(color = FieldTeamInk, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Color.White),
            decorationBox = { input ->
                Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = FieldTeamInk.copy(alpha = .5f), fontSize = 14.sp)
                    input()
                }
            })
    }
}

@Composable
private fun FieldTeamAction(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, action: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = action)
        .padding(vertical = 9.dp, horizontal = 3.dp), contentAlignment = Alignment.CenterStart) {
        Text(label, color = FieldTeamInk.copy(alpha = if (enabled) 1f else .4f),
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
