package com.example.overdex.data.registration

import com.example.overdex.model.CaptureRegion
import com.example.overdex.model.ObservationType
import com.example.overdex.model.PokemonType

/** One movable layout. Y is an alignment aid, not a promise about GO's page layout. */
object RegistrationFields {
    val regions = listOf(
        box("CombatPower", .35f, .06f, .30f, .055f),
        box("ShinyIndicator", .80f, .12f, .10f, .045f, true),
        box("FavoriteStar", .89f, .06f, .08f, .055f, true),
        box("SpeciesName", .10f, .43f, .70f, .045f),
        box("Gender", .85f, .43f, .08f, .045f, true),
        box("HpRow", .25f, .48f, .50f, .035f),
        box("SizeLabel", .72f, .53f, .20f, .035f),
        box("TypeIcons", .30f, .575f, .40f, .045f),
        box("CandyPanel", .38f, .635f, .54f, .055f),
        box("EvolutionRow", .05f, .69f, .27f, .045f),
        box("ShadowStatus", .05f, .53f, .35f, .035f),
        box("MoveHeading", .05f, .74f, .90f, .035f),
        box("FastMoveRow", .05f, .785f, .90f, .045f),
        box("ChargedMoveRowA", .05f, .84f, .90f, .045f),
        box("ChargedMoveRowB", .05f, .895f, .90f, .045f),
        box("MovesEnd", .15f, .95f, .70f, .035f),
    )
    fun label(id: String): String = when (id) {
        "SpeciesName" -> "Name / Nickname"
        "CombatPower" -> "CP"
        "HpRow" -> "HP Row"
        "TypeIcons" -> "Type Labels"
        "CandyPanel" -> "Candy Family"
        "FastMoveRow", "SummaryFastMove" -> "Fast Move Row"
        "ChargedMoveRowA" -> "Charged Move 1"
        "ChargedMoveRowB" -> "Charged Move 2"
        "MoveHeading" -> "Gyms & Raids / Trainer Battles"
        "MovesEnd" -> "New Attack / Moves End"
        "EvolutionRow" -> "Evolution"
        "ShadowStatus" -> "Shadow / Purified"
        "SizeLabel" -> "Size Label"
        "ShinyIndicator" -> "Shiny"
        "FavoriteStar" -> "Favorite"
        else -> id
    }
    fun choices(id: String): List<Pair<String, Pair<String, String>>> = when (id) {
        "ShinyIndicator" -> listOf("Shiny" to ("SHINY" to "true"), "Not shiny" to ("SHINY" to "false"))
        "FavoriteStar" -> listOf("Favorite" to ("FAVORITE" to "true"), "Not favorite" to ("FAVORITE" to "false"))
        "Gender" -> listOf("Female" to ("GENDER" to "female"), "Male" to ("GENDER" to "male"), "Genderless" to ("GENDER" to "none"))
        "ShadowStatus" -> listOf("Shadow" to ("STATUS" to "shadow"), "Purified" to ("STATUS" to "purified"), "Neither" to ("STATUS" to "normal"))
        else -> emptyList()
    }
    private fun box(id: String, x: Float, y: Float, w: Float, h: Float, image: Boolean = false) =
        CaptureRegion(id, x, y, w, h, if (image) ObservationType.IMAGE_MATCH else ObservationType.OCR_TEXT)
}

/** A user-selected box establishes the field's role; unrelated page anchors are unnecessary. */
class RegistrationFieldReader(private val catalogue: RegistrationCatalogue) {
    fun read(id: String, lines: List<DetailTextLine>): DetailPageRead {
        val fields = mutableListOf<DetailFieldRead>()
        fun add(field: String, value: String, line: DetailTextLine) { fields += DetailFieldRead(field, value, line) }
        val ordered = lines.filter { it.text.isNotBlank() }.sortedBy { it.top }
        ordered.forEach { line ->
            val text = line.text.trim()
            val key = textKey(text)
            when (id) {
                "SpeciesName" -> if (text.any(Char::isLetter) && !key.startsWith("CP") && key !in setOf("HP", "WEIGHT", "HEIGHT"))
                    add("TITLE", text.removeSuffix("♂").removeSuffix("♀").trim(), line)
                "CombatPower" -> Regex("(?i)^CP\\s*([0-9OIl]{2,5})$").matchEntire(text)?.groupValues?.get(1)
                    ?.uppercase()?.replace('O', '0')?.replace('I', '1')?.replace('L', '1')?.toIntOrNull()
                    ?.takeIf { it in 10..99999 }?.let { add("CP", it.toString(), line) }
                "HpRow" -> if (Regex("(?i)\\d+\\s*/\\s*\\d+\\s*HP|HP\\s*\\d+\\s*/\\s*\\d+").containsMatchIn(text)) add("HP", text, line)
                "CandyPanel" -> {
                    val name = key.substringBefore("CANDY")
                    val species = if ("CANDY" in key && name.isNotEmpty()) catalogue.byName[name] else
                        if (key in setOf("CANDY", "CANDYXL")) ordered.lastOrNull { it.bottom <= line.top }
                            ?.let { catalogue.byName[textKey(it.text)] } else null
                    species?.let { add("CANDY", it.name, line) }
                }
                "TypeIcons" -> PokemonType.entries.filter { it.name in text.uppercase().split(Regex("[^A-Z]+")) }
                    .forEach { add("TYPE", it.name, line) }
                "SizeLabel" -> if (key in setOf("XXS", "XS", "XL", "XXL")) add("SIZE", key, line)
                "EvolutionRow" -> {
                    if (key.startsWith("EVOLVE")) add("CAN_EVOLVE", "true", line)
                    val target = key.removePrefix("EVOLVESINTO").removePrefix("EVOLVETO")
                    if (target != key) catalogue.byName[target]?.let { add("EVOLUTION", it.name, line) }
                }
                "ShadowStatus" -> when {
                    key.startsWith("PURIFY") || key.contains("SHADOWBONUS") -> add("STATUS", "shadow", line)
                    key.contains("PURIFIED") -> add("STATUS", "purified", line)
                }
                "MoveHeading" -> if (key in setOf("GYMSRAIDS", "GYMSANDRAIDS", "TRAINERBATTLES")) add("MOVE_HEADING", text, line)
                "MovesEnd" -> if (key.startsWith("NEWATTACK")) add("MOVES_END", "true", line)
                "FastMoveRow", "ChargedMoveRowA", "ChargedMoveRowB" -> {
                    val candidate = text.replace(Regex("(?i)\\s+shadow\\s+bonus.*$"), "")
                        .replace(Regex("\\s+[0-9]+(?:\\s*\\+\\s*[0-9]+)?\\s*$"), "")
                    val moveKey = textKey(candidate)
                    val keys = setOf(moveKey) + if (line.left < .22f && moveKey.firstOrNull() in setOf('O', '0')) setOf(moveKey.drop(1)) else emptySet()
                    val fast = id == "FastMoveRow"
                    (if (fast) catalogue.fastMoves else catalogue.chargedMoves).singleOrNull { textKey(it) in keys }
                        ?.let { add(if (fast) "FAST" else "CHARGED", it, line) }
                }
            }
        }
        val unique = fields.distinctBy { it.field to it.value }
        // Several move names in one box means alignment is ambiguous; never pick the first.
        val valid = if (id != "TypeIcons" && unique.groupBy { it.field }.any { it.value.size > 1 }) emptyList() else unique
        return DetailPageRead(valid.isNotEmpty(), valid)
    }
}
