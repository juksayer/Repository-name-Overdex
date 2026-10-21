package com.example.overdex.data.registration

import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class RegistrationReadEvidence(
    val read: DetailFieldRead,
    val capturedAt: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sourceId: String? = null,
    val confirmedByUser: Boolean = false,
    val regionId: String? = null,
    val superseded: Boolean = false,
)

@Serializable
data class RegistrationDraft(
    val cardId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val evidence: List<RegistrationReadEvidence> = emptyList(),
    val confirmedSpeciesId: Int? = null,
    val saved: Boolean = false,
    val boundaryConflict: Boolean = false,
    val cries: List<RegistrationCryEvidence> = emptyList(),
) {
    /** Two independent frames, or an explicit review of screenshot readings, confirm a field. */
    fun values(field: String): List<String> = evidence.filter { it.read.field == field && !it.superseded }
        .groupBy { it.read.value }.filterValues { rows -> rows.any { it.confirmedByUser } ||
            rows.map { it.sourceId ?: it.capturedAt.toString() }.distinct().size >= 2 }.keys.toList()

    /** A changed header may be a correction or another specimen; ask before replacing it. */
    fun identityChanges(read: DetailPageRead): List<DetailFieldRead> = read.fields.filter { next ->
        next.field in setOf("CP", "TITLE", "CANDY") && values(next.field).let { previous ->
            previous.isNotEmpty() && previous.none { textKey(it) == textKey(next.value) }
        }
    }

    fun accept(read: DetailPageRead, at: Long, width: Int, height: Int, sourceId: String? = null, confirmedByUser: Boolean = false): RegistrationDraft {
        if (saved || boundaryConflict || !read.isDetailPage) return this
        // Stable CP/title changes indicate another specimen. Never combine its moves
        // with the previous card. The user explicitly begins the next scan.
        val changedHeader = read.fields.filter { it.field in setOf("CP", "TITLE") }
            .filter { values(it.field).isNotEmpty() && it.value !in values(it.field) }
        val accepted = if (changedHeader.isNotEmpty()) changedHeader else read.fields
        val additions = accepted.filter { field ->
            evidence.none { (if (sourceId == null) it.capturedAt == at else it.sourceId == sourceId) &&
                it.read.field == field.field && it.read.value == field.value && (!confirmedByUser || it.confirmedByUser) } &&
                (evidence.count { it.read.field == field.field && it.read.value == field.value } < 2 || confirmedByUser)
        }.map { RegistrationReadEvidence(it, at, width, height, sourceId, confirmedByUser) }
        val next = copy(evidence = (evidence + additions).takeLast(120))
        return next.copy(boundaryConflict = next.values("CP").size > 1 || next.values("TITLE").size > 1)
    }

    /** An explicit field correction replaces that box's value while retaining its reading history. */
    fun acceptField(regionId: String, read: DetailPageRead, at: Long, width: Int, height: Int, sourceId: String): RegistrationDraft {
        if (saved || boundaryConflict || !read.isDetailPage) return this
        val fieldNames = read.fields.map { it.field }.toSet()
        val previous = evidence.map { old ->
            if (old.regionId == regionId || (old.regionId == null && old.read.field in fieldNames && old.read.field != "CHARGED"))
                old.copy(superseded = true) else old
        }
        val additions = read.fields.map { RegistrationReadEvidence(it, at, width, height, sourceId, true, regionId) }
        val all = previous + additions
        val current = all.filterNot { it.superseded }
        // Repeated corrections must never evict another field's still-current value.
        val history = all.filter { it.superseded }.takeLast((120 - current.size).coerceAtLeast(0))
        return copy(evidence = history + current)
    }

    fun resolve(catalogue: RegistrationCatalogue): RegistrationResolution {
        val title = values("TITLE").singleOrNull()
        val candies = values("CANDY")
        val types = values("TYPE").toSet()
        val fast = values("FAST")
        val charged = values("CHARGED")
        val family = candies.map(catalogue::family).reduceOrNull { a, b -> a.intersect(b) }
        var candidates = catalogue.species.filter { family == null || textKey(it.name) in family }
        if (types.isNotEmpty()) candidates = candidates.filter { pokemon -> pokemon.types.map { it.name }.toSet() == types }
        if (values("CAN_EVOLVE").isNotEmpty()) candidates = candidates.filter { it.nextEvolutions.isNotEmpty() }
        val targets = values("EVOLUTION").map(::textKey)
        if (targets.isNotEmpty()) candidates = candidates.filter { p -> targets.all { t -> p.nextEvolutions.any { textKey(it.name) == t } } }
        if (fast.isNotEmpty()) candidates = candidates.filter { p -> fast.all { move -> p.fastMoves.any { textKey(it.name) == textKey(move) } } }
        if (charged.isNotEmpty()) candidates = candidates.filter { p -> charged.all { move -> p.chargedMoves.any { textKey(it.name) == textKey(move) } } }
        val named = title?.let { catalogue.byName[textKey(it)] }
        val manual = confirmedSpeciesId?.let { id -> catalogue.species.find { it.id == id } }
        val identified = manual ?: named?.takeIf { it in candidates } ?: candidates.singleOrNull()?.takeIf { family != null }
        val conflicts = when {
            boundaryConflict -> "The name or CP changed. Start a new scan for another Pokémon."
            manual == null && named != null && family != null && textKey(named.name) !in family ->
                "The name and candy belong to different Pokémon families. Re-read the name, start the next Pokémon, or choose the species if this is a nickname."
            fast.size > 1 || charged.size > 2 -> "Conflicting move readings. Restart this scan."
            identified != null && (fast.any { m -> identified.fastMoves.none { textKey(it.name) == textKey(m) } } ||
                charged.any { m -> identified.chargedMoves.none { textKey(it.name) == textKey(m) } }) -> "These moves do not match the selected species."
            family != null && candidates.isEmpty() && manual == null -> "The candy, type or moves disagree. Choose the species."
            else -> null
        }
        val missing = buildList {
            if (identified == null) add("species")
            if (values("CP").singleOrNull() == null) add("CP at the top")
            if (candies.isEmpty() && manual == null) add("candy family")
            if (fast.isEmpty()) add("fast move")
            if (charged.isEmpty()) add("charged move")
            if (charged.size < 2 && values("MOVES_END").isEmpty()) add("bottom of the move list")
        }
        return RegistrationResolution(identified, candidates.takeIf { family != null }.orEmpty(), title,
            values("CP").singleOrNull()?.toIntOrNull(), fast.singleOrNull(), charged, candies, missing, conflicts)
    }

    fun card(resolution: RegistrationResolution): OwnedPokemon? {
        if (!resolution.ready) return null
        return OwnedPokemon(
            id = cardId, speciesId = requireNotNull(resolution.species).id,
            displayName = resolution.title?.takeUnless { textKey(it) == textKey(resolution.species.name) },
            cp = resolution.cp, fastMove = resolution.fastMove,
            chargedMove1 = resolution.chargedMoves.getOrNull(0), chargedMove2 = resolution.chargedMoves.getOrNull(1),
            isShadow = values("STATUS").singleOrNull()?.let { it == "shadow" } ?: values("SHADOW").isNotEmpty(),
            isPurified = values("STATUS").singleOrNull() == "purified",
            isShiny = values("SHINY").singleOrNull() == "true",
            isFavorite = values("FAVORITE").singleOrNull() == "true", createdAt = createdAt, updatedAt = System.currentTimeMillis(),
            size = values("SIZE").singleOrNull()?.let { value -> com.example.overdex.model.PokemonSize.entries.find { it.name == value } }
                ?: com.example.overdex.model.PokemonSize.UNKNOWN,
        )
    }
}

data class RegistrationResolution(
    val species: Pokemon?, val candidates: List<Pokemon>, val title: String?, val cp: Int?,
    val fastMove: String?, val chargedMoves: List<String>, val candyFamilies: List<String>,
    val missing: List<String>, val conflict: String?,
) { val ready: Boolean get() = missing.isEmpty() && conflict == null }
