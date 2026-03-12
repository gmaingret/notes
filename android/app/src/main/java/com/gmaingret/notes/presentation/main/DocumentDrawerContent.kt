package com.gmaingret.notes.presentation.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DocumentDrawerContent(
    uiState: MainUiState,
    onDocumentClick: (com.gmaingret.notes.domain.model.Document) -> Unit,
    onCreateDocument: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSubmitRename: (String, String) -> Unit,
    onCancelRename: () -> Unit,
    onMoveLocally: (Int, Int) -> Unit,
    onCommitReorder: (String) -> Unit,
    onRetry: () -> Unit,
    onBookmarksClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // Header
        Text(
            text = "Notes",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        )
        HorizontalDivider()

        // Bookmarks entry — above document list
        NavigationDrawerItem(
            label = { Text("Bookmarks") },
            icon = { Icon(Icons.Default.Star, contentDescription = "Bookmarks") },
            selected = false,
            onClick = onBookmarksClick,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        HorizontalDivider()

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState) {
                is MainUiState.Loading -> {
                    ShimmerDrawerRows()
                }
                is MainUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            text = "Couldn't load documents",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                is MainUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            text = "No documents yet",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = onCreateDocument,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("+ Create document")
                        }
                    }
                }
                is MainUiState.Success -> {
                    val view = LocalView.current
                    val lazyListState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        onMoveLocally(from.index, to.index)
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.documents, key = { it.id }) { doc ->
                            ReorderableItem(reorderableState, key = doc.id) { isDragging ->
                                DocumentRow(
                                    document = doc,
                                    isSelected = doc.id == uiState.openDocumentId,
                                    isEditing = doc.id == uiState.inlineEditingDocId,
                                    isDragging = isDragging,
                                    dragModifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        },
                                        onDragStopped = {
                                            onCommitReorder(doc.id)
                                        }
                                    ),
                                    onTap = { onDocumentClick(doc) },
                                    onRename = { onRename(doc.id) },
                                    onDelete = { onDelete(doc.id) },
                                    onSubmitRename = { title -> onSubmitRename(doc.id, title) },
                                    onCancelRename = onCancelRename
                                )
                            }
                        }
                    }
                }
            }
        }

        // Footer
        HorizontalDivider()
        Row(modifier = Modifier.padding(8.dp)) {
            TextButton(onClick = onCreateDocument) {
                Text("+ New document")
            }
        }
    }
}

@Composable
private fun ShimmerDrawerRows() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerColor)
            )
        }
    }
}
