package com.example.overdex.model.observation

/**
 * Interface responsible for distilling a list of observations into a single resolved value.
 */
interface ObservationResolver {
    /**
     * Resolves the current best observation for a given set of candidate observations.
     * Returns null if no observation can be resolved.
     */
    fun resolve(observations: List<Observation>): Observation?
}

/**
 * Default implementation of [ObservationResolver] that uses the "Highest Confidence Wins" policy.
 * If confidence is equal, it preserves the older observation (first in list).
 * Missing or null observations are ignored.
 */
class DefaultObservationResolver : ObservationResolver {
    override fun resolve(observations: List<Observation>): Observation? {
        if (observations.isEmpty()) return null

        var currentBest: Observation? = null

        for (observation in observations) {
            if (currentBest == null) {
                currentBest = observation
            } else if (observation.confidence.score > currentBest.confidence.score) {
                currentBest = observation
            }
        }

        return currentBest
    }
}
