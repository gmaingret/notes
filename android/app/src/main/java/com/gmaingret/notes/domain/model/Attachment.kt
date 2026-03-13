package com.gmaingret.notes.domain.model

/**
 * Domain model for a file attachment on a bullet.
 *
 * [downloadUrl] is constructed as "https://notes.gregorymaingret.fr/api/attachments/{id}/file"
 * and used by Coil (for image rendering) or DownloadManager (for file downloads).
 * Authentication is handled by the auth-intercepted OkHttpClient wired into Coil.
 */
data class Attachment(
    val id: String,
    val bulletId: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val downloadUrl: String
)
