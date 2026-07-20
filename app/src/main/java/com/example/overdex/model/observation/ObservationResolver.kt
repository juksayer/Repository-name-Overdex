package com.example.overdex.model.observation

/**
 * Interface responsible for distilling a list of competing observations into a single resolved belief.
 */
interface ObservationResolver {
    /**
     * Resolves the "best" observation for a given set of candidate observations.
     * 
     * @param observations A list of raw observations for a single field or property.
     * @return The resolved [Observation], or null if no consistent belief can be formed.
     */
    fun resolve(observations: List<Observation>): Observation?
}

/**
 * Default implementation of [ObservationResolver] that uses a "Highest Confidence Wins" policy.
 * 
 * If multiple observations have the same highest confidence, it preserves the older
 * observation (the one that appears earlier in the list).
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
