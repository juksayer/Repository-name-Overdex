package com.example.overdex.ui.components

import java.util.Locale

/** Display labels only. Keys and all caller-owned canonical move names stay unchanged. */
object BattleHudMoveLabels {
    private const val KEEP_FULL_THROUGH = 12
    private val trailingQualifier = Regex("^(.*?)(\\s+\\([^()]+\\))$")

    /** Resolve the entire visible/legal pool together so abbreviations cannot conceal a different move. */
    fun forPool(canonicalMoveNames: Collection<String>): Map<String, String> {
        val labels = canonicalMoveNames.distinct().associateWith(::compact).toMutableMap()
        while (true) {
            val ambiguous = labels.entries.groupBy { it.value.lowercase(Locale.ROOT) }.values
                .filter { entries -> entries.map { it.key.lowercase(Locale.ROOT) }.distinct().size > 1 }
                .flatten()
            var changed = false
            ambiguous.forEach { entry ->
                if (labels[entry.key] != entry.key) {
                    labels[entry.key] = entry.key
                    changed = true
                }
            }
            if (!changed) return labels.toMap()
        }
    }

    private fun compact(name: String): String {
        if (name.length <= KEEP_FULL_THROUGH || name != name.trim()) return name
        val qualified = trailingQualifier.matchEntire(name)
        val core = qualified?.groupValues?.get(1) ?: name
        val qualifier = qualified?.groupValues?.get(2).orEmpty()
        if (core.equals("Power-Up Punch", ignoreCase = true)) return "P-Up Punch$qualifier"
        val words = core.split(Regex("\\s+"), limit = 2)
        if (words.size != 2 || words[0].count(Char::isLetter) <= 1 || words[0].any { !it.isLetter() && it != '\'' }) {
            // An unknown long single word or an already abbreviated label stays readable in full.
            return name
        }
        return "${words[0].first().uppercaseChar()}. ${words[1]}$qualifier"
    }
}
