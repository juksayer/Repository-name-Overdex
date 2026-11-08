package com.example.overdex.data

import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.SyllableCorrection
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** A deliberate, portable snapshot. Drafts and local journal settings are not transfer records. */
@Serializable
data class FieldNoteTransferPackage(
    val format: String = "overdex.field-notes",
    val version: Int = 1,
    val packageId: String,
    val senderTrainerId: String,
    val createdAt: Long,
    val notes: List<PersonalFieldNote>,
    val corrections: List<SyllableCorrection> = emptyList(),
)

enum class FieldNoteImportDecision { NEW, DUPLICATE, NEWER, OLDER, CONFLICT, OWNED_CONFLICT }

data class FieldNoteImportCandidate(val note: PersonalFieldNote, val decision: FieldNoteImportDecision)

private val fieldNoteTransferJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

/** File and future nearby transports share these limits and exactly the same validation. */
object FieldNoteTransferCodec {
    const val MAX_PACKAGE_BYTES = 256 * 1024
    const val MAX_NOTES = 100
    const val MAX_CORRECTIONS = 500

    fun encode(value: FieldNoteTransferPackage): ByteArray {
        validate(value)
        // This flag describes storage on one device, never authorship or trust on another.
        val portable = value.copy(notes = value.notes.map { it.copy(received = false) })
        val bytes = fieldNoteTransferJson.encodeToString(portable).encodeToByteArray(throwOnInvalidSequence = true)
        require(bytes.size <= MAX_PACKAGE_BYTES) { "The field-note package exceeds 256 KiB." }
        return bytes
    }

    fun decode(bytes: ByteArray): FieldNoteTransferPackage {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PACKAGE_BYTES) { "Choose a field-note package of at most 256 KiB." }
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        requireBoundedNesting(text)
        val root = fieldNoteTransferJson.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("The field-note package must be a JSON object.")
        require(root.keys.containsAll(listOf("format", "version", "packageId", "senderTrainerId", "createdAt", "notes"))) {
            "The field-note package is missing its format, identity, or contents."
        }
        validateWireTypes(root)
        return fieldNoteTransferJson.decodeFromJsonElement<FieldNoteTransferPackage>(root).also(::validate)
    }

    internal fun validate(value: FieldNoteTransferPackage) {
        require(value.format == "overdex.field-notes" && value.version == 1) { "This field-note package format or version is not supported." }
        requireUuid(value.packageId, "package")
        requireUuid(value.senderTrainerId, "sending trainer")
        require(value.createdAt >= 0) { "The package has an invalid creation time." }
        require(value.notes.size <= MAX_NOTES && value.corrections.size <= MAX_CORRECTIONS) {
            "A package can contain at most 100 notes and 500 corrections."
        }
        require(value.notes.map { noteKey(it.id) }.distinct().size == value.notes.size) { "The package contains duplicate note IDs." }
        require(value.corrections.map { it.id.lowercase(Locale.ROOT) }.distinct().size == value.corrections.size) {
            "The package contains duplicate correction IDs."
        }
        value.notes.forEach(::validateTransferNote)
        value.corrections.forEach { correction ->
            requireUuid(correction.id, "correction")
            requireEntryId(correction.entryId)
            requireUuid(correction.originTrainerId, "correction's original trainer")
            require(correction.entryRevision >= 1 && correction.lineIndex in 0..99 && correction.createdAt >= 0) {
                "A correction needs a valid source revision, line, and creation time."
            }
            require(correction.proposedCount == null || correction.proposedCount in 0..99) { "A syllable count must be between 0 and 99." }
            require(correction.proposedCount != null || correction.pronunciation.isNotBlank() || correction.replacementLine.isNotBlank()) {
                "A correction needs a count, pronunciation, or proposed wording."
            }
            requireText(correction.pronunciation, 2_000, "pronunciation")
            requireText(correction.replacementLine, 2_000, "replacement line")
            requireText(correction.originalLine, 2_000, "original line")
            requireText(correction.author, 200, "correction author", nonblank = true)
        }
    }

    /** JSON's primitive coercions must not turn quoted numbers or flags into trusted fields. */
    private fun validateWireTypes(root: JsonObject) {
        fun fields(obj: JsonObject, strings: List<String>, integers: List<String>, booleans: List<String> = emptyList()) {
            strings.forEach { key -> obj[key]?.let { require(it is JsonPrimitive && it.isString) { "$key must be text." } } }
            integers.forEach { key -> obj[key]?.let { require(it is JsonPrimitive && !it.isString && it.longOrNull != null) { "$key must be an integer." } } }
            booleans.forEach { key -> obj[key]?.let { require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) { "$key must be a boolean." } } }
        }
        fields(root, listOf("format", "packageId", "senderTrainerId"), listOf("version", "createdAt"))
        fun records(key: String): List<JsonObject> {
            val value = root[key] ?: return emptyList()
            require(value is JsonArray) { "$key must be an array." }
            val maximum = if (key == "notes") MAX_NOTES else MAX_CORRECTIONS
            require(value.size <= maximum) { "The package contains too many $key." }
            return value.map { it as? JsonObject ?: throw IllegalArgumentException("Each $key record must be an object.") }
        }
        records("notes").forEach { note ->
            fields(note, listOf("id", "speciesName", "title", "lesson", "author", "originTrainerId"),
                listOf("speciesId", "revision", "createdAt", "updatedAt"), listOf("shareable", "received"))
            note["lines"]?.let { lines ->
                require(lines is JsonArray && lines.size <= 100 && lines.all { it is JsonPrimitive && it.isString }) {
                    "Poem lines must be an array of at most 100 strings."
                }
            }
        }
        records("corrections").forEach { correction ->
            fields(correction, listOf("id", "entryId", "pronunciation", "replacementLine", "originalLine", "author", "originTrainerId"),
                listOf("entryRevision", "lineIndex", "createdAt"))
            correction["proposedCount"]?.takeUnless { it == JsonNull }?.let {
                require(it is JsonPrimitive && !it.isString && it.intOrNull != null) { "proposedCount must be an integer or null." }
            }
        }
    }
}

