package com.gmaingret.notes.presentation.bullet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gmaingret.notes.domain.model.FlatBullet
import kotlinx.coroutines.delay

private const val MAX_DISPLAY_DEPTH = 7
private val INDENT_DP = 24.dp
private val GUIDE_LINE_OFFSET_DP = 8.dp

/**
 * Renders a single bullet in the flat list.
 *
 * Two rendering modes:
 * - **Focused**: [BasicTextField] for inline editing with Enter/Backspace intercept
 * - **Unfocused**: Markdown-rendered text + inline chips, or strikethrough for completed bullets
 *
 * Layout (Row from left to right):
 * - Left padding based on [FlatBullet.depth], capped at [MAX_DISPLAY_DEPTH]
 * - Vertical guide lines drawn behind the row for each depth level
 * - Bullet icon (circle or checkbox for completed bullets)
 * - Content area (weighted to fill remaining width)
 * - Collapse/expand arrow (only for bullets with children)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BulletRow(
    flatBullet: FlatBullet,
    isFocused: Boolean,
    contentOverride: String?,
    focusCursorEnd: Boolean,
    onFocusRequest: () -> Unit,
    onContentChange: (String) -> Unit,
    onEnterWithContent: () -> Unit,
    onEnterOnEmpty: () -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    onCollapseToggle: () -> Unit,
    onBulletIconTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bullet = flatBullet.bullet
    val depth = minOf(flatBullet.depth, MAX_DISPLAY_DEPTH)
    val indentPx = INDENT_DP * depth

    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val guideLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    // Local text state for the editing field
    var localText by remember(bullet.id) { mutableStateOf(contentOverride ?: bullet.content) }
    var textFieldValue by remember(bullet.id) {
        mutableStateOf(TextFieldValue(contentOverride ?: bullet.content))
    }

    // Keep in sync when content changes from outside (e.g., backspace merge)
    LaunchedEffect(contentOverride) {
        if (contentOverride != null && contentOverride != localText) {
            localText = contentOverride
            val newCursor = if (focusCursorEnd) contentOverride.length else textFieldValue.selection.start.coerceAtMost(contentOverride.length)
            textFieldValue = TextFieldValue(contentOverride, selection = TextRange(newCursor))
        }
    }

    // Request focus and bring into view when isFocused becomes true
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
            if (focusCursorEnd) {
                val text = textFieldValue.text
                textFieldValue = textFieldValue.copy(selection = TextRange(text.length))
            }
            delay(50)
            bringIntoViewRequester.bringIntoView()
        }
    }

    val collapseRotation by animateFloatAsState(
        targetValue = if (bullet.isCollapsed) 0f else 0f, // will differentiate by icon choice
        label = "collapseRotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(start = indentPx, top = 2.dp, bottom = 2.dp, end = 4.dp)
            .drawBehind {
                // Draw vertical guide lines for each depth level
                for (level in 1..depth) {
                    val x = (-(depth - level) * INDENT_DP.toPx()) + GUIDE_LINE_OFFSET_DP.toPx() - indentPx.toPx()
                    val lineX = GUIDE_LINE_OFFSET_DP.toPx() + ((level - 1).toFloat() * -INDENT_DP.toPx())
                    // Correct: draw line at (level-1)*24+8 offset from the ROW start (after padding applied)
                    // Since padding is already applied by Modifier.padding, draw relative to content area
                    drawLine(
                        color = guideLineColor,
                        start = Offset(x = (((level - 1) * INDENT_DP.toPx()) - depth * INDENT_DP.toPx() + GUIDE_LINE_OFFSET_DP.toPx()), y = 0f),
                        end = Offset(x = (((level - 1) * INDENT_DP.toPx()) - depth * INDENT_DP.toPx() + GUIDE_LINE_OFFSET_DP.toPx()), y = size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bullet icon
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable { onBulletIconTap() },
            contentAlignment = Alignment.Center
        ) {
            if (bullet.isComplete) {
                Icon(
                    imageVector = Icons.Filled.CheckBox,
                    contentDescription = "Completed",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp)) {
                    drawCircle(
                        color = Color(0xFF808080).copy(alpha = 0.6f),
                        radius = 4.dp.toPx(),
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (!isFocused) Modifier.clickable { onFocusRequest() } else Modifier
                )
        ) {
            if (isFocused) {
                // Edit mode: BasicTextField
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val newText = newValue.text
                        val newlineIndex = newText.indexOf('\n')
                        if (newlineIndex >= 0) {
                            val textBeforeNewline = newText.substring(0, newlineIndex)
                            if (textBeforeNewline.isEmpty() && localText.isEmpty()) {
                                onEnterOnEmpty()
                            } else {
                                localText = textBeforeNewline
                                textFieldValue = TextFieldValue(
                                    textBeforeNewline,
                                    selection = TextRange(textBeforeNewline.length)
                                )
                                onContentChange(textBeforeNewline)
                                onEnterWithContent()
                            }
                            // Do not update textFieldValue with newline
                        } else {
                            localText = newText
                            textFieldValue = newValue
                            onContentChange(newText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { keyEvent ->
                            when (keyEvent.key) {
                                Key.Backspace -> {
                                    if (localText.isEmpty()) {
                                        onBackspaceOnEmpty()
                                        true
                                    } else false
                                }
                                Key.Enter -> {
                                    if (localText.isEmpty()) {
                                        onEnterOnEmpty()
                                    } else {
                                        onEnterWithContent()
                                    }
                                    true
                                }
                                else -> false
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                // Display mode
                if (bullet.isComplete) {
                    // Completed: strikethrough at 50% opacity
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            ) {
                                append(bullet.content)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Render content segments (chips + markdown)
                    val segments = parseContentSegments(bullet.content)
                    if (segments.isEmpty()) {
                        // Empty bullet — show placeholder
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (segments.all { it is ContentSegment.TextSegment }) {
                        // All text — use markdown AnnotatedString renderer
                        Text(
                            text = buildMarkdownAnnotatedString(bullet.content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        // Mixed text and chips — use FlowRow layout
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            segments.forEach { segment ->
                                when (segment) {
                                    is ContentSegment.TextSegment -> {
                                        Text(
                                            text = buildMarkdownAnnotatedString(segment.text),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    is ContentSegment.ChipSegment -> {
                                        InlineChip(segment)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Collapse/expand arrow — only shown if bullet has children
        if (flatBullet.hasChildren) {
            androidx.compose.material3.IconButton(
                onClick = onCollapseToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (bullet.isCollapsed) Icons.Filled.ArrowRight else Icons.Filled.ArrowDropDown,
                    contentDescription = if (bullet.isCollapsed) "Expand" else "Collapse",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Small inline chip composable for #tag, @mention, !!date segments.
 * Rendered as a rounded box with type-specific background color.
 */
@Composable
private fun InlineChip(chip: ContentSegment.ChipSegment) {
    val backgroundColor = when (chip.type) {
        ChipType.TAG -> Color(0xFF1565C0).copy(alpha = 0.15f)        // blue
        ChipType.MENTION -> Color(0xFF2E7D32).copy(alpha = 0.15f)    // green
        ChipType.DATE -> Color(0xFFE65100).copy(alpha = 0.15f)       // orange
    }
    val textColor = when (chip.type) {
        ChipType.TAG -> Color(0xFF1565C0)
        ChipType.MENTION -> Color(0xFF2E7D32)
        ChipType.DATE -> Color(0xFFE65100)
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .then(
                Modifier.drawBehind {
                    drawRoundRect(
                        color = backgroundColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                }
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = chip.text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
