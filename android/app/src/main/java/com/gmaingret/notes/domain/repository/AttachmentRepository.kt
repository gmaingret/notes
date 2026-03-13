package com.gmaingret.notes.domain.repository

import android.net.Uri
import com.gmaingret.notes.domain.model.Attachment

/**
 * Repository interface for fetching and uploading attachments on a bullet.
 */
interface AttachmentRepository {
    suspend fun getAttachments(bulletId: String): Result<List<Attachment>>
    suspend fun uploadAttachment(bulletId: String, uri: Uri, filename: String, mimeType: String): Result<Attachment>
    suspend fun deleteAttachment(attachmentId: String): Result<Unit>
}
