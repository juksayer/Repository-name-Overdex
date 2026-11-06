package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.R
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.FlattenedNode
import com.example.overdex.model.navigation.TreeNodeIcon
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val DirectoryPhosphor = Color(0xFF76ED91)
private val DirectorySelected = Color(0xFFCCFFD7)
private val DirectoryBranch = Color(0xFF367646)
private val DirectoryAncestor = Color(0xFFBD93E8)
private val DirectorySelectionWash = Color(0xFF122D1A)
private val DirectoryTypeface = FontFamily(Font(R.font.share_tech_mono_regular))

@Composable
fun DirectoryTree(
    visibleNodes: List<FlattenedNode>,
    selectedPath: String,
    onNodeSelected: (FlattenedNode) -> Unit,
    modifier: Modifier = Modifier,
    scrollEnabled: Boolean = true,
) {
    val scrollState = rememberScrollState()
    val treeModifier = if (scrollEnabled) {
        modifier.fillMaxWidth().verticalScroll(scrollState)
    } else {
        modifier.fillMaxWidth()
    }
    val rows = remember(visibleNodes, selectedPath) { directoryRows(visibleNodes, selectedPath) }
    // Adjacent rows share their rail edges, including when names wrap or a card
    // field displays its value underneath. Spacing lives inside each row.
    Column(modifier = treeModifier) {
        rows.forEachIndexed { index, presentation ->
            val flattened = presentation.row
            key(flattened.path) {
                val isSelected = selectedPath == flattened.path
                val node = flattened.node
                val directory = node as? DirectoryNode
                val color = when {
                    isSelected -> DirectorySelected
                    presentation.isActiveAncestor -> DirectoryAncestor
                    else -> DirectoryPhosphor
                }
                val requester = remember { BringIntoViewRequester() }
                var headingHeight by remember { mutableIntStateOf(0) }
                LaunchedEffect(isSelected, visibleNodes) {
                    if (isSelected) requester.bringIntoView()
                }
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = if (flattened.depth == 0 && index > 0) 6.dp else 0.dp)
                        .bringIntoViewRequester(requester)
                        .testTag("directory-node:${flattened.path}")
                        .background(if (isSelected) DirectorySelectionWash else Color.Transparent,
                            RoundedCornerShape(2.dp))
                        .clickable { onNodeSelected(flattened) }
                        .semantics(mergeDescendants = true) {
                            selected = isSelected
                            if (directory != null) {
                                stateDescription = if (flattened.isExpanded) "open folder" else "closed folder"
                            }
                        }
                        .drawBehind {
                            val cursorWidth = 12.dp.toPx()
                            val branchWidth = 17.dp.toPx()
                            val lineWidth = 1.dp.toPx()
                            val centerY = if (headingHeight > 0) headingHeight / 2f else size.height / 2f
                            presentation.continuingAncestors.forEachIndexed { level, continues ->
                                if (continues) {
                                    val x = cursorWidth + level * branchWidth + 7.dp.toPx()
                                    drawLine(DirectoryBranch, Offset(x, 0f), Offset(x, size.height), lineWidth)
                                }
                            }
                            if (flattened.depth > 0) {
                                val start = cursorWidth + (flattened.depth - 1) * branchWidth
                                val x = start + 7.dp.toPx()
                                drawLine(DirectoryBranch, Offset(x, 0f),
                                    Offset(x, if (presentation.hasFollowingSibling) size.height else centerY), lineWidth)
                                drawLine(DirectoryBranch, Offset(x, centerY),
                                    Offset(start + branchWidth, centerY), lineWidth)
                            }
                            if (isSelected) {
                                val x = 4.dp.toPx()
                                val radius = 3.dp.toPx()
                                drawLine(DirectorySelected, Offset(x, centerY - radius),
                                    Offset(x + radius, centerY), lineWidth)
                                drawLine(DirectorySelected, Offset(x + radius, centerY),
                                    Offset(x, centerY + radius), lineWidth)
                            }
                        }
                        .padding(start = (12 + flattened.depth.coerceAtLeast(0) * 17).dp, end = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp)
                            .onSizeChanged { headingHeight = it.height }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (node.icon == TreeNodeIcon.BINDER) {
                            BinderDirectoryIcon(color, Modifier.size(17.dp)
                                .semantics { contentDescription = "Binder" })
                        } else {
                            DirectoryNodeIcon(directory != null, flattened.isExpanded, color)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = presentation.label,
                            color = color,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontFamily = DirectoryTypeface,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    (node as? ActionNode)?.detail?.let { detail ->
                        TypeIconText(
                            text = detail,
                            color = if (isSelected) DirectorySelected else DirectoryPhosphor.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontFamily = DirectoryTypeface,
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Small outline folder/file family matching the instrument's binder glyph. */
@Composable
private fun DirectoryNodeIcon(isFolder: Boolean, isExpanded: Boolean, color: Color) {
    Canvas(Modifier.size(17.dp).semantics {
        contentDescription = when {
            !isFolder -> "File"
            isExpanded -> "Open folder"
            else -> "Closed folder"
        }
    }) {
        val unit = size.minDimension / 20f
        val outline = Stroke(width = 1.4f * unit)
        fun path(block: Path.() -> Unit) = Path().apply(block)
        if (isFolder) {
            val back = path {
                moveTo(2f * unit, 16f * unit)
                lineTo(2f * unit, 5f * unit)
                quadraticTo(2f * unit, 3.5f * unit, 3.5f * unit, 3.5f * unit)
                lineTo(8f * unit, 3.5f * unit)
                lineTo(10f * unit, 6f * unit)
                lineTo(17f * unit, 6f * unit)
                lineTo(17f * unit, if (isExpanded) 8f * unit else 16f * unit)
                if (!isExpanded) { lineTo(2f * unit, 16f * unit); close() }
            }
            drawPath(back, color, style = outline)
            if (isExpanded) {
                drawPath(path {
                    moveTo(2f * unit, 16f * unit)
                    lineTo(5f * unit, 8.5f * unit)
                    lineTo(19f * unit, 8.5f * unit)
                    lineTo(16f * unit, 16f * unit)
                    close()
                }, color, style = outline)
            }
        } else {
            drawPath(path {
                moveTo(4f * unit, 2f * unit)
                lineTo(12f * unit, 2f * unit)
                lineTo(17f * unit, 7f * unit)
                lineTo(17f * unit, 18f * unit)
                lineTo(4f * unit, 18f * unit)
                close()
                moveTo(12f * unit, 2f * unit)
                lineTo(12f * unit, 7f * unit)
                lineTo(17f * unit, 7f * unit)
                moveTo(7f * unit, 11f * unit)
                lineTo(14f * unit, 11f * unit)
                moveTo(7f * unit, 14f * unit)
                lineTo(13f * unit, 14f * unit)
            }, color, style = outline)
        }
    }
}

@Composable
fun TerminalScreen(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun TerminalPathIndicator(path: String) {
    Text(
        text = path,
        color = TerminalDimGreen,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun TerminalHeader(text: String, color: Color = TerminalPurple) {
    Text(
        text = "[ $text ]",
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * A standardized terminal section with header and vertical spacing.
 */
@Composable
fun ColumnScope.TerminalSection(
    title: String,
    headerColor: Color = TerminalPurple,
    spacing: Int = 24,
    content: @Composable ColumnScope.() -> Unit
) {
    Spacer(modifier = Modifier.height(spacing.dp))
    TerminalHeader(text = title, color = headerColor)
    content()
}

@Composable
fun TerminalMenuOption(
    label: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    selected: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (selected) "> " else "  ",
                color = if (selected) TerminalGreen else TerminalDimGreen,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = if (selected) TerminalGreen else TerminalDimGreen,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }

        if (status != null) {
            TypeIconText(
                text = "[ $status ]",
                // Menu rows no longer paint a light selection background, so
                // black status text disappeared until focus moved elsewhere.
                color = TerminalGreen,
                fontSize = 14.sp
            )
        }
    }
}

/** Open ring binder with card pockets, legible in the Directory Tree's small icon cell. */
@Composable
private fun BinderDirectoryIcon(
    color: Color,
    modifier: Modifier = Modifier.size(17.dp),
) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 20f
        val outline = Stroke(width = 1.3f * unit)
        // Flat pocket sheets and a visible gap distinguish the binder from a book.
        drawPath(Path().apply {
            moveTo(1.4f * unit, 3f * unit)
            lineTo(8.6f * unit, 3.8f * unit)
            lineTo(8.6f * unit, 17.2f * unit)
            lineTo(1.4f * unit, 16.4f * unit)
            close()
            moveTo(11.4f * unit, 3.8f * unit)
            lineTo(18.6f * unit, 3f * unit)
            lineTo(18.6f * unit, 16.4f * unit)
            lineTo(11.4f * unit, 17.2f * unit)
            close()
        }, color, style = outline)
        for (x in listOf(3.2f, 13.3f)) {
            for (y in listOf(5.4f, 11f)) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x * unit, y * unit),
                    size = androidx.compose.ui.geometry.Size(3.5f * unit, 4.1f * unit),
                    cornerRadius = CornerRadius(0.3f * unit),
                    style = Stroke(width = unit),
                )
            }
        }
        repeat(3) { ring ->
            val y = (6.5f + ring * 3.5f) * unit
            drawPath(Path().apply {
                moveTo(8.2f * unit, y)
                cubicTo(8.2f * unit, y - unit, 11.8f * unit, y - unit, 11.8f * unit, y)
            }, color, style = Stroke(width = unit))
        }
    }
}

@Composable
fun TerminalText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TerminalGreen,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
fun TerminalButton(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (selected) TerminalGreen else TerminalGreen.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Text(
            text = if (selected) "▶ $text" else "> $text",
            color = if (selected) TerminalBlack else TerminalGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HardwareNumericEntry(
    value: String,
    onValueChange: (String) -> Unit,
    maxDigits: Int = 4,
    isFocused: Boolean = true,
    focusedDigitIndex: Int = 0,
    onDigitFocusChange: ((Int) -> Unit)? = null
) {
    val paddedValue = value.padStart(maxDigits, '0')
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until maxDigits) {
            val digitSelected = isFocused && focusedDigitIndex == i
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(40.dp)
                    .background(
                        color = if (digitSelected) TerminalGreen else TerminalBlack,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (digitSelected) TerminalBlack else TerminalDimGreen,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(vertical = 8.dp)
                    .then(if (onDigitFocusChange != null) Modifier.clickable { onDigitFocusChange(i) } else Modifier)
            ) {
                Text(
                    text = paddedValue[i].toString(),
                    color = if (digitSelected) TerminalBlack else TerminalGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AttributeToggle(
    label: String,
    value: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) TerminalGreen else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (value) "[ X ]" else "[   ]",
            color = if (selected) TerminalBlack else TerminalGreen,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label.uppercase(),
            color = if (selected) TerminalBlack else TerminalGreen,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
