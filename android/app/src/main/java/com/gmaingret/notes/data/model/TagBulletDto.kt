package com.gmaingret.notes.data.model

import com.gmaingret.notes.domain.model.TagBullet

/**
 * API response DTO for a bullet returned by the tag/mention/date bullet search.
 *
 * Mirrors the server response shape: { id, content, documentId, documentTitle }.
 * This is a flat representation (no tree fields like parentId or position).
 */
data class TagBulletDto(
    val id: String,
    val content: String,
    val documentId: String,
    val documentTitle: String
) {
    fun toDomain(): TagBullet = TagBullet(
        bulletId = id,
        content = content,
        documentId = documentId,
        documentTitle = documentTitle
    )
}
