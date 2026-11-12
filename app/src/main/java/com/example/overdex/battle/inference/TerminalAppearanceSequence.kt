package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.TerminalAppearanceSupport
import com.example.overdex.battle.custody.TerminalParticleSample

internal data class TerminalAppearanceSequence(
    override val startedAt: Long, val contractedAt: Long, override val clearedAt: Long,
    override val evidenceReferences: List<String>,
) : TerminalFinishSequence

/** A chromatic disappearance candidate. Living recalls can pass; no faint or side is implied. */
internal object TerminalAppearanceSequences {
    // These describe the independent particle crop. Appearance has its own
    // baseline, geometry, visibility and predecessor checks below.
    private val particleMeasurementStatuses = setOf("SAMPLED", "BASELINE", "SOURCE_GAP",
        "GEOMETRY_CHANGED", "INVALID", "UNREADABLE", "CLUTTER")
    private data class Track(val baseline: TerminalAppearanceSupport, val refs: MutableSet<String>,
        var contractedAt: Long? = null)

    fun find(frames: List<TerminalEffectFrame>): List<TerminalAppearanceSequence> {
        val tracks=linkedMapOf<Long,Track>()
        val rejected=mutableSetOf<Long>()
        val result=mutableListOf<TerminalAppearanceSequence>()
        var previous: TerminalParticleSample?=null
        var cooldown=Long.MIN_VALUE
        for((_,copies) in frames.groupBy { it.sample.sourceAtNanos }.toSortedMap()) {
            val readings=copies.map { it.sample }.distinct()
            if(readings.size!=1) { tracks.clear(); rejected.clear(); previous=null; continue }
            val frame=copies.minBy { it.articleId }; val s=frame.sample; val at=s.sourceAtNanos
            if(s.appearance?.status=="THROTTLED") continue
            val old=previous; previous=s
            val a=s.appearance; val oldA=old?.appearance
            val continuous=old!=null && at-old.sourceAtNanos in 1..250_000_000L &&
                a?.previousSourceAtNanos==old.sourceAtNanos && s.sourceWidth>0 && s.sourceHeight>0 && s.sourceWidth==old.sourceWidth &&
                s.sourceHeight==old.sourceHeight && s.publicationStream==old.publicationStream &&
                (s.publicationSequence==null || old.publicationSequence==null || s.publicationSequence>old.publicationSequence) &&
                a?.regions==oldA?.regions && a?.width==oldA?.width && a?.height==oldA?.height
            val valid=a!=null && a.version==1 && a.status=="SAMPLED" && a.width in 32..336 && a.height in 32..1344 &&
                a.regions.size==3 && a.regions.all { b -> b.size==4 && b[0]>=0 && b[1]>=0 &&
                    b[2]<=a.width && b[3]<=a.height && b[2]-b[0]>=8 && b[3]-b[1]>=8 } &&
                a.backgroundChange?.let { it.isFinite() && it in 0.0..5.0 }==true &&
                a.backgroundTexture?.let { it.isFinite() && it>=8 }==true &&
                a.backgroundMean?.let { it.isFinite() && it in 20.0..230.0 }==true && a.supports.size<=21 &&
                a.supports.distinctBy { it.baselineAtNanos }.size==a.supports.size && a.supports.all { p ->
                        p.baselineAtNanos>=0 && p.baselineAtNanos<=at && at-p.baselineAtNanos<=2_000_000_000L &&
                        p.hueBins.isNotEmpty() && p.hueBins==p.hueBins.distinct().sorted() && p.hueBins.all { it in 0..17 } &&
                        p.baselineArea>=a.width.toDouble()*a.height*(3000.0/(336*747)) &&
                        p.baselineLargestComponent in 0..p.baselineArea && p.baselineLargestComponent>=.7*p.baselineArea &&
                        p.area in 0..((a.regions[0][2]-a.regions[0][0])*(a.regions[0][3]-a.regions[0][1])) &&
                        p.baselineArea<=(a.regions[0][2]-a.regions[0][0])*(a.regions[0][3]-a.regions[0][1]) &&
                        p.brightenedFraction.isFinite() && p.brightenedFraction in 0.0..1.0
                }
            if(!continuous || !valid || s.status !in particleMeasurementStatuses) {
                tracks.clear(); rejected.clear(); continue
            }
            val sample=requireNotNull(a)
            // Missing support cannot be bridged as a measured absence.
            tracks.keys.retainAll(sample.supports.map { it.baselineAtNanos }.toSet())
            rejected.removeAll { at-it>2_000_000_000L }
            var completed: TerminalAppearanceSequence?=null
            for(p in sample.supports.sortedBy { it.baselineAtNanos }) {
                if(p.baselineAtNanos<cooldown || p.baselineAtNanos in rejected) continue
                val track=tracks[p.baselineAtNanos]
                if(track==null) {
                    if(p.baselineAtNanos==at && p.area==p.baselineArea && p.brightenedFraction==0.0)
                        tracks[at]=Track(p,linkedSetOf(frame.articleId))
                    continue
                }
                if(p.hueBins!=track.baseline.hueBins || p.baselineArea!=track.baseline.baselineArea ||
                    p.baselineLargestComponent!=track.baseline.baselineLargestComponent ||
                    p.area>1.25*p.baselineArea || (p.area<=.65*p.baselineArea && p.brightenedFraction>.25)) {
                    tracks.remove(p.baselineAtNanos); rejected+=p.baselineAtNanos; continue
                }
                track.refs+=frame.articleId
                if(p.area>.02*p.baselineArea && p.area<=.65*p.baselineArea && at-p.baselineAtNanos>=150_000_000L)
                    track.contractedAt=at
                if(p.area<=.02*p.baselineArea && track.contractedAt?.let { at-it in 1..500_000_000L }==true) {
                    completed=TerminalAppearanceSequence(p.baselineAtNanos,track.contractedAt!!,at,track.refs.toList())
                    break
                }
            }
            if(completed!=null) { result+=completed; tracks.clear(); rejected.clear(); cooldown=at+500_000_000L }
        }
        return result
    }
}
