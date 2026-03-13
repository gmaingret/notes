package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.AttachmentDto
import okhttp3.MultipartBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Retrofit interface for attachment listing and uploading.
 *
 * GET /api/attachments/bullets/:bulletId — list all attachments for a bullet.
 * POST /api/attachments/bullets/:bulletId — upload a new file attachment.
 *
 * File download is handled via the constructed downloadUrl in [AttachmentDto],
 * not through a Retrofit call — Android DownloadManager handles downloads directly.
 */
interface AttachmentApi {
    @GET("api/attachments/bullets/{bulletId}")
    suspend fun getAttachments(@Path("bulletId") bulletId: String): List<AttachmentDto>

    @Multipart
    @POST("api/attachments/bullets/{bulletId}")
    suspend fun uploadAttachment(
        @Path("bulletId") bulletId: String,
        @Part file: MultipartBody.Part
    ): AttachmentDto

    @DELETE("api/attachments/{attachmentId}")
    suspend fun deleteAttachment(@Path("attachmentId") attachmentId: String)
}
