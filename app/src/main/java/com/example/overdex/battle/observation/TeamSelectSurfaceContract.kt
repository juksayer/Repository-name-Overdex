package com.example.overdex.battle.observation

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

internal data class TeamSelectSignal(
    val articleId: String,
    val observedAtNanos: Long,
    val confidence: Float,
    val perceivedAtMillis: Long = 0L,
)

internal data class TeamSelectSurfaceEvidence(
    val leagueText: TeamSelectSignal? = null,
    val leagueBadge: TeamSelectSignal? = null,
    val partyCards: Map<Int, TeamSelectSignal> = emptyMap(),
    val restrictions: TeamSelectSignal? = null,
    val useThisParty: TeamSelectSignal? = null
)

internal data class TeamSelectSurfaceAcceptance(
    val basis: String,
    val confidence: Float,
    val evidenceArticleIds: List<String>,
    val latestSource: TeamSelectSignal,
)

/**
 * Associate asynchronous readings by capture time, not completion order. Keep
 * a bounded history of small measurements so a queued label can still find the
 * badge/cards from its own frame. The original artifacts stay in the ledger.
 */
internal class TeamSelectSurfaceEvidenceWindow {
    private val frames = java.util.TreeMap<Long, TeamSelectSurfaceEvidence>()

    fun observe(
        atNanos: Long,
        update: (TeamSelectSurfaceEvidence) -> TeamSelectSurfaceEvidence,
    ): TeamSelectSurfaceAcceptance? {
        frames[atNanos] = update(frames[atNanos] ?: TeamSelectSurfaceEvidence())
        while (frames.size > 32) frames.pollFirstEntry()
        val nearby = frames.entries
            .filter { kotlin.math.abs(it.key - atNanos) <= TeamSelectSurfaceContract.AGREEMENT_WINDOW_NANOS }
            .sortedBy { kotlin.math.abs(it.key - atNanos) }
            .map { it.value }
        val aligned = TeamSelectSurfaceEvidence(
            leagueText = nearby.firstNotNullOfOrNull { it.leagueText },
            leagueBadge = nearby.firstNotNullOfOrNull { it.leagueBadge },
            restrictions = nearby.firstNotNullOfOrNull { it.restrictions },
            useThisParty = nearby.firstNotNullOfOrNull { it.useThisParty },
            partyCards = (1..3).mapNotNull { slot ->
                nearby.firstNotNullOfOrNull { it.partyCards[slot] }?.let { slot to it }
            }.toMap(),
        )
        return TeamSelectSurfaceContract.evaluate(aligned)
    }
}

/**
 * Downstream agreement contract for the independently preserved Team Select apertures.
 * Color resemblance is only supporting evidence. Require page-specific words
 * before accepting a party; overworld terrain also contains pale/green panels.
 */
internal object TeamSelectSurfaceContract {
    internal const val AGREEMENT_WINDOW_NANOS = 2_000_000_000L

    fun evaluate(evidence: TeamSelectSurfaceEvidence): TeamSelectSurfaceAcceptance? {
        val all = buildList {
            evidence.leagueText?.let(::add)
            evidence.leagueBadge?.let(::add)
            addAll(evidence.partyCards.values)
            evidence.restrictions?.let(::add)
            evidence.useThisParty?.let(::add)
        }
        if (all.isEmpty()) return null
        val newest = all.maxOf { it.observedAtNanos }
        fun fresh(signal: TeamSelectSignal?) = signal?.takeIf {
            newest - it.observedAtNanos <= AGREEMENT_WINDOW_NANOS
        }
        val leagueText = fresh(evidence.leagueText)
        val leagueBadge = fresh(evidence.leagueBadge)
        val restrictions = fresh(evidence.restrictions)
        val useThisParty = fresh(evidence.useThisParty)
        val cards = evidence.partyCards.toSortedMap().mapNotNull { (slot, signal) ->
            fresh(signal)?.let { slot to it }
        }

        fun accepted(basis: String, confidence: Float, signals: List<TeamSelectSignal>) =
            TeamSelectSurfaceAcceptance(
                basis = basis,
                confidence = min(confidence, signals.minOf { it.confidence }),
                evidenceArticleIds = signals.map { it.articleId }.distinct(),
                latestSource = signals.maxBy { it.observedAtNanos },
            )

        if (leagueText != null && useThisParty != null) {
            return accepted("LEAGUE_TEXT_AND_USE_PARTY_CONTROL", 0.97f, listOf(leagueText, useThisParty))
        }
        if (leagueBadge != null && useThisParty != null) {
            return accepted("LEAGUE_BADGE_AND_USE_PARTY_CONTROL", 0.96f, listOf(leagueBadge, useThisParty))
        }
        if (useThisParty != null && cards.isNotEmpty()) {
            return accepted("USE_PARTY_TEXT_AND_PARTY_CARD", 0.95f, listOf(useThisParty, cards.first().second))
        }
        if (leagueText != null && restrictions != null && cards.size >= 2) {
            return accepted("LEAGUE_AND_RESTRICTION_TEXT_WITH_PARTY_CARDS", 0.95f,
                listOf(leagueText, restrictions) + cards.take(2).map { it.second })
        }
        return null
    }
}

/** Purpose-specific appearance checks; none of these decide that Team Select is visible. */
internal object TeamSelectSurfaceSignalDetector {
    fun partyCardConfidence(bitmap: Bitmap): Float? = partyCardConfidence(
        buildList(bitmap.width * bitmap.height) {
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) add(bitmap.getPixel(x, y))
            }
        }
    )

    internal fun partyCardConfidence(colors: Iterable<Int>): Float? {
        var samples = 0
        var pale = 0
        var ink = 0
        var colorful = 0
        colors.forEach { color ->
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff
            val high = max(red, max(green, blue))
            val low = min(red, min(green, blue))
            val luminance = (red * 299 + green * 587 + blue * 114) / 1000
            samples++
            if (luminance >= 205 && high - low <= 42) pale++
            if (luminance in 35..175) ink++
            if (high - low >= 48 && high >= 115) colorful++
        }
        if (samples == 0) return null
        val paleFraction = pale.toFloat() / samples
        val inkFraction = ink.toFloat() / samples
        val colorfulFraction = colorful.toFloat() / samples
        if (paleFraction < 0.36f || inkFraction < 0.025f || colorfulFraction < 0.018f) return null
        return (0.62f + paleFraction * 0.22f + inkFraction.coerceAtMost(0.20f) * 0.45f +
            colorfulFraction.coerceAtMost(0.20f) * 0.35f).coerceIn(0f, 0.96f)
    }

    fun usePartyControlConfidence(bitmap: Bitmap): Float? {
        var samples = 0
        var green = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val color = bitmap.getPixel(x, y)
                val red = color ushr 16 and 0xff
                val g = color ushr 8 and 0xff
                val blue = color and 0xff
                samples++
                if (g >= 145 && g > red + 18 && g > blue + 8 && blue >= 80) green++
            }
        }
        if (samples == 0) return null
        val fraction = green.toFloat() / samples
        return if (fraction >= 0.20f) (0.72f + fraction * 0.45f).coerceAtMost(0.97f) else null
    }

    fun isRestrictionText(rawText: String): Boolean {
        val normalized = rawText.uppercase().replace(Regex("[^A-Z0-9]+"), " ")
        return ("MAX" in normalized && "CP" in normalized) || "NO LIMIT" in normalized
    }

    fun isUsePartyText(rawText: String): Boolean {
        val normalized = rawText.uppercase().replace(Regex("[^A-Z]+"), " ")
        return "USE" in normalized && "PARTY" in normalized
    }
}
