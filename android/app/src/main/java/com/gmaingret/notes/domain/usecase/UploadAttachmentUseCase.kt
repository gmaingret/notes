package com.gmaingret.notes.domain.usecase

import android.net.Uri
import com.gmaingret.notes.domain.model.Attachment
import com.gmaingret.notes.domain.repository.AttachmentRepository
import javax.inject.Inject

/**
 * Use case for uploading a file as an attachment on a bullet.
 *
 * Accepts the bullet ID, the content URI from the file picker, the filename, and MIME type.
 * Returns Result<Attachment> with the newly created attachment on success.
 */
class UploadAttachmentUseCase @Inject constructor(
    private val repo: AttachmentRepository
) {
    suspend operator fun invoke(
        bulletId: String,
        uri: Uri,
        filename: String,
        mimeType: String
    ): Result<Attachment> = repo.uploadAttachment(bulletId, uri, filename, mimeType)
}
