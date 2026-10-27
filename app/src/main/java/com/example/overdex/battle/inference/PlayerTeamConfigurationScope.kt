package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.PlayerTeamRosterConfirmation
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.reality.RealityArticle
import java.util.TreeMap
import java.util.TreeSet

/** A confirmed scan identifies the roster, not its moves or owned-card identity. */
internal class PlayerTeamConfigurationScope {
    private val confirmations = TreeSet<Long>()
    private val configurations = mutableMapOf<Int, TreeMap<Long, RealityArticle>>()
    private val undatedConfigurations = mutableMapOf<Int, RealityArticle>()

    val latestConfirmationNanos: Long? get() = confirmations.lastOrNull()

    /** True only when this article establishes a newer invalidation boundary. */
    fun accept(article: RealityArticle): Boolean {
        val at = article.monotonicTimeNanos
        if (at == null) {
            val configured = article.payload as? PlayerTeamSlotConfigured
            // Preserve legacy inputs before any scan boundary. An undated save
            // cannot establish that it happened after a confirmed replacement.
            if (configured != null && confirmations.isEmpty()) undatedConfigurations[configured.slot] = article
            return false
        }
        when (val payload = article.payload) {
            is PlayerTeamRosterConfirmation -> if (payload.confirmed) {
                val previous = latestConfirmationNanos
                confirmations.add(at)
                undatedConfigurations.clear()
                return previous == null || at > previous
            }
            is PlayerTeamSlotConfigured -> {
                configurations.getOrPut(payload.slot) { TreeMap() }[at] = article
                undatedConfigurations.remove(payload.slot)
            }
            else -> Unit
        }
        return false
    }

    fun at(atNanos: Long): Map<Int, RealityArticle> {
        val boundary = confirmations.floor(atNanos)
        val dated = configurations.mapNotNull { (slot, history) ->
            history.floorEntry(atNanos)?.takeIf { boundary == null || it.key > boundary }
                ?.let { slot to it.value }
        }.toMap()
        return if (boundary == null) dated + undatedConfigurations else dated
    }

    fun current(): Map<Int, RealityArticle> = at(Long.MAX_VALUE)

    fun isCurrent(article: RealityArticle): Boolean {
        val slot = (article.payload as? PlayerTeamSlotConfigured)?.slot ?: return false
        return current()[slot]?.id == article.id
    }

    fun clearConfigurations() { configurations.clear(); undatedConfigurations.clear() }
}
