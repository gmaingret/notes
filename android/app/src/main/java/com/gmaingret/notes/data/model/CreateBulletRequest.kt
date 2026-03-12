package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /api/bullets.
 *
 * [documentId] is required. [parentId] sets the parent bullet (null = root level).
 * [afterId] positions the new bullet after an existing sibling (null = first among siblings).
 * [content] is optional — defaults to empty string on the server if omitted.
 */
data class CreateBulletRequest(
    @SerializedName("document_id") val documentId: String,
    @SerializedName("parent_id") val parentId: String?,
    @SerializedName("after_id") val afterId: String?,
    val content: String?
)
