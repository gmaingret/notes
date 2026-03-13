package com.gmaingret.notes.data.model

import com.gmaingret.notes.domain.model.Attachment

/**
 * API response DTO for an attachment.
 *
 * Excludes userId and storagePath — server-only fields not needed client-side.
 * The [downloadUrl] is constructed from the attachment id rather than stored server-side.
 */
data class AttachmentDto(
    val id: String,
    val bulletId: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val createdAt: String
) {
    fun toDomain(): Attachment = Attachment(
        id = id,
        bulletId = bulletId,
        filename = filename,
        mimeType = mimeType,
        size = size,
        downloadUrl = "https://notes.gregorymaingret.fr/api/attachments/$id/file"
    )
}
