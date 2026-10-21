package com.example.overdex.battle.replay

import kotlinx.serialization.Serializable
import java.util.UUID

/** Editorial ordering, never a Match and never input to battle statistics. */
@Serializable
data class ReplayCompilation(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val entries: List<ReplayCompilationEntry> = emptyList(),
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ReplayCompilationEntry(
    val id: String = UUID.randomUUID().toString(),
    val excerptId: String,
)

fun ReplayCompilation.moveEntry(from: Int, to: Int): ReplayCompilation {
    if (from !in entries.indices || to !in entries.indices || from == to) return this
    val reordered = entries.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(entries = reordered)
}

/** Presentation limits only: the model still receives the complete original Timeline. */
data class ReplayWindow(val startNanos: Long, val endNanos: Long) {
    init { require(endNanos > startNanos) { "Choose an end after the clip start." } }
    val durationNanos: Long get() = endNanos - startNanos
    fun clamp(time: Long): Long = time.coerceIn(startNanos, endNanos)
}
