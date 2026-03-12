package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.AttachmentApi
import com.gmaingret.notes.domain.model.Attachment
import com.gmaingret.notes.domain.repository.AttachmentRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [AttachmentRepository].
 *
 * Wraps the Retrofit call in try/catch and returns Result.success / Result.failure.
 * The [downloadUrl] is constructed from the attachment id in [AttachmentDto.toDomain].
 */
@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    private val attachmentApi: AttachmentApi
) : AttachmentRepository {

    override suspend fun getAttachments(bulletId: String): Result<List<Attachment>> = try {
        val attachments = attachmentApi.getAttachments(bulletId).map { it.toDomain() }
        Result.success(attachments)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
