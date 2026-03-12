package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /api/bullets/{id}/move.
 *
 * [newParentId] is the target parent (null = move to root level).
 * [afterId] positions the bullet after an existing sibling in the target parent
 * (null = place as first child of newParentId).
 */
data class MoveBulletRequest(
    @SerializedName("new_parent_id") val newParentId: String?,
    @SerializedName("after_id") val afterId: String?
)
