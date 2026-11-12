package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import kotlin.reflect.*
import kotlin.reflect.full.*

/** Test-only reverse mapping. Every restored payload must round-trip exactly before admission. */
internal object ProcessingArchiveInput {
    fun restore(a: ArchivedRealityArticle): TestimonyPayload {
        val p = a.payload
        fun provenance(v: Any): BattleCropProvenance {
            val f = properties(v)
            return BattleCropProvenance(f.getValue("cropName") as String, f.getValue("sourceWidth") as Int,
                f.getValue("sourceHeight") as Int, BattleCropBounds(f.getValue("cropLeft") as Int,
                    f.getValue("cropTop") as Int, f.getValue("cropRight") as Int, f.getValue("cropBottom") as Int))
        }
        val result: TestimonyPayload = when (p) {
            // These two projection-only fields are never supplied by the accepted OCR witness.
            is ArchivedActivePokemonSpeciesWitnessed -> ActivePokemonSpeciesWitnessed(
                ActivePokemonSide.valueOf(p.side), p.speciesName, p.speciesId, p.ocrText, p.ocrMatchBasis)
            is ArchivedRawText -> RawTestimony(p.value, p.ocr)
            is ArchivedRawInt -> RawTestimony(p.value)
            is ArchivedCropCaptured -> CropCaptured(CropArtifactReference(p.artifactPath,p.sha256,p.byteCount,p.mediaType), provenance(p),p.overlayBounds)
            is ArchivedAudioCaptured -> AudioCaptured(AudioArtifactReference(p.artifactPath,p.sha256,p.byteCount,p.mediaType),p.sampleRateHz,p.channelCount,p.durationNanos,p.cueKind,p.captureSource,p.peakAmplitude)
            is ArchivedCountdownGlyphWitnessed -> CountdownGlyphWitnessed(p.glyph,p.similarity,p.frameIndex,
                if (p.cropName == null) null else provenance(p),p.basis)
            else -> {
                val name = p.javaClass.simpleName.removePrefix("Archived")
                val klass = Class.forName("com.example.overdex.battle.custody.$name").kotlin
                convert(p, klass.createType()) as TestimonyPayload
            }
        }
        val raw = RealityArticle(ArticleId(a.articleId),a.perceivedAt,a.recordedAt,SourceId(a.sourceId),result,
            matchId = com.example.overdex.battle.observation.MatchId(a.matchId), confidence = a.confidence,
            monotonicTimeNanos = a.monotonicTimeNanos, sequenceNumber = a.sequenceNumber,
            evidenceReferences = a.evidenceReferences.orEmpty())
        check(RealityArticleArchiveMapper.map(raw).payload == p) { "Round-trip mismatch: ${p.javaClass.simpleName}" }
        return result
    }
    private fun properties(value: Any): Map<String, Any?> = value::class.memberProperties.associate {
        it.name to it.getter.call(value)
    }
    private fun convert(value: Any?, type: KType): Any? {
        if (value == null) { check(type.isMarkedNullable); return null }
        val cls = type.classifier as KClass<*>
        if (cls == List::class || cls == Set::class) {
            val converted = (value as Iterable<*>).map { convert(it, type.arguments.single().type!!) }
            return if (cls == Set::class) converted.toSet() else converted
        }
        if (cls.isInstance(value)) return value
        if (cls.java.isEnum) return requireNotNull(cls.java.enumConstants).single { (it as Enum<*>).name == value }
        cls.objectInstance?.let { return it }
        val fields = properties(value)
        val constructor = requireNotNull(cls.primaryConstructor) { "No constructor for $cls" }
        return constructor.callBy(constructor.parameters.associateWith { parameter ->
            check(fields.containsKey(parameter.name)) { "Missing ${cls.simpleName}.${parameter.name}" }
            convert(fields[parameter.name], parameter.type)
        })
    }
}
