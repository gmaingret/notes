package com.gmaingret.notes.presentation.bullet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.gmaingret.notes.domain.model.Attachment
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
 * Layout (Column):
 * - Top Row: depth-indented bullet icon + indicators + content area + collapse arrow
 * - Below row: [NoteField] (animated, shown when [isNoteExpanded] is true)
 * - Below note: [AttachmentList] (animated, shown when [isAttachmentsExpanded] is true and non-empty)
 *
 * Swipe gestures (complete/delete) are handled at BulletTreeScreen level via SwipeToDismissBox.
 *
 * Long-press on unfocused content area opens a context menu with:
 * - Bookmark / Remove bookmark
 * - Attachments (toggle inline attachment list)
 * - Delete
 *
 * Indicators shown inline next to bullet dot:
 * - Star icon when [isBookmarked] is true
 * - Paperclip icon when [attachments] is non-empty (lazy-loaded on first expansion)
 *
 * [onChipClick] wires chip taps from [InlineChip] — Plan 03 passes a search trigger.
 * When the bullet is focused (editing), chip clicks are disabled (null passed to InlineChip).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BulletRow(
    flatBullet: FlatBullet,
    isFocused: Boolean,
    contentOverride: String?,
    focusCursorEnd: Boolean,
    isDragging: Boolean,
    dragHorizontalOffsetPx: Float,
    isNoteExpanded: Boolean,
    isBookmarked: Boolean,
    isAttachmentsExpanded: Boolean,
    attachments: List<Attachment>,
    onFocusRequest: () -> Unit,
    onContentChange: (String) -> Unit,
    onEnterWithContent: () -> Unit,
    onEnterOnEmpty: () -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    onCollapseToggle: () -> Unit,
    onBulletIconTap: () -> Unit,
    onToggleNote: () -> Unit,
    onNoteChange: (String) -> Unit,
    onToggleComplete: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleAttachments: () -> Unit,
    onDeleteFromMenu: () -> Unit,
    onDownloadAttachment: (Attachment) -> Unit,
    onChipClick: ((String) -> Unit)? = null,
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

    // Guard: tracks the last text for which onEnterWithContent was invoked.
    // Prevents double-firing when Compose/IME re-delivers the same onValueChange
    // after textFieldValue is updated programmatically inside the callback.
    var enterHandledForText by remember(bullet.id) { mutableStateOf<String?>(null) }

    // Context menu state
    var showContextMenu by remember { mutableStateOf(false) }

    // Keep in sync when content changes from outside (e.g., backspace merge)
    LaunchedEffect(contentOverride) {
        if (contentOverride != null && contentOverride != localText) {
            localText = contentOverride
            val newCursor = if (focusCursorEnd) contentOverride.length else textFieldValue.selection.start.coerceAtMost(contentOverride.length)
            textFieldValue = TextFieldValue(contentOverride, selection = TextRange(newCursor))
        }
    }

    // Request focus and bring into view when isFocused becomes true.
    // Always place cursor at the end of the text on focus so the user can
    // continue typing naturally regardless of tap position.
    LaunchedEffect(isFocused) {
        if (isFocused) {
            enterHandledForText = null
            focusRequester.requestFocus()
            val text = textFieldValue.text
            textFieldValue = textFieldValue.copy(selection = TextRange(text.length))
            delay(50)
            bringIntoViewRequester.bringIntoView()
        }
    }

    val collapseRotation by animateFloatAsState(
        targetValue = if (bullet.isCollapsed) 0f else 0f, // differentiated by icon choice
        label = "collapseRotation"
    )

    // Note indicator: shown when note exists and note field is NOT expanded
    val hasNote = !bullet.note.isNullOrEmpty()
    val showNoteIndicator = hasNote && !isNoteExpanded

    // Paperclip indicator: shown after attachments loaded and non-empty
    val showAttachmentIndicator = attachments.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (isDragging) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 8f
                    translationX = dragHorizontalOffsetPx
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .padding(start = indentPx, top = 2.dp, bottom = 2.dp, end = 4.dp)
                .drawBehind {
                    // Draw vertical guide lines for each depth level
                    for (level in 1..depth) {
                        val x = (-(depth - level) * INDENT_DP.toPx()) + GUIDE_LINE_OFFSET_DP.toPx() - indentPx.toPx()
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
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bullet.isComplete) {
                    Icon(
                        imageVector = Icons.Filled.CheckBox,
                        contentDescription = "Completed",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onBulletIconTap() },
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onBulletIconTap() }
                    ) {
                        drawCircle(
                            color = Color(0xFF808080).copy(alpha = 0.6f),
                            radius = 4.dp.toPx(),
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                    }
                }
            }

            // Bookmark indicator: star icon when bookmarked
            if (isBookmarked) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Bookmarked",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            // Paperclip indicator: shown after attachments loaded and non-empty
            if (showAttachmentIndicator) {
                Icon(
                    imageVector = Icons.Filled.Attachment,
                    contentDescription = "Has attachments",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Content area — combinedClickable for tap (focus) + long-press (context menu)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!isFocused) {
                            Modifier.combinedClickable(
                                onClick = { onFocusRequest() },
                                onLongClick = { showContextMenu = true }
                            )
                        } else {
                            Modifier
                        }
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
                                    // Guard: only fire onEnterOnEmpty once per empty-state Enter
                                    if (enterHandledForText != "") {
                                        enterHandledForText = ""
                                        onEnterOnEmpty()
                                    }
                                } else {
                                    // Guard: only fire onEnterWithContent once per unique text value.
                                    if (enterHandledForText != textBeforeNewline) {
                                        enterHandledForText = textBeforeNewline
                                        localText = textBeforeNewline
                                        textFieldValue = TextFieldValue(
                                            textBeforeNewline,
                                            selection = TextRange(textBeforeNewline.length)
                                        )
                                        if (textBeforeNewline != (contentOverride ?: bullet.content)) {
                                            onContentChange(textBeforeNewline)
                                        }
                                        onEnterWithContent()
                                    }
                                }
                                // Do not update textFieldValue with newline
                            } else {
                                // Regular typing — clear enter guard so next Enter can fire
                                if (newText != enterHandledForText) {
                                    enterHandledForText = null
                                }
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
                                    // Note: Enter is handled via onValueChange newline detection.
                                    else -> false
                                }
                            },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    // Display mode: use contentOverride (latest typed text) if available,
                    // otherwise fall back to bullet.content (server value).
                    val displayContent = contentOverride ?: bullet.content
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
                                    append(displayContent)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        // Render content segments (chips + markdown)
                        val segments = parseContentSegments(displayContent)
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
                                text = buildMarkdownAnnotatedString(displayContent),
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
                                            // Pass onChipClick only when not focused (editing)
                                            InlineChip(
                                                chip = segment,
                                                onChipClick = onChipClick
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Context menu — anchored to the content Box
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove bookmark" else "Bookmark") },
                        onClick = { onToggleBookmark(); showContextMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Attachments") },
                        onClick = { onToggleAttachments(); showContextMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { onDeleteFromMenu(); showContextMenu = false }
                    )
                }
            }

            // Note indicator icon — only shown when note exists and note field is collapsed
            if (showNoteIndicator) {
                Icon(
                    imageVector = Icons.Filled.StickyNote2,
                    contentDescription = "Has note",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onToggleNote() },
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(4.dp))
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

        // Inline note field — animated expand/collapse below the bullet content row
        NoteField(
            note = bullet.note,
            isExpanded = isNoteExpanded,
            onNoteChange = onNoteChange,
            modifier = Modifier.padding(start = indentPx)
        )

        // Inline attachment list — animated, shown when expanded and attachments exist
        AnimatedVisibility(visible = isAttachmentsExpanded && attachments.isNotEmpty()) {
            AttachmentList(
                attachments = attachments,
                onDownload = onDownloadAttachment,
                modifier = Modifier.padding(start = indentPx)
            )
        }
    }
}

/**
 * Small inline chip composable for #tag, @mention, !!date segments.
 * Rendered as a rounded box with type-specific background color.
 *
 * [onChipClick] is called with the chip text (including prefix) when tapped.
 * Pass null to disable click (e.g., when the bullet is focused for editing).
 */
@Composable
internal fun InlineChip(
    chip: ContentSegment.ChipSegment,
    onChipClick: ((String) -> Unit)? = null
) {
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
            .then(
                if (onChipClick != null) {
                    Modifier.clickable { onChipClick(chip.text) }
                } else {
                    Modifier
                }
            )
    ) {
        Text(
            text = chip.text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}
