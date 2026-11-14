package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.WavPcm16
import com.example.overdex.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Reads original ZIP entries in place. No extracted or synthetic media is persisted. */
object ArchivedEndingAudioRecognizer {
    suspend fun recognize(archive: MatchArchive, moves: List<Move>, speciesIds: Set<Int>,
                          openArchive: () -> InputStream?, readAsset: (String) -> ByteArray): ReplayAudioRecovery = withContext(Dispatchers.Default) {
        if (ReplayRetainedAudioRecovery.selectedArticles(archive).isEmpty()) return@withContext ReplayAudioRecovery()
        if (!archive.mediaIncluded) return@withContext ReplayAudioRecovery(unavailableArtifacts = listOf("Archive has no audio media"))
        val missing = mutableListOf<String>()
        fun key(s: String) = s.uppercase().filter(Char::isLetterOrDigit)
        val wanted = moves.map { key(it.name) }.toMutableSet()
        // A configured or recorded move is still a candidate even when reference legality disagrees.
        archive.articles.forEach { a -> when (val p = a.payload) {
            is ArchivedPlayerTeamSlotConfigured -> wanted.addAll((p.chargedMoveNames + p.fastMoveName).map(::key))
            is ArchivedFastMoveIdentified -> wanted.add(key(p.moveName))
            is ArchivedChargedMoveEnergySpent -> wanted.add(key(p.moveName))
            else -> Unit
        } }
        val references = mutableListOf<ReplayAudioReference>()
        fun entries(path: String) = try { Json.parseToJsonElement(readAsset(path).decodeToString()).jsonObject.getValue("references").jsonArray }
            catch (e: java.io.IOException) { missing += path; JsonArray(emptyList()) }
        fun reference(name: String, charged: Boolean, species: Int?, path: String, sha: String) {
            val bytes = try { readAsset(path) } catch (e: java.io.IOException) { null }
            val decoded = bytes?.takeIf { ReplayRetainedAudioRecovery.sha(it) == sha }?.let(WavPcm16::decodeBundledReference)
            if (decoded == null) missing += name else references += ReplayAudioReference(name, charged, species, sha, decoded)
        }
        for (charged in listOf(false, true)) {
            val dir = if (charged) "charged_move_sounds" else "fast_move_sounds"
            for (entry in entries("$dir/manifest.json")) {
                val r = entry.jsonObject; val name = r.getValue("moveName").jsonPrimitive.content
                if (key(name) in wanted) reference(name, charged, null, "$dir/${r.getValue("file").jsonPrimitive.content}", r.getValue("sha256").jsonPrimitive.content)
            }
        }
        missing += wanted.filter { name -> references.none { key(it.name) == name } }
        for (entry in entries("cry_references/manifest.json")) {
            val r = entry.jsonObject; val id = r["speciesId"]?.jsonPrimitive?.intOrNull ?: continue
            if (id in speciesIds) reference("Cry $id", false, id, r.getValue("assetPath").jsonPrimitive.content, r.getValue("sha256").jsonPrimitive.content)
        }
        missing += speciesIds.filter { id -> references.none { it.speciesId == id } }.map { "Cry $it" }
        val targets = ReplayRetainedAudioRecovery.selectedArticles(archive).map { it.payload as ArchivedAudioCaptured }.associateBy { it.artifactPath }
        val bytes = mutableMapOf<String, ByteArray>()
        val seen = mutableSetOf<String>()
        openArchive()?.use { input -> ZipInputStream(input).use { zip ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                val target = targets[entry.name]
                if (target != null) {
                    val buffer = ByteArray(target.byteCount.toInt() + 1)
                    var used = 0
                    while (used < buffer.size) {
                        val n = zip.read(buffer, used, buffer.size - used)
                        if (n < 0) break
                        used += n
                    }
                    val data = buffer.copyOf(used)
                    // Conflicting duplicate names cannot silently replace verified evidence.
                    if (!seen.add(entry.name)) { bytes.remove(entry.name); missing += "Duplicate artifact ${entry.name}" }
                    else bytes[entry.name] = data
                }
                zip.closeEntry()
            }
        } }
        val context = coroutineContext
        ReplayRetainedAudioRecovery.recover(archive, references, missing, { context.ensureActive() }) { bytes[it.artifactPath] }
    }
}
