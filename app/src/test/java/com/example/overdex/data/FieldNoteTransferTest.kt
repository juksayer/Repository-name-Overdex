package com.example.overdex.data

import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.SyllableCorrection
import java.util.UUID
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FieldNoteTransferTest {
    private val local = UUID(0xabcdef, 1).toString()
    private val originalTrainer = UUID(0, 2).toString()
    private val forwardingTrainer = UUID(0, 3).toString()

    @Test fun `forwarded notes and corrections retain original attribution revision text and times`() {
        val note = note().copy(author = "Original writer", revision = 7, received = true, shareable = true,
            lines = listOf("火 glows beside water", "A second line"), lesson = "A literal [a+b] lesson.")
        val correction = correction().copy(author = "Correction writer", entryId = note.id, entryRevision = 6,
            originalLine = "A previous line", replacementLine = "A suggested line")
        val transfer = packet(listOf(note), listOf(correction))

        val bytes = FieldNoteTransferCodec.encode(transfer)
        val restored = FieldNoteTransferCodec.decode(bytes)

        assertEquals(transfer.copy(notes = listOf(note.copy(received = false))), restored)
        assertEquals(forwardingTrainer, restored.senderTrainerId)
        assertEquals(originalTrainer, restored.notes.single().originTrainerId)
        assertEquals(originalTrainer, restored.corrections.single().originTrainerId)
        assertTrue("A correction may target a source note outside the package",
            FieldNoteTransferCodec.decode(FieldNoteTransferCodec.encode(packet(emptyList(), listOf(correction)))).notes.isEmpty())
    }

    @Test fun `local flags do not change canonical content but authorship identity revisions and timestamps do`() {
        val original = note()
        assertEquals(canonicalNoteHash(original), canonicalNoteHash(original.copy(received = true, shareable = true)))
        val changed = listOf(
            original.copy(id = "personal-${UUID(0, 9)}"), original.copy(originTrainerId = forwardingTrainer),
            original.copy(speciesId = 2), original.copy(speciesName = "Ivysaur"), original.copy(title = "Another title"),
            original.copy(lines = listOf("Different words")), original.copy(lesson = "Another lesson"),
            original.copy(author = "Another writer"), original.copy(revision = 2),
            original.copy(createdAt = 11), original.copy(updatedAt = 21),
        )
        changed.forEach { assertNotEquals("Changed content or identity must alter the hash: $it", canonicalNoteHash(original), canonicalNoteHash(it)) }
    }

    @Test fun `equivalent uppercase UUIDs remain duplicate without rewriting their source spelling`() {
        val existing = note().copy(
            id = "personal-abcdef12-3456-4abc-8def-123456abcdef",
            originTrainerId = "fedcba98-7654-4abc-8def-012345abcdef",
            received = true,
            revision = 3,
        )
        val incoming = existing.copy(
            id = "personal-${existing.id.removePrefix("personal-").uppercase()}",
            originTrainerId = existing.originTrainerId.uppercase(),
            received = false,
            shareable = true,
        )
        assertNotEquals(existing.id, incoming.id)
        assertNotEquals(existing.originTrainerId, incoming.originTrainerId)
        assertEquals(canonicalNoteHash(existing), canonicalNoteHash(incoming))
        assertNotEquals("Writing remains case-sensitive", canonicalNoteHash(existing),
            canonicalNoteHash(existing.copy(title = existing.title.uppercase())))

        val result = reviewFieldNoteImport(packet(listOf(incoming)), listOf(existing), local).single()

        assertEquals(FieldNoteImportDecision.DUPLICATE, result.decision)
        assertEquals("Hash normalization must not modify the received record", incoming, result.note)
        assertEquals("personal-abcdef12-3456-4abc-8def-123456abcdef", existing.id)
        assertEquals("fedcba98-7654-4abc-8def-012345abcdef", existing.originTrainerId)
    }

    @Test fun `review distinguishes new duplicate newer older and conflicting received copies without changing state`() {
        val duplicate = note(1).copy(received = true, shareable = false)
        val newer = note(2).copy(received = true, revision = 2)
        val older = note(3).copy(received = true, revision = 4)
        val conflict = note(4).copy(received = true)
        val existing = listOf(duplicate, newer, older, conflict)
        val inputs = listOf(note(5), duplicate.copy(received = false, shareable = true),
            newer.copy(revision = 3, updatedAt = 30), older.copy(revision = 3), conflict.copy(title = "Changed same revision"))
        val transfer = packet(inputs)

        val result = reviewFieldNoteImport(transfer, existing, local)

        assertEquals(listOf(FieldNoteImportDecision.NEW, FieldNoteImportDecision.DUPLICATE,
            FieldNoteImportDecision.NEWER, FieldNoteImportDecision.OLDER, FieldNoteImportDecision.CONFLICT), result.map { it.decision })
        assertEquals(inputs, result.map { it.note })
        assertEquals(listOf(duplicate, newer, older, conflict), existing)
        assertEquals(inputs, transfer.notes)
    }

    @Test fun `returning own duplicate is harmless but another package cannot replace or recreate own writing`() {
        val mine = note().copy(originTrainerId = local, received = false)
        assertEquals(FieldNoteImportDecision.DUPLICATE,
            reviewFieldNoteImport(packet(listOf(mine.copy(received = true, shareable = true))), listOf(mine), local).single().decision)
        val changed = mine.copy(revision = 2, title = "A remote replacement", received = true)
        assertEquals(FieldNoteImportDecision.OWNED_CONFLICT,
            reviewFieldNoteImport(packet(listOf(changed)), listOf(mine), local).single().decision)
        assertEquals(FieldNoteImportDecision.OWNED_CONFLICT,
            reviewFieldNoteImport(packet(listOf(changed)), emptyList(), local).single().decision)
        assertEquals(FieldNoteImportDecision.OWNED_CONFLICT,
            reviewFieldNoteImport(packet(listOf(note().copy(originTrainerId = forwardingTrainer))), listOf(mine), local).single().decision)
        val upperCaseOwnOrigin = local.uppercase()
        assertNotEquals(local, upperCaseOwnOrigin)
        assertEquals(FieldNoteImportDecision.OWNED_CONFLICT,
            reviewFieldNoteImport(packet(listOf(changed.copy(originTrainerId = upperCaseOwnOrigin))), emptyList(), local).single().decision)
    }

    @Test fun `same remote ID with changed origin creation time or same revision timestamp is a conflict`() {
        val previous = note().copy(received = true)
        listOf(previous.copy(originTrainerId = forwardingTrainer, revision = 2),
            previous.copy(createdAt = 11, revision = 2), previous.copy(updatedAt = 21)).forEach { incoming ->
            assertEquals(FieldNoteImportDecision.CONFLICT,
                reviewFieldNoteImport(packet(listOf(incoming)), listOf(previous), local).single().decision)
        }
    }

    @Test fun `file byte limit accepts the boundary and rejects oversized encoded or incoming data`() {
        val transfer = packet()
        val encoded = FieldNoteTransferCodec.encode(transfer)
        val bounded = encoded + ByteArray(FieldNoteTransferCodec.MAX_PACKAGE_BYTES - encoded.size) { ' '.code.toByte() }
        assertEquals(transfer, FieldNoteTransferCodec.decode(bounded))
        rejected { FieldNoteTransferCodec.decode(bounded + byteArrayOf(' '.code.toByte())) }
        rejected { FieldNoteTransferCodec.decode(byteArrayOf()) }
        val large = packet(listOf(note().copy(lines = List(100) { "火".repeat(2_000) })))
        rejected { FieldNoteTransferCodec.encode(large) }
    }

    @Test fun `strict UTF8 JSON types and envelope prevent replacement decoding drafts and unknown versions`() {
        val bytes = FieldNoteTransferCodec.encode(packet())
        val title = bytes.decodeToString().indexOf("A personal note")
        val corrupted = bytes.copyOf().apply { this[title] = 0xc3.toByte(); this[title + 1] = 0x28 }
        rejected { FieldNoteTransferCodec.decode(corrupted) }
        rejected { FieldNoteTransferCodec.decode(changeRoot(bytes, "version", JsonPrimitive(2))) }
        rejected { FieldNoteTransferCodec.decode(changeRoot(bytes, "version", JsonPrimitive("1"))) }
        rejected { FieldNoteTransferCodec.decode(changeRoot(bytes, "format", JsonPrimitive("other-format"))) }
        rejected { FieldNoteTransferCodec.decode(changeRoot(bytes, "packageId", JsonPrimitive("not-a-uuid"))) }
        rejected { FieldNoteTransferCodec.decode(changeRoot(bytes, "senderTrainerId", JsonPrimitive(""))) }
        rejected { FieldNoteTransferCodec.decode(changeRoot(bytes, "draft", buildJsonObject { put("title", "Unfinished writing") })) }
        listOf("received", "shareable").forEach { flag ->
            rejected { FieldNoteTransferCodec.decode(changeNote(bytes, flag, JsonPrimitive("true"))) }
            rejected { FieldNoteTransferCodec.decode(changeNote(bytes, flag, JsonPrimitive(1))) }
        }
        rejected { FieldNoteTransferCodec.decode(changeNote(bytes, "revision", JsonPrimitive("2"))) }
        rejected { FieldNoteTransferCodec.decode(changeNote(bytes, "speciesId", JsonPrimitive(1.5))) }
        rejected { FieldNoteTransferCodec.decode(("[".repeat(17) + "0" + "]".repeat(17)).encodeToByteArray()) }
        val missingIdentity = Json.parseToJsonElement(bytes.decodeToString()).jsonObject - "packageId"
        rejected { FieldNoteTransferCodec.decode(JsonObject(missingIdentity).toString().encodeToByteArray()) }
    }

    @Test fun `record counts duplicate UUIDs and malformed note fields reject before review`() {
        rejected { FieldNoteTransferCodec.encode(packet((1..101).map(::note))) }
        rejected { FieldNoteTransferCodec.encode(packet(corrections = (1..501).map(::correction))) }
        rejected { FieldNoteTransferCodec.encode(packet(listOf(note(), note()))) }
        val sameUuid = note(10)
        val upperCaseUuid = sameUuid.copy(id = "personal-${sameUuid.id.removePrefix("personal-").uppercase()}")
        assertNotEquals(sameUuid.id, upperCaseUuid.id)
        rejected { FieldNoteTransferCodec.encode(packet(listOf(sameUuid, upperCaseUuid))) }
        rejected { FieldNoteTransferCodec.encode(packet(corrections = listOf(correction(), correction()))) }
        val original = note()
        val invalid = listOf(original.copy(id = "001-001"), original.copy(id = "personal-1-1-1-1-1"),
            original.copy(originTrainerId = ""), original.copy(speciesId = 0), original.copy(speciesName = " "),
            original.copy(speciesName = "x".repeat(201)), original.copy(title = " "), original.copy(title = "x".repeat(201)),
            original.copy(lines = emptyList()), original.copy(lines = listOf("", " ")), original.copy(lines = List(101) { "line" }),
            original.copy(lines = listOf("x".repeat(2001))), original.copy(lesson = "x".repeat(4001)),
            original.copy(author = "x".repeat(201)), original.copy(author = " "), original.copy(revision = 0),
            original.copy(createdAt = -1), original.copy(updatedAt = 9), original.copy(lines = listOf("\uD800")))
        invalid.forEach { rejected { FieldNoteTransferCodec.encode(packet(listOf(it))) } }
        rejected { reviewFieldNoteImport(packet(), listOf(note(), note()), local) }
        rejected { reviewFieldNoteImport(packet(), emptyList(), "invalid-local-trainer") }
    }

    @Test fun `corrections require bounded source revision line target UUID attribution and a proposal`() {
        val original = correction()
        val invalid = listOf(original.copy(id = ""), original.copy(entryId = "001"), original.copy(entryId = "000-001"),
            original.copy(entryId = "001-000"), original.copy(entryId = "personal-not-a-uuid"),
            original.copy(entryRevision = 0), original.copy(lineIndex = -1), original.copy(lineIndex = 100),
            original.copy(proposedCount = -1), original.copy(proposedCount = 100),
            original.copy(proposedCount = null, pronunciation = "", replacementLine = ""),
            original.copy(createdAt = -1), original.copy(pronunciation = "x".repeat(2001)),
            original.copy(replacementLine = "x".repeat(2001)), original.copy(originalLine = "x".repeat(2001)),
            original.copy(author = "x".repeat(201)), original.copy(author = " "), original.copy(originTrainerId = ""))
        invalid.forEach { rejected { FieldNoteTransferCodec.encode(packet(corrections = listOf(it))) } }
        val lastLine = original.copy(lineIndex = 99, proposedCount = null, pronunciation = "A pronunciation")
        assertEquals(lastLine, FieldNoteTransferCodec.decode(FieldNoteTransferCodec.encode(packet(corrections = listOf(lastLine)))).corrections.single())
    }

    private fun note(index: Int = 1) = PersonalFieldNote(
        id = "personal-${UUID(0, 100L + index)}", speciesId = 1, speciesName = "Bulbasaur", title = "A personal note",
        lines = listOf("A seed waits in rain", "Two lines are intentional"), lesson = "Keep learning.", author = "Unknown Author",
        createdAt = 10, updatedAt = 20, originTrainerId = originalTrainer,
    )

    private fun correction(index: Int = 1) = SyllableCorrection(
        id = UUID(0, 1_000L + index).toString(), entryId = "001-001", entryRevision = 1, lineIndex = 0,
        proposedCount = 5, pronunciation = "", replacementLine = "", originalLine = "A seed waits in rain",
        createdAt = 30, author = "Unknown Author", originTrainerId = originalTrainer,
    )

    private fun packet(notes: List<PersonalFieldNote> = listOf(note()), corrections: List<SyllableCorrection> = emptyList()) =
        FieldNoteTransferPackage(packageId = UUID(0, 20).toString(), senderTrainerId = forwardingTrainer,
            createdAt = 40, notes = notes, corrections = corrections)

    private fun changeRoot(bytes: ByteArray, name: String, value: JsonElement): ByteArray =
        JsonObject(Json.parseToJsonElement(bytes.decodeToString()).jsonObject + (name to value)).toString().encodeToByteArray()

    private fun changeNote(bytes: ByteArray, name: String, value: JsonElement): ByteArray {
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val notes = root.getValue("notes").jsonArray
        val changed = JsonObject(notes.first().jsonObject + (name to value))
        return JsonObject(root + ("notes" to JsonArray(listOf(changed) + notes.drop(1)))).toString().encodeToByteArray()
    }

    private fun rejected(block: () -> Unit) { assertTrue("Malformed or excessive transfer data must be rejected", runCatching(block).isFailure) }
}
