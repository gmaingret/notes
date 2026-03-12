package com.gmaingret.notes.domain.model

/**
 * A bullet with computed display metadata for rendering in a flat LazyColumn.
 *
 * [depth] is the nesting depth (0 = root level, 1 = one level in, etc.),
 * capped at maxDisplayDepth by FlattenTreeUseCase for visual purposes.
 * [hasChildren] indicates whether this bullet has any children in the tree,
 * used to decide whether to show the collapse/expand toggle arrow.
 */
data class FlatBullet(
    val bullet: Bullet,
    val depth: Int,
    val hasChildren: Boolean
)
