package com.example.overdex.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.PokemonType

/**
 * A type-colored edge and narrow white keyline leave the established HUD ink
 * intact. OCR's size/color/position filter still needs those letter interiors;
 * a move's type color must not turn our own words into game testimony.
 */
@Composable
internal fun BattleHudMoveText(
    text: AnnotatedString,
    type: PokemonType,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    val backingStyle = with(LocalDensity.current) {
        TextStyle(
            drawStyle = Stroke(width = .65.dp.toPx()),
            shadow = Shadow(type.color, Offset(.9.dp.toPx(), .9.dp.toPx()), .35.dp.toPx()),
        )
    }
    Box(modifier.semantics(mergeDescendants = true) { }) {
        // The foreground alone owns layout and accessibility. This backing is
        // paint for the same glyphs, not another row, badge, or spoken label.
        Text(
            text = text.text,
            modifier = Modifier.matchParentSize().clearAndSetSemantics { },
            color = Color.White,
            style = backingStyle,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.End,
            fontWeight = fontWeight,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.End,
            fontWeight = fontWeight,
            fontFamily = FontFamily.Monospace,
        )
    }
}
