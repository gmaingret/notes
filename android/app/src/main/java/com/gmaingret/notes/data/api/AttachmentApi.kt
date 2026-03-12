package com.gmaingret.notes.data.api

import com.gmaingret.notes.data.model.AttachmentDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for attachment listing.
 *
 * GET /api/attachments/bullets/:bulletId — list all attachments for a bullet.
 *
 * File download is handled via the constructed downloadUrl in [AttachmentDto],
 * not through a Retrofit call — Android DownloadManager handles downloads directly.
 */
interface AttachmentApi {
    @GET("api/attachments/bullets/{bulletId}")
    suspend fun getAttachments(@Path("bulletId") bulletId: String): List<AttachmentDto>
}
