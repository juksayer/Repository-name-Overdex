package com.example.overdex.ui.components

import com.example.overdex.data.HudMoveAppearance
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

/**
 * Adjustable lettering and an offset shadow contrast with the battlefield.
 */
@Composable
internal fun BattleHudMoveText(
    text: AnnotatedString,
    appearance: HudMoveAppearance,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    val backingStyle = with(LocalDensity.current) {
        TextStyle(
            drawStyle = Stroke(width = .45.dp.toPx()),
            shadow = Shadow(Color(appearance.shadowColor), Offset(1.35.dp.toPx(), 1.5.dp.toPx()), 1.dp.toPx()),
        )
    }
    Box(modifier.semantics(mergeDescendants = true) { }) {
        // The foreground alone owns layout and accessibility. This backing is
        // paint for the same glyphs, not another row, badge, or spoken label.
        Text(
            text = text.text,
            modifier = Modifier.matchParentSize().clearAndSetSemantics { },
            color = Color(appearance.textColor),
            style = backingStyle,
            fontSize = appearance.fontSp.sp,
            lineHeight = (appearance.fontSp + 2).sp,
            textAlign = TextAlign.End,
            fontWeight = fontWeight,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = Color(appearance.textColor),
            fontSize = appearance.fontSp.sp,
            lineHeight = (appearance.fontSp + 2).sp,
            textAlign = TextAlign.End,
            fontWeight = fontWeight,
            fontFamily = FontFamily.Monospace,
        )
    }
}
