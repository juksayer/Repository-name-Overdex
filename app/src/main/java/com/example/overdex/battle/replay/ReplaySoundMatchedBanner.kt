package com.example.overdex.battle.replay

import com.example.overdex.battle.observation.ActiveHpBarMeasurement
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.ChargedImpactHpBarMeasurer
import android.graphics.Bitmap
import com.example.overdex.battle.archive.*
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.Move
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** A loose banner supplies spelling and owner. Two separate archived clips supply the move clock. */
internal object ReplaySoundMatchedBanner {
    const val BASIS = "SOUND_MATCHED_BANNER_AND_LAST_OPPONENT_REPEAT_CRY"
    private const val S = 1_000_000_000L
    private val banner = Regex("""^(.+?)\s+used\s+(.+)""", RegexOption.IGNORE_CASE)
    private fun letters(s: String) = s.filter(Char::isLetter).lowercase()

    suspend fun read(archive: MatchArchive, directory: File, moves: Map<String, List<Move>>,
                     audio: ReplayAudioRecovery, open: () -> InputStream?, asset: (String) -> ByteArray, cache: ReplayReviewCache? = null
    ): ReplayImpactEvidence? = withContext(Dispatchers.IO) {
        if (!archive.mediaIncluded || !directory.isDirectory) return@withContext null
        val rows = archive.articles.filter { it.matchId == archive.matchId && it.monotonicTimeNanos != null }
            .sortedBy { it.monotonicTimeNanos }
        val result = rows.firstOrNull { it.payload is ArchivedMatchEnded } ?: return@withContext null
        if ((result.payload as ArchivedMatchEnded).result != "WIN") return@withContext null
        val end = result.monotonicTimeNanos!!
        val cry = audio.partialCryCandidates.singleOrNull { end - it.estimatedOnsetNanos in 0..20*S } ?: return@withContext null
        val at = cry.estimatedOnsetNanos
        val target = rows.lastOrNull { it.monotonicTimeNanos!! < at &&
            (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.side == "OPPONENT" }?.payload as? ArchivedActivePokemonSpeciesWitnessed
            ?: return@withContext null
        if (target.speciesId != cry.speciesId || cry.fragments.isEmpty()) return@withContext null
        val entrance = rows.lastOrNull { row ->
            val p = row.payload as? ArchivedBattleCryLocalized
            p?.speciesId == cry.speciesId && p.correlation >= .95f && p.referenceSha256 == cry.fragments.first().referenceSha256 &&
                at - row.monotonicTimeNanos!! > p.durationNanos + 5*S
        } ?: return@withContext null
        val regionRow = rows.lastOrNull { it.monotonicTimeNanos!! in at-6*S..at &&
            (it.payload as? ArchivedCropCaptured)?.cropName == "AnnouncementCrop" } ?: return@withContext null
        val region = regionRow.payload as ArchivedCropCaptured
        val roster = ArchivedPlayerTeamConfiguration(rows).recordsAt().map { it.payload as ArchivedPlayerTeamSlotConfigured }
        if (roster.any { it.speciesId == target.speciesId }) return@withContext null
        val reads = mutableListOf<Triple<File, ArchivedPlayerTeamSlotConfigured, String>>()
        // File modification time bounds IO only. It never dates a move or a faint.
        val files = directory.listFiles { f -> f.extension == "png" && f.nameWithoutExtension.length == 64 &&
            abs(f.lastModified() - regionRow.perceivedAt) <= 6_000 && f.length() in 1..8_000_000 }.orEmpty()
        if (files.size !in 1..16) return@withContext null
        for (file in files) {
            coroutineContext.ensureActive()
            val bytes = file.readBytes()
            if (ReplayRetainedAudioRecovery.sha(bytes) != file.nameWithoutExtension) continue
            val bmp = ArchivedImpactFrameRecognizer.decode(bytes, region.sourceWidth, region.sourceHeight) ?: continue
            val line = try { readBanner(bmp, region) } finally { bmp.recycle() } ?: continue
            val actor = roster.singleOrNull { it.speciesName.equals(line.groupValues[1], true) } ?: continue
            val label = letters(line.groupValues[2])
            val move = actor.chargedMoveNames.singleOrNull {
                val name = letters(it)
                name == label || (label.length >= 6 && name.startsWith(label) && name.length - label.length <= 3)
            } ?: continue
            reads += Triple(file, actor, move)
        }
        val (file, actor, name) = reads.singleOrNull() ?: return@withContext null
        val equipped = actor.chargedMoveNames + actor.fastMoveName
        val player = moves[actor.speciesName].orEmpty().filter { it.name in equipped }
        val opposing = moves[target.speciesName].orEmpty()
        if (player.map { it.name }.toSet() != equipped.toSet() || opposing.isEmpty()) return@withContext null
        val sounds = ArchivedEndingAudioRecognizer.recognize(archive, player + opposing,
            setOf(actor.speciesId, cry.speciesId), open, at-8*S..at+2*S, name, asset, cache)
        val charge = sounds.charges.singleOrNull() ?: return@withContext null
        if (charge.moveName != name || at - charge.estimatedOnsetNanos !in 1..6*S ||
            !sounds.referenceCandidateNames.containsAll(equipped) || sounds.unavailableArtifacts.isNotEmpty() || sounds.fastEffects.isNotEmpty()) return@withContext null
        val clips = charge.fragments.sortedBy { it.captureAtNanos }
        val onset = charge.estimatedOnsetNanos
        if (clips.map { it.audioSha256 }.distinct().size < 2 || clips.any { abs(it.estimatedOnsetNanos - onset) > 20_000_000L }) return@withContext null
        val departure = sounds.partialCryCandidates.singleOrNull { it.speciesId == cry.speciesId &&
            abs(it.estimatedOnsetNanos - at) <= 5_000_000L }?.fragments?.singleOrNull { it.durationNanos >= S }
            ?: return@withContext null
        val hit = rows.singleOrNull { row -> row.monotonicTimeNanos!! in onset..at &&
            (row.payload as? ArchivedCropCaptured)?.cropName == "MatchOutcomeCrop" } ?: return@withContext null
        val crop = hit.payload as ArchivedCropCaptured
        val previous = rows.lastOrNull { row ->
            val hp = row.payload as? ArchivedCompleteHpBarObserved
            hp?.side == "OPPONENT" && row.monotonicTimeNanos!! in entrance.monotonicTimeNanos!!..onset &&
                hp.sourceWidth == crop.sourceWidth && hp.sourceHeight == crop.sourceHeight
        }?.payload as? ArchivedCompleteHpBarObserved ?: return@withContext null
        if (!hasCenteredImpact(open, crop, previous)) return@withContext null
        ReplayImpactEvidence(ReplayImpactFrame(clips.first().articleId, file.nameWithoutExtension, onset, actor, name,
            hit.articleId, hit.monotonicTimeNanos!!, listOf(entrance.articleId, departure.articleId),
            departure.captureAtNanos, charge), sounds)
    }

    private fun hasCenteredImpact(open: () -> InputStream?, crop: ArchivedCropCaptured, hp: ArchivedCompleteHpBarObserved): Boolean {
        val bounds = BattleCropBounds(crop.cropLeft, crop.cropTop, crop.cropRight, crop.cropBottom)
        val w = bounds.right - bounds.left
        val h = bounds.bottom - bounds.top
        if (crop.byteCount !in 1..750_000 || w <= 0 || h <= 0 || w.toLong() * h > 500_000) return false
        val bytes = ByteArray(crop.byteCount.toInt())
        var found = false
        ZipInputStream(open() ?: return false).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name != crop.artifactPath) continue
                if (found) return false
                DataInputStream(zip).readFully(bytes)
                if (zip.read() != -1) return false
                found = true
            }
        }
        if (!found || ReplayRetainedAudioRecovery.sha(bytes) != crop.sha256) return false
        val bitmap = ArchivedImpactFrameRecognizer.decode(bytes, w, h) ?: return false
        val pixels = IntArray(w * h)
        try { bitmap.getPixels(pixels, 0, w, 0, 0, w, h) } finally { bitmap.recycle() }
        val shape = ActiveHpBarMeasurement(hp.barLeft, hp.barTop, hp.barRight, hp.barBottom, 0f, 0f)
        // In a stored crop the final row can itself contain the bottom rail. All four edges
        // must still be visible. The orange profile dates visible damage, never numeric zero.
        val sample = ChargedImpactHpBarMeasurer.acquireComplete(crop.sourceWidth, crop.sourceHeight,
            shape, bounds, includeLastRail = true) { x, y -> pixels[(y-bounds.top)*w + x-bounds.left] }
        return sample != null && sample.liveColumns == 0 && sample.damageColumns >= 3
    }

    private suspend fun readBanner(bitmap: Bitmap, box: ArchivedCropCaptured): MatchResult? {
        // The rightmost HUD move list can overlap the final word. Audio must independently resolve the spelling.
        val right = minOf(box.cropRight, bitmap.width * 3 / 4)
        if (box.cropLeft !in 0 until right || box.cropTop !in 0 until box.cropBottom || box.cropBottom > bitmap.height) return null
        val strip = Bitmap.createBitmap(bitmap, box.cropLeft, box.cropTop, right-box.cropLeft, box.cropBottom-box.cropTop)
        return try { SharedLatinTextRecognizer.readText(strip).lineSequence().mapNotNull { banner.matchEntire(it.trim()) }.singleOrNull() }
            finally { if (strip !== bitmap) strip.recycle() }
    }
}
