package com.gmaingret.notes.data.repository

import android.content.Context
import android.net.Uri
import com.gmaingret.notes.data.api.AttachmentApi
import com.gmaingret.notes.domain.model.Attachment
import com.gmaingret.notes.domain.repository.AttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [AttachmentRepository].
 *
 * Wraps the Retrofit calls in try/catch and returns Result.success / Result.failure.
 * The [downloadUrl] is constructed from the attachment id in [AttachmentDto.toDomain].
 */
@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    private val attachmentApi: AttachmentApi,
    @ApplicationContext private val context: Context
) : AttachmentRepository {

    override suspend fun getAttachments(bulletId: String): Result<List<Attachment>> = try {
        val attachments = attachmentApi.getAttachments(bulletId).map { it.toDomain() }
        Result.success(attachments)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun uploadAttachment(
        bulletId: String,
        uri: Uri,
        filename: String,
        mimeType: String
    ): Result<Attachment> = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Result.failure(Exception("Cannot read file"))
        val mediaType = mimeType.toMediaTypeOrNull()
        val requestBody = bytes.toRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", filename, requestBody)
        val dto = attachmentApi.uploadAttachment(bulletId, part)
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteAttachment(attachmentId: String): Result<Unit> = try {
        attachmentApi.deleteAttachment(attachmentId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
