package com.example.overdex.battle.observation

/** A bounded, observed OCR alias; this never selects a nearest grade for unknown text. */
internal data class QteProgressTextResolution(
    val originalText: String,
    val acceptedText: String,
    val basis: String,
)

/**
 * Native ML Kit read the supplied Pixel 4 NICE screenshot as NIICE! at native
 * resolution. The dedicated grade field has exactly NICE, GREAT and EXCELLENT
 * as its vocabulary. Repair only this observed extra-I alias as a complete
 * token; never remove arbitrary letters or search prose for a convenient grade.
 */
internal fun resolveQteProgressText(text: String): QteProgressTextResolution? {
    val matched = DUPLICATED_I_NICE.matchEntire(text.trim()) ?: return null
    return QteProgressTextResolution(text, "NICE${matched.groupValues[1]}", "KNOWN_QTE_GRADE_DUPLICATED_I")
}

private val DUPLICATED_I_NICE = Regex("NIICE(!?)", RegexOption.IGNORE_CASE)
