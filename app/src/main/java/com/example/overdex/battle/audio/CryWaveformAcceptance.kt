package com.example.overdex.battle.audio

/**
 * Conservative pilot acoustic threshold, not a species-side or KO confidence.
 * The recorded study separates its reviewed cries (> .5) from two negatives (< .15).
 * Competing species at the same onset remain unresolved; distinct cries can overlap in time.
 */
internal object CryWaveformAcceptance {
    const val MIN_CORRELATION = .5f
    private const val MIN_MARGIN = .1f
    private const val SAME_ONSET_NANOS = 100_000_000L
    fun select(fits: List<CryWaveformFit>): List<CryWaveformFit> = fits.filter { fit ->
        fit.correlation.isFinite() && fit.correlation in MIN_CORRELATION..1f &&
            fits.none { other -> other.speciesId != fit.speciesId &&
                kotlin.math.abs(other.offsetNanos - fit.offsetNanos) <= SAME_ONSET_NANOS &&
                other.correlation >= fit.correlation - MIN_MARGIN }
    }.groupBy { it.speciesId }.values.map { it.maxBy(CryWaveformFit::correlation) }
}
