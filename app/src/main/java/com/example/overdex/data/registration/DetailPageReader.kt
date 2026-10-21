package com.example.overdex.data.registration

import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import java.util.Locale
import kotlinx.serialization.Serializable

/** OCR coordinates belong to the captured GO frame, never the CRT preview. */
@Serializable
data class DetailTextLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

@Serializable
data class DetailFieldRead(val field: String, val value: String, val line: DetailTextLine)

data class DetailPageRead(val isDetailPage: Boolean, val fields: List<DetailFieldRead>)

internal fun textKey(text: String): String = text.uppercase(Locale.ROOT)
    .replace("♀", "F").replace("♂", "M").filter(Char::isLetterOrDigit)

/** Reference identities and moves, including the undirected candy/evolution family. */
class RegistrationCatalogue(val species: List<Pokemon>) {
    val byName = species.associateBy { textKey(it.name) }
    val fastMoves = species.flatMap { it.fastMoves }.map { it.name }.distinctBy(::textKey)
    val chargedMoves = species.flatMap { it.chargedMoves }.map { it.name }.distinctBy(::textKey)
    private val links = mutableMapOf<String, MutableSet<String>>()

    init {
        species.forEach { pokemon ->
            val name = textKey(pokemon.name)
            links.getOrPut(name) { mutableSetOf() }.add(name)
            (pokemon.prevEvolutions + pokemon.nextEvolutions).forEach { evolution ->
                val relative = textKey(evolution.name)
                links.getOrPut(name) { mutableSetOf() }.add(relative)
                links.getOrPut(relative) { mutableSetOf() }.add(name)
            }
        }
    }

    fun family(name: String): Set<String> {
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(textKey(name))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (visited.add(current)) links[current].orEmpty().filterNot(visited::contains).forEach(pending::addLast)
        }
        return visited
    }
}

