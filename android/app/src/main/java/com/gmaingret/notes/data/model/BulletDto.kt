package com.gmaingret.notes.data.model

import com.gmaingret.notes.domain.model.Bullet

/**
 * API response DTO for a bullet, mirroring the server's Bullet JSON shape.
 *
 * The [toDomain] mapper extracts only the fields needed by the domain layer.
 * [deletedAt] is present in the server response but not used in the domain model.
 */
data class BulletDto(
    val id: String,
    val documentId: String,
    val parentId: String?,
    val content: String,
    val position: Double,
    val isComplete: Boolean,
    val isCollapsed: Boolean,
    val note: String?,
    val deletedAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val userId: String?
) {
    fun toDomain(): Bullet = Bullet(
        id = id,
        documentId = documentId,
        parentId = parentId,
        content = content,
        position = position,
        isComplete = isComplete,
        isCollapsed = isCollapsed,
        note = note
    )
}
