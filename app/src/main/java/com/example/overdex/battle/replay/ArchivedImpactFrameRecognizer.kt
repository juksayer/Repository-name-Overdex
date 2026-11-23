package com.example.overdex.battle.replay

import com.example.overdex.battle.observation.withOcrPixels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.LegalChargedMoveTextResolver
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** Independent observations in Replay. The full-frame digest and its pixel-matched crop
 * identify the source. Orange records impact support, never a zero-HP measurement. */
@kotlinx.serialization.Serializable
data class ReplayImpactFrame(
    val announcementCropId: String, val frameSha256: String, val at: Long,
    val actor: ArchivedPlayerTeamSlotConfigured, val move: String,
    val profileId: String, val landing: Long, val departureIds: List<String>, val clearedAt: Long?,
    val chargeSound: ReplayRecoveredCharge? = null,
)

internal object ArchivedImpactFrameRecognizer {
    private const val S = 1_000_000_000L
    private val used = Regex("""^(.+?)\s+used\s+(.+?)[!.]?$""", RegexOption.IGNORE_CASE)
    private val digest = Regex("[a-f0-9]{64}")

    suspend fun recognize(archive: MatchArchive, frameDirectory: File, moves: List<Move>,
                          openArchive: () -> InputStream?): ReplayImpactFrame? = withContext(Dispatchers.IO) {
        if (!archive.mediaIncluded || !frameDirectory.isDirectory) return@withContext null
        val ordered = archive.articles.filter { it.matchId == archive.matchId && it.monotonicTimeNanos != null }
            .sortedBy { it.monotonicTimeNanos }
        val end = ordered.firstOrNull { it.payload is ArchivedMatchEnded } ?: return@withContext null
        if ((end.payload as ArchivedMatchEnded).result != "WIN") return@withContext null
        val profile = ordered.lastOrNull {
            val hp = it.payload as? ArchivedCompleteHpBarObserved
            hp != null && hp.side == "OPPONENT" && hp.damageColumns > 0 && hp.liveColumns == 0 &&
                hp.barRight - hp.barLeft >= 40 && hp.barBottom > hp.barTop &&
                hp.barRight - hp.barLeft >= 6 * (hp.barBottom - hp.barTop) &&
                it.confidence?.let { confidence -> confidence in .80f..1f } == true &&
                hp.interiorColumns >= hp.damageColumns && hp.barLeft >= 0 && hp.barRight < hp.sourceWidth &&
                hp.barTop >= 0 && hp.barBottom < hp.sourceHeight &&
                hp.barRight - hp.barLeft >= hp.interiorColumns &&
                end.monotonicTimeNanos!! - it.monotonicTimeNanos!! in 0..20*S
        } ?: return@withContext null
        val hit = profile.monotonicTimeNanos!!
        if (ordered.any { it.payload is ArchivedChargedMoveEnergySpent &&
                it.monotonicTimeNanos!! in hit-2*S..hit }) return@withContext null
        val crops = ordered.filter { (it.payload as? ArchivedCropCaptured)?.cropName == "ChargedImpactHpCrop" &&
            it.monotonicTimeNanos!! in hit-2*S..hit+4*S }
        val hp = profile.payload as ArchivedCompleteHpBarObserved
        if (crops.size !in 2..24 || crops.none { article ->
                val crop = article.payload as ArchivedCropCaptured
                article.monotonicTimeNanos == hit && hp.sourceWidth == crop.sourceWidth && hp.sourceHeight == crop.sourceHeight &&
                    hp.barLeft >= crop.cropLeft && hp.barRight < crop.cropRight &&
                    hp.barTop >= crop.cropTop && hp.barBottom < crop.cropBottom
            }) return@withContext null
        val region = ordered.filter { (it.payload as? ArchivedCropCaptured)?.cropName == "AnnouncementCrop" }
            .minByOrNull { abs(it.monotonicTimeNanos!! - hit) }?.payload as? ArchivedCropCaptured ?: return@withContext null
        val files = frameDirectory.listFiles { file ->
            file.extension == "png" && digest.matches(file.nameWithoutExtension) &&
                abs(file.lastModified() - profile.perceivedAt) <= 8000 && file.length() in 1..8_000_000
        }.orEmpty()
        if (files.isEmpty() || files.size > 24) return@withContext null
        val targets = crops.map { it.payload as ArchivedCropCaptured }.associateBy { it.artifactPath }
        val images = mutableMapOf<String, Bitmap>()
        val seen = mutableSetOf<String>()
        try {
            ZipInputStream(openArchive() ?: return@withContext null).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val path = zip.nextEntry?.name ?: break
                    val p = targets[path] ?: continue
                    if (!seen.add(path) || p.byteCount !in 1..750_000) return@withContext null
                    val bytes = ByteArray(p.byteCount.toInt())
                    java.io.DataInputStream(zip).readFully(bytes)
                    if (zip.read() != -1) return@withContext null
                    if (bytes.size.toLong() != p.byteCount || ReplayRetainedAudioRecovery.sha(bytes) != p.sha256)
                        return@withContext null
                    images[path] = decode(bytes, p.cropRight-p.cropLeft, p.cropBottom-p.cropTop) ?: return@withContext null
                }
            }
            val roster = ArchivedPlayerTeamConfiguration(ordered).recordsAt().map { it.payload as ArchivedPlayerTeamSlotConfigured }
            val matches = mutableListOf<ReplayImpactFrame>()
            for (file in files) {
                coroutineContext.ensureActive()
                val bytes = file.readBytes()
                if (ReplayRetainedAudioRecovery.sha(bytes) != file.nameWithoutExtension) continue
                val full = decode(bytes, region.sourceWidth, region.sourceHeight) ?: continue
                try {
                    val anchor = crops.filter { it.monotonicTimeNanos!! <= hit }.singleOrNull { a ->
                        val p = a.payload as ArchivedCropCaptured
                        val saved = images[p.artifactPath] ?: return@singleOrNull false
                        subImage(full, p)?.let { cut ->
                            try { cut.sameAs(saved) } finally { if (cut !== full) cut.recycle() }
                        } == true
                    } ?: continue
                    // Repeated identical crops cannot give a loose frame a unique source time.
                    val anchorCrop = anchor.payload as ArchivedCropCaptured
                    if (ordered.any { it.monotonicTimeNanos != anchor.monotonicTimeNanos && it.payload == anchorCrop }) continue
                    val strip = subImage(full, region) ?: continue
                    val text = try { strip.withOcrPixels(region.statusBarRows) { SharedLatinTextRecognizer.readText(it) } } finally { if (strip !== full) strip.recycle() }
                    val banner = text.lines().mapNotNull { used.matchEntire(it.trim()) }.singleOrNull() ?: continue
                    val actor = roster.singleOrNull { it.speciesName.equals(banner.groupValues[1], true) } ?: continue
                    val move = LegalChargedMoveTextResolver.bestMatches(banner.groupValues[2],
                        moves.filter { !it.isFast && it.name in actor.chargedMoveNames }).singleOrNull()?.move ?: continue
                    val departure = crops.filter { it.monotonicTimeNanos!! > hit }.windowed(3).filter { triple ->
                        triple.map { it.payload as ArchivedCropCaptured }.all { it.sourceWidth == anchorCrop.sourceWidth &&
                            it.sourceHeight == anchorCrop.sourceHeight && it.cropLeft == anchorCrop.cropLeft &&
                            it.cropRight == anchorCrop.cropRight && it.cropTop == anchorCrop.cropTop && it.cropBottom == anchorCrop.cropBottom } &&
                            triple.zipWithNext().all { (a,b) -> b.monotonicTimeNanos!! - a.monotonicTimeNanos!! in 1..S } &&
                            isBurst(triple.map { images[(it.payload as ArchivedCropCaptured).artifactPath] })
                    }.singleOrNull()
                    matches += ReplayImpactFrame(anchor.articleId, file.nameWithoutExtension, anchor.monotonicTimeNanos!!,
                        actor, move.name, profile.articleId, hit, departure.orEmpty().map { it.articleId }, departure?.last()?.monotonicTimeNanos)
                } finally { full.recycle() }
            }
            matches.singleOrNull()
        } finally { images.values.forEach(Bitmap::recycle) }
    }

    internal fun decode(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0 || width.toLong()*height > 5_000_000) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return if (options.outWidth == width && options.outHeight == height)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
    }

    private fun subImage(full: Bitmap, crop: ArchivedCropCaptured): Bitmap? {
        if (full.width != crop.sourceWidth || full.height != crop.sourceHeight ||
            crop.cropLeft !in 0 until crop.cropRight || crop.cropTop !in 0 until crop.cropBottom ||
            crop.cropRight > full.width || crop.cropBottom > full.height) return null
        return Bitmap.createBitmap(full, crop.cropLeft, crop.cropTop, crop.cropRight-crop.cropLeft, crop.cropBottom-crop.cropTop)
    }

    /** A localized white flash bracketed by similar field views. This alone also occurs on switches. */
    private fun isBurst(images: List<Bitmap?>): Boolean {
        val before = images[0] ?: return false
        val middle = images[1] ?: return false
        val after = images[2] ?: return false
        if (images.any { it?.width != before.width || it.height != before.height }) return false
        var total = 0
        var stable = 0
        var flash = 0
        for (y in 0 until before.height step 4) for (x in 0 until before.width step 4) {
            val a = before.getPixel(x,y)
            val b = middle.getPixel(x,y)
            val c = after.getPixel(x,y)
            total++
            fun channel(p: Int, shift: Int) = (p ushr shift) and 255
            val shifts = RGB
            if (shifts.all { abs(channel(a,it)-channel(c,it)) < 32 }) stable++
            if (y > before.height/3 && shifts.all { channel(a,it)<235 && channel(b,it)>244 && channel(c,it)<235 }) flash++
        }
        return stable > total*.65 && flash > total*.01 && flash < total*.20
    }
    private val RGB = intArrayOf(0,8,16)
}
