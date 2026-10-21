package com.example.overdex.battle.observation

/** Give current evidence a turn without dropping a brief older frame between two screens. */
internal class FreshEvidenceQueue<T> {
    private val pending = ArrayDeque<T>()
    private var newest = true
    @Synchronized fun offer(value: T) { pending.addLast(value) }
    @Synchronized fun poll(): T? {
        if (pending.isEmpty()) return null
        val value = if (newest) pending.removeLast() else pending.removeFirst()
        newest = !newest
        return value
    }
}
