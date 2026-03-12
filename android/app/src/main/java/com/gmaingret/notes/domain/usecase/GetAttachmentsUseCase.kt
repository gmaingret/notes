package com.gmaingret.notes.domain.usecase

import com.gmaingret.notes.domain.model.Attachment
import com.gmaingret.notes.domain.repository.AttachmentRepository
import javax.inject.Inject

/**
 * Use case for retrieving all attachments for a bullet.
 */
class GetAttachmentsUseCase @Inject constructor(
    private val repo: AttachmentRepository
) {
    suspend operator fun invoke(bulletId: String): Result<List<Attachment>> =
        repo.getAttachments(bulletId)
}
