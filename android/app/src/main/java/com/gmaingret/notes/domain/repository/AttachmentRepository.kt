package com.gmaingret.notes.domain.repository

import com.gmaingret.notes.domain.model.Attachment

/**
 * Repository interface for fetching attachments on a bullet.
 */
interface AttachmentRepository {
    suspend fun getAttachments(bulletId: String): Result<List<Attachment>>
}
