package com.gmaingret.notes.presentation.bullet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Sticky toolbar shown above the keyboard when a bullet is focused.
 *
 * Contains 7 icon buttons for structural editing operations:
 * 1. Outdent — move bullet up one indent level
 * 2. Indent — move bullet down one indent level
 * 3. Move up — swap with previous sibling
 * 4. Move down — swap with next sibling
 * 5. Undo — undo last operation
 * 6. Redo — redo last undone operation
 * 7. Comment/Note — open note input for the focused bullet
 *
 * Disabled buttons render at 38% alpha automatically via M3 [IconButton] semantics.
 */
@Composable
fun BulletEditingToolbar(
    bulletId: String,
    canIndent: Boolean,
    canOutdent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hasNote: Boolean,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onComment: () -> Unit,
    onAttachment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
        ) {
            // 1. Outdent
            IconButton(
                onClick = onOutdent,
                enabled = canOutdent
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatIndentDecrease,
                    contentDescription = "Outdent"
                )
            }

            // 2. Indent
            IconButton(
                onClick = onIndent,
                enabled = canIndent
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatIndentIncrease,
                    contentDescription = "Indent"
                )
            }

            // 3. Move up
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "Move up"
                )
            }

            // 4. Move down
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = "Move down"
                )
            }

            // 5. Undo
            IconButton(
                onClick = onUndo,
                enabled = canUndo
            ) {
                Icon(
                    imageVector = Icons.Filled.Undo,
                    contentDescription = "Undo"
                )
            }

            // 6. Redo
            IconButton(
                onClick = onRedo,
                enabled = canRedo
            ) {
                Icon(
                    imageVector = Icons.Filled.Redo,
                    contentDescription = "Redo"
                )
            }

            // 7. Comment / Note
            IconButton(
                onClick = onComment,
                enabled = true
            ) {
                Icon(
                    imageVector = if (hasNote) Icons.Filled.Comment else Icons.Outlined.Comment,
                    contentDescription = "Note"
                )
            }

            // 8. Attachment
            IconButton(
                onClick = onAttachment,
                enabled = true
            ) {
                Icon(
                    imageVector = Icons.Filled.Attachment,
                    contentDescription = "Attachments"
                )
            }
        }
    }
}
