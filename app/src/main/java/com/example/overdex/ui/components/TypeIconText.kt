package com.example.overdex.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.example.overdex.model.NicknameTokens

@Composable
fun TypeIconText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val tokens = remember(text) { NicknameTokens.tokens(text) }
    val annotated = remember(text, tokens) {
        buildAnnotatedString {
            var cursor = 0
            tokens.forEachIndexed { index, token ->
                append(text.substring(cursor, token.range.first))
                appendInlineContent("type-$index", "[${token.type.name}]")
                cursor = token.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    val inlineContent = tokens.mapIndexed { index, token ->
        "type-$index" to InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
            PokemonTypeIcon(token.type, modifier = Modifier.fillMaxSize())
        }
    }.toMap()
    Text(
        text = annotated, inlineContent = inlineContent, modifier = modifier,
        color = color, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily,
        textAlign = textAlign, maxLines = maxLines, overflow = overflow, lineHeight = lineHeight,
    )
}
