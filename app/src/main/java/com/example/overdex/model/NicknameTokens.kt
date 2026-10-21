package com.example.overdex.model

import java.util.Locale

/** Stable text storage, rendered as type icons wherever a nickname is displayed. */
object NicknameTokens {
    data class Token(val range: IntRange, val type: PokemonType)
    private val names = PokemonType.entries.joinToString("|") { it.name }
    private val pattern = Regex(":(?:TYPE:)?($names)(?:TYPE)?:|TYPE:($names)(?![A-Z])", RegexOption.IGNORE_CASE)

    fun tokens(text: String): List<Token> = pattern.findAll(text).map { match ->
        Token(match.range, PokemonType.valueOf(match.groupValues.drop(1).first(String::isNotEmpty).uppercase(Locale.ROOT)))
    }.toList()

    fun applyKey(text: String, key: String): String = when {
        key == "SPACE" -> text + " "
        key == "DELETE" -> {
            val lastToken = tokens(text).lastOrNull()?.takeIf { it.range.last == text.lastIndex }
            if (lastToken != null) text.take(lastToken.range.first)
            else if (text.isEmpty()) text else text.dropLast(Character.charCount(text.codePointBefore(text.length)))
        }
        key.startsWith("TYPE:") -> {
            val type = PokemonType.entries.firstOrNull { it.name == key.removePrefix("TYPE:") }
            if (type == null) text else "$text:${type.name}:"
        }
        key == "#" -> text
        else -> text + key
    }
}
