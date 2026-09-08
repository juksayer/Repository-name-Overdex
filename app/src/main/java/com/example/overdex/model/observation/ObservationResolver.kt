package com.example.overdex.model.observation

/**
 * Resolves a list of competing [Observation] facts into a single best belief,
 * prioritizing higher confidence scores and stability.
 */
interface ObservationResolver {
    fun resolve(observations: List<Observation>): Observation?
}

/**
 * Default resolver that selects the observation with the highest confidence score
 * or (in case of ties) preserves the existing observation.
 */
class DefaultObservationResolver : ObservationResolver {
    override fun resolve(observations: List<Observation>): Observation? {
        if (observations.isEmpty()) return null

        var currentBest: Observation? = null

        for (observation in observations) {
            if (currentBest == null) {
                currentBest = observation
            } else {
                val bestScore = currentBest.confidence.score ?: -1f
                val obsScore = observation.confidence.score ?: -1f
                if (obsScore > bestScore) {
                    currentBest = observation
                }
            }
        }

        return currentBest
    }
}
