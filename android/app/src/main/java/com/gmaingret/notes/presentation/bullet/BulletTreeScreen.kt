package com.gmaingret.notes.presentation.bullet

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

/**
 * Top-level composable for the bullet tree editor.
 *
 * Collects [BulletTreeUiState] from the [BulletTreeViewModel] and renders:
 * - [BulletTreeUiState.Loading]: shimmer skeleton rows
 * - [BulletTreeUiState.Success]: LazyColumn of [BulletRow]s with toolbar and drag-reorder
 * - [BulletTreeUiState.Error]: error message with retry button
 *
 * Features:
 * - Drag-reorder with long-press: uses Calvin-LL/Reorderable pattern (same as Phase 10 DocumentDrawerContent)
 * - Reparenting via horizontal drag: horizontal displacement snapped to INDENT_DP units determines
 *   target depth delta; newParentId computed by walking up from drop position
 * - Cycle prevention: moving a bullet under its own descendant is blocked with a Snackbar
 * - BreadcrumbRow: AnimatedVisibility strip shown when zoomed into a subtree
 * - Note expansion: Set<String> tracks which bullet IDs have their note field open;
 *   toolbar comment button toggles the focused bullet's note
 * - Collapse/expand animations handled by LazyColumn animateItem() modifier
 */
@Composable
fun BulletTreeScreen(
    documentId: String,
    documentTitle: String,
    modifier: Modifier = Modifier,
    viewModel: BulletTreeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zoomRootId by viewModel.zoomRootId.collectAsState()
    val breadcrumbPath by viewModel.breadcrumbPath.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Consume snackbar messages from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Load bullets when the document changes
    LaunchedEffect(documentId) {
        viewModel.loadBullets(documentId)
    }

    // Track which bullets have their note field expanded (by bullet ID)
    var expandedNoteIds by remember { mutableStateOf(setOf<String>()) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Breadcrumb row — animated, only visible when zoomed into a bullet
            AnimatedVisibility(visible = breadcrumbPath.isNotEmpty()) {
                BreadcrumbRow(
                    breadcrumbs = breadcrumbPath,
                    onCrumbClick = { bulletId -> viewModel.zoomTo(bulletId) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is BulletTreeUiState.Loading -> {
                        ShimmerBulletRows()
                    }

                    is BulletTreeUiState.Success -> {
                        // Auto-create empty bullet if document has no bullets
                        LaunchedEffect(state.flatList.isEmpty()) {
                            if (state.flatList.isEmpty()) {
                                viewModel.createBullet(null, zoomRootId)
                            }
                        }

                        val focusedBulletId = state.focusedBulletId
                        val flatList = state.flatList

                        // Compute toolbar enabled states
                        val focusedIndex = flatList.indexOfFirst { it.bullet.id == focusedBulletId }
                        val focusedBullet = if (focusedIndex >= 0) flatList[focusedIndex] else null
                        val canIndent = focusedBullet?.let { fb ->
                            // Can indent if there is a previous sibling
                            focusedIndex > 0 && flatList[focusedIndex - 1].bullet.parentId == fb.bullet.parentId
                        } ?: false
                        val canOutdent = focusedBullet?.bullet?.parentId != null
                        val canMoveUp = focusedIndex > 0
                        val canMoveDown = focusedIndex >= 0 && focusedIndex < flatList.size - 1

                        // Drag state for reparenting
                        val view = LocalView.current
                        val density = LocalDensity.current
                        val lazyListState = rememberLazyListState()
                        var dragHorizontalOffset by remember { mutableFloatStateOf(0f) }
                        var draggedBulletId by remember { mutableStateOf<String?>(null) }

                        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                            viewModel.moveBulletLocally(from.index, to.index)
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .imePadding()
                            ) {
                                items(
                                    items = flatList,
                                    key = { it.bullet.id }
                                ) { flatBullet ->
                                    ReorderableItem(reorderableState, key = flatBullet.bullet.id) { isDragging ->
                                        val indentPerLevel = 24.dp
                                        val targetDepthDelta = if (isDragging) {
                                            val indentPx = with(density) { indentPerLevel.toPx() }
                                            (dragHorizontalOffset / indentPx).roundToInt()
                                                .coerceIn(-flatBullet.depth, 1)
                                        } else 0

                                        BulletRow(
                                            flatBullet = flatBullet,
                                            isFocused = flatBullet.bullet.id == focusedBulletId,
                                            contentOverride = null,
                                            focusCursorEnd = state.focusCursorEnd,
                                            isDragging = isDragging,
                                            isNoteExpanded = flatBullet.bullet.id in expandedNoteIds,
                                            onFocusRequest = {
                                                viewModel.setFocusedBullet(flatBullet.bullet.id)
                                            },
                                            onContentChange = { content ->
                                                viewModel.updateContent(flatBullet.bullet.id, content)
                                            },
                                            onEnterWithContent = {
                                                viewModel.createBullet(
                                                    afterBulletId = flatBullet.bullet.id,
                                                    parentId = flatBullet.bullet.parentId
                                                )
                                            },
                                            onEnterOnEmpty = {
                                                viewModel.enterOnEmpty(flatBullet.bullet.id)
                                            },
                                            onBackspaceOnEmpty = {
                                                viewModel.backspaceOnEmpty(flatBullet.bullet.id)
                                            },
                                            onCollapseToggle = {
                                                viewModel.toggleCollapse(flatBullet.bullet.id)
                                            },
                                            onBulletIconTap = {
                                                viewModel.zoomTo(flatBullet.bullet.id)
                                            },
                                            onToggleNote = {
                                                val bulletId = flatBullet.bullet.id
                                                expandedNoteIds = if (bulletId in expandedNoteIds) {
                                                    expandedNoteIds - bulletId
                                                } else {
                                                    expandedNoteIds + bulletId
                                                }
                                            },
                                            onNoteChange = { note ->
                                                viewModel.saveNote(flatBullet.bullet.id, note)
                                            },
                                            modifier = Modifier
                                                .animateItem()
                                                .longPressDraggableHandle(
                                                    onDragStarted = {
                                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                        draggedBulletId = flatBullet.bullet.id
                                                        dragHorizontalOffset = 0f
                                                    },
                                                    onDragStopped = {
                                                        val bullet = flatBullet.bullet
                                                        val currentIndex = flatList.indexOfFirst { it.bullet.id == bullet.id }
                                                        val targetDepth = (flatBullet.depth + targetDepthDelta).coerceAtLeast(0)

                                                        // Find newParentId: walk up from drop position to find bullet at targetDepth-1
                                                        var newParentId: String? = null
                                                        if (targetDepth > 0 && currentIndex > 0) {
                                                            for (i in (currentIndex - 1) downTo 0) {
                                                                if (flatList[i].depth == targetDepth - 1) {
                                                                    newParentId = flatList[i].bullet.id
                                                                    break
                                                                }
                                                            }
                                                        }

                                                        // Cycle prevention: newParentId must NOT be a descendant of the dragged bullet
                                                        val isDescendant = if (newParentId != null) {
                                                            // Descendants are bullets between dragged index and the next at same-or-lower depth
                                                            val descendantIds = mutableSetOf<String>()
                                                            if (currentIndex >= 0) {
                                                                val draggedDepth = flatBullet.depth
                                                                for (i in (currentIndex + 1) until flatList.size) {
                                                                    if (flatList[i].depth > draggedDepth) {
                                                                        descendantIds.add(flatList[i].bullet.id)
                                                                    } else {
                                                                        break
                                                                    }
                                                                }
                                                            }
                                                            newParentId in descendantIds
                                                        } else false

                                                        if (isDescendant) {
                                                            // Reset: show snackbar via ViewModel — launch in view scope not available here,
                                                            // so we use the snackbar channel approach
                                                            viewModel.showSnackbar("Cannot move bullet under its own child")
                                                        } else {
                                                            // Find afterId: previous sibling at same depth under same parent
                                                            var afterId: String? = null
                                                            if (currentIndex > 0) {
                                                                for (i in (currentIndex - 1) downTo 0) {
                                                                    val candidate = flatList[i]
                                                                    if (candidate.bullet.parentId == newParentId && candidate.depth == targetDepth) {
                                                                        afterId = candidate.bullet.id
                                                                        break
                                                                    }
                                                                    if (candidate.depth < targetDepth) break
                                                                }
                                                            }

                                                            viewModel.commitBulletMove(
                                                                bulletId = bullet.id,
                                                                newParentId = newParentId,
                                                                afterId = afterId
                                                            )
                                                        }

                                                        draggedBulletId = null
                                                        dragHorizontalOffset = 0f
                                                    }
                                                )
                                                .pointerInput(flatBullet.bullet.id) {
                                                    detectDragGestures { _, dragAmount ->
                                                        if (draggedBulletId == flatBullet.bullet.id) {
                                                            dragHorizontalOffset += dragAmount.x
                                                        }
                                                    }
                                                }
                                        )
                                    }
                                }
                            }

                            // Editing toolbar — animated, shown only when a bullet is focused
                            AnimatedVisibility(
                                visible = focusedBulletId != null,
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it }
                            ) {
                                focusedBulletId?.let { bulletId ->
                                    BulletEditingToolbar(
                                        bulletId = bulletId,
                                        canIndent = canIndent,
                                        canOutdent = canOutdent,
                                        canMoveUp = canMoveUp,
                                        canMoveDown = canMoveDown,
                                        canUndo = canUndo,
                                        canRedo = canRedo,
                                        hasNote = focusedBullet?.bullet?.note?.isNotEmpty() == true,
                                        onIndent = { viewModel.indentBullet(bulletId) },
                                        onOutdent = { viewModel.outdentBullet(bulletId) },
                                        onMoveUp = { viewModel.moveUp(bulletId) },
                                        onMoveDown = { viewModel.moveDown(bulletId) },
                                        onUndo = { viewModel.undo() },
                                        onRedo = { viewModel.redo() },
                                        onComment = {
                                            // Toggle note expansion for focused bullet
                                            expandedNoteIds = if (bulletId in expandedNoteIds) {
                                                expandedNoteIds - bulletId
                                            } else {
                                                expandedNoteIds + bulletId
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is BulletTreeUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(
                                text = "Couldn't load bullets",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.loadBullets(documentId) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        // Snackbar overlay
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Shimmer placeholder shown while bullets are loading.
 *
 * Renders 5 rows at varying indent levels with animated alpha transition
 * to give a skeleton loading effect. Follows the same shimmer pattern
 * used in Phase 10 DocumentDrawerContent.
 */
@Composable
private fun ShimmerBulletRows() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    val indentLevels = listOf(0, 1, 2, 1, 0) // varying depths for visual interest

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        indentLevels.forEach { indent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (indent * 24).dp,
                        top = 6.dp,
                        bottom = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bullet circle placeholder
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Text line placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f + indent * 0.1f)
                        .height(14.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.5f),
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
        }
    }
}
