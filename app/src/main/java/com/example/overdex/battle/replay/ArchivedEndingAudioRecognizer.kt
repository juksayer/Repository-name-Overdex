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
                          openArchive: () -> InputStream?, readAsset: (String) -> ByteArray): ReplayAudioRecovery =
        recognize(archive, moves, speciesIds, openArchive, null, readAsset)

    suspend fun recognize(archive: MatchArchive, moves: List<Move>, speciesIds: Set<Int>,
                          openArchive: () -> InputStream?, captureWindow: LongRange?, readAsset: (String) -> ByteArray): ReplayAudioRecovery =
        recognize(archive, moves, speciesIds, openArchive, captureWindow, null, readAsset)

    internal suspend fun recognize(archive: MatchArchive, moves: List<Move>, speciesIds: Set<Int>,
                          openArchive: () -> InputStream?, captureWindow: LongRange?, bannerMove: String?,
                          readAsset: (String) -> ByteArray, cache: ReplayReviewCache? = null,
                          fastSearches: Map<Long, Set<String>> = emptyMap(),
                          shieldBlockTimes: Set<Long> = emptySet(),
                          entranceWindows: List<ReplayEntranceWindow> = emptyList()): ReplayAudioRecovery = withContext(Dispatchers.Default) {
        val endingClips = ReplayRetainedAudioRecovery.selectedArticles(archive, captureWindow)
        val handoffClips = ReplayEntranceAudio.clips(archive, entranceWindows)
        if (handoffClips.isEmpty() && endingClips.isEmpty()) return@withContext ReplayAudioRecovery()
        if (!archive.mediaIncluded) return@withContext ReplayAudioRecovery(unavailableArtifacts = listOf("Archive has no audio media"))
        val missing = mutableListOf<String>()
        fun key(s: String) = s.uppercase().filter(Char::isLetterOrDigit)
        val wanted = moves.map { key(it.name) }.toMutableSet()
        wanted.addAll(fastSearches.values.flatten().map(::key))
        // A configured or recorded move is still a candidate even when reference legality disagrees.
        if (captureWindow == null) archive.articles.forEach { a -> when (val p = a.payload) {
            is ArchivedPlayerTeamSlotConfigured -> if (p.speciesId in speciesIds) wanted.addAll((p.chargedMoveNames + p.fastMoveName).map(::key))
            is ArchivedFastMoveIdentified -> wanted.add(key(p.moveName))
            is ArchivedChargedMoveEnergySpent -> wanted.add(key(p.moveName))
            else -> Unit
        } }
        val fastSearchKeys = fastSearches.toSortedMap().map { (at, names) -> "$at=${names.sorted().joinToString(",")}" }
        val entranceKeys = entranceWindows.map { "entrance-${it.at}-${it.previous.speciesId}-${it.confirmation.speciesId}-${it.moveNames.sorted()}" }
        val cacheKey = cache?.key(listOf("ending-audio-v3", captureWindow?.first.toString(), captureWindow?.last.toString(),
            bannerMove.orEmpty(), speciesIds.sorted().joinToString(",")) + wanted.sorted() + entranceKeys + fastSearchKeys + shieldBlockTimes.sorted().map { "shield-block-$it" })
        if (cache != null && cacheKey != null) cache.read<ReplayAudioRecovery>(cacheKey)?.let { return@withContext it }
        var assetReadFailed = false
        val references = mutableListOf<ReplayAudioReference>()
        fun entries(path: String) = try { Json.parseToJsonElement(readAsset(path).decodeToString()).jsonObject.getValue("references").jsonArray }
            catch (e: java.io.IOException) { assetReadFailed = true; missing += path; JsonArray(emptyList()) }
        fun reference(name: String, charged: Boolean, species: Int?, path: String, sha: String) {
            val bytes = try { readAsset(path) } catch (e: java.io.IOException) { assetReadFailed = true; null }
            val decoded = bytes?.takeIf { ReplayRetainedAudioRecovery.sha(it) == sha }?.let(WavPcm16::decodeBundledReference)
            if (decoded == null) { assetReadFailed = true; missing += name } else references += ReplayAudioReference(name, charged, species, sha, decoded)
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
        val targets = (handoffClips + endingClips).filter { captureWindow == null || it.monotonicTimeNanos in captureWindow }.map { it.payload as ArchivedAudioCaptured }.associateBy { it.artifactPath }
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
        val entranceSounds = ReplayEntranceAudio.measure(handoffClips, entranceWindows, references, { context.ensureActive() }) { bytes[it.artifactPath] }
        val result = ReplayRetainedAudioRecovery.recover(archive, references, missing, { context.ensureActive() },
            captureWindow, bannerMove, fastSearches, shieldBlockTimes) { bytes[it.artifactPath] }.copy(entranceSounds = entranceSounds)
        if (cache != null && cacheKey != null && !assetReadFailed && result.unavailableArtifacts.isEmpty()) {
            cache.write(cacheKey, result)
        }
        result
    }
}
