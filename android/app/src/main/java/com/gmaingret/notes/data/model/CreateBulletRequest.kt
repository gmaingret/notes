package com.gmaingret.notes.data.model

/**
 * Request body for POST /api/bullets.
 *
 * [documentId] is required. [parentId] sets the parent bullet (null = root level).
 * [afterId] positions the new bullet after an existing sibling (null = first among siblings).
 * [content] is optional — defaults to empty string on the server if omitted.
 */
data class CreateBulletRequest(
    val documentId: String,
    val parentId: String?,
    val afterId: String?,
    val content: String?
)
