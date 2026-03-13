package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.repository.AttachmentRepository
import javax.inject.Inject

/**
 * Use case for deleting a single attachment by ID.
 *
 * Calls DELETE /api/attachments/:id on the server.
 * Returns Result<Unit> — success means the attachment was removed, failure carries the error.
 */
class DeleteAttachmentUseCase @Inject constructor(
    private val attachmentRepository: AttachmentRepository
) {
    suspend operator fun invoke(attachmentId: String): Result<Unit> =
        attachmentRepository.deleteAttachment(attachmentId)
}
