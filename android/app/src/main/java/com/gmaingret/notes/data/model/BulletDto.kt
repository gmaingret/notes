package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName
import com.gmaingret.notes.domain.model.Bullet

/**
 * API response DTO for a bullet, mirroring the server's Bullet JSON shape.
 *
 * The [toDomain] mapper extracts only the fields needed by the domain layer.
 * [deletedAt] is present in the server response but not used in the domain model.
 */
data class BulletDto(
    val id: String,
    @SerializedName("document_id") val documentId: String,
    @SerializedName("parent_id") val parentId: String?,
    val content: String,
    val position: Double,
    @SerializedName("is_complete") val isComplete: Boolean,
    @SerializedName("is_collapsed") val isCollapsed: Boolean,
    val note: String?,
    @SerializedName("deleted_at") val deletedAt: String?
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