/** Compare content and UUID identity, independent of UUID spelling and device-local flags. */
fun canonicalNoteHash(note: PersonalFieldNote): String {
    validateTransferNote(note)
    val canonical = note.copy(
        id = noteKey(note.id),
        originTrainerId = note.originTrainerId.lowercase(Locale.ROOT),
        received = false,
        shareable = false,
    )
    val text = fieldNoteTransferJson.encodeToString(canonical)
    return MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Review only. Even NEWER is a proposed choice, never authorization to replace saved writing. */
fun reviewFieldNoteImport(
    value: FieldNoteTransferPackage,
    existing: List<PersonalFieldNote>,
    localTrainerId: String,
): List<FieldNoteImportCandidate> {
    FieldNoteTransferCodec.validate(value)
    requireUuid(localTrainerId, "local trainer")
    val byId = existing.associateBy { noteKey(it.id) }
    require(byId.size == existing.size) { "Saved note IDs must be unique before reviewing an import." }
    return value.notes.map { incoming ->
        val previous = byId[noteKey(incoming.id)]
        val decision = when {
            previous != null && previous.revision == incoming.revision &&
                canonicalNoteHash(previous) == canonicalNoteHash(incoming) -> FieldNoteImportDecision.DUPLICATE
            incoming.originTrainerId.equals(localTrainerId, ignoreCase = true) || previous?.received == false ->
                FieldNoteImportDecision.OWNED_CONFLICT
            previous == null -> FieldNoteImportDecision.NEW
            !previous.originTrainerId.equals(incoming.originTrainerId, ignoreCase = true) || previous.createdAt != incoming.createdAt ->
                FieldNoteImportDecision.CONFLICT
            incoming.revision > previous.revision -> FieldNoteImportDecision.NEWER
            incoming.revision < previous.revision -> FieldNoteImportDecision.OLDER
            else -> FieldNoteImportDecision.CONFLICT
        }
        FieldNoteImportCandidate(incoming, decision)
    }
}

private fun validateTransferNote(note: PersonalFieldNote) {
    require(note.id.startsWith("personal-")) { "A transferred note needs its original personal ID." }
    requireUuid(note.id.removePrefix("personal-"), "note")
    requireUuid(note.originTrainerId, "note's original trainer")
    require(note.speciesId > 0) { "Choose a Pokémon species." }
    requireText(note.speciesName, 200, "species name", nonblank = true)
    requireText(note.title, 200, "title", nonblank = true)
    require(note.lines.size in 1..100 && note.lines.any { it.isNotBlank() }) { "Use one to 100 poem lines with at least one nonblank line." }
    note.lines.forEach { requireText(it, 2_000, "poem line") }
    requireText(note.lesson, 4_000, "lesson")
    requireText(note.author, 200, "original author", nonblank = true)
    require(note.revision >= 1 && note.createdAt >= 0 && note.updatedAt >= note.createdAt) { "The note has invalid revision or timestamps." }
}

private fun requireUuid(value: String, label: String) {
    require(value.length == 36 && runCatching { UUID.fromString(value).toString().equals(value, ignoreCase = true) }.getOrDefault(false)) {
        "Keep the $label's original UUID."
    }
}

private fun requireEntryId(value: String) {
    if (value.startsWith("personal-")) requireUuid(value.removePrefix("personal-"), "target note")
    else require(Regex("[0-9]{3,5}-[0-9]{3}").matches(value) && value.substringBefore('-').toInt() > 0 && value.substringAfter('-').toInt() > 0) {
        "A correction needs the original stable entry ID."
    }
}

private fun noteKey(id: String) = id.lowercase(Locale.ROOT)

private fun requireText(value: String, limit: Int, label: String, nonblank: Boolean = false) {
    require(value.length <= limit && (!nonblank || value.isNotBlank())) { "The $label is empty or too long." }
    value.encodeToByteArray(throwOnInvalidSequence = true)
}

/** The v1 schema is shallow; reject unreasonable nesting before invoking a recursive JSON parser. */
private fun requireBoundedNesting(text: String) {
    var quoted = false
    var escaped = false
    var depth = 0
    text.forEach { character ->
        if (quoted) {
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') quoted = false
        } else when (character) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 16) { "The field-note package is nested too deeply." } }
            '}', ']' -> depth--
        }
    }
}
