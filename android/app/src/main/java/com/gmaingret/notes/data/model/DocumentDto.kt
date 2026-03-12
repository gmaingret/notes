package com.gmaingret.notes.data.model

import com.google.gson.annotations.SerializedName
import com.gmaingret.notes.domain.model.Document

/**
 * API response DTO for a document.
 *
 * Maps directly to the JSON returned by the backend's document endpoints.
 * The [toDomain] mapper extracts only the fields needed by the domain layer.
 */
data class DocumentDto(
    val id: String,
    val title: String,
    val position: Double,
    @SerializedName("user_id") val userId: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("last_opened_at") val lastOpenedAt: String?
) {
    fun toDomain(): Document = Document(
        id = id,
        title = title,
        position = position
    )
}

/** Request body for POST /api/documents */
data class CreateDocumentRequest(
    val title: String
)

/** Request body for PATCH /api/documents/{id} */
data class RenameDocumentRequest(
    val title: String
)

/**
 * Request body for PATCH /api/documents/{id}/position.
 *
 * [afterId] is the ID of the document after which to insert; null means move to first position.
 */
data class ReorderDocumentRequest(
    val afterId: String?
)
