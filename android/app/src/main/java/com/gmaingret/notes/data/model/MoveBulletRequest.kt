package com.gmaingret.notes.data.model

/**
 * Request body for POST /api/bullets/{id}/move.
 *
 * [newParentId] is the target parent (null = move to root level).
 * [afterId] positions the bullet after an existing sibling in the target parent
 * (null = place as first child of newParentId).
 */
data class MoveBulletRequest(
    val newParentId: String?,
    val afterId: String?
)
