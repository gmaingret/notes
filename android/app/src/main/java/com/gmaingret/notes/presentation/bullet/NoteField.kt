package com.gmaingret.notes.presentation.bullet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Inline expandable note / comment field shown directly below a bullet's content row.
 *
 * Visibility is controlled externally via [isExpanded]. The note field animates in
 * with expandVertically + fadeIn and out with shrinkVertically + fadeOut.
 *
 * Debounce: The [onNoteChange] callback fires on every keystroke. Debouncing at
 * 500ms is handled inside [BulletTreeViewModel.saveNote] — this composable does NOT
 * add its own delay.
 *
 * @param note Current note text (may be null or empty when no note exists).
 * @param isExpanded Whether the note field is currently visible.
 * @param onNoteChange Callback invoked with the new note text on every change.
 * @param modifier Optional modifier applied to the AnimatedVisibility wrapper.
 */
@Composable
fun NoteField(
    note: String?,
    isExpanded: Boolean,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 5.dp)
        ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                val currentNote = note ?: ""

                BasicTextField(
                    value = currentNote,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Placeholder shown when the note is empty
                if (currentNote.isEmpty()) {
                    Text(
                        text = "Add a note...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
