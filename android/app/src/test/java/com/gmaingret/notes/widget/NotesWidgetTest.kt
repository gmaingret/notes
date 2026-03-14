package com.gmaingret.notes.widget

import com.gmaingret.notes.domain.model.Bullet
import com.gmaingret.notes.domain.model.Document
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for NotesWidget.fetchWidgetData().
 *
 * Uses MockK to inject a mocked WidgetEntryPoint that returns controlled
 * repository results. No Android context needed — fetchWidgetData is a
 * pure Kotlin suspend function.
 */
class NotesWidgetTest {

    private lateinit var widget: NotesWidget
    private lateinit var mockEntryPoint: WidgetEntryPoint
    private val mockBulletRepo = mockk<com.gmaingret.notes.domain.repository.BulletRepository>()
    private val mockDocRepo = mockk<com.gmaingret.notes.domain.repository.DocumentRepository>()

    @Before
    fun setUp() {
        widget = NotesWidget()
        mockEntryPoint = mockk<WidgetEntryPoint>()
        io.mockk.every { mockEntryPoint.bulletRepository() } returns mockBulletRepo
        io.mockk.every { mockEntryPoint.documentRepository() } returns mockDocRepo
    }

    // -------------------------------------------------------------------------
    // NotConfigured
    // -------------------------------------------------------------------------

    @Test
    fun `null docId returns NotConfigured`() = runTest {
        val result = widget.fetchWidgetData(mockEntryPoint, docId = null)
        assertEquals(WidgetUiState.NotConfigured, result)
    }

    // -------------------------------------------------------------------------
    // Content — root-only bullet filtering
    // -------------------------------------------------------------------------

    @Test
    fun `valid docId with root bullets returns Content with only root bullets`() = runTest {
        val docId = "doc-1"
        val doc = Document(id = docId, title = "My Doc", position = 1.0)

        val rootBullet = makeBullet("b1", docId, parentId = null, "Root bullet")
        val childBullet = makeBullet("b2", docId, parentId = "b1", "Child bullet")

        coEvery { mockDocRepo.getDocuments() } returns Result.success(listOf(doc))
        coEvery { mockBulletRepo.getBullets(docId) } returns Result.success(
            listOf(rootBullet, childBullet)
        )

        val result = widget.fetchWidgetData(mockEntryPoint, docId = docId)

        assertTrue(result is WidgetUiState.Content)
        val content = result as WidgetUiState.Content
        assertEquals(docId, content.documentId)
        assertEquals("My Doc", content.documentTitle)
        assertEquals(1, content.bullets.size)
        assertEquals("b1", content.bullets[0].id)
        assertEquals("Root bullet", content.bullets[0].content)
    }

    // -------------------------------------------------------------------------
    // Empty
    // -------------------------------------------------------------------------

    @Test
    fun `valid docId with no root bullets returns Empty`() = runTest {
        val docId = "doc-2"
        val doc = Document(id = docId, title = "Empty Doc", position = 1.0)

        // Only child bullets, no root bullets
        val childBullet = makeBullet("b1", docId, parentId = "some-parent", "Child only")

        coEvery { mockDocRepo.getDocuments() } returns Result.success(listOf(doc))
        coEvery { mockBulletRepo.getBullets(docId) } returns Result.success(listOf(childBullet))

        val result = widget.fetchWidgetData(mockEntryPoint, docId = docId)
        assertEquals(WidgetUiState.Empty, result)
    }

    @Test
    fun `valid docId with empty bullet list returns Empty`() = runTest {
        val docId = "doc-3"
        val doc = Document(id = docId, title = "Empty Doc 2", position = 1.0)

        coEvery { mockDocRepo.getDocuments() } returns Result.success(listOf(doc))
        coEvery { mockBulletRepo.getBullets(docId) } returns Result.success(emptyList())

        val result = widget.fetchWidgetData(mockEntryPoint, docId = docId)
        assertEquals(WidgetUiState.Empty, result)
    }

    // -------------------------------------------------------------------------
    // DocumentNotFound
    // -------------------------------------------------------------------------

    @Test
    fun `document not in document list returns DocumentNotFound`() = runTest {
        val docId = "doc-deleted"
        val otherDoc = Document(id = "other-doc", title = "Other", position = 1.0)

        coEvery { mockDocRepo.getDocuments() } returns Result.success(listOf(otherDoc))
        coEvery { mockBulletRepo.getBullets(docId) } returns Result.success(emptyList())

        val result = widget.fetchWidgetData(mockEntryPoint, docId = docId)
        assertEquals(WidgetUiState.DocumentNotFound, result)
    }

    // -------------------------------------------------------------------------
    // SessionExpired
    // -------------------------------------------------------------------------

    @Test
    fun `repository failure with auth error returns SessionExpired`() = runTest {
        val docId = "doc-auth"
        val authException = retrofit2.HttpException(
            okhttp3.ResponseBody.create(null, "").let {
                retrofit2.Response.error<Any>(401, it)
            }
        )

        coEvery { mockDocRepo.getDocuments() } returns Result.failure(authException)

        val result = widget.fetchWidgetData(mockEntryPoint, docId = docId)
        assertEquals(WidgetUiState.SessionExpired, result)
    }

    @Test
    fun `bullet repository auth failure returns SessionExpired`() = runTest {
        val docId = "doc-auth2"
        val doc = Document(id = docId, title = "Doc", position = 1.0)
        val authException = retrofit2.HttpException(
            okhttp3.ResponseBody.create(null, "").let {
                retrofit2.Response.error<Any>(401, it)
            }
        )

        coEvery { mockDocRepo.getDocuments() } returns Result.success(listOf(doc))
        coEvery { mockBulletRepo.getBullets(docId) } returns Result.failure(authException)

        val result = widget.fetchWidgetData(mockEntryPoint, docId = docId)
        assertEquals(WidgetUiState.SessionExpired, result)
    }

    // -------------------------------------------------------------------------
    // Cap at 50 items
    // -------------------------------------------------------------------------

    @Test
    fun `bullet list is capped at 50 items`() = runTest {
        val docId = "doc-large"
        val doc = Document(id = docId, title = "Large Doc", position = 1.0)

        // 60 root bullets
        val bullets = (1..60).map { i ->
            makeBullet("b$i", docId, parentId = null, "Bullet $i")
        }

        coEvery { mockDocRepo.getDocuments() } returns Result.success(listOf(doc))
        coEvery { mockBulletRepo.getBullets(docId) } returns Result.success(bullets)

        val result = widget.fetchWidgetData(mockEntryPoint, docId = docId)

        assertTrue(result is WidgetUiState.Content)
        val content = result as WidgetUiState.Content
        assertEquals(50, content.bullets.size)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeBullet(
        id: String,
        docId: String,
        parentId: String?,
        content: String
    ) = Bullet(
        id = id,
        documentId = docId,
        parentId = parentId,
        content = content,
        position = 1.0,
        isComplete = false,
        isCollapsed = false,
        note = null
    )
}
