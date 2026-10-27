package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.team.FieldTeamEditor
import com.example.overdex.battle.team.SavedBattleTeam

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
    val member = team.slots[slot]
    val ink = Color(0xFF315E63)
    Column(Modifier.width(width).heightIn(max = 470.dp)
        .background(Color(0xFFF3FCF5), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("TEAM & MOVES", color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FieldTeamAction("SAVED TEAMS", !team.loading) { picker = "teams" }
            FieldTeamAction("RESCAN", !team.loading) { editor.close(); onRescan() }
            FieldTeamAction("CLOSE") { editor.close() }
        }
        when (picker) {
            "teams" -> {
                FieldTeamAction("‹ BACK") { picker = null }
                if (savedTeams.isEmpty()) Text("No named teams saved yet.", color = ink)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(savedTeams, key = { it.id }) { saved ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !team.loading) {
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
                Box(Modifier.fillMaxWidth().background(Color.White).padding(10.dp)) {
                    if (query.isEmpty()) Text("Search species…", color = ink.copy(alpha = .5f), fontSize = 14.sp)
                    BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = ink, fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(names.filter { it.contains(query, ignoreCase = true) }, key = { it }) { name ->
                        FieldTeamAction(name, !team.loading, Modifier.fillMaxWidth()) {
                            editor.chooseSpecies(slot, name); picker = null
                        }
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    team.slots.forEachIndexed { index, item ->
                        Column(Modifier.weight(1f).background(if (slot == index) Color(0xFFC2E7DB) else Color.Transparent,
                            RoundedCornerShape(6.dp)).clickable { slot = index }.padding(6.dp)) {
                            Text("${index + 1}${if (item.ready) " ✓" else ""}", color = ink, fontWeight = FontWeight.Bold)
                            Text(item.pokemon?.name ?: item.scannedName ?: "Choose", color = ink, fontSize = 11.sp)
                        }
                    }
                }
                Text(if (slot == 0) "Slot 1 enters first" else "Slot ${slot + 1}", color = ink, fontSize = 11.sp)
                FieldTeamAction("${member.pokemon?.name ?: member.scannedName ?: "CHOOSE SPECIES"}  ›", !team.loading) {
                    query = ""; picker = "species"
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                }
                Text("${team.slots.count { it.ready }}/3 move sets selected", color = ink, fontSize = 12.sp)
                Text("Tap USE THIS TEAM to save these moves.", color = ink, fontSize = 10.sp)
                FieldTeamAction("USE THIS TEAM", team.ready, Modifier.fillMaxWidth()) { editor.useTeam() }
            }
        }
        if (team.loading) Text("Loading team…", color = ink, fontSize = 12.sp)
        team.error?.let { Text(it, color = Color(0xFFAD3126), fontSize = 12.sp) }
    }
}

@Composable
private fun FieldTeamAction(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, action: () -> Unit) {
    Text(label, color = Color(0xFF315E63).copy(alpha = if (enabled) 1f else .4f),
        fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clickable(enabled = enabled, onClick = action).padding(vertical = 9.dp, horizontal = 3.dp))
}
