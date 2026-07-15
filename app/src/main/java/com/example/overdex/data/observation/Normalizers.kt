package com.example.overdex.data.observation

interface TextNormalizer {
    fun normalize(text: String?): String?
}

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

object CPNormalizer : TextNormalizer {
    override fun normalize(text: String?): String? {
        if (text == null) return null
        return text
            .replace(Regex("[^0-9]"), "")
            .trim()
    }
}

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