/** Finds semantic fields in the current viewport. No fixed Y positions survive a scroll. */
class DetailPageReader(private val catalogue: RegistrationCatalogue) {
    fun read(input: List<DetailTextLine>): DetailPageRead {
        val lines = input.filter { it.text.isNotBlank() }.sortedBy { it.top }
        val keys = lines.map { textKey(it.text) }
        val hp = lines.firstOrNull { HP.containsMatchIn(it.text) }
        val candy = lines.filter { textKey(it.text).contains("CANDY") }
        val detailAnchor = hp != null && keys.any { it in setOf("WEIGHT", "HEIGHT") } ||
            candy.isNotEmpty() && keys.any { it.startsWith("POWERUP") || it.startsWith("EVOLVE") || it.startsWith("PURIFY") } ||
            keys.any { it in setOf("GYMSRAIDS", "GYMSANDRAIDS", "TRAINERBATTLES") || it.startsWith("NEWATTACK") }
        if (!detailAnchor) return DetailPageRead(false, emptyList())

        val fields = mutableListOf<DetailFieldRead>()
        fun add(field: String, value: String, line: DetailTextLine) { fields += DetailFieldRead(field, value, line) }

        // CP above the name/HP is the specimen's CP. Power-up previews and costs are not CP.
        if (hp != null) {
            lines.firstOrNull { it.bottom < hp.top && CP.matches(it.text.trim()) }?.let { line ->
                val digits = CP.matchEntire(line.text.trim())?.groupValues?.get(1)
                    ?.uppercase(Locale.ROOT)?.replace('O', '0')?.replace('I', '1')?.replace('L', '1')
                digits?.toIntOrNull()?.takeIf { it in 10..99999 }?.let { add("CP", it.toString(), line) }
            }
            lines.filter {
                it.bottom <= hp.top && hp.top - it.bottom < 0.15f &&
                    (it.left + it.right) / 2 in 0.2f..0.8f &&
                    it.text.any(Char::isLetter) && !CP.matches(it.text.trim()) &&
                    textKey(it.text) !in HEADER_NOISE
            }.maxByOrNull { it.bottom }?.let { add("TITLE", it.text.trim().removeSuffix("♂").removeSuffix("♀").trim(), it) }
        }

        candy.forEach { line ->
            val key = textKey(line.text).substringBefore("CANDY")
            val family = catalogue.byName[key] ?: if (key.isEmpty()) {
                lines.filter { it.bottom <= line.top && line.top - it.bottom < 0.035f &&
                    kotlin.math.abs((it.left + it.right) - (line.left + line.right)) < 0.15f }
                    .maxByOrNull { it.bottom }?.let { catalogue.byName[textKey(it.text)] }
            } else null
            family?.let { add("CANDY", it.name, line) }
        }
        lines.forEach { line ->
            val key = textKey(line.text)
            if (key.startsWith("EVOLVE")) add("CAN_EVOLVE", "true", line)
            // Only a labelled target, never the candy name or a random species elsewhere.
            val target = key.removePrefix("EVOLVESINTO").removePrefix("EVOLVETO")
            if (target != key) catalogue.byName[target]?.let { add("EVOLUTION", it.name, line) }
            if (key.startsWith("PURIFY") || key.contains("SHADOWBONUS")) add("SHADOW", "true", line)
            if (key.startsWith("NEWATTACK")) add("MOVES_END", "true", line)
        }

        // Type labels in the weight/type/height row are identity evidence; move types are not.
        val measurements = lines.filter { textKey(it.text) in setOf("WEIGHT", "HEIGHT") }
        if (measurements.size >= 2) {
            val top = measurements.minOf { it.top } - 0.09f
            val bottom = measurements.maxOf { it.bottom } + 0.01f
            // The height label's size tag is separate from Candy XL lower down.
            val heightLabel = measurements.firstOrNull { textKey(it.text) == "HEIGHT" }
            heightLabel?.let { height ->
                lines.filter { it.top >= height.top - 0.09f && it.bottom <= height.bottom &&
                    it.left >= height.left - 0.1f && textKey(it.text) in setOf("XXS", "XS", "XL", "XXL") }
                    .forEach { add("SIZE", textKey(it.text), it) }
            }
            lines.filter { it.top >= top && it.bottom <= bottom && it.left > 0.22f && it.right < 0.78f }
                .forEach { line ->
                    val typeWords = line.text.uppercase(Locale.ROOT).split(Regex("[^A-Z]+"))
                    PokemonType.entries.filter { it.name in typeWords }.forEach { add("TYPE", it.name, line) }
                }
        }

        val moveHeading = lines.filter { textKey(it.text) in setOf("GYMSRAIDS", "GYMSANDRAIDS", "TRAINERBATTLES") }
            .minOfOrNull { it.top }
        lines.filter { line ->
            (hp == null || line.top > hp.bottom) && (moveHeading == null || line.top > moveHeading)
        }.forEach { line ->
            // The trailing damage number may share a line with a move. Match whole names;
            // never search arbitrary substrings (e.g. Return inside a button caption).
            val candidate = line.text.replace(Regex("(?i)\\s+shadow\\s+bonus.*$"), "")
                .replace(Regex("\\s+[0-9]+(?:\\s*\\+\\s*[0-9]+)?\\s*$"), "").trim()
            val key = textKey(candidate)
            // The round type badge can become an attached O/0 in ML Kit's line
            // (observed as "OIncinerate"). Only remove that one leading glyph in
            // the left icon column, and only when the remainder is an exact move.
            val keysToMatch = setOf(key) + if (line.left < 0.22f && key.firstOrNull() in setOf('O', '0')) setOf(key.drop(1)) else emptySet()
            catalogue.fastMoves.singleOrNull { textKey(it) in keysToMatch }?.let { add("FAST", it, line) }
            catalogue.chargedMoves.singleOrNull { textKey(it) in keysToMatch }?.let { add("CHARGED", it, line) }
        }
        return DetailPageRead(true, fields.distinctBy { it.field to it.value })
    }

    companion object {
        private val CP = Regex("(?i)^CP\\s*([0-9OIl]{2,5})$")
        private val HP = Regex("(?i)\\d+\\s*/\\s*\\d+\\s*HP|HP\\s*\\d+\\s*/\\s*\\d+")
        private val HEADER_NOISE = setOf("HP", "WEIGHT", "HEIGHT", "FAVORITE", "TAG", "TAGS")
    }
}
