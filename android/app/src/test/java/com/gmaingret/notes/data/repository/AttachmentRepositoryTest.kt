package com.gmaingret.notes.data.repository

import com.gmaingret.notes.data.api.AttachmentApi
import com.gmaingret.notes.data.model.AttachmentDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [AttachmentRepositoryImpl] verifying DTO-to-domain mapping
 * and download URL construction.
 */
class AttachmentRepositoryTest {

    private lateinit var api: AttachmentApi
    private lateinit var repo: AttachmentRepositoryImpl

    @Before
    fun setUp() {
        api = mockk()
        repo = AttachmentRepositoryImpl(api)
    }

    @Test
    fun `getAttachments maps DTO fields to domain model correctly`() = runTest {
        val dto = AttachmentDto(
            id = "att-1",
            bulletId = "bullet-1",
            filename = "photo.jpg",
            mimeType = "image/jpeg",
            size = 12345L,
            createdAt = "2026-01-01"
        )
        coEvery { api.getAttachments("bullet-1") } returns listOf(dto)

        val result = repo.getAttachments("bullet-1")

        assertTrue(result.isSuccess)
        val attachment = result.getOrThrow().first()
        assertEquals("att-1", attachment.id)
        assertEquals("bullet-1", attachment.bulletId)
        assertEquals("photo.jpg", attachment.filename)
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals(12345L, attachment.size)
    }

    @Test
    fun `getAttachments constructs downloadUrl from attachment id`() = runTest {
        val dto = AttachmentDto(
            id = "att-1",
            bulletId = "bullet-1",
            filename = "photo.jpg",
            mimeType = "image/jpeg",
            size = 12345L,
            createdAt = "2026-01-01"
        )
        coEvery { api.getAttachments("bullet-1") } returns listOf(dto)

        val result = repo.getAttachments("bullet-1")

        val attachment = result.getOrThrow().first()
        assertEquals(
            "https://notes.gregorymaingret.fr/api/attachments/att-1/file",
            attachment.downloadUrl
        )
    }

    @Test
    fun `getAttachments returns Result failure when API throws`() = runTest {
        coEvery { api.getAttachments(any()) } throws IOException("network error")

        val result = repo.getAttachments("bullet-1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getAttachments returns empty list when API returns empty`() = runTest {
        coEvery { api.getAttachments("bullet-1") } returns emptyList()

        val result = repo.getAttachments("bullet-1")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }
}
