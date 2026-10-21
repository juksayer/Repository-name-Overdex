package com.example.overdex.battle.inference

/**
 * Effectiveness text can linger after a charged impact. It describes that hit,
 * not a new fast-move type. Keep source times so delayed OCR is tested against
 * the appropriate impact instead of whichever event was published most recently.
 */
internal class ChargedMoveEffectivenessWindow {
    private val impacts = ArrayDeque<Long>()

    fun observeImpact(atNanos: Long) {
        if (atNanos !in impacts) impacts.addLast(atNanos)
        while (impacts.size > 64) impacts.removeFirst()
    }

    fun contains(atNanos: Long): Boolean = impacts.any {
        atNanos >= it && atNanos - it <= AFTERMATH_NANOS
    }

    companion object {
        // The Oct 14 Seed Bomb recording still displays its text 1.92 s later.
        const val AFTERMATH_NANOS = 3_000_000_000L
        private val USED_MOVE = Regex("^.+?\\s+used\\s+.+[!.]*$", RegexOption.IGNORE_CASE)
        fun isMoveAnnouncement(text: String): Boolean = USED_MOVE.matches(text.trim())
    }
}
