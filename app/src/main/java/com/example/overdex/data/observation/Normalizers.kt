package com.example.overdex.data.observation

/**
 * Interface for components that clean and standardize raw OCR text into domain-compliant values.
 */
interface TextNormalizer {
    /**
     * Standardizes the provided text.
     * 
     * @param text The raw string from a recognizer.
     * @return The cleaned and normalized string, or null if it cannot be processed.
     */
    fun normalize(text: String?): String?
}

/**
 * Normalizes Move names by removing noise like damage numbers and standardizing casing.
 */
object MoveNormalizer : TextNormalizer {
    override fun normalize(text: String?): String? {
        if (text == null) return null
        return text
            .replace(Regex("^0\\s+"), "")      // Remove leading "0 "
            .replace(Regex("\\d+\\+\\d+"), "")  // Remove "+ damage" noise
            .lowercase()
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}

/**
 * Normalizes Pokémon species names by removing numerical prefixes and standardizing casing.
 */
object SpeciesNormalizer : TextNormalizer {
    override fun normalize(text: String?): String? {
        if (text == null) return null
        return text
            .replace(Regex("^#\\d+"), "")   // Remove "#15"
            .replace(Regex("^\\d+"), "")    // Remove "15"
            .replace(Regex("\\b[A-Z]{2,}\\b"), "") // Remove all-caps artifacts like CP/HP if mixed with name
            .trim()
            .uppercase()
    }
}

/**
 * Normalizes Combat Power values by stripping non-numeric characters.
 */
object CPNormalizer : TextNormalizer {
    override fun normalize(text: String?): String? {
        if (text == null) return null
        return text
            .replace(Regex("[^0-9]"), "")
            .trim()
    }
}

/**
 * Normalizes names from the Candy Panel by isolating the species prefix.
 */
object CandyNormalizer : TextNormalizer {
    override fun normalize(text: String?): String? {
        if (text == null) return null
        val lower = text.lowercase()
        return when {
            lower.contains(" candy") -> text.substring(0, lower.indexOf(" candy")).trim()
            lower.contains(" mega energy") -> text.substring(0, lower.indexOf(" mega energy")).trim()
            else -> text.trim()
        }
    }
}
