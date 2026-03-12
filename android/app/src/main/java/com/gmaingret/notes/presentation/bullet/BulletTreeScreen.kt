package com.gmaingret.notes.presentation.bullet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Top-level composable for the bullet tree editor.
 *
 * Collects [BulletTreeUiState] from the [BulletTreeViewModel] and renders:
 * - [BulletTreeUiState.Loading]: shimmer skeleton rows
 * - [BulletTreeUiState.Success]: LazyColumn of [BulletRow]s with toolbar
 * - [BulletTreeUiState.Error]: error message with retry button
 *
 * The LazyColumn uses [Modifier.imePadding] so content stays visible when
 * the software keyboard is shown.
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

    // Load bullets when the document changes
    LaunchedEffect(documentId) {
        viewModel.loadBullets(documentId)
    }

    Column(modifier = modifier.fillMaxSize()) {

        // Breadcrumb row — only visible when zoomed into a bullet
        if (breadcrumbPath.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Root",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp)
                )
                breadcrumbPath.forEach { crumb ->
                    Text(
                        text = " > ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = crumb.content.take(20),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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

                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .imePadding()
                        ) {
                            items(
                                items = flatList,
                                key = { it.bullet.id }
                            ) { flatBullet ->
                                BulletRow(
                                    flatBullet = flatBullet,
                                    isFocused = flatBullet.bullet.id == focusedBulletId,
                                    contentOverride = null,
                                    focusCursorEnd = state.focusCursorEnd,
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
                                    modifier = Modifier.animateItem()
                                )
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
                                    onComment = { /* TODO: open note editor */ }
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
